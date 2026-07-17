package io.docpilot.core.incremental.prompt

/** Deterministic bounded context supplied to one prompt. */
data class PromptContext(
    val changedFiles: List<PromptChangedFile>,
    val affectedKnowledge: List<PromptKnowledge>,
    val evidence: List<PromptEvidence>,
    val previousSectionContent: String?,
    val omittedChangedFileCount: Int,
    val omittedKnowledgeCount: Int,
    val omittedEvidenceCount: Int,
    val previousSectionTruncated: Boolean,
) {
    init {
        require(changedFiles == changedFiles.sortedBy { it.relativePath }) {
            "Prompt changed files must be sorted by relativePath."
        }
        require(changedFiles.map { it.relativePath }.distinct().size == changedFiles.size) {
            "Prompt changed files must be unique."
        }
        require(affectedKnowledge == affectedKnowledge.sortedBy { it.id }) {
            "Prompt knowledge must be sorted by id."
        }
        require(affectedKnowledge.map { it.id }.distinct().size == affectedKnowledge.size) {
            "Prompt knowledge ids must be unique."
        }
        require(
            evidence == evidence.sortedWith(
                compareBy<PromptEvidence> { it.sourcePath }
                    .thenBy { it.lineStart ?: Int.MAX_VALUE }
                    .thenBy { it.id },
            ),
        ) {
            "Prompt evidence must use deterministic source order."
        }
        require(evidence.map { it.id }.distinct().size == evidence.size) {
            "Prompt evidence ids must be unique."
        }
        require(previousSectionContent == null || previousSectionContent.isNotBlank()) {
            "Prompt previous section content must be null or non-blank."
        }
        require(omittedChangedFileCount >= 0)
        require(omittedKnowledgeCount >= 0)
        require(omittedEvidenceCount >= 0)
    }
}
