package io.docpilot.core.generator.adr

import io.docpilot.core.document.Document
import io.docpilot.core.document.DocumentFormat
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

class DefaultAdrGeneratorTest {

    @Test
    fun `builds ADR document request from explicit decision data`() {
        var captured: DocumentRequest? = null
        val expected = Document(title = "Use repository pattern", sections = emptyList())
        val generator = DefaultAdrGenerator(
            templates = BuiltInTemplates.registry(),
            documents = DocumentService { request ->
                captured = request
                expected
            },
        )
        val knowledge = emptyKnowledge()
        val query = KnowledgeQuery(name = "repository-decision", limit = 12)

        val actual = generator.generate(
            AdrGenerationRequest(
                knowledge = knowledge,
                modelId = AiModelId("test-model"),
                title = "Use repository pattern",
                status = AdrStatus.PROPOSED,
                context = "Data access is duplicated across features.",
                decision = "Introduce repository interfaces at the domain boundary.",
                alternatives = "Keep direct data-source access; use active record.",
                consequences = "More abstractions, but testable domain logic.",
                query = query,
                variables = PromptVariables(mapOf("audience" to "maintainers")),
                format = DocumentFormat.PLAIN_TEXT,
                temperature = 0.1,
                maxOutputTokens = 1500,
                responseFormat = AiResponseFormat.JSON,
                generationMetadata = mapOf("trace" to "rfc-0023"),
                documentMetadata = mapOf("project" to "docpilot-core"),
            ),
        )

        assertSame(expected, actual)
        val request = requireNotNull(captured)
        assertEquals("Use repository pattern", request.title)
        assertEquals("adr", request.sectionId)
        assertEquals("Architecture Decision Record", request.sectionTitle)
        assertEquals(DocumentFormat.PLAIN_TEXT, request.format)
        assertEquals("adr", request.metadataType)
        assertEquals(
            mapOf(
                "project" to "docpilot-core",
                "template.id" to "adr",
                "document.generator" to "adr",
                "adr.status" to "proposed",
                "adr.title" to "Use repository pattern",
            ),
            request.metadata,
        )

        val generation = request.generation
        assertSame(knowledge, generation.knowledge)
        assertSame(query, generation.query)
        assertEquals("document-adr", generation.template.name)
        assertEquals("maintainers", generation.variables["audience"])
        assertEquals("Use repository pattern", generation.variables["adr.title"])
        assertEquals("proposed", generation.variables["adr.status"])
        assertEquals("Data access is duplicated across features.", generation.variables["adr.context"])
        assertEquals("Introduce repository interfaces at the domain boundary.", generation.variables["adr.decision"])
        assertEquals("Keep direct data-source access; use active record.", generation.variables["adr.alternatives"])
        assertEquals("More abstractions, but testable domain logic.", generation.variables["adr.consequences"])
        assertEquals(AiModelId("test-model"), generation.modelId)
        assertEquals(0.1, generation.temperature)
        assertEquals(1500, generation.maxOutputTokens)
        assertEquals(AiResponseFormat.JSON, generation.responseFormat)
        assertEquals(mapOf("trace" to "rfc-0023"), generation.metadata)
    }

    @Test
    fun `uses accepted status and ADR defaults`() {
        var captured: DocumentRequest? = null
        val generator = DefaultAdrGenerator(
            templates = BuiltInTemplates.registry(),
            documents = DocumentService { request ->
                captured = request
                Document(request.title, emptyList(), request.format)
            },
        )

        generator.generate(minimalRequest())

        val request = requireNotNull(captured)
        assertEquals("accepted", request.metadata["adr.status"])
        assertEquals("accepted", request.generation.variables["adr.status"])
        assertEquals(KnowledgeQuery(name = "adr"), request.generation.query)
        assertEquals(DocumentFormat.MARKDOWN, request.format)
        assertEquals("No alternatives were supplied.", request.generation.variables["adr.alternatives"])
    }

    @Test
    fun `fails when ADR template is not registered`() {
        val generator = DefaultAdrGenerator(
            templates = InMemoryTemplateRegistry(),
            documents = DocumentService { error("must not be called") },
        )

        val error = assertFailsWith<TemplateNotFoundException> {
            generator.generate(minimalRequest())
        }

        assertEquals(TemplateId("adr"), error.templateId)
    }

    @Test
    fun `generator metadata cannot be overridden by caller metadata`() {
        var captured: DocumentRequest? = null
        val generator = DefaultAdrGenerator(
            templates = BuiltInTemplates.registry(),
            documents = DocumentService { request ->
                captured = request
                Document(request.title, emptyList())
            },
        )

        generator.generate(
            minimalRequest().copy(
                status = AdrStatus.DEPRECATED,
                documentMetadata = mapOf(
                    "template.id" to "caller",
                    "document.generator" to "caller",
                    "adr.status" to "caller",
                    "adr.title" to "caller",
                ),
            ),
        )

        assertEquals("adr", captured?.metadata?.get("template.id"))
        assertEquals("adr", captured?.metadata?.get("document.generator"))
        assertEquals("deprecated", captured?.metadata?.get("adr.status"))
        assertEquals("Adopt immutable document model", captured?.metadata?.get("adr.title"))
    }

    @Test
    fun `rejects caller attempts to redefine reserved ADR variables`() {
        assertFailsWith<IllegalArgumentException> {
            minimalRequest().copy(
                variables = PromptVariables(mapOf("adr.status" to "caller")),
            )
        }
    }

    private fun minimalRequest(): AdrGenerationRequest = AdrGenerationRequest(
        knowledge = emptyKnowledge(),
        modelId = AiModelId("test-model"),
        title = "Adopt immutable document model",
        context = "Generated output needs a provider-neutral representation.",
        decision = "Represent generated documents with immutable Kotlin values.",
        consequences = "Renderers and generators share one stable contract.",
    )

    private fun emptyKnowledge(): KnowledgeBuildResult = KnowledgeBuildResult(
        graph = KnowledgeGraph(nodes = emptyList(), edges = emptyList()),
        evidence = EvidenceCollection(emptyList()),
    )
}
