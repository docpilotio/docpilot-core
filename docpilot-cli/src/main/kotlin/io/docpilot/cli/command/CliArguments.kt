package io.docpilot.cli.command

internal class CliArguments private constructor(
    private val values: Map<String, String>,
) {
    fun required(name: String): String =
        values[name]?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing required option: --$name")

    fun optional(name: String): String? = values[name]?.takeIf(String::isNotBlank)

    companion object {
        fun parse(tokens: List<String>): CliArguments {
            val values = linkedMapOf<String, String>()
            var index = 0
            while (index < tokens.size) {
                val token = tokens[index]
                require(token.startsWith("--")) { "Unexpected argument: $token" }
                val name = token.removePrefix("--")
                require(name.isNotBlank()) { "Option name must not be blank." }
                require(index + 1 < tokens.size) { "Missing value for option: --$name" }
                val value = tokens[index + 1]
                require(!value.startsWith("--")) { "Missing value for option: --$name" }
                require(values.put(name, value) == null) { "Duplicate option: --$name" }
                index += 2
            }
            return CliArguments(values)
        }
    }
}
