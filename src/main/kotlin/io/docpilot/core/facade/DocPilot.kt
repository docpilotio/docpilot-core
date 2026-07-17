package io.docpilot.core.facade

import io.docpilot.core.document.Document
import io.docpilot.core.generator.adr.AdrGenerationRequest
import io.docpilot.core.generator.architecture.ArchitectureGenerationRequest

/** Stable facade for DocPilot document generation entry points. */
interface DocPilot {
    fun generateArchitecture(request: ArchitectureGenerationRequest): Document

    fun generateAdr(request: AdrGenerationRequest): Document
}
