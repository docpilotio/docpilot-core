package io.docpilot.core.model.source

/**
 * Syntax-level kind of a declared source symbol.
 *
 * Android-specific meanings such as Activity, Fragment, ViewModel,
 * or Service are intentionally not represented here.
 */
enum class SourceSymbolKind {
    CLASS,
    INTERFACE,
    OBJECT,
    ENUM_CLASS,
    ANNOTATION_CLASS,
    FUNCTION,
    PROPERTY,
    TYPE_ALIAS,
    UNKNOWN,
}