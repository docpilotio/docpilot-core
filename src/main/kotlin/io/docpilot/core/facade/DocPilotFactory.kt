package io.docpilot.core.facade

import io.docpilot.core.api.AiProvider
import io.docpilot.core.document.service.DefaultDocumentService
import io.docpilot.core.generation.DefaultGenerationPipeline
import io.docpilot.core.generator.adr.DefaultAdrGenerator
import io.docpilot.core.generator.architecture.DefaultArchitectureGenerator
import io.docpilot.core.knowledge.DefaultKnowledgeRetriever
import io.docpilot.core.prompt.DefaultPromptRenderer
import io.docpilot.core.template.BuiltInTemplates

/** Creates the standard in-process DocPilot composition for one AI provider. */
object DocPilotFactory {
    fun create(aiProvider: AiProvider): DocPilot {
        val templates = BuiltInTemplates.registry()
        val pipeline = DefaultGenerationPipeline(
            knowledgeRetriever = DefaultKnowledgeRetriever(),
            promptRenderer = DefaultPromptRenderer(),
            aiProvider = aiProvider,
        )
        val documents = DefaultDocumentService(pipeline)

        return DefaultDocPilot(
            architectureGenerator = DefaultArchitectureGenerator(templates, documents),
            adrGenerator = DefaultAdrGenerator(templates, documents),
        )
    }
}
