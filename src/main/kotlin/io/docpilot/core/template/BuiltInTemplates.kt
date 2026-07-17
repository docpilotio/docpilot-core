package io.docpilot.core.template

import io.docpilot.core.model.prompt.PromptTemplate

/** Built-in templates shipped by DocPilot. */
object BuiltInTemplates {
    val ARCHITECTURE: DocumentTemplate = template(
        id = "architecture",
        name = "Architecture Document",
        description = "Explains the system context, components, responsibilities, dependencies, and key design decisions.",
        sectionTitle = "Architecture",
        instructions = """
            Generate a concise architecture document from the supplied project knowledge.
            Describe the system context, major components, responsibilities, dependency directions,
            important runtime flows, constraints, risks, and evidence-backed design decisions.
            Clearly distinguish observed facts from inference.
        """.trimIndent(),
    )

    val ADR: DocumentTemplate = template(
        id = "adr",
        name = "Architecture Decision Record",
        description = "Produces an ADR describing context, decision, alternatives, and consequences.",
        sectionTitle = "Architecture Decision Record",
        instructions = """
            Generate an Architecture Decision Record using this supplied decision data:

            Title: {{adr.title}}
            Status: {{adr.status}}

            Context:
            {{adr.context}}

            Decision:
            {{adr.decision}}

            Considered alternatives:
            {{adr.alternatives}}

            Consequences:
            {{adr.consequences}}

            Structure the result with Status, Context, Decision, Alternatives, and Consequences sections.
            Use the project knowledge as supporting evidence, clearly distinguish observed facts from
            inference, and do not replace the explicitly supplied decision with an invented one.
        """.trimIndent(),
    )

    val API: DocumentTemplate = template(
        id = "api",
        name = "API Reference",
        description = "Documents public APIs, contracts, inputs, outputs, errors, and examples.",
        sectionTitle = "API Reference",
        instructions = """
            Generate an API reference from the supplied project knowledge.
            Document public contracts, parameters, return values, errors, lifecycle expectations,
            extension points, and minimal usage examples. Avoid documenting implementation details
            as public guarantees unless the evidence explicitly establishes them.
        """.trimIndent(),
    )

    val README: DocumentTemplate = template(
        id = "readme",
        name = "Project README",
        description = "Produces an onboarding-oriented project README.",
        sectionTitle = "README",
        instructions = """
            Generate a project README from the supplied project knowledge.
            Include purpose, capabilities, requirements, setup, build and test commands,
            basic usage, project structure, current limitations, and contribution guidance.
            Use only commands and capabilities supported by the evidence.
        """.trimIndent(),
    )

    val ALL: List<DocumentTemplate> = listOf(
        ARCHITECTURE,
        ADR,
        API,
        README,
    ).sortedBy(DocumentTemplate::id)

    fun registry(): TemplateRegistry = InMemoryTemplateRegistry(ALL)

    private fun template(
        id: String,
        name: String,
        description: String,
        sectionTitle: String,
        instructions: String,
    ): DocumentTemplate = DocumentTemplate(
        id = TemplateId(id),
        name = name,
        description = description,
        prompt = PromptTemplate(
            name = "document-$id",
            content = """
                $instructions

                Project knowledge:
                {{knowledge}}
            """.trimIndent(),
        ),
        defaultSectionTitle = sectionTitle,
        metadata = mapOf("template.id" to id),
    )
}
