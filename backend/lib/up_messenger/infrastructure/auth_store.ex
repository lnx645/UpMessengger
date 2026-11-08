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
  @pending_table :upme_pending
  @blocked_table :upme_blocked
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
        user = %Auth{
          phone: phone,
          pin: pin,
          name: name,
          token: pair.access_token,
          refresh_token: pair.refresh_token,
          created_at: System.system_time(:millisecond),
          two_fa_enabled: false,
          identity_pub: Base.encode64(pub),
          auto_approve: true,
          who_can_add: true,
          block_invite: false
        }
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

  @doc "Ambil privacy settings untuk user."
  def get_privacy(phone) do
    case find_by_phone(phone) do
      {:ok, user} ->
        {:ok,
         %{
           auto_approve: Map.get(user, :auto_approve, true),
           who_can_add: Map.get(user, :who_can_add, true),
           block_invite: Map.get(user, :block_invite, false)
         }}

      err -> err
    end
  end

  @doc "Update privacy settings (DETS persist). Field autoApprove sync ke DETS."
  def set_privacy(phone, attrs, meta \\ %{}) when is_map(attrs) do
    norm = Auth.normalize_phone(phone)
    ip = Map.get(meta, :ip)

    result =
      case :ets.lookup(@user_table, norm) do
        [{^norm, user}] ->
          auto_approve =
            case Map.get(attrs, "auto_approve") do
              v when is_boolean(v) -> v
              _ -> Map.get(user, :auto_approve, true)
            end

          who_can_add =
            case Map.get(attrs, "who_can_add") do
              v when is_boolean(v) -> v
              _ -> Map.get(user, :who_can_add, true)
            end

          block_invite =
            case Map.get(attrs, "block_invite") do
              v when is_boolean(v) -> v
              _ -> Map.get(user, :block_invite, false)
            end

          updated = %{user | auto_approve: auto_approve, who_can_add: who_can_add, block_invite: block_invite}
          :ets.insert(@user_table, {norm, updated})
          persist_user(norm, updated)
          {:ok, updated}

        [] ->
          {:error, :not_found}
      end

    AuditLog.log(:set_privacy, %{phone: norm, ip: ip, result: elem(result, 0)})
    result
  end

  def find_by_phone(phone) do
    norm = Auth.normalize_phone(phone)
    case :ets.lookup(@user_table, norm) do
      [{^norm, user}] -> {:ok, user}
      [] -> {:error, :not_found}
    end
  end

  def find_by_pin(pin) when is_binary(pin) do
    pin_up = String.upcase(pin)
    case :ets.match(@user_table, {:"$1", %{pin: pin_up}}) do
      [] ->
        # fallback scan
        :ets.tab2list(@user_table)
        |> Enum.find(fn {_phone, %Auth{pin: p}} -> p == pin_up end)
        |> case do
          {_, user} -> {:ok, user}
          nil -> {:error, :not_found}
        end
      [[phone] | _] -> find_by_phone(phone)
    end
  end
  def find_by_pin(_), do: {:error, :not_found}

  def search_users(query) when is_binary(query) do
    q = String.upcase(String.trim(query))
    if byte_size(q) < 2 do
      []
    else
      :ets.tab2list(@user_table)
      |> Enum.filter(fn {_phone, %Auth{pin: p, name: n}} ->
        String.contains?(p, q) or String.contains?(String.upcase(n), q)
      end)
      |> Enum.take(20)
      |> Enum.map(fn {_phone, u} -> %{pin: u.pin, name: u.name, phone: u.phone} end)
    end
  end

  # Pending / invite logic
  def invite(from_phone, target_pin, _meta \\ %{}) when is_binary(from_phone) and is_binary(target_pin) do
    raw = String.upcase(String.trim(target_pin) |> String.replace(" ", ""))
    cond do
      byte_size(raw) != 8 or not Regex.match?(~r/^[0-9A-F]{8}$/, raw) ->
        {:error, :invalid_pin}
      true ->
        with {:ok, from_user} <- find_by_phone(from_phone),
             true <- from_user.pin != raw or {:error, :self_invite},
             {:ok, target_user} <- find_by_pin(raw) do
          if Map.get(target_user, :block_invite, false) do
            {:error, :blocked}
          else
            # check already teman? client handles, but also check pending exists
            key = {from_user.pin, raw}
            case :ets.lookup(@pending_table, key) do
              [{^key, :pending}] -> {:error, :already_pending}
              _ ->
                if not Map.get(target_user, :who_can_add, true) do
                  {:error, :not_allowed}
                else
                  if Map.get(target_user, :auto_approve, true) do
                    {:ok, :accepted}
                  else
                    :ets.insert(@pending_table, {key, :pending, System.system_time(:millisecond)})
                    :ets.insert(@pending_table, {{raw, from_user.pin}, :pending_in, System.system_time(:millisecond)})
                    {:ok, :pending}
                  end
                end
            end
          end
        else
          {:error, :self_invite} -> {:error, :self_invite}
          {:error, _} = e -> e
          false -> {:error, :self_invite}
        end
    end
  end

  def pending_for(pin) when is_binary(pin) do
    pin_up = String.upcase(pin)
    :ets.match(@pending_table, {{pin_up, :"$1"}, :pending_in, :"$2"})
    |> Enum.map(fn [from_pin, ts] ->
      case find_by_pin(from_pin) do
        {:ok, u} -> %{pin: from_pin, name: u.name, phone: u.phone, created_at: ts}
        _ -> %{pin: from_pin, name: from_pin, phone: "", created_at: ts}
      end
    end)
  end

  def accept_invite(my_pin, from_pin) when is_binary(my_pin) and is_binary(from_pin) do
    my_up = String.upcase(my_pin)
    from_up = String.upcase(from_pin)
    key_in = {my_up, from_up}
    key_out = {from_up, my_up}
    case :ets.lookup(@pending_table, key_in) do
      [{^key_in, :pending_in, _}] ->
        :ets.delete(@pending_table, key_in)
        :ets.delete(@pending_table, key_out)
        {:ok, :accepted}
      _ -> {:error, :not_found}
    end
  end

  def reject_invite(my_pin, from_pin) when is_binary(my_pin) and is_binary(from_pin) do
    my_up = String.upcase(my_pin)
    from_up = String.upcase(from_pin)
    key_in = {my_up, from_up}
    key_out = {from_up, my_up}
    case :ets.lookup(@pending_table, key_in) do
      [{^key_in, :pending_in, _}] ->
        :ets.delete(@pending_table, key_in)
        :ets.delete(@pending_table, key_out)
        {:ok, :rejected}
      _ -> {:error, :not_found}
    end
  end

  def blocked_for(pin) when is_binary(pin) do
    pin_up = String.upcase(pin)
    case :ets.lookup(@blocked_table, pin_up) do
      [{^pin_up, list}] -> list
      [] -> []
    end
  end

  def block_pin(my_pin, target_pin) when is_binary(my_pin) and is_binary(target_pin) do
    my_up = String.upcase(my_pin)
    t_up = String.upcase(target_pin)
    list = blocked_for(my_up)
    if t_up in list do
      {:ok, list}
    else
      new_list = [t_up | list]
      :ets.insert(@blocked_table, {my_up, new_list})
      # also remove pending
      :ets.delete(@pending_table, {my_up, t_up})
      :ets.delete(@pending_table, {t_up, my_up})
      {:ok, new_list}
    end
  end

  def unblock_pin(my_pin, target_pin) when is_binary(my_pin) and is_binary(target_pin) do
    my_up = String.upcase(my_pin)
    t_up = String.upcase(target_pin)
    list = blocked_for(my_up) |> Enum.reject(&(&1 == t_up))
    :ets.insert(@blocked_table, {my_up, list})
    {:ok, list}
  end

  def phone_for_pin(pin) when is_binary(pin) do
    case find_by_pin(pin) do
      {:ok, u} -> {:ok, u.phone}
      e -> e
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
    for t <- [@otp_table, @user_table, @rate_table, @attempt_table, @pending_table, @blocked_table] do
      case :ets.whereis(t) do
        :undefined -> :ets.new(t, [:named_table, :set, :public, read_concurrency: true, write_concurrency: true])
        _ -> :ok
      end
    end
    # ensure JWT keys and users already loaded in Application.start; reload if needed (idempotent)
    UpMessenger.Domain.Jwt.ensure_keys!()
    try do
      Persistence.load_users!()
    rescue _ -> :ok end
    {:ok, %{}}
  end
end
