package io.docpilot.core.api

import io.docpilot.core.model.source.KotlinToken
import io.docpilot.core.model.source.SourceFile

fun interface KotlinSymbolExtractor {
    fun extract(
        relativePath: String,
        tokens: List<KotlinToken>,
    ): SourceFile
}
