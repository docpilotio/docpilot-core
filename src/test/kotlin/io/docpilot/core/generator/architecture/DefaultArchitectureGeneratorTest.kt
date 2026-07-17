package io.docpilot.core.generator.architecture

import io.docpilot.core.document.Document
import io.docpilot.core.document.DocumentFormat
import io.docpilot.core.document.DocumentSection
import io.docpilot.core.document.service.DocumentRequest
import io.docpilot.core.document.service.DocumentService
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiResponseFormat
import io.docpilot.core.model.evidence.EvidenceCollection
import io.docpilot.core.model.knowledge.KnowledgeBuildResult
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.KnowledgeQuery
import io.docpilot.core.model.prompt.PromptVariables
import io.docpilot.core.template.BuiltInTemplates
import io.docpilot.core.template.InMemoryTemplateRegistry
import io.docpilot.core.template.TemplateId
import io.docpilot.core.template.TemplateNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DefaultArchitectureGeneratorTest {

    @Test
    fun `builds architecture document request from built in template`() {
        var captured: DocumentRequest? = null
        val expected = Document(
            title = "System Architecture",
            sections = listOf(
                DocumentSection(
                    id = "architecture",
                    title = "Architecture",
                    content = "generated",
                ),
            ),
        )
        val service = DocumentService { request ->
            captured = request
            expected
        }
        val generator = DefaultArchitectureGenerator(
            templates = BuiltInTemplates.registry(),
            documents = service,
        )
        val knowledge = emptyKnowledge()
        val query = KnowledgeQuery(name = "docpilot", limit = 10)
        val variables = PromptVariables(mapOf("audience" to "maintainers"))

        val actual = generator.generate(
            ArchitectureGenerationRequest(
                knowledge = knowledge,
                modelId = AiModelId("test-model"),
                title = "System Architecture",
                query = query,
                variables = variables,
                format = DocumentFormat.PLAIN_TEXT,
                temperature = 0.2,
                maxOutputTokens = 2048,
                responseFormat = AiResponseFormat.JSON,
                generationMetadata = mapOf("trace" to "rfc-0022"),
                documentMetadata = mapOf("project" to "docpilot-core"),
            ),
        )

        assertSame(expected, actual)
        val request = requireNotNull(captured)
        assertEquals("System Architecture", request.title)
        assertEquals("architecture", request.sectionId)
        assertEquals("Architecture", request.sectionTitle)
        assertEquals(DocumentFormat.PLAIN_TEXT, request.format)
        assertEquals("architecture-document", request.metadataType)
        assertEquals(
            mapOf(
                "project" to "docpilot-core",
                "template.id" to "architecture",
                "document.generator" to "architecture",
            ),
            request.metadata,
        )

        val generation = request.generation
        assertSame(knowledge, generation.knowledge)
        assertSame(query, generation.query)
        assertSame(variables, generation.variables)
        assertEquals("document-architecture", generation.template.name)
        assertEquals(AiModelId("test-model"), generation.modelId)
        assertEquals(0.2, generation.temperature)
        assertEquals(2048, generation.maxOutputTokens)
        assertEquals(AiResponseFormat.JSON, generation.responseFormat)
        assertEquals(mapOf("trace" to "rfc-0022"), generation.metadata)
    }

    @Test
    fun `uses architecture defaults`() {
        var captured: DocumentRequest? = null
        val service = DocumentService { request ->
            captured = request
            Document(
                title = request.title,
                sections = emptyList(),
                format = request.format,
            )
        }
        val generator = DefaultArchitectureGenerator(
            templates = BuiltInTemplates.registry(),
            documents = service,
        )

        generator.generate(
            ArchitectureGenerationRequest(
                knowledge = emptyKnowledge(),
                modelId = AiModelId("test-model"),
            ),
        )

        val request = requireNotNull(captured)
        assertEquals("Architecture", request.title)
        assertEquals(KnowledgeQuery(name = "architecture"), request.generation.query)
        assertEquals(DocumentFormat.MARKDOWN, request.format)
        assertEquals(PromptVariables.EMPTY, request.generation.variables)
    }

    @Test
    fun `fails when architecture template is not registered`() {
        val generator = DefaultArchitectureGenerator(
            templates = InMemoryTemplateRegistry(),
            documents = DocumentService { error("must not be called") },
        )

        val error = assertFailsWith<TemplateNotFoundException> {
            generator.generate(
                ArchitectureGenerationRequest(
                    knowledge = emptyKnowledge(),
                    modelId = AiModelId("test-model"),
                ),
            )
        }

        assertEquals(TemplateId("architecture"), error.templateId)
    }

    @Test
    fun `template metadata cannot be overridden by caller metadata`() {
        var captured: DocumentRequest? = null
        val generator = DefaultArchitectureGenerator(
            templates = BuiltInTemplates.registry(),
            documents = DocumentService { request ->
                captured = request
                Document(request.title, emptyList())
            },
        )

        generator.generate(
            ArchitectureGenerationRequest(
                knowledge = emptyKnowledge(),
                modelId = AiModelId("test-model"),
                documentMetadata = mapOf(
                    "template.id" to "caller-value",
                    "document.generator" to "caller-value",
                ),
            ),
        )

        assertEquals("architecture", captured?.metadata?.get("template.id"))
        assertEquals("architecture", captured?.metadata?.get("document.generator"))
    }

    private fun emptyKnowledge(): KnowledgeBuildResult = KnowledgeBuildResult(
        graph = KnowledgeGraph(
            nodes = emptyList(),
            edges = emptyList(),
        ),
        evidence = EvidenceCollection(emptyList()),
    )
}
