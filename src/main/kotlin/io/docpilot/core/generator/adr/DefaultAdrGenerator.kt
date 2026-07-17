package io.docpilot.core.generator.adr

import io.docpilot.core.document.Document
import io.docpilot.core.document.service.DocumentRequest
import io.docpilot.core.document.service.DocumentService
import io.docpilot.core.generation.GenerationRequest
import io.docpilot.core.model.prompt.PromptVariables
import io.docpilot.core.template.BuiltInTemplates
import io.docpilot.core.template.TemplateRegistry

/** ADR-specific facade over the template, generation, and document services. */
class DefaultAdrGenerator(
    private val templates: TemplateRegistry,
    private val documents: DocumentService,
) : AdrGenerator {

    override fun generate(request: AdrGenerationRequest): Document {
        val template = templates.get(BuiltInTemplates.ADR.id)
        val adrVariables = mapOf(
            AdrGenerationRequest.TITLE_VARIABLE to request.title,
            AdrGenerationRequest.STATUS_VARIABLE to request.status.value,
            AdrGenerationRequest.CONTEXT_VARIABLE to request.context,
            AdrGenerationRequest.DECISION_VARIABLE to request.decision,
            AdrGenerationRequest.ALTERNATIVES_VARIABLE to request.alternatives,
            AdrGenerationRequest.CONSEQUENCES_VARIABLE to request.consequences,
        )

        return documents.generate(
            DocumentRequest(
                title = request.title,
                generation = GenerationRequest(
                    knowledge = request.knowledge,
                    query = request.query,
                    template = template.prompt,
                    variables = PromptVariables(request.variables.values + adrVariables),
                    modelId = request.modelId,
                    temperature = request.temperature,
                    maxOutputTokens = request.maxOutputTokens,
                    responseFormat = request.responseFormat,
                    metadata = request.generationMetadata,
                ),
                sectionId = AdrMetadata.SECTION_ID,
                sectionTitle = template.defaultSectionTitle,
                format = request.format,
                metadataType = AdrMetadata.DOCUMENT_TYPE,
                metadata = request.documentMetadata +
                    template.metadata +
                    mapOf(
                        AdrMetadata.GENERATOR_KEY to AdrMetadata.GENERATOR_VALUE,
                        AdrMetadata.STATUS_KEY to request.status.value,
                        AdrMetadata.TITLE_KEY to request.title,
                    ),
            ),
        )
    }
}
