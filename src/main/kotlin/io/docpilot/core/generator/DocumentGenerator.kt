package io.docpilot.core.generator

import io.docpilot.core.document.Document

/** Common contract implemented by document-specific generators. */
fun interface DocumentGenerator<in R> {
    fun generate(request: R): Document
}
