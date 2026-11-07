defmodule UpMessenger.Domain.KeyDirectory do
  @moduledoc "Direktori kunci publik: Signal prekey 1-1 dan MLS KeyPackage grup. Hanya publik, bukan secret."
  @doc "Validasi blob KeyPackage MLS (biner, maks 8KB) sebelum disimpan."
  @spec valid_key_package?(binary()) :: boolean()
  def valid_key_package?(bin) when is_binary(bin), do: byte_size(bin) > 32 and byte_size(bin) < 8 * 1024
  def valid_key_package?(_), do: false
end
