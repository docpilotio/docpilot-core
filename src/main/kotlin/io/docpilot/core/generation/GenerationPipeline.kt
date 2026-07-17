package io.docpilot.core.generation

fun interface GenerationPipeline {
    fun generate(request: GenerationRequest): GenerationResult
}
