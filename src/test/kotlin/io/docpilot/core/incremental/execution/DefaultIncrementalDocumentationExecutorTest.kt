package io.docpilot.core.incremental.execution

import io.docpilot.core.api.SpecificationRenderer
import io.docpilot.core.api.DocumentationArtifactDescriptor
import io.docpilot.core.api.DocumentationArtifactId
import io.docpilot.core.api.DocumentationArtifactKind
import io.docpilot.core.api.SelectiveSpecificationRenderer
import io.docpilot.core.incremental.specification.ChangeKind
import io.docpilot.core.incremental.specification.IncrementalUpdateAction
import io.docpilot.core.incremental.specification.IncrementalUpdatePlan
import io.docpilot.core.incremental.specification.IncrementalUpdateTarget
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultIncrementalDocumentationExecutorTest {
    @Test
    fun `skips renderer and writer when no changes exist`() {
        var renderCalls = 0
        val writer = RecordingWriter()
        val executor = DefaultIncrementalDocumentationExecutor(
            renderer = SpecificationRenderer {
                renderCalls += 1
                listOf(artifact("new"))
            },
            writer = writer,
        )

        val result = executor.execute(request(updatePlan = IncrementalUpdatePlan.EMPTY))

        assertEquals(IncrementalExecutionMode.NO_CHANGES, result.mode)
        assertEquals(0, renderCalls)
        assertTrue(writer.writes.isEmpty())
        assertTrue(writer.deletes.isEmpty())
        assertFalse(result.writePerformed)
    }

    @Test
    fun `creates updates deletes and keeps artifacts deterministically`() {
        val writer = RecordingWriter()
        val rendered = listOf(
            artifact("updated", "docs/b.md"),
            artifact("same", "docs/a.md"),
            artifact("created", "docs/c.md"),
        )
        val executor = DefaultIncrementalDocumentationExecutor(SpecificationRenderer { rendered }, writer)

        val result = executor.execute(
            request(
                existingArtifacts = listOf(
                    existing("removed", "docs/d.md"),
                    existing("old", "docs/b.md"),
                    existing("same", "docs/a.md"),
                ),
            ),
        )

        assertEquals(IncrementalExecutionMode.INCREMENTAL_UPDATE, result.mode)
        assertEquals(listOf("docs/c.md"), result.createdArtifacts)
        assertEquals(listOf("docs/b.md"), result.updatedArtifacts)
        assertEquals(listOf("docs/d.md"), result.deletedArtifacts)
        assertEquals(listOf("docs/a.md"), result.keptArtifacts)
        assertEquals(listOf("docs/b.md", "docs/c.md"), writer.writes.map { it.relativePath })
        assertEquals(listOf("docs/d.md"), writer.deletes)
        assertTrue(result.writePerformed)
        assertEquals(result.artifactActions.sortedBy { it.relativePath }, result.artifactActions)
    }

    @Test
    fun `uses full regeneration when previous specification is missing`() {
        val writer = RecordingWriter()
        val executor = DefaultIncrementalDocumentationExecutor(
            SpecificationRenderer { listOf(artifact("generated")) },
            writer,
        )

        val result = executor.execute(request(previousSpecification = null, existingArtifacts = emptyList()))

        assertEquals(IncrementalExecutionMode.FULL_REGENERATION, result.mode)
        assertEquals(IncrementalFallbackReason.PREVIOUS_SPECIFICATION_MISSING, result.fallbackReason)
        assertEquals(listOf("docs/project.md"), result.createdArtifacts)
    }

    @Test
    fun `uses full regeneration when schema versions differ`() {
        val executor = DefaultIncrementalDocumentationExecutor(
            SpecificationRenderer { listOf(artifact("generated")) },
            RecordingWriter(),
        )

        val result = executor.execute(
            request(
                previousSpecification = specification("0.2"),
                currentSpecification = specification("0.3"),
                existingArtifacts = listOf(existing("old")),
            ),
        )

        assertEquals(IncrementalExecutionMode.FULL_REGENERATION, result.mode)
        assertEquals(IncrementalFallbackReason.SCHEMA_VERSION_MISMATCH, result.fallbackReason)
    }

    @Test
    fun `uses full regeneration when incremental output is missing`() {
        val executor = DefaultIncrementalDocumentationExecutor(
            SpecificationRenderer { listOf(artifact("generated")) },
            RecordingWriter(),
        )

        val result = executor.execute(request(existingArtifacts = emptyList()))

        assertEquals(IncrementalExecutionMode.FULL_REGENERATION, result.mode)
        assertEquals(IncrementalFallbackReason.EXISTING_DOCUMENTATION_MISSING, result.fallbackReason)
    }

    @Test
    fun `returns failed when renderer fails`() {
        val executor = DefaultIncrementalDocumentationExecutor(
            SpecificationRenderer { error("render failed") },
            RecordingWriter(),
        )

        val result = executor.execute(request(existingArtifacts = listOf(existing("old"))))

        assertEquals(IncrementalExecutionMode.FAILED, result.mode)
        assertEquals("render failed", result.errorMessage)
    }

    @Test
    fun `returns failed when writer fails`() {
        val executor = DefaultIncrementalDocumentationExecutor(
            SpecificationRenderer { listOf(artifact("new")) },
            object : DocumentationArtifactWriter {
                override fun write(artifact: RenderedArtifact) = error("write failed")
                override fun delete(relativePath: String) = Unit
            },
        )

        val result = executor.execute(request(existingArtifacts = listOf(existing("old"))))

        assertEquals(IncrementalExecutionMode.FAILED, result.mode)
        assertEquals("write failed", result.errorMessage)
    }

    @Test
    fun `does not rewrite unchanged rendered artifact`() {
        val writer = RecordingWriter()
        val executor = DefaultIncrementalDocumentationExecutor(
            SpecificationRenderer { listOf(artifact("same")) },
            writer,
        )

        val result = executor.execute(request(existingArtifacts = listOf(existing("same"))))

        assertEquals(IncrementalExecutionMode.INCREMENTAL_UPDATE, result.mode)
        assertEquals(listOf("docs/project.md"), result.keptArtifacts)
        assertFalse(result.writePerformed)
        assertTrue(writer.writes.isEmpty())
    }

    @Test
    fun `selective renderer receives only changed artifact ids and never deletes orphans`() {
        val writer = RecordingWriter()
        val renderer = RecordingSelectiveRenderer()
        val executor = DefaultIncrementalDocumentationExecutor(renderer, writer)

        val result = executor.execute(
            request(
                existingArtifacts = listOf(
                    existing("old-a", "docs/a.md"),
                    existing("same-b", "docs/b.md"),
                    existing("legacy", "docs/legacy.md"),
                ),
            ),
        )

        assertEquals(setOf(DocumentationArtifactId("component:a")), renderer.renderedIds)
        assertEquals(listOf("docs/a.md"), writer.writes.map { it.relativePath })
        assertTrue(writer.deletes.isEmpty())
        assertEquals(listOf("docs/b.md"), result.keptArtifacts)
        assertEquals(64, result.artifactPlanSha256?.length)
    }

    private fun request(
        previousSpecification: ProjectSpecification? = specification(),
        currentSpecification: ProjectSpecification = specification(),
        updatePlan: IncrementalUpdatePlan = changedPlan(),
        existingArtifacts: List<ExistingDocumentationArtifact> = listOf(existing("old")),
    ) = IncrementalDocumentationExecutionRequest(
        previousSpecification = previousSpecification,
        currentSpecification = currentSpecification,
        updatePlan = updatePlan,
        existingArtifacts = existingArtifacts,
    )

    private fun changedPlan() = IncrementalUpdatePlan(
        actions = listOf(
            IncrementalUpdateAction(
                target = IncrementalUpdateTarget.TYPE,
                id = "type:sample",
                changeKind = ChangeKind.MODIFIED,
            ),
        ),
        changedTypeIds = listOf("type:sample"),
    )

    private fun specification(schemaVersion: String = "0.3") = ProjectSpecification(
        schemaVersion = schemaVersion,
        project = ProjectDescriptor("project:sample", "Sample"),
    )

    private fun artifact(content: String, path: String = "docs/project.md") = RenderedArtifact(
        relativePath = path,
        mediaType = "text/markdown",
        content = content,
    )

    private fun existing(content: String, path: String = "docs/project.md") = ExistingDocumentationArtifact(
        relativePath = path,
        mediaType = "text/markdown",
        content = content,
    )

    private class RecordingWriter : DocumentationArtifactWriter {
        val writes = mutableListOf<RenderedArtifact>()
        val deletes = mutableListOf<String>()

        override fun write(artifact: RenderedArtifact) {
            writes += artifact
        }

        override fun delete(relativePath: String) {
            deletes += relativePath
        }
    }

    private inner class RecordingSelectiveRenderer : SelectiveSpecificationRenderer {
        var renderedIds: Set<DocumentationArtifactId> = emptySet()

        override fun describe(specification: ProjectSpecification) = listOf(
            DocumentationArtifactDescriptor(
                DocumentationArtifactId("component:a"),
                "docs/a.md",
                "text/markdown",
                DocumentationArtifactKind.COMPONENT,
                listOf("type:sample"),
            ),
            DocumentationArtifactDescriptor(
                DocumentationArtifactId("component:b"),
                "docs/b.md",
                "text/markdown",
                DocumentationArtifactKind.COMPONENT,
                listOf("type:unrelated"),
            ),
        )

        override fun render(
            specification: ProjectSpecification,
            artifactIds: Set<DocumentationArtifactId>,
        ): List<RenderedArtifact> {
            renderedIds = artifactIds
            return artifactIds.map {
                when (it.value) {
                    "component:a" -> artifact("new-a", "docs/a.md")
                    else -> artifact("same-b", "docs/b.md")
                }
            }
        }
    }
}
