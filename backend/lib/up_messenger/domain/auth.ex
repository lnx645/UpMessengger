defmodule UpMessenger.Domain.Auth do
  @moduledoc "Arsitektur WhatsApp: registrasi via nomor HP + OTP 6 digit. PIN 8 hex, JWT RS256, Registration Lock 2FA."
  @enforce_keys [:phone, :pin, :name]
  defstruct [:phone, :pin, :name, :token, :refresh_token, :created_at, :two_fa_hash, :two_fa_salt, :two_fa_enabled, :identity_pub]
  @type t :: %__MODULE__{phone: binary(), pin: binary(), name: binary(), token: binary() | nil, refresh_token: binary() | nil, created_at: integer(), two_fa_hash: binary() | nil, two_fa_salt: binary() | nil, two_fa_enabled: boolean(), identity_pub: binary() | nil}

  @spec normalize_phone(binary()) :: binary()
  def normalize_phone(phone) when is_binary(phone) do
    phone |> String.replace(~r/[^0-9+]/, "") |> String.trim()
  end

  @spec valid_phone?(binary()) :: boolean()
  def valid_phone?(phone) when is_binary(phone) do
    p = normalize_phone(phone)
    # Coba libphonenumber (ExPhoneNumber) jika tersedia, fallback ke regex E.164 ketat
    if Code.ensure_loaded?(ExPhoneNumber) do
      try do
        case ExPhoneNumber.parse(p, "ID") do
          {:ok, parsed} -> ExPhoneNumber.is_valid_number?(parsed)
          _ -> e164_strict?(p)
        end
      rescue _ -> e164_strict?(p)
      catch _ -> e164_strict?(p)
      end
    else
      e164_strict?(p)
    end
  end
  def valid_phone?(_), do: false

  # E.164 ketat: wajib '+', 1-9 di awal, total 8..15 digit setelah '+'
  defp e164_strict?(p) when is_binary(p) do
    String.match?(p, ~r/^\+[1-9]\d{7,14}$/)
  end

  @spec valid_otp?(binary()) :: boolean()
  def valid_otp?(otp) when is_binary(otp), do: String.match?(otp, ~r/^[0-9]{6}$/)
  def valid_otp?(_), do: false

  @spec generate_pin() :: binary()
  def generate_pin do
    1..8 |> Enum.map(fn _ -> Enum.random(~c"0123456789ABCDEF") end) |> List.to_string()
  end

  @spec generate_otp() :: binary()
  def generate_otp do
    1..6 |> Enum.map(fn _ -> Enum.random(~c"0123456789") end) |> List.to_string()
  end

  @spec generate_token() :: binary()
  def generate_token, do: UpMessenger.Domain.Jwt.generate(%{phone: "dummy", pin: "dummy", name: "dummy"})

  @spec generate_jwt(map()) :: binary()
  def generate_jwt(%{phone: phone, pin: pin, name: name}), do: UpMessenger.Domain.Jwt.generate(%{phone: phone, pin: pin, name: name})

  @spec valid_two_fa_pin?(binary()) :: boolean()
  def valid_two_fa_pin?(pin) when is_binary(pin), do: String.match?(pin, ~r/^[0-9]{6}$/)
  def valid_two_fa_pin?(_), do: false

  @spec hash_two_fa(binary()) :: {binary(), binary()}
  def hash_two_fa(pin) when is_binary(pin) do
    salt = :crypto.strong_rand_bytes(16) |> Base.encode64(padding: false)
    hash = :crypto.hash(:sha256, salt <> pin) |> Base.encode64(padding: false)
    # 100k iterasi simulasi PBKDF2 sederhana
    iterated = Enum.reduce(1..100_000, hash, fn _, acc -> :crypto.hash(:sha256, acc) |> Base.encode64(padding: false) end)
    {iterated, salt}
  end

  @spec verify_two_fa(binary(), binary(), binary()) :: boolean()
  def verify_two_fa(pin, hash, salt) when is_binary(pin) and is_binary(hash) and is_binary(salt) do
    test_hash = :crypto.hash(:sha256, salt <> pin) |> Base.encode64(padding: false)
    iterated = Enum.reduce(1..100_000, test_hash, fn _, acc -> :crypto.hash(:sha256, acc) |> Base.encode64(padding: false) end)
    iterated == hash
  end
  def verify_two_fa(_, _, _), do: false
end
