package io.docpilot.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DocPilotCoreTest {

    @Test
    fun `exposes the current development version`() {
        assertEquals("0.1.0-SNAPSHOT", DocPilotCore.VERSION)
    }
}
