package io.docpilot.core.api

import io.docpilot.core.model.source.KotlinToken

fun interface KotlinLexer {
    fun tokenize(source: String): List<KotlinToken>
}
