package io.docpilot.core.facade

import io.docpilot.core.document.Document
import io.docpilot.core.generator.adr.AdrGenerationRequest
import io.docpilot.core.generator.adr.AdrGenerator
import io.docpilot.core.generator.architecture.ArchitectureGenerationRequest
import io.docpilot.core.generator.architecture.ArchitectureGenerator

/** Default facade delegating to specialized document generators. */
class DefaultDocPilot(
    private val architectureGenerator: ArchitectureGenerator,
    private val adrGenerator: AdrGenerator,
) : DocPilot {
    override fun generateArchitecture(request: ArchitectureGenerationRequest): Document =
        architectureGenerator.generate(request)

    override fun generateAdr(request: AdrGenerationRequest): Document =
        adrGenerator.generate(request)
}
