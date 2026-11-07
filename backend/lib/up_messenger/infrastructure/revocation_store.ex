defmodule UpMessenger.Infrastructure.RevocationStore do
  @moduledoc "Revoke list JWT jti -> exp. ETS + DETS priv/revoked.dets, prune tiap jam."
  use GenServer
  require Logger
  @table :upme_revoked
  @dets_table :upme_revoked_dets
  @dets_path "priv/revoked.dets"
  @prune_interval 3_600_000

  def start_link(_), do: GenServer.start_link(__MODULE__, %{}, name: __MODULE__)

  @spec revoke(binary(), integer()) :: :ok
  def revoke(jti, exp) when is_binary(jti) and is_integer(exp) do
    GenServer.call(__MODULE__, {:revoke, jti, exp})
  rescue
    _ ->
      # fallback langsung ETS jika GenServer belum start (test)
      ensure_table!()
      :ets.insert(@table, {jti, exp})
      persist_one(jti, exp)
      :ok
  end

  @spec revoked?(binary()) :: boolean()
  def revoked?(jti) when is_binary(jti) do
    case :ets.whereis(@table) do
      :undefined -> false
      _ ->
        case :ets.lookup(@table, jti) do
          [{_, exp}] -> System.system_time(:second) < exp
          [] -> false
        end
    end
  end
  def revoked?(_), do: false

  @spec clear() :: :ok
  def clear do
    ensure_table!()
    :ets.delete_all_objects(@table)
    try do
      dets_file = String.to_charlist(@dets_path)
      case :dets.open_file(@dets_table, [file: dets_file, type: :set]) do
        {:ok, _} -> :dets.delete_all_objects(@dets_table); :dets.close(@dets_table)
        _ -> :ok
      end
    rescue _ -> :ok end
    :ok
  end

  # GenServer callbacks
  @impl true
  def init(_) do
    ensure_table!()
    load_dets!()
    schedule_prune()
    {:ok, %{}}
  end

  @impl true
  def handle_call({:revoke, jti, exp}, _from, s) do
    ensure_table!()
    :ets.insert(@table, {jti, exp})
    persist_one(jti, exp)
    {:reply, :ok, s}
  end

  @impl true
  def handle_info(:prune, s) do
    prune_expired()
    schedule_prune()
    {:noreply, s}
  end

  defp ensure_table! do
    case :ets.whereis(@table) do
      :undefined -> :ets.new(@table, [:named_table, :set, :public, read_concurrency: true])
      _ -> :ok
    end
  end

  defp load_dets! do
    File.mkdir_p!("priv")
    dets_file = String.to_charlist(@dets_path)
    case :dets.open_file(@dets_table, [file: dets_file, type: :set]) do
      {:ok, _} ->
        now = System.system_time(:second)
        :dets.traverse(@dets_table, fn {jti, exp} ->
          if exp > now, do: :ets.insert(@table, {jti, exp})
          :continue
        end)
        :dets.close(@dets_table)
      _ -> :ok
    end
  rescue
    e -> Logger.warning("revocation load_dets failed: #{inspect(e)}")
  end

  defp persist_one(jti, exp) do
    File.mkdir_p!("priv")
    dets_file = String.to_charlist(@dets_path)
    case :dets.open_file(@dets_table, [file: dets_file, type: :set]) do
      {:ok, _} -> :dets.insert(@dets_table, {jti, exp}); :dets.sync(@dets_table); :dets.close(@dets_table)
      _ -> :ok
    end
  rescue
    _ -> :ok
  end

  defp prune_expired do
    now = System.system_time(:second)
    :ets.tab2list(@table)
    |> Enum.each(fn {jti, exp} ->
      if exp <= now do
        :ets.delete(@table, jti)
        # also dets
        try do
          dets_file = String.to_charlist(@dets_path)
          case :dets.open_file(@dets_table, [file: dets_file, type: :set]) do
            {:ok, _} -> :dets.delete(@dets_table, jti); :dets.close(@dets_table)
            _ -> :ok
          end
        rescue _ -> :ok end
      end
    end)
  end

  defp schedule_prune, do: Process.send_after(self(), :prune, @prune_interval)
end
