package io.docpilot.core.incremental.review

fun interface ReviewResponseNormalizer {
    fun normalize(rawResponse: String): SectionReviewResult
}

class DefaultReviewResponseNormalizer : ReviewResponseNormalizer {
    override fun normalize(rawResponse: String): SectionReviewResult {
        var text = rawResponse.trim()
        require(text.isNotEmpty()) { "Review response must not be blank." }
        val fenced = Regex("^```(?:text)?\\s*\\n([\\s\\S]*?)\\n```$", RegexOption.IGNORE_CASE)
        text = fenced.matchEntire(text)?.groupValues?.get(1)?.trim() ?: text
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        require(lines.isNotEmpty() && lines.first().startsWith("DECISION: ")) {
            "Review response must start with DECISION."
        }
        val decision = enumValueOf<ReviewDecision>(lines.first().substringAfter("DECISION: ").trim())
        val scoreLine = lines.getOrNull(1)
        require(scoreLine?.startsWith("SCORES: ") == true) { "Review response must include SCORES as the second line." }
        val scores = parseScores(scoreLine!!.substringAfter("SCORES: "))
        val issueLines = lines.drop(2).takeWhile { it.startsWith("ISSUE: ") }
        val feedbackLine = lines.drop(2 + issueLines.size).singleOrNull()
        require(feedbackLine?.startsWith("FEEDBACK: ") == true) { "Review response must end with one FEEDBACK line." }
        val issues = issueLines.map(::parseIssue).sortedWith(SectionReviewResult.REVIEW_ISSUE_ORDER)
        val feedbackValue = feedbackLine!!.substringAfter("FEEDBACK: ").trim()
        return SectionReviewResult(
            decision = decision,
            score = scores,
            issues = issues,
            feedback = feedbackValue.takeUnless { it == "-" },
        )
    }

    private fun parseScores(value: String): ReviewScore {
        val parsed = value.split(';').associate { component ->
            val parts = component.split('=', limit = 2)
            require(parts.size == 2) { "Invalid review score component: $component" }
            parts[0].trim() to parts[1].trim().toInt()
        }
        val expected = setOf("evidenceSupport", "consistency", "completeness", "contractCompliance", "overall")
        require(parsed.keys == expected) { "Review response contains invalid score keys." }
        return ReviewScore(
            parsed.getValue("evidenceSupport"),
            parsed.getValue("consistency"),
            parsed.getValue("completeness"),
            parsed.getValue("contractCompliance"),
            parsed.getValue("overall"),
        )
    }

    private fun parseIssue(line: String): ReviewIssue {
        val parts = line.substringAfter("ISSUE: ").split('|', limit = 4).map(String::trim)
        require(parts.size == 4) { "Invalid review issue line." }
        val evidenceIds = parts[3].takeUnless { it == "-" }
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.sorted()
            .orEmpty()
        return ReviewIssue(
            enumValueOf(parts[0]),
            enumValueOf(parts[1]),
            parts[2],
            evidenceIds,
        )
    }
}
