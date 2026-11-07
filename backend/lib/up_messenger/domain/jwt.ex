defmodule UpMessenger.Domain.Jwt do
  @moduledoc "JWT RS256 sekelas WhatsApp: RSA 2048 + kid + exp access 15m / refresh 30d + revoke list. Persist ke priv/jwt_keys.json."
  @access_exp 15 * 60
  @refresh_exp 30 * 24 * 3600
  @exp_seconds 30 * 24 * 3600
  @keys_table :upme_jwt_keys
  @current_kid :current

  def access_exp, do: @access_exp
  def refresh_exp, do: @refresh_exp

  def ensure_keys! do
    case :ets.whereis(@keys_table) do
      :undefined ->
        :ets.new(@keys_table, [:named_table, :set, :public, read_concurrency: true])
        # coba load dari file dulu
        case UpMessenger.Infrastructure.Persistence.load_jwt_keys!() do
          :ok ->
            if :ets.lookup(@keys_table, @current_kid) == [], do: generate_key("1")
            :ok
          :no_file ->
            generate_key("1")
          _ ->
            if :ets.lookup(@keys_table, @current_kid) == [], do: generate_key("1")
            :ok
        end
      _ ->
        if :ets.lookup(@keys_table, @current_kid) == [], do: generate_key("1")
        :ok
    end
  end

  defp generate_key(kid) do
    priv = :public_key.generate_key({:rsa, 2048, 65537})
    {:RSAPrivateKey, _, mod, pub_exp, _, _, _, _, _, _, _} = priv
    pub = {:RSAPublicKey, mod, pub_exp}
    :ets.insert(@keys_table, {kid, priv, pub})
    :ets.insert(@keys_table, {@current_kid, kid})
    # persist async, jangan block
    try do
      UpMessenger.Infrastructure.Persistence.persist_jwt_keys!()
    rescue _ -> :ok end
    {kid, priv, pub}
  end

  @spec secret() :: binary()
  def secret do
    System.get_env("UPME_JWT_SECRET") || "dev_secret_change_in_prod_32bytes_min_32"
  end

  defp current_kid do
    ensure_keys!()
    case :ets.lookup(@keys_table, @current_kid) do
      [{_, kid}] -> kid
      [] -> "1"
    end
  end

  defp current_priv do
    kid = current_kid()
    case :ets.lookup(@keys_table, kid) do
      [{_, priv, _pub}] -> {kid, priv}
      [] -> generate_key(kid); current_priv()
    end
  end

  defp get_pub(kid) do
    case :ets.lookup(@keys_table, kid) do
      [{_, _priv, pub}] -> {:ok, pub}
      [] -> :error
    end
  end

  @spec generate(map()) :: binary()
  def generate(%{phone: phone, pin: pin, name: name}) do
    generate_access(%{phone: phone, pin: pin, name: name})
  end

  @spec generate_access(map()) :: binary()
  def generate_access(%{phone: phone, pin: pin, name: name}) do
    ensure_keys!()
    {kid, priv} = current_priv()
    header = %{"alg" => "RS256", "typ" => "JWT", "kid" => kid}
    now = System.system_time(:second)
    jti = :crypto.strong_rand_bytes(12) |> Base.url_encode64(padding: false)
    payload = %{"sub" => phone, "pin" => pin, "name" => name, "iat" => now, "exp" => now + @access_exp, "jti" => jti, "type" => "access"}
    sign(header, payload, priv)
  end

  @spec generate_refresh(map()) :: binary()
  def generate_refresh(%{phone: phone, pin: pin, name: name}) do
    ensure_keys!()
    {kid, priv} = current_priv()
    header = %{"alg" => "RS256", "typ" => "JWT", "kid" => kid}
    now = System.system_time(:second)
    jti = :crypto.strong_rand_bytes(12) |> Base.url_encode64(padding: false)
    payload = %{"sub" => phone, "pin" => pin, "name" => name, "iat" => now, "exp" => now + @refresh_exp, "jti" => jti, "type" => "refresh"}
    sign(header, payload, priv)
  end

  @doc "Generate pasangan access + refresh."
  @spec generate_pair(map()) :: %{access_token: binary(), refresh_token: binary()}
  def generate_pair(%{phone: _} = attrs) do
    %{access_token: generate_access(attrs), refresh_token: generate_refresh(attrs)}
  end

  defp sign(header, payload, priv) do
    h64 = header |> Jason.encode!() |> Base.url_encode64(padding: false)
    p64 = payload |> Jason.encode!() |> Base.url_encode64(padding: false)
    signing_input = h64 <> "." <> p64
    sig = :public_key.sign(signing_input, :sha256, priv) |> Base.url_encode64(padding: false)
    signing_input <> "." <> sig
  end

  @spec generate_hs256(map()) :: binary()
  def generate_hs256(%{phone: phone, pin: pin, name: name}) do
    header = %{"alg" => "HS256", "typ" => "JWT"}
    now = System.system_time(:second)
    jti = :crypto.strong_rand_bytes(12) |> Base.url_encode64(padding: false)
    payload = %{"sub" => phone, "pin" => pin, "name" => name, "iat" => now, "exp" => now + @exp_seconds, "jti" => jti, "type" => "access"}
    h64 = header |> Jason.encode!() |> Base.url_encode64(padding: false)
    p64 = payload |> Jason.encode!() |> Base.url_encode64(padding: false)
    signing_input = h64 <> "." <> p64
    sig = :crypto.mac(:hmac, :sha256, secret(), signing_input) |> Base.url_encode64(padding: false)
    signing_input <> "." <> sig
  end

  @spec verify(binary()) :: {:ok, map()} | {:error, atom()}
  def verify(token) when is_binary(token) do
    ensure_keys!()
    with [h64, p64, sig64] <- String.split(token, "."),
         {:ok, header_json} <- Base.url_decode64(h64, padding: false),
         {:ok, payload_json} <- Base.url_decode64(p64, padding: false),
         {:ok, header} <- Jason.decode(header_json),
         {:ok, payload} <- Jason.decode(payload_json),
         exp when is_integer(exp) <- payload["exp"],
         true <- System.system_time(:second) < exp,
         false <- revoked?(payload["jti"]) do
      case header["alg"] do
        "RS256" ->
          kid = header["kid"] || current_kid()
          with {:ok, pub} <- get_pub(kid),
               {:ok, sig} <- Base.url_decode64(sig64, padding: false),
               true <- :public_key.verify(h64 <> "." <> p64, :sha256, sig, pub) do
            {:ok, payload}
          else
            _ -> {:error, :invalid_token}
          end
        "HS256" ->
          signing_input = h64 <> "." <> p64
          expected = :crypto.mac(:hmac, :sha256, secret(), signing_input) |> Base.url_encode64(padding: false)
          if secure_compare(expected, sig64), do: {:ok, payload}, else: {:error, :invalid_token}
        _ -> {:error, :invalid_token}
      end
    else
      true -> {:error, :revoked}
      _ -> {:error, :invalid_token}
    end
  end
  def verify(_), do: {:error, :invalid_token}

  @doc "Verify khusus refresh token (type == refresh)."
  @spec verify_refresh(binary()) :: {:ok, map()} | {:error, atom()}
  def verify_refresh(token) when is_binary(token) do
    case verify(token) do
      {:ok, %{"type" => "refresh"} = p} -> {:ok, p}
      {:ok, %{"type" => t}} when t != "refresh" -> {:error, :invalid_token}
      {:ok, payload} when not is_map_key(payload, "type") ->
        # legacy tanpa type dianggap bukan refresh
        {:error, :invalid_token}
      other -> other
    end
  end
  def verify_refresh(_), do: {:error, :invalid_token}

  @spec rotate() :: {:ok, binary()}
  def rotate do
    ensure_keys!()
    new_kid = Integer.to_string(System.system_time(:millisecond))
    generate_key(new_kid)
    {:ok, new_kid}
  end

  @doc "Revoke jti sampai exp. Dipanggil saat logout atau refresh rotation."
  @spec revoke(binary()) :: :ok
  def revoke(jti) when is_binary(jti) and byte_size(jti) > 0 do
    # cari exp dari? jika tidak tau, pakai now + refresh_exp
    exp = System.system_time(:second) + @refresh_exp
    revoke_with_exp(jti, exp)
  end
  def revoke(_), do: :ok

  @doc "Revoke dengan exp eksplisit (agar prune tepat)."
  @spec revoke_with_exp(binary(), integer()) :: :ok
  def revoke_with_exp(jti, exp) when is_binary(jti) do
    try do
      UpMessenger.Infrastructure.RevocationStore.revoke(jti, exp)
    rescue _ -> :ok catch _ -> :ok end
    :ok
  end

  @doc "Cek apakah jti sudah revoked."
  @spec revoked?(binary() | nil) :: boolean()
  def revoked?(nil), do: false
  def revoked?(jti) when is_binary(jti) do
    try do
      UpMessenger.Infrastructure.RevocationStore.revoked?(jti)
    rescue _ -> false catch _ -> false end
  end
  def revoked?(_), do: false

  defp secure_compare(a, b) when byte_size(a) != byte_size(b), do: false
  defp secure_compare(a, b), do: :crypto.hash(:sha256, a) == :crypto.hash(:sha256, b) and a == b
end
