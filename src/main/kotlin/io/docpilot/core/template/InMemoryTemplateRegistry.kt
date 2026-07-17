package io.docpilot.core.template

/** Thread-safe in-memory registry with deterministic listing. */
class InMemoryTemplateRegistry(
    templates: Iterable<DocumentTemplate> = emptyList(),
) : TemplateRegistry {
    private val templatesById = linkedMapOf<TemplateId, DocumentTemplate>()

    init {
        templates.forEach(::register)
    }

    @Synchronized
    override fun register(template: DocumentTemplate) {
        if (template.id in templatesById) {
            throw DuplicateTemplateException(template.id)
        }
        templatesById[template.id] = template
    }

    @Synchronized
    override fun find(id: TemplateId): DocumentTemplate? = templatesById[id]

    @Synchronized
    override fun list(): List<DocumentTemplate> =
        templatesById.values.sortedBy(DocumentTemplate::id)
}
