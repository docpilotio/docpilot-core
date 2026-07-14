package io.docpilot.core.model

import java.nio.file.Path

/**
 * Input passed to an [io.docpilot.core.api.AnalysisEngine].
 */
public data class AnalysisRequest(
    public val projectRoot: Path,
    public val changedPaths: Set<Path> = emptySet(),
    public val mode: AnalysisMode = AnalysisMode.FULL,
) {
    init {
        require(projectRoot.isAbsolute) {
            "projectRoot must be an absolute path."
        }
    }
}

public enum class AnalysisMode {
    FULL,
    INCREMENTAL,
}
