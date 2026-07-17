package io.docpilot.core.generator.architecture

import io.docpilot.core.document.Document

fun interface ArchitectureGenerator {
    fun generate(request: ArchitectureGenerationRequest): Document
}
