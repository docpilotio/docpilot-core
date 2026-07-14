package io.docpilot.core.api

import io.docpilot.core.model.AnalysisRequest
import io.docpilot.core.model.AnalysisResult
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.RenderedArtifact
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CoreApiContractTest {

    @Test
    fun `analysis engine returns a project specification`() {
        val engine = AnalysisEngine { request ->
            AnalysisResult(
                specification = ProjectSpecification(
                    project = ProjectDescriptor(
                        id = "sample",
                        name = request.projectRoot.fileName.toString(),
                    ),
                ),
            )
        }

        val result = engine.analyze(
            AnalysisRequest(
                projectRoot = Path.of("C:/workspace/sample").toAbsolutePath(),
            ),
        )

        assertEquals("sample", result.specification.project.id)
    }

    @Test
    fun `renderer produces a reviewable artifact`() {
        val renderer = SpecificationRenderer {
            listOf(
                RenderedArtifact(
                    relativePath = "docs/project-summary.md",
                    mediaType = "text/markdown",
                    content = "# ${it.project.name}",
                ),
            )
        }

        val artifacts = renderer.render(
            ProjectSpecification(
                project = ProjectDescriptor(
                    id = "sample",
                    name = "Sample",
                ),
            ),
        )

        assertEquals("# Sample", artifacts.single().content)
    }

    @Test
    fun `plugin validation can report an invalid plugin`() {
        val result: PluginValidationResult =
            PluginValidationResult.Invalid(listOf("Missing configuration"))

        assertIs<PluginValidationResult.Invalid>(result)
    }
}
