package io.docpilot.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PluginValidationResultTest {

    @Test
    fun `represents successful validation`() {
        val result: PluginValidationResult =
            PluginValidationResult.Valid

        assertIs<PluginValidationResult.Valid>(result)
    }

    @Test
    fun `represents failed validation`() {
        val result: PluginValidationResult =
            PluginValidationResult.Invalid(
                errors = listOf("Duplicate plugin ID."),
            )

        val invalid =
            assertIs<PluginValidationResult.Invalid>(result)

        assertEquals(
            listOf("Duplicate plugin ID."),
            invalid.errors,
        )
    }

    @Test
    fun `rejects empty validation errors`() {
        assertFailsWith<IllegalArgumentException> {
            PluginValidationResult.Invalid(
                errors = emptyList(),
            )
        }
    }
}
