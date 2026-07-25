package io.docpilot.release

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class JunitXmlAggregatorTest {
    @Test
    fun `aggregates suites once in deterministic path order`() {
        val root = Files.createTempDirectory("junit-aggregate")
        Files.writeString(
            root.resolve("TEST-b.xml"),
            """<testsuite tests="2" failures="1" errors="0" skipped="0"/>""",
        )
        Files.writeString(
            root.resolve("TEST-a.xml"),
            """<testsuite tests="3" failures="0" errors="1" skipped="1"/>""",
        )

        val result = JunitXmlAggregator().aggregate(listOf(root), 0)

        assertEquals(TestAggregate(2, 5, 1, 1, 1, true, false), result)
    }

    @Test
    fun `marks old XML as stale`() {
        val root = Files.createTempDirectory("junit-stale")
        Files.writeString(root.resolve("TEST-a.xml"), """<testsuite tests="1"/>""")

        val result = JunitXmlAggregator().aggregate(listOf(root), System.currentTimeMillis() + 10_000)

        assertFalse(result.fresh)
    }

    @Test
    fun `rejects external entity declarations`() {
        val root = Files.createTempDirectory("junit-xxe")
        Files.writeString(
            root.resolve("TEST-a.xml"),
            """<!DOCTYPE x [<!ENTITY xxe SYSTEM "file:///nope">]><testsuite tests="1"/>""",
        )

        assertFailsWith<Exception> { JunitXmlAggregator().aggregate(listOf(root), 0) }
    }
}
