defmodule UpMessenger.Infrastructure.AuditLog do
  @moduledoc """
  Audit log untuk auth: request_otp, verify_otp, verify_two_fa, set_two_fa, disable_two_fa.
  ETS :upme_audit + file append priv/audit.log (JSON lines). Query in-memory.
  """
  use GenServer
  require Logger
  @table :upme_audit
  @log_path "priv/audit.log"
  @max_ets 10_000

  def start_link(_), do: GenServer.start_link(__MODULE__, %{}, name: __MODULE__)

  @doc "Log event. Metadata: %{phone, ip, result, detail}."
  @spec log(atom(), map()) :: :ok
  def log(event, meta \\ %{}) when is_atom(event) do
    entry = %{
      "event" => Atom.to_string(event),
      "at" => DateTime.utc_now() |> DateTime.to_iso8601(),
      "ts" => System.system_time(:millisecond),
      "phone" => Map.get(meta, :phone),
      "ip" => Map.get(meta, :ip),
      "result" => inspect(Map.get(meta, :result, :ok)),
      "meta" => meta |> Map.drop([:phone, :ip, :result]) |> stringify_keys()
    }
    GenServer.cast(__MODULE__, {:log, entry})
  rescue
    _ -> :ok
  catch
    _, _ -> :ok
  end

  @doc "Query audit entries, filter by phone dan sejak ts (millisecond). Return list terbaru dulu, max 100."
  @spec query(binary() | nil, integer() | nil) :: [map()]
  def query(phone \\ nil, since_ms \\ nil) do
    case :ets.whereis(@table) do
      :undefined -> []
      _ ->
        :ets.tab2list(@table)
        |> Enum.map(fn {_id, e} -> e end)
        |> Enum.filter(fn e ->
          phone_ok = is_nil(phone) or e["phone"] == phone
          since_ok = is_nil(since_ms) or (is_integer(e["ts"]) and e["ts"] >= since_ms)
          phone_ok and since_ok
        end)
        |> Enum.sort_by(& &1["ts"], :desc)
        |> Enum.take(100)
    end
  end

  @doc "Semua entries (untuk debug). Max 200."
  def all, do: query(nil, nil) |> Enum.take(200)

  @impl true
  def init(_) do
    case :ets.whereis(@table) do
      :undefined -> :ets.new(@table, [:named_table, :ordered_set, :public, read_concurrency: true])
      _ -> :ok
    end
    File.mkdir_p!("priv")
    # ensure file exists
    unless File.exists?(@log_path), do: File.write!(@log_path, "")
    {:ok, %{seq: 0}}
  end

  @impl true
  def handle_cast({:log, entry}, %{seq: seq} = s) do
    case :ets.whereis(@table) do
      :undefined -> :ets.new(@table, [:named_table, :ordered_set, :public, read_concurrency: true])
      _ -> :ok
    end
    id = {entry["ts"], seq}
    :ets.insert(@table, {id, entry})
    # cap
    if :ets.info(@table, :size) > @max_ets do
      first = :ets.first(@table)
      :ets.delete(@table, first)
    end
    # append to file
    try do
      line = Jason.encode!(entry)
      File.write!(@log_path, line <> "\n", [:append])
    rescue
      e -> Logger.warning("audit append failed: #{inspect(e)}")
    end
    # also Logger
    Logger.info("[AUDIT] #{entry["event"]} phone=#{entry["phone"]} ip=#{entry["ip"]} result=#{entry["result"]}")
    {:noreply, %{s | seq: seq + 1}}
  end

  defp stringify_keys(map) when is_map(map) do
    Enum.into(map, %{}, fn {k, v} -> {to_string(k), inspect(v)} end)
  end
end
