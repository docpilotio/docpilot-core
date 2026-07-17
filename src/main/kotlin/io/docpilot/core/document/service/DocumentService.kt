package io.docpilot.core.document.service

import io.docpilot.core.document.Document

fun interface DocumentService {
    fun generate(request: DocumentRequest): Document
}
