package io.docpilot.core.generation

import io.docpilot.core.model.ai.AiGenerationResult
import io.docpilot.core.model.knowledge.KnowledgeResult
import io.docpilot.core.model.prompt.RenderedPrompt

data class GenerationResult(
    val knowledge: KnowledgeResult,
    val prompt: RenderedPrompt,
    val ai: AiGenerationResult,
)
