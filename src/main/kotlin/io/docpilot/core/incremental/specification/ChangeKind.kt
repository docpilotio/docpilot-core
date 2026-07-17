package io.docpilot.core.incremental.specification

/** Describes how a stable specification identity changed between two DIR snapshots. */
public enum class ChangeKind {
    ADDED,
    REMOVED,
    MODIFIED,
}
