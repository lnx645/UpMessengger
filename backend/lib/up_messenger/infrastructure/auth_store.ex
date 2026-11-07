defmodule UpMessenger.Infrastructure.AuthStore do
  @moduledoc "ETS storage hemat RAM + DETS persist: OTP (TTL 5 menit) + User (phone -> pin). JWT RS256 access 15m + refresh 30d, revoke list, audit log."
  use GenServer
  alias UpMessenger.Domain.Auth
  alias UpMessenger.Domain.Jwt
  alias UpMessenger.Infrastructure.Persistence
  alias UpMessenger.Infrastructure.AuditLog
  @otp_table :upme_otp
  @user_table :upme_users
  @rate_table :upme_rate
  @attempt_table :upme_otp_attempts
  @otp_ttl 300_000
  @rate_limit 5
  @rate_window 600_000
  @max_attempts 7
  # WA-style escalating lockout: 3x->1 jam, 5x->6 jam, 7x->24 jam
  @lock_after_3 3_600_000
  @lock_after_5 21_600_000
  @lock_after_7 86_400_000

  def start_link(_), do: GenServer.start_link(__MODULE__, %{}, name: __MODULE__)

  def request_otp(phone, meta \\ %{}) do
    norm = Auth.normalize_phone(phone)
    ip = Map.get(meta, :ip)
    if Auth.valid_phone?(norm) do
      now = System.system_time(:millisecond)
      count = get_rate_count(norm, now)
      if count >= @rate_limit do
        AuditLog.log(:request_otp, %{phone: norm, ip: ip, result: :rate_limited})
        {:error, :rate_limited}
      else
        otp = Auth.generate_otp()
        :ets.insert(@otp_table, {norm, otp, now})
        :ets.delete(@attempt_table, norm)
        put_rate(norm, now)
        try do
          UpMessenger.Infrastructure.SmsGate.send_otp_async(norm, otp)
        rescue _ -> :ok end
        AuditLog.log(:request_otp, %{phone: norm, ip: ip, result: :ok})
        {:ok, otp}
      end
    else
      AuditLog.log(:request_otp, %{phone: phone, ip: ip, result: :invalid_phone})
      {:error, :invalid_phone}
    end
  end

  def verify_otp(phone, otp, meta \\ %{}) do
    norm = Auth.normalize_phone(phone)
    ip = Map.get(meta, :ip)
    now = System.system_time(:millisecond)
    case check_escalating_lock(norm, now) do
      {:locked, retry_after} ->
        AuditLog.log(:verify_otp, %{phone: norm, ip: ip, result: :too_many_attempts, retry_after: retry_after})
        {:error, {:too_many_attempts, retry_after}}
      :ok ->
        case :ets.lookup(@otp_table, norm) do
        [{^norm, stored, ts}] ->
          expired = System.system_time(:millisecond) - ts > @otp_ttl
          cond do
            expired ->
              :ets.delete(@otp_table, norm)
              AuditLog.log(:verify_otp, %{phone: norm, ip: ip, result: :expired})
              {:error, :expired}
            stored != otp ->
              inc_attempts(norm)
              AuditLog.log(:verify_otp, %{phone: norm, ip: ip, result: :invalid_otp})
              {:error, :invalid_otp}
            true ->
              :ets.delete(@otp_table, norm)
              :ets.delete(@attempt_table, norm)
              result = get_or_create_user(norm)
              case result do
                {:need_two_fa, _} -> AuditLog.log(:verify_otp, %{phone: norm, ip: ip, result: :need_two_fa})
                {:ok, _} -> AuditLog.log(:verify_otp, %{phone: norm, ip: ip, result: :ok})
                _ -> :ok
              end
              result
          end
        [] ->
          AuditLog.log(:verify_otp, %{phone: norm, ip: ip, result: :not_found})
          {:error, :not_found}
      end
    end
  end

  defp get_or_create_user(phone) do
    case :ets.lookup(@user_table, phone) do
      [{^phone, %Auth{two_fa_enabled: true} = user}] -> {:need_two_fa, user}
      [{^phone, user}] -> {:ok, refresh_jwt(user)}
      [] ->
        pin = Auth.generate_pin()
        name = "User #{String.slice(pin, 0, 4)}"
        pair = Jwt.generate_pair(%{phone: phone, pin: pin, name: name})
        {pub, _priv} = :crypto.generate_key(:ecdh, :x25519)
        user = %Auth{phone: phone, pin: pin, name: name, token: pair.access_token, refresh_token: pair.refresh_token, created_at: System.system_time(:millisecond), two_fa_enabled: false, identity_pub: Base.encode64(pub)}
        :ets.insert(@user_table, {phone, user})
        persist_user(phone, user)
        {:ok, user}
    end
  end

  defp refresh_jwt(%Auth{} = user) do
    pair = Jwt.generate_pair(%{phone: user.phone, pin: user.pin, name: user.name})
    updated = %{user | token: pair.access_token, refresh_token: pair.refresh_token}
    :ets.insert(@user_table, {user.phone, updated})
    persist_user(user.phone, updated)
    updated
  end

  defp persist_user(phone, user) do
    try do
      Persistence.persist_user!(phone, user)
    rescue _ -> :ok end
    # async fallback
    Task.start(fn ->
      try do
        Persistence.persist_user!(phone, user)
      rescue _ -> :ok end
    end)
    :ok
  end

  @doc "Refresh access token pakai refresh_token (30d). Revoke old jti optional."
  def refresh_access(refresh_token) when is_binary(refresh_token) do
    with {:ok, payload} <- Jwt.verify_refresh(refresh_token),
         phone when is_binary(phone) <- payload["sub"],
         {:ok, user} <- find_by_phone(phone),
         true <- payload["pin"] == user.pin,
         true <- refresh_token == user.refresh_token do
      # revoke old access jti if needed? revoke refresh jti setelah rotation
      # generate new pair (rotation)
      old_jti = payload["jti"]
      old_exp = payload["exp"]
      if is_binary(old_jti), do: Jwt.revoke_with_exp(old_jti, old_exp)
      pair = Jwt.generate_pair(%{phone: user.phone, pin: user.pin, name: user.name})
      updated = %{user | token: pair.access_token, refresh_token: pair.refresh_token}
      :ets.insert(@user_table, {user.phone, updated})
      persist_user(user.phone, updated)
      AuditLog.log(:refresh_token, %{phone: phone, result: :ok, jti: old_jti})
      {:ok, updated}
    else
      {:error, :revoked} -> {:error, :revoked}
      _ -> {:error, :invalid_token}
    end
  end
  def refresh_access(_), do: {:error, :invalid_token}

  @doc "Logout: revoke access + refresh jti."
  def logout(phone, token) when is_binary(phone) and is_binary(token) do
    norm = Auth.normalize_phone(phone)
    case Jwt.verify(token) do
      {:ok, payload} ->
        jti = payload["jti"]
        exp = payload["exp"] || System.system_time(:second) + 900
        if is_binary(jti), do: Jwt.revoke_with_exp(jti, exp)
        # also revoke stored refresh if matches
        case :ets.lookup(@user_table, norm) do
          [{_, user}] when is_binary(user.refresh_token) ->
            case Jwt.verify(user.refresh_token) do
              {:ok, rp} ->
                rjti = rp["jti"]; rexp = rp["exp"] || System.system_time(:second) + 2_592_000
                if is_binary(rjti), do: Jwt.revoke_with_exp(rjti, rexp)
              _ -> :ok
            end
          _ -> :ok
        end
        AuditLog.log(:logout, %{phone: norm, result: :ok, jti: jti})
        :ok
      _ -> {:error, :invalid_token}
    end
  end
  def logout(_, _), do: {:error, :invalid_token}

  def verify_two_fa(phone, pin, meta \\ %{}) do
    norm = Auth.normalize_phone(phone)
    ip = Map.get(meta, :ip)
    result =
      case :ets.lookup(@user_table, norm) do
        [{^norm, %Auth{two_fa_enabled: true, two_fa_hash: hash, two_fa_salt: salt} = user}] ->
          if Auth.verify_two_fa(pin, hash, salt) do
            {:ok, refresh_jwt(user)}
          else
            {:error, :invalid_two_fa}
          end
        [{^norm, %Auth{two_fa_enabled: false}}] -> {:error, :not_enabled}
        [] -> {:error, :not_found}
      end
    AuditLog.log(:verify_two_fa, %{phone: norm, ip: ip, result: elem(result, 0)})
    result
  end

  def set_two_fa(phone, pin, meta \\ %{}) do
    norm = Auth.normalize_phone(phone)
    ip = Map.get(meta, :ip)
    if !Auth.valid_two_fa_pin?(pin) do
      AuditLog.log(:set_two_fa, %{phone: norm, ip: ip, result: :invalid_pin})
      {:error, :invalid_pin}
    else
      result =
        case :ets.lookup(@user_table, norm) do
          [{^norm, user}] ->
            {hash, salt} = Auth.hash_two_fa(pin)
            updated = %{user | two_fa_hash: hash, two_fa_salt: salt, two_fa_enabled: true}
            :ets.insert(@user_table, {norm, updated})
            persist_user(norm, updated)
            {:ok, updated}
          [] -> {:error, :not_found}
        end
      AuditLog.log(:set_two_fa, %{phone: norm, ip: ip, result: elem(result, 0)})
      result
    end
  end

  def disable_two_fa(phone, pin, meta \\ %{}) do
    norm = Auth.normalize_phone(phone)
    ip = Map.get(meta, :ip)
    result =
      case :ets.lookup(@user_table, norm) do
        [{^norm, %Auth{two_fa_hash: hash, two_fa_salt: salt} = user}] ->
          if Auth.verify_two_fa(pin, hash, salt) do
            updated = %{user | two_fa_enabled: false, two_fa_hash: nil, two_fa_salt: nil}
            :ets.insert(@user_table, {norm, updated})
            persist_user(norm, updated)
            {:ok, updated}
          else
            {:error, :invalid_two_fa}
          end
        [] -> {:error, :not_found}
      end
    AuditLog.log(:disable_two_fa, %{phone: norm, ip: ip, result: elem(result, 0)})
    result
  end

  def find_by_phone(phone) do
    norm = Auth.normalize_phone(phone)
    case :ets.lookup(@user_table, norm) do
      [{^norm, user}] -> {:ok, user}
      [] -> {:error, :not_found}
    end
  end

  @doc "Verifikasi JWT stateless (efisien, tanpa DB scan). Cek signature + exp + revoked, lalu cek phone masih terdaftar."
  def find_by_token(token) do
    with {:ok, payload} <- Jwt.verify(token),
         phone when is_binary(phone) <- payload["sub"],
         {:ok, user} <- find_by_phone(phone) do
      if payload["pin"] == user.pin, do: {:ok, %{user | token: token}}, else: {:error, :invalid_token}
    else
      {:error, :revoked} -> {:error, :revoked}
      _ -> {:error, :not_found}
    end
  end

  defp get_rate_count(phone, now) do
    case :ets.lookup(@rate_table, phone) do
      [{^phone, timestamps}] ->
        fresh = Enum.filter(timestamps, fn t -> now - t < @rate_window end)
        if length(fresh) != length(timestamps), do: :ets.insert(@rate_table, {phone, fresh})
        length(fresh)
      [] -> 0
    end
  end

  defp put_rate(phone, now) do
    timestamps = case :ets.lookup(@rate_table, phone) do
      [{^phone, ts}] -> Enum.filter(ts, fn t -> now - t < @rate_window end)
      [] -> []
    end
    :ets.insert(@rate_table, {phone, timestamps ++ [now]})
  end

  defp get_attempts(phone) do
    case :ets.lookup(@attempt_table, phone) do
      [{^phone, c, _lock}] -> c
      [{^phone, c}] -> c
      [] -> 0
    end
  end

  defp get_attempt_record(phone) do
    case :ets.lookup(@attempt_table, phone) do
      [{^phone, c, lock_until}] -> {c, lock_until}
      [{^phone, c}] -> {c, 0}
      [] -> {0, 0}
    end
  end

  defp escalating_lock_ms(count) do
    cond do
      count >= 7 -> @lock_after_7
      count >= 5 -> @lock_after_5
      count >= 3 -> @lock_after_3
      true -> 0
    end
  end

  defp check_escalating_lock(phone, now) do
    {count, lock_until} = get_attempt_record(phone)
    if lock_until > now do
      {:locked, lock_until - now}
    else
      # if lock expired but count still high, keep count but clear stale lock
      if lock_until != 0 and count > 0 do
        :ets.insert(@attempt_table, {phone, count, 0})
      end
      :ok
    end
  end

  defp inc_attempts(phone) do
    {count, _} = get_attempt_record(phone)
    c = count + 1
    lock_ms = escalating_lock_ms(c)
    lock_until = if lock_ms > 0, do: System.system_time(:millisecond) + lock_ms, else: 0
    :ets.insert(@attempt_table, {phone, c, lock_until})
    c
  end

  @impl true
  def init(_) do
    :ets.new(@otp_table, [:named_table, :set, :public, read_concurrency: true, write_concurrency: true])
    :ets.new(@user_table, [:named_table, :set, :public, read_concurrency: true, write_concurrency: true])
    :ets.new(@rate_table, [:named_table, :set, :public, read_concurrency: true, write_concurrency: true])
    :ets.new(@attempt_table, [:named_table, :set, :public, read_concurrency: true, write_concurrency: true])
    UpMessenger.Domain.Jwt.ensure_keys!()
    # load persisted users (DETS -> ETS)
    try do
      Persistence.load_users!()
    rescue _ -> :ok end
    {:ok, %{}}
  end
end
