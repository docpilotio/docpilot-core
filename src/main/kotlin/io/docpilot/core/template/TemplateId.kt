package io.docpilot.core.template

/** Stable identifier for a document template. */
@JvmInline
value class TemplateId(val value: String) : Comparable<TemplateId> {
    init {
        require(value.isNotBlank()) { "Template id must not be blank." }
        require(ID_PATTERN.matches(value)) {
            "Template id must contain only lowercase letters, digits, '.', '_' or '-'."
        }
    }

    override fun compareTo(other: TemplateId): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9._-]*")
    }
}
