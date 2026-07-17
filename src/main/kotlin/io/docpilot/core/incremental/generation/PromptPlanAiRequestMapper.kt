package io.docpilot.core.incremental.generation

import io.docpilot.core.incremental.planning.GenerationJob
import io.docpilot.core.incremental.prompt.PromptPlan
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiResponseFormat

fun interface PromptPlanAiRequestMapper {
    fun map(promptPlan: PromptPlan, job: GenerationJob, modelId: AiModelId): AiRequest
}

class DefaultPromptPlanAiRequestMapper : PromptPlanAiRequestMapper {
    override fun map(promptPlan: PromptPlan, job: GenerationJob, modelId: AiModelId): AiRequest =
        AiRequest(
            modelId = modelId,
            messages = listOf(
                AiMessage(AiMessageRole.SYSTEM, promptPlan.systemInstruction),
                AiMessage(AiMessageRole.USER, renderUserPrompt(promptPlan)),
            ),
            temperature = 0.0,
            maxOutputTokens = job.section.maxOutputTokens,
            responseFormat = AiResponseFormat.TEXT,
            metadata = mapOf("sectionId" to job.section.id.value),
        )

    private fun renderUserPrompt(plan: PromptPlan): String = buildString {
        appendLine(plan.taskInstruction)
        appendLine()
        appendLine("Constraints:")
        plan.constraints.forEach { appendLine("- [${it.id}] ${it.instruction}") }
        appendLine()
        appendLine("Changed files:")
        plan.context.changedFiles.forEach { appendLine("- ${it.relativePath} (${it.type})") }
        appendLine("Affected knowledge:")
        plan.context.affectedKnowledge.forEach { appendLine("- ${it.id}: ${it.name} (${it.kind}); attributes=${it.attributes}") }
        appendLine("Evidence:")
        plan.context.evidence.forEach { appendLine("- ${it.id} @ ${it.sourcePath}: ${it.summary}") }
        plan.context.previousSectionContent?.let {
            appendLine("Previous section:")
            appendLine(it)
        }
        appendLine()
        append("Return the requested Markdown section only.")
    }
}
