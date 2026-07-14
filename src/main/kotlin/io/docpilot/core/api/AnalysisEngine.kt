package io.docpilot.core.api

import io.docpilot.core.model.AnalysisRequest
import io.docpilot.core.model.AnalysisResult

/**
 * Contract for transforming a software project into a structured analysis result.
 *
 * Platform-specific implementations, such as DocPilot Droid, implement this interface.
 */
public fun interface AnalysisEngine {
    public fun analyze(request: AnalysisRequest): AnalysisResult
}
