defmodule UpMessenger.MixProject do
  @moduledoc "Mix project UpMessenger relay server (production, low RAM)."
  use Mix.Project
  def project, do: [app: :up_messenger, version: "1.0.0", elixir: "~> 1.14", start_permanent: Mix.env() == :prod, deps: deps()]
  def application, do: [extra_applications: [:logger, :crypto, :ssl, :inets], mod: {UpMessenger.Application, []}]
  defp deps, do: [{:thousand_island, "~> 1.3"}, {:jason, "~> 1.4"}, {:ex_phone_number, "~> 0.4"}]
end
