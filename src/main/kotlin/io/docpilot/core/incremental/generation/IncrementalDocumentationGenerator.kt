package io.docpilot.core.incremental.generation

fun interface IncrementalDocumentationGenerator {
    fun generate(request: DocumentationGenerationRequest): DocumentationGenerationResult
}
