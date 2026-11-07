defmodule UpMessenger.Application.SendMessage do
  @moduledoc "Use case kirim pesan: validasi -> simpan antrean -> forward ke sesi online penerima."
  alias UpMessenger.Domain.Message
  alias UpMessenger.Infrastructure.Queue
  @doc "Eksekusi pengiriman opaque. Return {:ok, msg_id} agar interface bisa balas MSG_ACK."
  @spec execute(map()) :: {:ok, binary()} | {:error, atom()}
  def execute(attrs) do
    # 1. Validasi domain dulu, jangan sentuh socket/DB sebelum valid.
    with {:ok, msg} <- Message.new(attrs),
         # 2. Simpan ke antrean offline (ETS, TTL sampai ACK penerima).
         :ok <- Queue.enqueue(msg),
         # 3. Coba forward langsung bila penerima online.
         :ok <- Queue.try_deliver(msg) do
      {:ok, msg.id}
    end
  end
end
