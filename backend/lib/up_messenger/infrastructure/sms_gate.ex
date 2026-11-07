defmodule UpMessenger.Infrastructure.SmsGate do
  @moduledoc "SMS Gateway via api.sms-gate.app (Cloud Server). Kredensial dari ENV atau default screenshot HP."
  require Logger

  @cloud_url "https://api.sms-gate.app/3rdparty/v1/messages"
  @timeout 8000

  def config do
    user = System.get_env("SMS_GATE_USERNAME")
    pass = System.get_env("SMS_GATE_PASSWORD")
    dev = System.get_env("SMS_GATE_DEVICE_ID")
    # In test, allow dummy creds agar mix test tidak raise — prod tetap harus set ENV
    if Mix.env() == :test and (is_nil(user) or user == "") do
      %{username: "test_user", password: "test_pass", device_id: dev || "test_device", url: System.get_env("SMS_GATE_URL") || @cloud_url}
    else
      if is_nil(user) or user == "" or is_nil(pass) or pass == "" do
        raise "SMS_GATE_USERNAME/PASSWORD belum diset di ENV — hapus hardcode untuk keamanan"
      end
      %{
        username: user,
        password: pass,
        device_id: dev || raise("SMS_GATE_DEVICE_ID belum diset"),
        url: System.get_env("SMS_GATE_URL") || @cloud_url
      }
    end
  end

  @spec send_otp(String.t(), String.t()) :: :ok | {:error, term()}
  def send_otp(phone, otp) when is_binary(phone) and is_binary(otp) do
    message = "UpMessenger OTP: #{otp} (5 menit). Jangan bagikan."
    send_sms(phone, message)
  end

  @spec send_sms(String.t(), String.t()) :: :ok | {:error, term()}
  def send_sms(phone, text) when is_binary(phone) and is_binary(text) do
    %{username: user, password: pass, device_id: _device, url: url} = config()
    # Normalisasi phone ke E164
    auth = Base.encode64("#{user}:#{pass}")
    body = Jason.encode!(%{
      "message" => text,
      "phoneNumbers" => [phone],
      # "simNumber" => 1 # opsional
    })
    headers = [
      {'Authorization', 'Basic ' ++ String.to_charlist(auth)},
      {'Content-Type', 'application/json'},
      {'Accept', 'application/json'}
    ]
    # Ensure inets started
    :inets.start()
    :ssl.start()
    host = URI.parse(url).host || "api.sms-gate.app"
    http_opts = [timeout: @timeout, ssl: [verify: :verify_peer, cacerts: :public_key.cacerts_get(), versions: [:"tlsv1.3"], server_name_indication: String.to_charlist(host), customize_hostname_check: [match_fun: :public_key.pkix_verify_hostname_match_fun(:https)]]]
    opts = [body_format: :binary]

    case :httpc.request(:post, {String.to_charlist(url), headers, 'application/json', body}, http_opts, opts) do
      {:ok, {{_ver, code, _}, _headers, resp_body}} when code in [200, 201, 202] ->
        Logger.info("SMS queued/sent #{code} to #{phone} via sms-gate.app")
        _ = resp_body
        :ok
      {:ok, {{_ver, code, _}, _headers, resp_body}} ->
        Logger.warning("SMS failed #{code}: #{resp_body}")
        {:error, {:http_error, code, resp_body}}
      {:error, reason} ->
        Logger.warning("SMS http error: #{inspect(reason)}")
        {:error, reason}
    end
  end

  # Async fire-and-forget agar request_otp tidak block
  def send_otp_async(phone, otp) do
    try do
      case Task.Supervisor.start_child(UpMessenger.TaskSupervisor, fn -> send_otp(phone, otp) end) do
        {:ok, _} -> :ok
        {:error, _} -> Task.start(fn -> send_otp(phone, otp) end); :ok
        _ -> :ok
      end
    catch
      :exit, _ -> Task.start(fn -> send_otp(phone, otp) end); :ok
      _, _ -> Task.start(fn -> send_otp(phone, otp) end); :ok
    else
      _ -> :ok
    end
  rescue
    _ -> try do Task.start(fn -> send_otp(phone, otp) end) catch _, _ -> :ok end; :ok
  end
end
