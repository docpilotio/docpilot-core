package io.docpilot.core.scanner

import io.docpilot.core.loader.LocalProjectLoader
import io.docpilot.core.model.ProjectFileType
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSourceScannerTest {

    private val loader = LocalProjectLoader()
    private val scanner = LocalSourceScanner()

    @Test
    fun `classifies supported project files`() {
        val root = Files.createTempDirectory("docpilot-scan")
        root.resolve("settings.gradle.kts").writeText("")
        root.resolve("build.gradle.kts").writeText("")
        root.resolve("README.md").writeText("")

        val source = root.resolve("app/src/main/kotlin/example")
            .createDirectories()
        source.resolve("Main.kt").writeText("class Main")

        val manifest = root.resolve("app/src/main")
            .createDirectories()
        manifest.resolve("AndroidManifest.xml").writeText("<manifest />")

        val resources = root.resolve("app/src/main/res/layout")
            .createDirectories()
        resources.resolve("screen.xml").writeText("<layout />")

        val inventory = scanner.scan(loader.load(root))

        assertEquals(
            1,
            inventory.filesOfType(ProjectFileType.KOTLIN_SOURCE).size,
        )
        assertEquals(
            1,
            inventory.filesOfType(ProjectFileType.GRADLE_SETTINGS).size,
        )
        assertEquals(
            1,
            inventory.filesOfType(ProjectFileType.GRADLE_BUILD).size,
        )
        assertEquals(
            1,
            inventory.filesOfType(ProjectFileType.ANDROID_MANIFEST).size,
        )
        assertEquals(
            1,
            inventory.filesOfType(ProjectFileType.XML_RESOURCE).size,
        )
        assertEquals(
            1,
            inventory.filesOfType(ProjectFileType.MARKDOWN).size,
        )
    }

    @Test
    fun `returns relative paths in deterministic order`() {
        val root = Files.createTempDirectory("docpilot-order")
        root.resolve("z.kt").writeText("")
        root.resolve("a.kt").writeText("")

        val inventory = scanner.scan(loader.load(root))

        assertEquals(
            listOf("a.kt", "z.kt"),
            inventory.files.map { it.relativePath },
        )
    }

    @Test
    fun `ignores generated and tool managed directories`() {
        val root = Files.createTempDirectory("docpilot-exclusions")
        root.resolve("src").createDirectories()
            .resolve("Main.kt").writeText("")

        root.resolve("build/generated").createDirectories()
            .resolve("Generated.kt").writeText("")

        root.resolve(".git").createDirectories()
            .resolve("config").writeText("")

        val inventory = scanner.scan(loader.load(root))

        assertTrue(
            inventory.files.any { it.relativePath == "src/Main.kt" },
        )
        assertFalse(
            inventory.files.any {
                it.relativePath.startsWith("build/")
            },
        )
        assertFalse(
            inventory.files.any {
                it.relativePath.startsWith(".git/")
            },
        )
    }

    @Test
    fun `reports directory and file totals`() {
        val root = Files.createTempDirectory("docpilot-counts")
        root.resolve("src/main").createDirectories()
        root.resolve("src/main/Main.kt").writeText("")

        val inventory = scanner.scan(loader.load(root))

        assertEquals(2, inventory.totalDirectoryCount)
        assertEquals(1, inventory.totalFileCount)
    }
}
