package io.docpilot.core.ai

import io.docpilot.core.model.ai.AiError
import io.docpilot.core.model.ai.AiErrorCode
import io.docpilot.core.model.ai.AiMessage
import io.docpilot.core.model.ai.AiMessageRole
import io.docpilot.core.model.ai.AiModelId
import io.docpilot.core.model.ai.AiProviderId
import io.docpilot.core.model.ai.AiRequest
import io.docpilot.core.model.ai.AiUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiDomainModelTest {
    @Test
    fun `validates request and identifiers`() {
        val request = AiRequest(
            modelId = AiModelId("local-model"),
            messages = listOf(
                AiMessage(
                    role = AiMessageRole.USER,
                    content = "Explain the architecture.",
                ),
            ),
            temperature = 0.2,
        )

        assertEquals("local-model", request.modelId.value)
        assertFailsWith<IllegalArgumentException> {
            AiProviderId("Invalid Provider")
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(temperature = 3.0)
        }
    }

    @Test
    fun `computes usage totals and validates errors`() {
        assertEquals(
            15,
            AiUsage(
                inputTokens = 10,
                outputTokens = 5,
            ).totalTokens,
        )

        assertFailsWith<IllegalArgumentException> {
            AiError(
                code = AiErrorCode.UNKNOWN,
                message = " ",
                retryable = false,
            )
        }
    }
}
