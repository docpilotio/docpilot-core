package io.docpilot.core.generator.architecture

import io.docpilot.core.document.Document
import io.docpilot.core.document.service.DocumentRequest
import io.docpilot.core.document.service.DocumentService
import io.docpilot.core.generation.GenerationRequest
import io.docpilot.core.template.BuiltInTemplates
import io.docpilot.core.template.TemplateRegistry

/**
 * Architecture-specific facade over the generic template, generation, and
 * document service infrastructure.
 */
class DefaultArchitectureGenerator(
    private val templates: TemplateRegistry,
    private val documents: DocumentService,
) : ArchitectureGenerator {

    override fun generate(request: ArchitectureGenerationRequest): Document {
        val template = templates.get(BuiltInTemplates.ARCHITECTURE.id)

        return documents.generate(
            DocumentRequest(
                title = request.title,
                generation = GenerationRequest(
                    knowledge = request.knowledge,
                    query = request.query,
                    template = template.prompt,
                    variables = request.variables,
                    modelId = request.modelId,
                    temperature = request.temperature,
                    maxOutputTokens = request.maxOutputTokens,
                    responseFormat = request.responseFormat,
                    metadata = request.generationMetadata,
                ),
                sectionId = ARCHITECTURE_SECTION_ID,
                sectionTitle = template.defaultSectionTitle,
                format = request.format,
                metadataType = ARCHITECTURE_METADATA_TYPE,
                metadata = request.documentMetadata +
                    template.metadata +
                    mapOf(GENERATOR_METADATA_KEY to GENERATOR_METADATA_VALUE),
            ),
        )
    }

    companion object {
        const val ARCHITECTURE_SECTION_ID: String = "architecture"
        const val ARCHITECTURE_METADATA_TYPE: String = "architecture-document"
        const val GENERATOR_METADATA_KEY: String = "document.generator"
        const val GENERATOR_METADATA_VALUE: String = "architecture"
    }
}
