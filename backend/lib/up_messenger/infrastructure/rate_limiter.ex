defmodule UpMessenger.Infrastructure.RateLimiter do
  @moduledoc "Rate-limit per-IP sliding window. ETS :upme_ip_rate. 20 req/menit, 100 req/jam per IP."
  use GenServer

  @table :upme_ip_rate
  @per_minute 20
  @per_hour 100
  @minute_window 60_000
  @hour_window 3_600_000

  def start_link(_), do: GenServer.start_link(__MODULE__, %{}, name: __MODULE__)

  @spec check_ip(String.t() | binary()) :: :ok | {:error, :rate_limited}
  def check_ip(ip) when is_binary(ip) do
    if ip == "unknown" or ip == "" do
      :ok
    else
      now = System.system_time(:millisecond)
      timestamps = case :ets.lookup(@table, ip) do
        [{^ip, ts}] -> ts
        [] -> []
      end
      fresh_hour = Enum.filter(timestamps, fn t -> now - t < @hour_window end)
      fresh_min = Enum.filter(fresh_hour, fn t -> now - t < @minute_window end)
      cond do
        length(fresh_min) >= @per_minute -> {:error, :rate_limited}
        length(fresh_hour) >= @per_hour -> {:error, :rate_limited}
        true ->
          :ets.insert(@table, {ip, fresh_hour ++ [now]})
          # prune stale entries periodically is handled by filter above
          :ok
      end
    end
  end
  def check_ip(_), do: :ok

  @impl true
  def init(_) do
    :ets.new(@table, [:named_table, :set, :public, read_concurrency: true, write_concurrency: true])
    # periodic cleanup tiap 10 menit
    :timer.send_interval(600_000, :prune)
    {:ok, %{}}
  end

  @impl true
  def handle_info(:prune, state) do
    now = System.system_time(:millisecond)
    :ets.tab2list(@table)
    |> Enum.each(fn {ip, ts} ->
      fresh = Enum.filter(ts, fn t -> now - t < @hour_window end)
      if fresh == [] do
        :ets.delete(@table, ip)
      else
        if length(fresh) != length(ts), do: :ets.insert(@table, {ip, fresh})
      end
    end)
    {:noreply, state}
  end
end
