defmodule UpMessenger.Interface.Frame do
  @moduledoc "Codec framing TCP: <<len::32-big, type::8, payload::binary>>. Parser hemat RAM, tolak >256KB."
  @max 256 * 1024 + 5
  @doc "Encode type + payload menjadi biner siap dikirim ke socket."
  @spec encode(byte(), binary()) :: binary()
  def encode(type, payload) when is_integer(type) and is_binary(payload) do
    # Length mencakup 1 byte type + payload.
    len = byte_size(payload) + 1
    <<len::big-unsigned-integer-32, type::8, payload::binary>>
  end
  @doc "Decode satu frame dari buffer. Return {:ok, type, payload, rest} atau {:need_more, buffer}."
  @spec decode(binary()) :: {:ok, byte(), binary(), binary()} | {:need_more, binary()} | {:error, atom()}
  def decode(<<len::big-unsigned-integer-32, rest::binary>>) when len > @max, do: {:error, :frame_too_large}
  def decode(<<len::big-unsigned-integer-32, rest::binary>>) do
    # Tunggu sampai seluruh frame tiba sebelum parsing (hindari busy loop).
    if byte_size(rest) < len, do: {:need_more, <<len::big-unsigned-integer-32, rest::binary>>}, else: (<<frame::binary-size(len), tail::binary>> = rest; <<type::8, payload::binary>> = frame; {:ok, type, payload, tail})
  end
  def decode(buf) when byte_size(buf) < 4, do: {:need_more, buf}
end
