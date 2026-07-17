package io.docpilot.core.incremental.prompt

/** Converts an RFC-0030 generation job into a provider-neutral prompt plan. */
fun interface IncrementalPromptBuilder {
    fun build(request: PromptBuildRequest): PromptPlan
}
