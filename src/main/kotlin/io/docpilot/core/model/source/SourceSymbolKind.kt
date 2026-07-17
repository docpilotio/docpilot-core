package io.docpilot.core.model.source

/** Syntax-level kind of a declared source symbol. */
enum class SourceSymbolKind {
    CLASS, INTERFACE, OBJECT, ENUM_CLASS, ANNOTATION_CLASS,
    FUNCTION, PROPERTY, CONSTRUCTOR, TYPE_ALIAS, ENUM_ENTRY, UNKNOWN,
}
