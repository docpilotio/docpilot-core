package io.docpilot.core.incremental.prompt

import io.docpilot.core.incremental.ProjectFileChangeType
import io.docpilot.core.model.evidence.Evidence
import io.docpilot.core.model.knowledge.KnowledgeNode
import kotlin.math.max

/**
 * Deterministically selects changed files, affected nodes, evidence, and an
 * optional previous section while enforcing a provider-independent budget.
 */
class DefaultPromptContextSelector(
    private val tokenEstimator: PromptTokenEstimator =
        DeterministicPromptTokenEstimator(),
) : PromptContextSelector {

    override fun select(
        request: PromptBuildRequest,
        budget: PromptContextBudget,
    ): PromptContextSelection {
        val warnings = mutableSetOf<PromptBuildWarning>()
        val graphNodesById = request.knowledge.graph.nodes.associateBy { it.id }
        val evidenceById = request.knowledge.evidence.items.associateBy { it.id.value }

        val missingNodeIds = request.job.affectedNodeIds.filterNot(graphNodesById::containsKey)
        missingNodeIds.forEach { id ->
            warnings += PromptBuildWarning(
                code = PromptBuildWarningCode.MISSING_KNOWLEDGE_NODE,
                message = "Affected knowledge node '$id' was not present in the current graph.",
            )
        }

        val missingEvidenceIds = request.job.affectedEvidenceIds.filterNot(evidenceById::containsKey)
        missingEvidenceIds.forEach { id ->
            warnings += PromptBuildWarning(
                code = PromptBuildWarningCode.MISSING_EVIDENCE,
                message = "Affected evidence '$id' was not present in the current evidence collection.",
            )
        }

        val changedFiles = request.changes.changes
            .asSequence()
            .filter { it.type != ProjectFileChangeType.UNCHANGED }
            .map { PromptChangedFile(it.relativePath, it.type) }
            .distinctBy { it.relativePath }
            .sortedBy { it.relativePath }
            .toList()

        val knowledgeCandidates = request.job.affectedNodeIds
            .mapNotNull(graphNodesById::get)
            .map(::toPromptKnowledge)
            .distinctBy { it.id }
            .sortedBy { it.id }

        val changedPaths = changedFiles.mapTo(mutableSetOf()) { it.relativePath }
        val affectedNodeEvidenceIds = knowledgeCandidates
            .flatMap { graphNodesById.getValue(it.id).evidenceRefs }
            .toSet()

        val evidencePriority = buildMap<String, Int> {
            request.job.affectedEvidenceIds.forEach { put(it, 0) }
            affectedNodeEvidenceIds.forEach { id ->
                put(id, minOf(get(id) ?: Int.MAX_VALUE, 1))
            }
            request.knowledge.evidence.items
                .filter { it.location.relativePath in changedPaths }
                .forEach { evidence ->
                    put(evidence.id.value, minOf(get(evidence.id.value) ?: Int.MAX_VALUE, 2))
                }
        }

        val evidenceCandidates = evidencePriority.keys
            .mapNotNull(evidenceById::get)
            .sortedWith(
                compareBy<Evidence> { evidencePriority.getValue(it.id.value) }
                    .thenBy { it.location.relativePath }
                    .thenBy { it.location.lineStart ?: Int.MAX_VALUE }
                    .thenBy { it.id.value },
            )
            .map(::toPromptEvidence)

        val selectedFiles = mutableListOf<PromptChangedFile>()
        val selectedKnowledge = mutableListOf<PromptKnowledge>()
        val selectedEvidence = mutableListOf<PromptEvidence>()
        var usedTokens = tokenEstimator.estimate(CONTEXT_STRUCTURE)

        val previousReserve = request.previousSectionContent
            ?.let { minOf(budget.maxTokens / 4, tokenEstimator.estimate(it)) }
            ?: 0
        val selectionCeiling = max(MIN_SELECTION_TOKENS, budget.maxTokens - previousReserve)

        changedFiles.forEach { candidate ->
            val cost = tokenEstimator.estimate(render(candidate))
            if (usedTokens + cost <= selectionCeiling) {
                selectedFiles += candidate
                usedTokens += cost
            }
        }

        if (changedFiles.isNotEmpty() && selectedFiles.isEmpty()) {
            throw PromptBuildException.InsufficientTokenBudget(
                availableTokens = budget.maxTokens,
                requiredTokens = usedTokens + tokenEstimator.estimate(render(changedFiles.first())),
            )
        }

        knowledgeCandidates.forEach { candidate ->
            val cost = tokenEstimator.estimate(render(candidate))
            if (usedTokens + cost <= selectionCeiling) {
                selectedKnowledge += candidate
                usedTokens += cost
            }
        }

        evidenceCandidates.forEach { candidate ->
            val bounded = candidate.copy(summary = truncateSummary(candidate.summary))
            val cost = tokenEstimator.estimate(render(bounded))
            if (usedTokens + cost <= selectionCeiling) {
                selectedEvidence += bounded
                usedTokens += cost
            }
        }

        val previousResult = selectPreviousSection(
            previous = request.previousSectionContent,
            availableTokens = budget.maxTokens - usedTokens,
        )
        usedTokens += previousResult.estimatedTokens

        val omittedFiles = changedFiles.size - selectedFiles.size
        val omittedKnowledge = knowledgeCandidates.size - selectedKnowledge.size
        val omittedEvidence = evidenceCandidates.size - selectedEvidence.size
        if (omittedFiles + omittedKnowledge + omittedEvidence > 0) {
            warnings += PromptBuildWarning(
                code = PromptBuildWarningCode.CONTEXT_TRUNCATED,
                message = "Prompt context omitted $omittedFiles changed files, " +
                    "$omittedKnowledge knowledge nodes, and $omittedEvidence evidence items.",
            )
        }
        if (previousResult.truncated) {
            warnings += PromptBuildWarning(
                code = PromptBuildWarningCode.PREVIOUS_SECTION_TRUNCATED,
                message = "Previous section content was truncated to fit the prompt budget.",
            )
        }

        val context = PromptContext(
            changedFiles = selectedFiles.sortedBy { it.relativePath },
            affectedKnowledge = selectedKnowledge.sortedBy { it.id },
            evidence = selectedEvidence.sortedWith(
                compareBy<PromptEvidence> { it.sourcePath }
                    .thenBy { it.lineStart ?: Int.MAX_VALUE }
                    .thenBy { it.id },
            ),
            previousSectionContent = previousResult.content,
            omittedChangedFileCount = omittedFiles,
            omittedKnowledgeCount = omittedKnowledge,
            omittedEvidenceCount = omittedEvidence,
            previousSectionTruncated = previousResult.truncated,
        )

        return PromptContextSelection(
            context = context,
            estimatedTokens = usedTokens,
            warnings = warnings.sortedWith(
                compareBy<PromptBuildWarning> { it.code.name }.thenBy { it.message },
            ),
        )
    }

    private fun selectPreviousSection(
        previous: String?,
        availableTokens: Int,
    ): PreviousSectionSelection {
        if (previous == null || availableTokens <= 0) {
            return PreviousSectionSelection(
                content = null,
                estimatedTokens = 0,
                truncated = previous != null,
            )
        }

        val normalized = previous.trim()
        val fullCost = tokenEstimator.estimate(normalized)
        if (fullCost <= availableTokens) {
            return PreviousSectionSelection(normalized, fullCost, false)
        }

        val truncated = fitWithEllipsis(
            value = normalized,
            maxTokens = availableTokens,
        ) ?: return PreviousSectionSelection(null, 0, true)

        return PreviousSectionSelection(
            content = truncated,
            estimatedTokens = tokenEstimator.estimate(truncated),
            truncated = true,
        )
    }


    private fun fitWithEllipsis(
        value: String,
        maxTokens: Int,
    ): String? {
        if (maxTokens <= 0 || tokenEstimator.estimate(ELLIPSIS) > maxTokens) {
            return null
        }

        var low = 0
        var high = value.length
        var best = ELLIPSIS
        while (low <= high) {
            val middle = (low + high) ushr 1
            val candidate = value.take(middle).trimEnd() + ELLIPSIS
            if (tokenEstimator.estimate(candidate) <= maxTokens) {
                best = candidate
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return best
    }

    private fun toPromptKnowledge(node: KnowledgeNode): PromptKnowledge =
        PromptKnowledge(
            id = node.id,
            name = node.name,
            kind = node.kind,
            attributes = node.attributes.toSortedMap(),
        )

    private fun toPromptEvidence(evidence: Evidence): PromptEvidence =
        PromptEvidence(
            id = evidence.id.value,
            sourcePath = evidence.location.relativePath,
            lineStart = evidence.location.lineStart,
            symbol = evidence.attributes["symbolName"]
                ?: evidence.attributes["packageName"]
                ?: evidence.attributes["qualifiedName"],
            summary = oneLine(evidence.summary),
        )

    private fun truncateSummary(summary: String): String {
        if (summary.length <= MAX_EVIDENCE_SUMMARY_CHARACTERS) return summary
        return summary.take(MAX_EVIDENCE_SUMMARY_CHARACTERS - ELLIPSIS.length)
            .trimEnd() + ELLIPSIS
    }

    private fun render(file: PromptChangedFile): String =
        "${file.type}:${file.relativePath}"

    private fun render(knowledge: PromptKnowledge): String = buildString {
        append(knowledge.id)
        append('|').append(knowledge.kind)
        append('|').append(knowledge.name)
        knowledge.attributes.entries.forEach { (key, value) ->
            append('|').append(key).append('=').append(value)
        }
    }

    private fun render(evidence: PromptEvidence): String = buildString {
        append(evidence.id)
        append('|').append(evidence.sourcePath)
        evidence.lineStart?.let { append(':').append(it) }
        evidence.symbol?.let { append('|').append(it) }
        append('|').append(evidence.summary)
    }

    private fun oneLine(value: String): String =
        value.replace(WHITESPACE, " ").trim()

    private data class PreviousSectionSelection(
        val content: String?,
        val estimatedTokens: Int,
        val truncated: Boolean,
    )

    companion object {
        private const val CONTEXT_STRUCTURE =
            "changed-files affected-knowledge evidence previous-section"
        private const val MIN_SELECTION_TOKENS = 1
        private const val MAX_EVIDENCE_SUMMARY_CHARACTERS = 500
        private const val ELLIPSIS = "…"
        private val WHITESPACE = Regex("\\s+")
    }
}
