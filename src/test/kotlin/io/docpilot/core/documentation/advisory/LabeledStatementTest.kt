package io.docpilot.core.documentation.advisory

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LabeledStatementTest {
    @Test
    fun `AS_IS with evidence succeeds`() {
        LabeledStatement(StatementLabel.AS_IS, "Sample is a component.", setOf("evidence:1"))
    }

    @Test
    fun `AS_IS with no evidence is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            LabeledStatement(StatementLabel.AS_IS, "Sample is a component.", emptySet())
        }
    }

    @Test
    fun `TO_BE with no evidence succeeds`() {
        LabeledStatement(StatementLabel.TO_BE, "Consider adding a description.", emptySet())
    }

    @Test
    fun `TO_BE with evidence is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            LabeledStatement(StatementLabel.TO_BE, "Consider adding a description.", setOf("evidence:1"))
        }
    }

    @Test
    fun `blank text is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            LabeledStatement(StatementLabel.TO_BE, " ", emptySet())
        }
    }
}
