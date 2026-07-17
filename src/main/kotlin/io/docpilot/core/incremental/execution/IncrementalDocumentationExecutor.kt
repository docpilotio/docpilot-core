package io.docpilot.core.incremental.execution

/** Executes an already calculated incremental update plan. */
public fun interface IncrementalDocumentationExecutor {
    public fun execute(request: IncrementalDocumentationExecutionRequest): IncrementalDocumentationExecutionResult
}
