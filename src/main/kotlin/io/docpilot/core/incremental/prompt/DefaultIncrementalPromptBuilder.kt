package io.docpilot.core.incremental.prompt

/** Default RFC-0031 provider-neutral prompt builder. */
class DefaultIncrementalPromptBuilder(
    private val tokenEstimator: PromptTokenEstimator =
        DeterministicPromptTokenEstimator(),
    contextSelector: PromptContextSelector? = null,
) : IncrementalPromptBuilder {

    private val contextSelector: PromptContextSelector =
        contextSelector ?: DefaultPromptContextSelector(tokenEstimator)

    override fun build(request: PromptBuildRequest): PromptPlan {
        val systemInstruction = SYSTEM_INSTRUCTION
        val taskInstruction = taskInstruction(request)
        val constraints = constraintsFor(request.job.section.id.value)
        val outputContract = PromptOutputContract(
            format = PromptOutputFormat.MARKDOWN_SECTION,
            sectionId = request.job.section.id,
            includeHeading = true,
            allowAdditionalSections = false,
        )

        val fixedTokens = estimateFixed(
            systemInstruction = systemInstruction,
            taskInstruction = taskInstruction,
            constraints = constraints,
            outputContract = outputContract,
        )
        val availableContextTokens = request.job.contextTokenBudget - fixedTokens
        if (availableContextTokens < MIN_CONTEXT_TOKENS) {
            throw PromptBuildException.InsufficientTokenBudget(
                availableTokens = request.job.contextTokenBudget,
                requiredTokens = fixedTokens + MIN_CONTEXT_TOKENS,
            )
        }

        val selection = contextSelector.select(
            request = request,
            budget = PromptContextBudget(availableContextTokens),
        )
        val totalEstimate = fixedTokens + selection.estimatedTokens

        return PromptPlan(
            systemInstruction = systemInstruction,
            taskInstruction = taskInstruction,
            context = selection.context,
            constraints = constraints,
            outputContract = outputContract,
            estimatedInputTokens = totalEstimate,
            inputTokenBudget = request.job.contextTokenBudget,
            warnings = selection.warnings,
        )
    }

    private fun taskInstruction(request: PromptBuildRequest): String {
        val section = request.job.section
        val action = if (request.previousSectionContent == null) {
            "Create"
        } else {
            "Update"
        }
        val preservation = if (request.previousSectionContent == null) {
            ""
        } else {
            " Preserve unaffected content and apply only evidence-backed changes."
        }
        return "$action only the '${section.title}' section " +
            "(id: ${section.id.value}). ${section.instruction}$preservation"
    }

    private fun constraintsFor(sectionId: String): List<PromptConstraint> =
        buildList {
            add(
                PromptConstraint(
                    "evidence-only",
                    "Use only the supplied context and evidence; do not invent facts or relationships.",
                ),
            )
            add(
                PromptConstraint(
                    "preserve-identifiers",
                    "Preserve source identifiers, paths, and established project terminology.",
                ),
            )
            add(
                PromptConstraint(
                    "requested-section-only",
                    "Return only the requested section and no additional sections.",
                ),
            )
            add(
                PromptConstraint(
                    "avoid-unnecessary-rewrites",
                    "Do not rewrite unaffected content without an evidence-backed reason.",
                ),
            )
            add(
                PromptConstraint(
                    "markdown-no-fence",
                    "Return Markdown directly without wrapping it in a code fence.",
                ),
            )

            when (sectionId) {
                DEPENDENCIES_SECTION -> {
                    add(
                        PromptConstraint(
                            "dependency-accuracy",
                            "Do not infer library versions or dependencies absent from supplied evidence.",
                        ),
                    )
                    add(
                        PromptConstraint(
                            "direct-vs-transitive",
                            "Do not describe a dependency as direct unless the evidence establishes that fact.",
                        ),
                    )
                }

                SUMMARY_SECTION -> {
                    add(
                        PromptConstraint(
                            "summary-no-detail-duplication",
                            "Summarize architecture outcomes without duplicating lower-level implementation detail.",
                        ),
                    )
                }
            }
        }.sortedBy { it.id }

    private fun estimateFixed(
        systemInstruction: String,
        taskInstruction: String,
        constraints: List<PromptConstraint>,
        outputContract: PromptOutputContract,
    ): Int =
        tokenEstimator.estimate(systemInstruction) +
            tokenEstimator.estimate(taskInstruction) +
            constraints.sumOf {
                tokenEstimator.estimate("${it.id}:${it.instruction}")
            } +
            tokenEstimator.estimate(
                "${outputContract.format}:${outputContract.sectionId.value}:" +
                    "${outputContract.includeHeading}:" +
                    outputContract.allowAdditionalSections,
            )

    companion object {
        private const val MIN_CONTEXT_TOKENS = 16
        private const val DEPENDENCIES_SECTION = "dependencies-and-integrations"
        private const val SUMMARY_SECTION = "executive-summary"
        private const val SYSTEM_INSTRUCTION =
            "You are DocPilot's software architecture documentation assistant. " +
                "Produce precise evidence-grounded documentation without provider-specific assumptions."
    }
}
