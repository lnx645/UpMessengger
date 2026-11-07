ExUnit.start()
defmodule UpMessenger.FrameTest do
  use ExUnit.Case
  alias UpMessenger.Interface.Frame
  test "encode lalu decode roundtrip" do
    bin = Frame.encode(0x03, "halo")
    assert {:ok, 0x03, "halo", ""} = Frame.decode(bin)
  end
end
