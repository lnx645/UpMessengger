defmodule UpMessenger.Infrastructure.Queue do
  @moduledoc "Antrean offline sementara (ETS). Hapus pesan setelah penerima ACK. Bukan histori permanen (local-first). Per-user cap 500."
  use GenServer
  alias UpMessenger.Domain.Message
  @table :upme_queue
  @max_per_conversation 500
  @max_global 5000
  @doc "Start GenServer antrean."
  @spec start_link(any()) :: GenServer.on_start()
  def start_link(_), do: GenServer.start_link(__MODULE__, %{}, name: __MODULE__)
  @doc "Simpan pesan opaque ke antrean penerima."
  @spec enqueue(Message.t()) :: :ok | {:error, :queue_full}
  def enqueue(%Message{} = m), do: GenServer.call(__MODULE__, {:enqueue, m})
  @doc "Coba kirim langsung bila sesi TCP penerima terdaftar di Registry."
  @spec try_deliver(Message.t()) :: :ok
  def try_deliver(%Message{} = m) do
    # conversation_id format: "phoneA:phoneB" atau group_id. Cari penerima selain sender.
    # Untuk 1-1: conversation_id mengandung phone lawan. Kita lookup semua sesi yang match conversation.
    # Simpel: lookup by conversation_id sebagai room, dan kirim ke semua kecuali sender.
    # Jika conversation_id adalah phone lawan, lookup {:user, conversation_id}
    recipients = Registry.lookup(UpMessenger.SessionRegistry, {:conv, m.conversation_id}) ++
                 Registry.lookup(UpMessenger.SessionRegistry, {:user, m.conversation_id})
    Enum.each(recipients, fn {pid, _} -> send(pid, {:msg_recv, m}) end)
    :ok
  end
  @doc "Hapus pesan setelah penerima kirim MSG_ACK."
  @spec ack(binary()) :: :ok
  def ack(msg_id), do: GenServer.call(__MODULE__, {:ack, msg_id})
  @impl true
  def init(_) do
    :ets.new(@table, [:named_table, :set, :public, read_concurrency: true])
    {:ok, %{}}
  end
  @impl true
  def handle_call({:enqueue, %Message{} = m}, _from, s) do
    global = :ets.info(@table, :size)
    # Hitung per-conversation
    per_conv = :ets.tab2list(@table) |> Enum.count(fn {_, %Message{conversation_id: c}} -> c == m.conversation_id end)
    cond do
      global >= @max_global -> {:reply, {:error, :queue_full}, s}
      per_conv >= @max_per_conversation -> {:reply, {:error, :queue_full}, s}
      true -> :ets.insert(@table, {m.id, m}); {:reply, :ok, s}
    end
  end
  def handle_call({:ack, id}, _from, s) do
    :ets.delete(@table, id)
    {:reply, :ok, s}
  end
end
