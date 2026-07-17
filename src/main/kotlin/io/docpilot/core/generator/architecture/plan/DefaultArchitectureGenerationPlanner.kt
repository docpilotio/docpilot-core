package io.docpilot.core.generator.architecture.plan

import io.docpilot.core.generator.architecture.ArchitectureGenerationRequest

/**
 * Provider-independent default architecture plan.
 */
class DefaultArchitectureGenerationPlanner :
    ArchitectureGenerationPlanner {

    override fun plan(
        request: ArchitectureGenerationRequest,
    ): ArchitectureGenerationPlan {
        // The argument is intentionally retained for future deterministic
        // customization based on request fields.
        @Suppress("UNUSED_VARIABLE")
        val ignored = request

        return ArchitectureGenerationPlan(
            sections = DEFAULT_SECTIONS,
        )
    }

    companion object {
        val DEFAULT_SECTIONS: List<ArchitectureSection> = listOf(
            ArchitectureSection(
                id = ArchitectureSectionId("executive-summary"),
                title = "Executive Summary",
                instruction =
                    "Summarize the system purpose, architectural style, " +
                        "major responsibilities, and the most important " +
                        "architectural observations.",
                order = 1,
                maxOutputTokens = 512,
            ),
            ArchitectureSection(
                id = ArchitectureSectionId("system-context"),
                title = "System Context",
                instruction =
                    "Describe system boundaries, primary actors, external " +
                        "systems, execution environments, and major inputs " +
                        "and outputs.",
                order = 2,
                maxOutputTokens = 768,
            ),
            ArchitectureSection(
                id = ArchitectureSectionId(
                    "components-and-responsibilities",
                ),
                title = "Components and Responsibilities",
                instruction =
                    "Identify the major modules, components, services, and " +
                        "their responsibilities. Explain the most important " +
                        "collaboration boundaries.",
                order = 3,
                maxOutputTokens = 1_024,
            ),
            ArchitectureSection(
                id = ArchitectureSectionId("data-and-control-flow"),
                title = "Data and Control Flow",
                instruction =
                    "Explain important runtime flows, state transitions, " +
                        "data ownership, and control transfer between major " +
                        "components.",
                order = 4,
                maxOutputTokens = 1_024,
            ),
            ArchitectureSection(
                id = ArchitectureSectionId(
                    "dependencies-and-integrations",
                ),
                title = "Dependencies and Integrations",
                instruction =
                    "Describe internal dependencies, external libraries, " +
                        "platform integrations, infrastructure dependencies, " +
                        "and relevant coupling.",
                order = 5,
                maxOutputTokens = 768,
            ),
            ArchitectureSection(
                id = ArchitectureSectionId(
                    "quality-attributes-and-constraints",
                ),
                title = "Quality Attributes and Constraints",
                instruction =
                    "Assess performance, reliability, security, " +
                        "maintainability, testability, portability, and " +
                        "known technical or platform constraints.",
                order = 6,
                maxOutputTokens = 768,
            ),
            ArchitectureSection(
                id = ArchitectureSectionId(
                    "risks-and-recommendations",
                ),
                title = "Risks and Recommendations",
                instruction =
                    "Identify evidence-based architectural risks, unknowns, " +
                        "and prioritized recommendations. Clearly separate " +
                        "observed facts from inferred concerns.",
                order = 7,
                maxOutputTokens = 768,
            ),
        )
    }
}
