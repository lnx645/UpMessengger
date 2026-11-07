defmodule UpMessenger.Interface.TcpHandler do
  @moduledoc "Handler Thousand Island. WhatsApp arsitektur: OTP register -> verify -> session token -> MSG relay. Prod aman: no OTP leak, auth wajib. Support refresh/logout, audit, revoke. Rate-limit per-IP via ETS :upme_ip_rate."
  use ThousandIsland.Handler
  alias UpMessenger.Interface.Frame
  alias UpMessenger.Application.SendMessage
  alias UpMessenger.Infrastructure.AuthStore
  alias UpMessenger.Infrastructure.AuditLog
  alias UpMessenger.Infrastructure.RateLimiter
  require Logger

  defp client_ip(socket) do
    try do
      case ThousandIsland.Socket.peername(socket) do
        {:ok, {ip_tuple, _port}} when is_tuple(ip_tuple) -> ip_tuple |> :inet.ntoa() |> to_string()
        {:ok, {{a,b,c,d}, _}} -> "#{a}.#{b}.#{c}.#{d}"
        _ -> "unknown"
      end
    rescue _ -> "unknown"
    catch _ -> "unknown"
    end
  end

  @impl true
  def handle_connection(socket, state) do
    base = if is_map(state), do: state, else: %{}
    {:continue, Map.merge(base, %{buffer: <<>>, user_id: nil, phone: nil, last_seen: System.monotonic_time(:second)})}
    |> tap(fn _ -> ThousandIsland.Socket.setopts(socket, active: false) end)
  end

  @impl true
  def handle_data(data, socket, %{buffer: buf} = state) do
    ip = client_ip(socket)
    case Frame.decode(buf <> data) do
      {:ok, 0x08, _payload, rest} ->
        ThousandIsland.Socket.send(socket, Frame.encode(0x08, <<1>>))
        {:continue, %{state | buffer: rest}}

      {:ok, 0x02, payload, rest} ->
        result =
          with {:ok, map} <- Jason.decode(payload),
               token when is_binary(token) <- map["token"],
               {:ok, user} <- AuthStore.find_by_token(token) do
            Registry.register(UpMessenger.SessionRegistry, {:user, user.phone}, nil)
            ThousandIsland.Socket.send(socket, Frame.encode(0x02, Jason.encode!(%{status: "authenticated", phone: user.phone, pin: user.pin, name: user.name})))
            {:ok, user.phone}
          else
            {:error, :revoked} ->
              ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "revoked"})))
              :error
            _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "unauthenticated"})))
                 :error
          end
        new_state = case result do
          {:ok, phone} -> %{state | buffer: rest, user_id: phone, phone: phone}
          :error -> %{state | buffer: rest}
        end
        {:continue, new_state}

      {:ok, 0x03, payload, rest} ->
        if state.user_id == nil do
          ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "unauthenticated"})))
          {:continue, %{state | buffer: rest}}
        else
          with {:ok, map} <- Jason.decode(payload),
               id when is_binary(id) <- map["id"],
               conv when is_binary(conv) <- map["conversation_id"],
               b64 when is_binary(b64) <- map["ciphertext_b64"] || "",
               {:ok, ciphertext} <- Base.decode64(b64) do
            sender = map["sender_id"] || state.user_id
            if sender != state.user_id do
              ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "forbidden"})))
            else
              case SendMessage.execute(%{id: id, conversation_id: conv, sender_id: state.user_id, ciphertext: ciphertext, epoch: map["epoch"] || 0}) do
                {:ok, msg_id} -> ThousandIsland.Socket.send(socket, Frame.encode(0x04, Jason.encode!(%{id: msg_id, status: "sent"})))
                {:error, reason} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(reason)})))
              end
            end
          else
            :error -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_base64"})))
            {:error, _} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"})))
            _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"})))
          end
          {:continue, %{state | buffer: rest}}
        end

      {:ok, 0x10, payload, rest} ->
        case RateLimiter.check_ip(ip) do
          {:error, :rate_limited} ->
            AuditLog.log(:request_otp, %{phone: "unknown", ip: ip, result: :rate_limited_ip})
            ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "rate_limited_ip"})))
          :ok ->
            with {:ok, %{"phone" => phone}} <- Jason.decode(payload),
                 {:ok, _otp} <- AuthStore.request_otp(phone, %{ip: ip}) do
              norm = UpMessenger.Domain.Auth.normalize_phone(phone)
              ThousandIsland.Socket.send(socket, Frame.encode(0x12, Jason.encode!(%{phone: norm, status: "otp_sent", cooldown: 60})))
            else
              {:error, :rate_limited} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "rate_limited"})))
              {:error, r} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(r)})))
              _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"})))
            end
        end
        {:continue, %{state | buffer: rest}}

      {:ok, 0x11, payload, rest} ->
        case RateLimiter.check_ip(ip) do
          {:error, :rate_limited} ->
            AuditLog.log(:verify_otp, %{phone: "unknown", ip: ip, result: :rate_limited_ip})
            ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "rate_limited_ip"})))
            {:continue, %{state | buffer: rest}}
          :ok ->
            case Jason.decode(payload) do
              {:ok, %{"phone" => phone, "otp" => otp}} ->
                case AuthStore.verify_otp(phone, otp, %{ip: ip}) do
                  {:ok, user} ->
                    Registry.register(UpMessenger.SessionRegistry, {:user, user.phone}, nil)
                    ThousandIsland.Socket.send(socket, Frame.encode(0x13, Jason.encode!(%{phone: user.phone, pin: user.pin, token: user.token, refresh_token: user.refresh_token, name: user.name, identity_pub: user.identity_pub})))
                    {:continue, %{state | buffer: rest, user_id: user.phone, phone: user.phone}}
                  {:need_two_fa, _user} ->
                    ThousandIsland.Socket.send(socket, Frame.encode(0x14, Jason.encode!(%{status: "need_two_fa", phone: phone})))
                    {:continue, %{state | buffer: rest, phone: phone}}
                  {:error, {:too_many_attempts, retry_after}} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "too_many_attempts", retry_after_ms: retry_after}))); {:continue, %{state | buffer: rest}}
                  {:error, :too_many_attempts} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "too_many_attempts"}))); {:continue, %{state | buffer: rest}}
                  {:error, r} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(r)}))); {:continue, %{state | buffer: rest}}
                end
              _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"}))); {:continue, %{state | buffer: rest}}
            end
        end

      {:ok, 0x14, payload, rest} ->
        with {:ok, %{"phone" => phone, "pin" => pin}} <- Jason.decode(payload),
             {:ok, user} <- AuthStore.verify_two_fa(phone, pin, %{ip: ip}) do
          Registry.register(UpMessenger.SessionRegistry, {:user, user.phone}, nil)
          ThousandIsland.Socket.send(socket, Frame.encode(0x13, Jason.encode!(%{phone: user.phone, pin: user.pin, token: user.token, refresh_token: user.refresh_token, name: user.name, identity_pub: user.identity_pub})))
          {:continue, %{state | buffer: rest, user_id: user.phone, phone: user.phone}}
        else
          {:error, r} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(r)}))); {:continue, %{state | buffer: rest}}
          _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"}))); {:continue, %{state | buffer: rest}}
        end

      {:ok, 0x15, payload, rest} ->
        with {:ok, %{"phone" => phone, "pin" => pin, "token" => token}} <- Jason.decode(payload),
             {:ok, _user} <- AuthStore.find_by_token(token),
             {:ok, _} <- AuthStore.set_two_fa(phone, pin, %{ip: ip}) do
          ThousandIsland.Socket.send(socket, Frame.encode(0x15, Jason.encode!(%{status: "two_fa_enabled"})))
          {:continue, %{state | buffer: rest}}
        else
          {:error, r} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(r)}))); {:continue, %{state | buffer: rest}}
          _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"}))); {:continue, %{state | buffer: rest}}
        end

      {:ok, 0x16, payload, rest} ->
        with {:ok, %{"phone" => phone, "pin" => pin, "token" => token}} <- Jason.decode(payload),
             {:ok, _user} <- AuthStore.find_by_token(token),
             {:ok, _} <- AuthStore.disable_two_fa(phone, pin, %{ip: ip}) do
          ThousandIsland.Socket.send(socket, Frame.encode(0x16, Jason.encode!(%{status: "two_fa_disabled"})))
          {:continue, %{state | buffer: rest}}
        else
          {:error, r} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(r)}))); {:continue, %{state | buffer: rest}}
          _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"}))); {:continue, %{state | buffer: rest}}
        end

      {:ok, 0x17, payload, rest} ->
        # REFRESH: {refresh_token} -> new access + refresh
        with {:ok, %{"refresh_token" => rt}} <- Jason.decode(payload),
             {:ok, user} <- AuthStore.refresh_access(rt) do
          ThousandIsland.Socket.send(socket, Frame.encode(0x17, Jason.encode!(%{phone: user.phone, token: user.token, refresh_token: user.refresh_token, name: user.name})))
          {:continue, %{state | buffer: rest, user_id: user.phone, phone: user.phone}}
        else
          {:error, :revoked} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "revoked"}))); {:continue, %{state | buffer: rest}}
          {:error, r} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(r)}))); {:continue, %{state | buffer: rest}}
          _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"}))); {:continue, %{state | buffer: rest}}
        end

      {:ok, 0x18, payload, rest} ->
        # LOGOUT: {phone, token} atau {token}
        with {:ok, map} <- Jason.decode(payload),
             token when is_binary(token) <- map["token"] || map["refresh_token"],
             phone when is_binary(phone) <- map["phone"] || state.phone || state.user_id do
          case AuthStore.logout(phone, token) do
            :ok -> ThousandIsland.Socket.send(socket, Frame.encode(0x18, Jason.encode!(%{status: "logged_out"})))
            {:error, r} -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: inspect(r)})))
          end
          {:continue, %{state | buffer: rest, user_id: nil, phone: nil}}
        else
          _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "invalid_payload"}))); {:continue, %{state | buffer: rest}}
        end

      {:ok, 0x20, payload, rest} ->
        with {:ok, %{"phone" => phone, "token" => token, "identity_pub" => id_pub}} <- Jason.decode(payload),
             {:ok, user} <- AuthStore.find_by_token(token),
             true <- user.phone == UpMessenger.Domain.Auth.normalize_phone(phone) do
          prekey = Map.get(Jason.decode!(payload), "prekey_b64", "")
          pub_bin = Base.decode64!(id_pub)
          if UpMessenger.Domain.KeyDirectory.valid_key_package?(Base.decode64!(prekey || "")) or prekey == "" do
            :ets.insert(:upme_users, {user.phone, %{user | identity_pub: id_pub}})
            try do UpMessenger.Infrastructure.Persistence.persist_user!(user.phone, %{user | identity_pub: id_pub}) rescue _ -> :ok end
          end
          _ = pub_bin
          ThousandIsland.Socket.send(socket, Frame.encode(0x20, Jason.encode!(%{status: "prekey_stored"})))
          {:continue, %{state | buffer: rest}}
        else
          _ -> ThousandIsland.Socket.send(socket, Frame.encode(0x1F, Jason.encode!(%{error: "unauthenticated"}))); {:continue, %{state | buffer: rest}}
        end

      {:ok, _type, _payload, rest} ->
        {:continue, %{state | buffer: rest}}

      {:need_more, new_buf} ->
        {:continue, %{state | buffer: new_buf}}

      {:error, _} ->
        {:close, state}
    end
  end

  @impl true
  def handle_info({:msg_recv, msg}, {socket, state}) do
    payload = Jason.encode!(%{id: msg.id, conversation_id: msg.conversation_id, sender_id: msg.sender_id, ciphertext_b64: Base.encode64(msg.ciphertext), epoch: msg.epoch || 0})
    ThousandIsland.Socket.send(socket, Frame.encode(0x05, payload))
    {:noreply, {socket, state}}
  end

  def handle_info({:msg_recv, _msg}, state) when is_map(state) do
    {:noreply, state}
  end
end
