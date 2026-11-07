defmodule UpMessenger.Infrastructure.Persistence do
  @moduledoc """
  Persistensi prod berbasis DETS + file JSON.
  - JWT keys -> priv/jwt_keys.json (kid, priv DER base64, pub DER base64)
  - Users -> priv/users.dets (DETS table :upme_users_dets)
  - Revoked JTIs handled by RevocationStore, but helpers here.
  Low RAM, tidak butuh Ecto/SQLite.
  """
  require Logger

  @priv_dir "priv"
  @jwt_path "priv/jwt_keys.json"
  @users_dets_path "priv/users.dets"
  @users_dets_table :upme_users_dets

  @doc "Pastikan direktori priv ada."
  def ensure_priv! do
    File.mkdir_p!(@priv_dir)
  end

  # ── JWT keys JSON ──────────────────────────────────────────────
  @doc "Tulis semua kid->key ke priv/jwt_keys.json"
  def persist_jwt_keys! do
    ensure_priv!()
    case :ets.whereis(:upme_jwt_keys) do
      :undefined -> :ok
      _ ->
        entries = :ets.tab2list(:upme_jwt_keys)
        # entries contains {kid, priv, pub} and {:current, kid}
        current =
          case :ets.lookup(:upme_jwt_keys, :current) do
            [{_, kid}] -> kid
            [] -> nil
          end

        keys =
          entries
          |> Enum.filter(fn
            {k, _, _} when is_binary(k) -> true
            _ -> false
          end)
          |> Enum.map(fn {kid, priv, pub} ->
            # Encode RSA keys as PEM -> base64 DER for JSON
            priv_der = :public_key.der_encode(:RSAPrivateKey, priv)
            pub_der = :public_key.der_encode(:RSAPublicKey, pub)
            %{
              "kid" => kid,
              "priv_der_b64" => Base.encode64(priv_der),
              "pub_der_b64" => Base.encode64(pub_der)
            }
          end)

        payload = %{"current_kid" => current, "keys" => keys}
        json = Jason.encode!(payload, pretty: true)
        # atomic write + chmod 600 (HIGH: private key tidak world-readable)
        tmp = @jwt_path <> ".tmp"
        File.write!(tmp, json)
        File.chmod!(tmp, 0o600)
        File.rename(tmp, @jwt_path)
        File.chmod!(@jwt_path, 0o600)
        :ok
    end
  rescue
    e -> Logger.warning("persist_jwt_keys failed: #{inspect(e)}"); :ok
  end

  @doc "Load dari priv/jwt_keys.json ke ETS :upme_jwt_keys. Return :ok | :no_file"
  def load_jwt_keys! do
    ensure_priv!()
    case File.read(@jwt_path) do
      {:ok, content} ->
        case Jason.decode(content) do
          {:ok, %{"keys" => keys, "current_kid" => current_kid}} ->
            ensure_jwt_table!()
            Enum.each(keys, fn %{"kid" => kid, "priv_der_b64" => priv_b64, "pub_der_b64" => pub_b64} ->
              with {:ok, priv_der} <- Base.decode64(priv_b64),
                   {:ok, pub_der} <- Base.decode64(pub_b64),
                   priv <- :public_key.der_decode(:RSAPrivateKey, priv_der),
                   pub <- :public_key.der_decode(:RSAPublicKey, pub_der) do
                :ets.insert(:upme_jwt_keys, {kid, priv, pub})
              else
                _ -> :ok
              end
            end)
            if is_binary(current_kid), do: :ets.insert(:upme_jwt_keys, {:current, current_kid})
            :ok
          _ -> :error
        end
      {:error, :enoent} -> :no_file
      {:error, reason} -> Logger.warning("load_jwt_keys read error: #{inspect(reason)}"); :error
    end
  end

  defp ensure_jwt_table! do
    case :ets.whereis(:upme_jwt_keys) do
      :undefined -> :ets.new(:upme_jwt_keys, [:named_table, :set, :public, read_concurrency: true])
      _ -> :ok
    end
  end

  # ── Users DETS ─────────────────────────────────────────────────
  @doc "Buka DETS users, load ke ETS :upme_users"
  def load_users! do
    ensure_priv!()
    # ensure ETS exists
    ensure_users_table!()
    dets_file = String.to_charlist(@users_dets_path)
    case :dets.open_file(@users_dets_table, [file: dets_file, type: :set]) do
      {:ok, _} ->
        # copy all to ETS
        case :dets.match(@users_dets_table, :"$1") do
          list when is_list(list) ->
            Enum.each(list, fn [{phone, user_map}] ->
              user = map_to_user(user_map)
              :ets.insert(:upme_users, {phone, user})
            end)
          _ -> :ok
        end
        :dets.close(@users_dets_table)
        :ok
      {:error, reason} ->
        Logger.warning("dets open users failed: #{inspect(reason)}")
        :ok
    end
  rescue
    e -> Logger.warning("load_users failed: #{inspect(e)}"); :ok
  end

  @doc "Persist satu user ke DETS (write-through)"
  def persist_user!(phone, %UpMessenger.Domain.Auth{} = user) do
    ensure_priv!()
    dets_file = String.to_charlist(@users_dets_path)
    # open, insert, close - DETS is disk backed, sync
    case :dets.open_file(@users_dets_table, [file: dets_file, type: :set]) do
      {:ok, _} ->
        :dets.insert(@users_dets_table, {phone, user_to_map(user)})
        :dets.sync(@users_dets_table)
        :dets.close(@users_dets_table)
        :ok
      {:error, reason} ->
        Logger.warning("persist_user dets open failed: #{inspect(reason)}")
        :ok
    end
  rescue
    e -> Logger.warning("persist_user failed: #{inspect(e)}"); :ok
  end

  @doc "Hapus user dari DETS"
  def delete_user!(phone) do
    dets_file = String.to_charlist(@users_dets_path)
    case :dets.open_file(@users_dets_table, [file: dets_file, type: :set]) do
      {:ok, _} -> :dets.delete(@users_dets_table, phone); :dets.close(@users_dets_table); :ok
      _ -> :ok
    end
  rescue
    _ -> :ok
  end

  defp ensure_users_table! do
    case :ets.whereis(:upme_users) do
      :undefined -> :ets.new(:upme_users, [:named_table, :set, :public, read_concurrency: true, write_concurrency: true])
      _ -> :ok
    end
  end

  defp user_to_map(%UpMessenger.Domain.Auth{} = u) do
    %{
      "phone" => u.phone,
      "pin" => u.pin,
      "name" => u.name,
      "token" => u.token,
      "refresh_token" => Map.get(u, :refresh_token),
      "created_at" => u.created_at,
      "two_fa_hash" => u.two_fa_hash,
      "two_fa_salt" => u.two_fa_salt,
      "two_fa_enabled" => u.two_fa_enabled,
      "identity_pub" => u.identity_pub
    }
  end

  defp map_to_user(map) when is_map(map) do
    %UpMessenger.Domain.Auth{
      phone: map["phone"],
      pin: map["pin"],
      name: map["name"],
      token: map["token"],
      refresh_token: map["refresh_token"],
      created_at: map["created_at"],
      two_fa_hash: map["two_fa_hash"],
      two_fa_salt: map["two_fa_salt"],
      two_fa_enabled: map["two_fa_enabled"] || false,
      identity_pub: map["identity_pub"]
    }
  end
end
