defmodule UpMessenger.Domain.Message do
  @moduledoc "Entity pesan opaque. Server tidak pernah melihat plaintext, hanya metadata routing."
  @enforce_keys [:id, :conversation_id, :sender_id, :ciphertext]
  defstruct [:id, :conversation_id, :sender_id, :ciphertext, :epoch, :inserted_at]
  @type t :: %__MODULE__{id: binary(), conversation_id: binary(), sender_id: binary(), ciphertext: binary(), epoch: non_neg_integer(), inserted_at: integer()}
  @doc "Bangun entity pesan, validasi ukuran ciphertext maks 256KB."
  @spec new(map()) :: {:ok, t()} | {:error, atom()}
  def new(%{id: id, conversation_id: c, sender_id: s, ciphertext: ct} = attrs) when is_binary(ct) do
    # Tolak payload kebesaran agar RAM server aman.
    if byte_size(ct) > 256 * 1024, do: {:error, :too_large}, else: {:ok, %__MODULE__{id: id, conversation_id: c, sender_id: s, ciphertext: ct, epoch: Map.get(attrs, :epoch, 0), inserted_at: System.system_time(:millisecond)}}
  end
  def new(_), do: {:error, :invalid}
end
