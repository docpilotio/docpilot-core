package io.docpilot.core.incremental.prompt

/** Provider-independent deterministic estimate used before a real tokenizer is selected. */
fun interface PromptTokenEstimator {
    fun estimate(text: String): Int
}

/**
 * Conservative character-based estimate aligned with RFC-0025 budgeting.
 *
 * It deliberately avoids provider SDKs and model-specific tokenizers. A future
 * adapter can replace it without changing prompt planning contracts.
 */
class DeterministicPromptTokenEstimator(
    private val charactersPerToken: Int = DEFAULT_CHARACTERS_PER_TOKEN,
) : PromptTokenEstimator {

    init {
        require(charactersPerToken > 0) { "charactersPerToken must be positive." }
    }

    override fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length + charactersPerToken - 1) / charactersPerToken
    }

    companion object {
        const val DEFAULT_CHARACTERS_PER_TOKEN: Int = 4
    }
}
