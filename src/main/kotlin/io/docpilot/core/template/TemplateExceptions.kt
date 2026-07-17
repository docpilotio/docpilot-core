package io.docpilot.core.template

class DuplicateTemplateException(
    val templateId: TemplateId,
) : IllegalStateException("Template is already registered: $templateId")

class TemplateNotFoundException(
    val templateId: TemplateId,
) : NoSuchElementException("Template was not found: $templateId")
