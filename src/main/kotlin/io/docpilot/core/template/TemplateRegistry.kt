package io.docpilot.core.template

/** Registry for document templates. */
interface TemplateRegistry {
    fun register(template: DocumentTemplate)

    fun find(id: TemplateId): DocumentTemplate?

    fun get(id: TemplateId): DocumentTemplate =
        find(id) ?: throw TemplateNotFoundException(id)

    /** Returns templates in deterministic identifier order. */
    fun list(): List<DocumentTemplate>
}
