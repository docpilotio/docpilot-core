package io.docpilot.core.incremental

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultProjectSnapshotBuilderTest {

    private val builder = DefaultProjectSnapshotBuilder()

    @Test
    fun `snapshot normalizes paths and sorts files`() {
        val root = Files.createTempDirectory("docpilot-snapshot")
        root.resolve("a").toFile().mkdirs()
        root.resolve("a/First.kt").writeText("first")
        root.resolve("Second.kt").writeText("second")

        val snapshot = builder.build(
            projectRoot = root,
            relativePaths = listOf(
                "Second.kt",
                "a\\First.kt",
            ),
        )

        assertEquals(
            listOf("Second.kt", "a/First.kt"),
            snapshot.files.map { it.relativePath },
        )
    }

    @Test
    fun `same content produces same fingerprint`() {
        val root = Files.createTempDirectory("docpilot-snapshot")
        root.resolve("Sample.kt").writeText("class Sample")

        val first = builder.build(root, listOf("Sample.kt"))
        val second = builder.build(root, listOf("Sample.kt"))

        assertEquals(
            first.files.single().contentSha256,
            second.files.single().contentSha256,
        )
    }

    @Test
    fun `duplicate normalized paths are rejected`() {
        val root = Files.createTempDirectory("docpilot-snapshot")
        root.resolve("Sample.kt").writeText("class Sample")

        assertFailsWith<IllegalArgumentException> {
            builder.build(
                root,
                listOf(
                    "Sample.kt",
                    "./Sample.kt",
                ),
            )
        }
    }
}
