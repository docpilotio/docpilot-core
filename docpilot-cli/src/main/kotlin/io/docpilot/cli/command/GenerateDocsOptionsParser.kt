package io.docpilot.cli.command

import java.nio.file.Path

internal object GenerateDocsOptionsParser {
    fun parse(tokens: List<String>): DocumentationGenerationOptions {
        val values = linkedMapOf<String, MutableList<String>>()
        val flags = linkedSetOf<String>()
        val repeatable = setOf("artifact", "document-type", "enrichment-target")
        val flagNames = setOf("dry-run", "confirm", "full", "json", "enrich")
        val valueNames = setOf("project", "output", "profile", "plan-sha256", "provider", "model") + repeatable
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            require(token.startsWith("--")) { "Unexpected argument: $token" }
            val name = token.removePrefix("--")
            require(name in flagNames || name in valueNames) { "Unknown option: --$name" }
            if (name in flagNames) {
                require(flags.add(name)) { "Duplicate option: --$name" }
                index++
            } else {
                require(index + 1 < tokens.size && !tokens[index + 1].startsWith("--")) {
                    "Missing value for option: --$name"
                }
                val bucket = values.getOrPut(name, ::mutableListOf)
                require(name in repeatable || bucket.isEmpty()) { "Duplicate option: --$name" }
                bucket += tokens[index + 1]
                index += 2
            }
        }
        require("project" in values) { "Missing required option: --project" }
        require("output" in values) { "Missing required option: --output" }
        require(!("dry-run" in flags && "confirm" in flags)) { "--dry-run and --confirm cannot be used together." }
        val selective = values["artifact"].orEmpty().isNotEmpty() || values["document-type"].orEmpty().isNotEmpty()
        require(!("full" in flags && selective)) { "--full cannot be combined with selective options." }
        val mode = if ("confirm" in flags) DocumentationGenerationMode.APPLY else DocumentationGenerationMode.PREVIEW
        val planSha = values["plan-sha256"]?.single()
        require(mode == DocumentationGenerationMode.APPLY || planSha == null) { "--plan-sha256 requires --confirm." }
        planSha?.let { require(it.matches(Regex("[0-9a-f]{64}"))) { "Invalid --plan-sha256 value." } }
        val enrich = "enrich" in flags
        require(enrich || ("provider" !in values && "model" !in values && "enrichment-target" !in values)) {
            "--provider, --model, and --enrichment-target require --enrich."
        }
        require(!enrich || ("provider" in values && "model" in values)) { "--enrich requires --provider and --model." }
        require(!(enrich && mode == DocumentationGenerationMode.PREVIEW)) {
            "Dry-run enrichment does not invoke providers; use --confirm to apply enrichment."
        }
        return DocumentationGenerationOptions(
            projectRoot = Path.of(values.getValue("project").single()),
            outputRoot = Path.of(values.getValue("output").single()),
            profile = values["profile"]?.single() ?: "kotlin-android@1",
            mode = mode,
            full = "full" in flags || !selective,
            artifactIds = values["artifact"].orEmpty().toSet(),
            documentTypes = values["document-type"].orEmpty().map { it.uppercase().replace('-', '_') }.toSet(),
            expectedPlanSha256 = planSha,
            json = "json" in flags,
            enrich = enrich,
            provider = values["provider"]?.single(),
            model = values["model"]?.single(),
            enrichmentTargets = values["enrichment-target"].orEmpty().toSet(),
        )
    }
}
