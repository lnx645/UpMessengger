defmodule UpMessenger.Application do
  @moduledoc "Entry point OTP. Menyalakan Registry sesi + TCP listener Thousand Island. TLS opsional via env UPME_TLS."
  use Application
  @doc "Start supervision tree: session registry, offline queue, TCP listener."
  @spec start(any(), any()) :: {:ok, pid()}
  def start(_type, _args) do
    port = String.to_integer(System.get_env("UPME_PORT", "5222"))
    tls = System.get_env("UPME_TLS", "false") == "true"
    transport_options = if tls do
      cert = System.get_env("UPME_TLS_CERT", "priv/cert.pem")
      key = System.get_env("UPME_TLS_KEY", "priv/key.pem")
      [certfile: cert, keyfile: key, verify: :verify_none, versions: [:"tlsv1.3"]]
    else
      []
    end

    ti_opts = [
      port: port,
      handler_module: UpMessenger.Interface.TcpHandler,
      transport_module: if(tls, do: ThousandIsland.Transports.SSL, else: ThousandIsland.Transports.TCP),
      transport_options: transport_options,
      num_acceptors: 10
    ]

    children = [
      {Registry, keys: :duplicate, name: UpMessenger.SessionRegistry},
      {UpMessenger.Infrastructure.Queue, []},
      {UpMessenger.Infrastructure.RevocationStore, []},
      {UpMessenger.Infrastructure.AuditLog, []},
      {UpMessenger.Infrastructure.RateLimiter, []},
      {UpMessenger.Infrastructure.AuthStore, []},
      {Task.Supervisor, name: UpMessenger.TaskSupervisor},
      {ThousandIsland, ti_opts}
    ]
    opts = [strategy: :one_for_one, name: UpMessenger.Supervisor]
    Supervisor.start_link(children, opts)
  end
end
