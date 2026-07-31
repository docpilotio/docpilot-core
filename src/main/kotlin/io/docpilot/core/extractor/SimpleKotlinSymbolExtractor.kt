package io.docpilot.core.extractor

import io.docpilot.core.api.KotlinSymbolExtractor
import io.docpilot.core.model.source.*
import java.security.MessageDigest

class SimpleKotlinSymbolExtractor : KotlinSymbolExtractor {
    override fun extract(relativePath: String, tokens: List<KotlinToken>): SourceFile {
        require(relativePath.isNotBlank()) { "relativePath must not be blank." }
        val packageName = extractPackageName(tokens)
        val normalizedPath = relativePath.replace('\\', '/')
        val imports = extractImports(tokens)
        val symbols = Parser(normalizedPath, packageName, tokens).parse()
        return SourceFile(
            relativePath = normalizedPath,
            language = SourceLanguage.KOTLIN,
            packageName = packageName,
            imports = imports,
            symbols = symbols,
            composeNavigation = extractComposeNavigation(normalizedPath, tokens, imports, symbols),
        )
    }

    private fun extractComposeNavigation(
        path: String,
        tokens: List<KotlinToken>,
        imports: List<SourceImport>,
        symbols: List<SourceSymbol>,
    ): ComposeNavigationSourceObservations {
        val allSymbols = symbols.flatMap(::flatten)
        val routes = allSymbols.mapNotNull { symbol ->
            val qualifiedName = symbol.qualifiedName ?: return@mapNotNull null
            val location = symbol.location ?: return@mapNotNull null
            when {
                symbol.kind == SourceSymbolKind.PROPERTY &&
                    SourceModifier.CONST in symbol.modifiers &&
                    symbol.initializerExpression != null ->
                    ComposeRouteDeclarationObservation(
                        id = "compose-route:$qualifiedName",
                        symbolId = "symbol:${symbol.id}",
                        qualifiedName = qualifiedName,
                        kind = ComposeRouteDeclarationKind.CONST_STRING_ROUTE,
                        expression = symbol.initializerExpression,
                        location = location,
                    )
                symbol.kind in setOf(SourceSymbolKind.OBJECT, SourceSymbolKind.CLASS) &&
                    symbol.annotations.any { it.substringAfterLast('.') == "Serializable" } ->
                    ComposeRouteDeclarationObservation(
                        id = "compose-route:$qualifiedName",
                        symbolId = "symbol:${symbol.id}",
                        qualifiedName = qualifiedName,
                        kind = if (symbol.kind == SourceSymbolKind.OBJECT) {
                            if (SourceModifier.DATA in symbol.modifiers) ComposeRouteDeclarationKind.DATA_OBJECT_ROUTE
                            else ComposeRouteDeclarationKind.SERIALIZABLE_OBJECT_ROUTE
                        } else {
                            ComposeRouteDeclarationKind.SERIALIZABLE_CLASS_ROUTE
                        },
                        expression = qualifiedName,
                        location = location,
                    )
                else -> null
            }
        }.distinctBy { it.id }.sortedBy { it.id }

        val importByLocalName = imports.groupBy { it.alias ?: it.qualifiedName.substringAfterLast('.') }
        val localFunctions = allSymbols.filter { it.kind == SourceSymbolKind.FUNCTION }
            .groupBy { it.name }
        val registrations = mutableListOf<ComposeNavigationRegistrationObservation>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.type != KotlinTokenType.IDENTIFIER) {
                index++
                continue
            }
            val imported = importByLocalName[token.text].orEmpty().map { it.qualifiedName }.distinct()
            val callee = imported.singleOrNull()?.takeIf(NAVIGATION_APIS::containsKey)
            if (callee == null) {
                index++
                continue
            }
            var cursor = index + 1
            var genericRoute: String? = null
            if (tokens.getOrNull(cursor)?.text == "<") {
                val close = matching(tokens, cursor, "<", ">")
                genericRoute = render(tokens, cursor + 1, close).takeIf(String::isNotBlank)
                cursor = close + 1
            }
            if (tokens.getOrNull(cursor)?.text != "(") {
                index++
                continue
            }
            val close = matching(tokens, cursor, "(", ")")
            val routeExpression = genericRoute ?: routeArgument(tokens, cursor + 1, close)
            if (routeExpression == null) {
                index = close + 1
                continue
            }
            val ownerCandidates = allSymbols.filter { symbol ->
                val location = symbol.location ?: return@filter false
                val startLine = location.lineStart ?: return@filter false
                symbol.kind == SourceSymbolKind.FUNCTION &&
                    token.line >= startLine && token.line <= (location.lineEnd ?: startLine)
            }
            val owner = ownerCandidates.singleOrNull()
            if (owner == null) {
                index = close + 1
                continue
            }
            val lambdaOpen = (close + 1 until minOf(tokens.size, close + 16))
                .filter { tokens[it].text == "{" }
                .minOrNull()
            val destinationCalls = if (lambdaOpen == null) emptyList() else {
                val lambdaClose = matching(tokens, lambdaOpen, "{", "}")
                destinationCalls(
                    path, tokens, lambdaOpen + 1, lambdaClose, importByLocalName, localFunctions,
                )
            }
            val kind = NAVIGATION_APIS.getValue(callee)
            val semanticRoute = routeExpression.replace(" ", "")
            val ownerId = "symbol:${owner.id}"
            registrations += ComposeNavigationRegistrationObservation(
                id = "compose-registration:$ownerId:$semanticRoute:${kind.name.lowercase()}",
                apiKind = kind,
                calleeQualifiedName = callee,
                ownerSymbolId = ownerId,
                ownerQualifiedName = owner.qualifiedName ?: owner.name,
                routeExpression = routeExpression,
                genericRouteType = genericRoute,
                destinationCalls = destinationCalls,
                location = SourceLocation(path, token.line, token.column),
            )
            index = close + 1
        }
        return ComposeNavigationSourceObservations(
            routes = routes,
            registrations = registrations.distinctBy { it.id }.sortedBy { it.id },
        )
    }

    private fun destinationCalls(
        path: String,
        tokens: List<KotlinToken>,
        start: Int,
        end: Int,
        imports: Map<String, List<SourceImport>>,
        localFunctions: Map<String, List<SourceSymbol>>,
    ): List<ComposeDestinationCallObservation> {
        val result = mutableListOf<ComposeDestinationCallObservation>()
        var depth = 0
        for (index in start until end) {
            when (tokens[index].text) {
                "{" -> depth++
                "}" -> depth--
            }
            if (tokens.getOrNull(index + 1)?.text !in setOf("(", "{")) continue
            val imported = imports[tokens[index].text].orEmpty().map { it.qualifiedName }.distinct()
            val local = localFunctions[tokens[index].text].orEmpty()
                .mapNotNull { it.qualifiedName }.distinct()
            val qualifiedName = (imported + local).distinct().singleOrNull() ?: continue
            result += ComposeDestinationCallObservation(
                calleeQualifiedName = qualifiedName,
                nestingDepth = depth,
                location = SourceLocation(path, tokens[index].line, tokens[index].column),
            )
        }
        return result.distinctBy { Triple(it.calleeQualifiedName, it.nestingDepth, it.location.lineStart) }
            .sortedWith(compareBy({ it.nestingDepth }, { it.calleeQualifiedName }, { it.location.lineStart }))
    }

    private fun routeArgument(tokens: List<KotlinToken>, start: Int, end: Int): String? {
        val segments = splitTopLevel(tokens, start, end, ",")
        val named = segments.mapNotNull { (from, to) ->
            val equals = topLevelIndexOf(tokens, from, to, "=") ?: return@mapNotNull null
            val name = render(tokens, from, equals)
            if (name == "route") render(tokens, equals + 1, to) else null
        }
        if (named.size == 1) return named.single().takeIf(String::isNotBlank)
        if (named.size > 1) return null
        val first = segments.singleOrNull { (from, to) ->
            topLevelIndexOf(tokens, from, to, "=") == null
        } ?: return null
        return render(tokens, first.first, first.second).takeIf(String::isNotBlank)
    }

    private fun flatten(symbol: SourceSymbol): List<SourceSymbol> =
        listOf(symbol) + symbol.children.flatMap(::flatten)

    private fun matching(tokens: List<KotlinToken>, open: Int, left: String, right: String): Int {
        var depth = 0
        for (index in open until tokens.size) {
            if (tokens[index].text == left) depth++
            if (tokens[index].text == right && --depth == 0) return index
        }
        return tokens.lastIndex
    }

    private fun splitTopLevel(
        tokens: List<KotlinToken>,
        start: Int,
        end: Int,
        delimiter: String,
    ): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var segmentStart = start
        var depth = 0
        for (index in start until end) {
            when (tokens[index].text) {
                "(", "<", "[", "{" -> depth++
                ")", ">", "]", "}" -> depth--
            }
            if (tokens[index].text == delimiter && depth == 0) {
                result += segmentStart to index
                segmentStart = index + 1
            }
        }
        if (segmentStart < end) result += segmentStart to end
        return result
    }

    private fun topLevelIndexOf(
        tokens: List<KotlinToken>,
        start: Int,
        end: Int,
        value: String,
    ): Int? {
        var depth = 0
        for (index in start until end) {
            when (tokens[index].text) {
                "(", "<", "[", "{" -> depth++
                ")", ">", "]", "}" -> depth--
            }
            if (tokens[index].text == value && depth == 0) return index
        }
        return null
    }

    private fun render(tokens: List<KotlinToken>, start: Int, end: Int): String =
        tokens.subList(start.coerceAtLeast(0), end.coerceAtMost(tokens.size))
            .filter { it.type != KotlinTokenType.END_OF_FILE }
            .joinToString(" ") { it.text }
            .replace(" . ", ".")
            .replace(" < ", "<")
            .replace(" >", ">")
            .replace(" ( ", "(")
            .replace(" )", ")")
            .replace(" ,", ",")
            .replace(" : ", ": ")
            .trim()

    private fun extractPackageName(tokens: List<KotlinToken>): String? {
        val i = tokens.indexOfFirst { it.text == "package" }
        return if (i < 0) null else readQualifiedName(tokens, i + 1).first.takeIf(String::isNotBlank)
    }

    private fun extractImports(tokens: List<KotlinToken>): List<SourceImport> {
        val result = mutableListOf<SourceImport>()
        var i = 0
        while (i < tokens.size) {
            if (tokens[i].text != "import") { i++; continue }
            val (name, next, wildcard) = readQualifiedName(tokens, i + 1)
            var cursor = next
            val alias = if (tokens.getOrNull(cursor)?.text == "as") tokens.getOrNull(cursor + 1)?.text else null
            if (name.isNotBlank()) result += SourceImport(name, alias, wildcard)
            i = maxOf(i + 1, cursor + if (alias == null) 0 else 2)
        }
        return result
    }

    private fun readQualifiedName(tokens: List<KotlinToken>, start: Int): QualifiedNameResult {
        val parts = mutableListOf<String>(); var i = start; var wildcard = false; var expectName = true
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                expectName && t.type == KotlinTokenType.IDENTIFIER -> { parts += t.text; expectName = false; i++ }
                !expectName && t.text == "." -> {
                    if (tokens.getOrNull(i + 1)?.text == "*") { wildcard = true; i += 2; break }
                    expectName = true; i++
                }
                else -> break
            }
        }
        return QualifiedNameResult(parts.joinToString("."), i, wildcard)
    }

    private data class QualifiedNameResult(val first: String, val next: Int, val wildcard: Boolean)

    private class Parser(
        private val path: String,
        private val packageName: String?,
        private val tokens: List<KotlinToken>,
    ) {
        fun parse(): List<SourceSymbol> = parseRange(0, tokens.lastIndex, null, packageName)
            .sortedWith(symbolComparator)

        private fun parseRange(start: Int, end: Int, parentId: String?, ownerName: String?): List<SourceSymbol> {
            val result = mutableListOf<SourceSymbol>()
            var i = start
            var depth = 0
            while (i < end) {
                when (tokens[i].text) {
                    "{" -> { depth++; i++; continue }
                    "}" -> { depth--; i++; continue }
                }
                if (depth != 0) { i++; continue }
                val parsed = parseDeclaration(i, end, parentId, ownerName)
                if (parsed == null) i++ else { result += parsed.symbol; i = parsed.nextIndex }
            }
            return result
        }

        private fun parseDeclaration(index: Int, end: Int, parentId: String?, ownerName: String?): Parsed? {
            var i = index
            val annotations = mutableListOf<String>()
            val modifiers = linkedSetOf<SourceModifier>()
            var visibility = SourceVisibility.DEFAULT
            while (i < end) {
                if (tokens[i].text == "@") {
                    val annotation = tokens.getOrNull(i + 1)?.text ?: break
                    annotations += annotation
                    i += 2
                    while (tokens.getOrNull(i)?.text == ".") { annotations[annotations.lastIndex] += ".${tokens.getOrNull(i + 1)?.text ?: ""}"; i += 2 }
                    if (tokens.getOrNull(i)?.text == "(") i = skipBalanced(i, "(", ")", end)
                    continue
                }
                val parsedVisibility = visibilityOf(tokens[i].text)
                if (parsedVisibility != null) {
                    visibility = parsedVisibility
                    i++
                    continue
                }
                val parsedModifier = modifierOf(tokens[i].text)
                if (parsedModifier != null) {
                    modifiers += parsedModifier
                    i++
                    continue
                }
                break
            }

            val declarationStart = i
            val kindAndName = declarationKindAndName(i) ?: return null
            val kind = kindAndName.first
            val nameIndex = kindAndName.second
            val nameToken = tokens.getOrNull(nameIndex) ?: return null
            if (nameToken.type != KotlinTokenType.IDENTIFIER && kind != SourceSymbolKind.CONSTRUCTOR) return null
            val name = if (kind == SourceSymbolKind.CONSTRUCTOR) "<init>" else nameToken.text
            val qName = if (kind == SourceSymbolKind.CONSTRUCTOR) {
                ownerName?.let { "$it.<init>" }
            } else {
                listOfNotNull(ownerName, name).joinToString(".").ifBlank { null }
            }
            val bodyStart = findBodyStart(nameIndex + 1, end)
            val declarationEnd = if (bodyStart >= 0) matchingBrace(bodyStart, end) else findDeclarationEnd(nameIndex + 1, end)
            val parameters = extractParameters(nameIndex + 1, if (bodyStart >= 0) bodyStart else declarationEnd)
            val type = extractDeclaredType(kind, nameIndex + 1, if (bodyStart >= 0) bodyStart else declarationEnd)
            val receiver = if (kind == SourceSymbolKind.FUNCTION) extractReceiver(i, nameIndex) else null
            val signature = renderSignature(declarationStart, if (bodyStart >= 0) bodyStart else declarationEnd)
            val id = stableId(path, qName ?: name, kind, tokens[declarationStart].line, tokens[declarationStart].column)
            val children = if (bodyStart >= 0 && declarationEnd > bodyStart) {
                parseRange(bodyStart + 1, declarationEnd, id, qName).let { nested ->
                    val constructors = if (kind in classKinds) extractPrimaryConstructor(nameIndex + 1, bodyStart, id, qName) else emptyList()
                    (constructors + nested).sortedWith(symbolComparator)
                }
            } else emptyList()
            val location = SourceLocation(
                relativePath = path,
                lineStart = tokens[declarationStart].line,
                columnStart = tokens[declarationStart].column,
                lineEnd = tokens.getOrNull(maxOf(declarationStart, declarationEnd - 1))?.line,
                columnEnd = tokens.getOrNull(maxOf(declarationStart, declarationEnd - 1))?.column,
            )
            val symbol = SourceSymbol(
                name = name,
                kind = kind,
                visibility = visibility,
                location = location,
                annotations = annotations.sorted(),
                children = children,
                id = id,
                qualifiedName = qName,
                modifiers = modifiers,
                signature = signature,
                parentSymbolId = parentId,
                parameters = parameters,
                type = type,
                receiverType = receiver,
                mutable = when (tokens[i].text) { "var" -> true; "val" -> false; else -> null },
                hasInitializer = if (kind == SourceSymbolKind.PROPERTY) containsAtTopLevel(nameIndex + 1, declarationEnd, "=") else null,
                typeParameters = extractTypeParameters(nameIndex + 1, if (bodyStart >= 0) bodyStart else declarationEnd),
                superTypes = if (kind in classKinds) extractSuperTypes(nameIndex + 1, if (bodyStart >= 0) bodyStart else declarationEnd) else emptyList(),
                initializerExpression = extractInitializer(kind, nameIndex + 1, declarationEnd),
            )
            return Parsed(symbol, if (bodyStart >= 0) declarationEnd + 1 else maxOf(index + 1, declarationEnd))
        }

        private fun declarationKindAndName(i: Int): Pair<SourceSymbolKind, Int>? = when (tokens.getOrNull(i)?.text) {
            "class" -> SourceSymbolKind.CLASS to (i + 1)
            "interface" -> SourceSymbolKind.INTERFACE to (i + 1)
            "object" -> SourceSymbolKind.OBJECT to (i + 1)
            "fun" -> SourceSymbolKind.FUNCTION to functionNameIndex(i + 1)
            "val", "var" -> SourceSymbolKind.PROPERTY to (i + 1)
            "typealias" -> SourceSymbolKind.TYPE_ALIAS to (i + 1)
            "constructor" -> SourceSymbolKind.CONSTRUCTOR to i
            "enum" -> if (tokens.getOrNull(i + 1)?.text == "class") SourceSymbolKind.ENUM_CLASS to (i + 2) else null
            "annotation" -> if (tokens.getOrNull(i + 1)?.text == "class") SourceSymbolKind.ANNOTATION_CLASS to (i + 2) else null
            else -> null
        }

        private fun functionNameIndex(start: Int): Int {
            var i = start; var candidate = start
            while (i < tokens.size && tokens[i].text != "(" && tokens[i].text != "{" && tokens[i].text != "=") {
                if (tokens[i].type == KotlinTokenType.IDENTIFIER) candidate = i
                i++
            }
            return candidate
        }

        private fun findBodyStart(start: Int, end: Int): Int {
            var paren = 0; var angle = 0
            for (i in start until end) {
                when (tokens[i].text) {
                    "(" -> paren++; ")" -> paren--
                    "<" -> angle++; ">" -> if (angle > 0) angle--
                    "{" -> if (paren == 0 && angle == 0) return i
                    "=", ";" -> if (paren == 0 && angle == 0) return -1
                    "}" -> if (paren == 0 && angle == 0) return -1
                }
            }
            return -1
        }

        private fun matchingBrace(open: Int, end: Int): Int {
            var depth = 0
            for (i in open until end) {
                if (tokens[i].text == "{") depth++
                if (tokens[i].text == "}" && --depth == 0) return i
            }
            return end - 1
        }

        private fun findDeclarationEnd(start: Int, end: Int): Int {
            var paren = 0; var angle = 0
            var i = start
            while (i < end) {
                when (tokens[i].text) {
                    "(" -> paren++; ")" -> paren--
                    "<" -> angle++; ">" -> if (angle > 0) angle--
                    ";" -> if (paren == 0 && angle == 0) return i + 1
                    "}" -> if (paren == 0 && angle == 0) return i
                }
                if (i >= start && paren == 0 && angle == 0 &&
                    tokens[i].line > tokens[i - 1].line &&
                    canStartDeclaration(i, end)
                ) return i
                i++
            }
            return end
        }


        private fun canStartDeclaration(index: Int, end: Int): Boolean {
            var cursor = index
            while (cursor < end) {
                if (tokens[cursor].text == "@") {
                    cursor += 2
                    while (tokens.getOrNull(cursor)?.text == ".") cursor += 2
                    if (tokens.getOrNull(cursor)?.text == "(") {
                        cursor = skipBalanced(cursor, "(", ")", end)
                    }
                    continue
                }
                if (visibilityOf(tokens[cursor].text) != null ||
                    modifierOf(tokens[cursor].text) != null
                ) {
                    cursor++
                    continue
                }
                break
            }
            return declarationKindAndName(cursor) != null
        }

        private fun extractParameters(start: Int, end: Int): List<SourceParameter> {
            val open = (start until end).firstOrNull { tokens[it].text == "(" } ?: return emptyList()
            val close = matching(open, "(", ")", end)
            if (close <= open) return emptyList()
            val chunks = splitTopLevel(open + 1, close, ",")
            return chunks.mapNotNull { (s, e) ->
                val colon = (s until e).firstOrNull { tokens[it].text == ":" } ?: return@mapNotNull null
                val name = tokens.subList(s, colon).lastOrNull { it.type == KotlinTokenType.IDENTIFIER }?.text ?: return@mapNotNull null
                val eq = (colon + 1 until e).firstOrNull { tokens[it].text == "=" }
                val typeEnd = eq ?: e
                val annotations = mutableListOf<String>()
                val modifiers = linkedSetOf<SourceModifier>()
                var cursor = s
                while (cursor < colon) {
                    if (tokens[cursor].text == "@") {
                        tokens.getOrNull(cursor + 1)?.text?.let(annotations::add)
                        cursor += 2
                    } else {
                        modifierOf(tokens[cursor].text)?.let(modifiers::add)
                        cursor++
                    }
                }
                SourceParameter(
                    name = name,
                    type = render(colon + 1, typeEnd).takeIf(String::isNotBlank),
                    hasDefaultValue = eq != null,
                    annotations = annotations.sorted(),
                    modifiers = modifiers,
                    location = SourceLocation(
                        relativePath = path,
                        lineStart = tokens[s].line,
                        columnStart = tokens[s].column,
                        lineEnd = tokens[e - 1].line,
                        columnEnd = tokens[e - 1].column,
                    ),
                )
            }
        }

        private fun extractPrimaryConstructor(start: Int, end: Int, parentId: String, owner: String?): List<SourceSymbol> {
            val open = (start until end).firstOrNull { tokens[it].text == "(" } ?: return emptyList()
            val params = extractParameters(open, end)
            if (params.isEmpty()) return emptyList()
            val id = stableId(path, "${owner ?: ""}.<init>", SourceSymbolKind.CONSTRUCTOR, tokens[open].line, tokens[open].column)
            return listOf(SourceSymbol("<init>", SourceSymbolKind.CONSTRUCTOR, location = SourceLocation(path, tokens[open].line, tokens[open].column, tokens[matching(open,"(",")",end)].line, tokens[matching(open,"(",")",end)].column), id = id, qualifiedName = owner?.plus(".<init>"), parentSymbolId = parentId, parameters = params, signature = render(open, matching(open,"(",")",end)+1)))
        }

        private fun extractDeclaredType(kind: SourceSymbolKind, start: Int, end: Int): String? {
            if (kind !in setOf(SourceSymbolKind.FUNCTION, SourceSymbolKind.PROPERTY, SourceSymbolKind.TYPE_ALIAS)) return null
            if (kind == SourceSymbolKind.TYPE_ALIAS) {
                val eq = (start until end).firstOrNull { tokens[it].text == "=" } ?: return null
                return render(eq + 1, end).takeIf(String::isNotBlank)
            }
            val searchStart = if (kind == SourceSymbolKind.FUNCTION) {
                val open = (start until end).firstOrNull { tokens[it].text == "(" }
                if (open == null) start else matching(open, "(", ")", end) + 1
            } else start
            val colon = (searchStart until end).firstOrNull { tokens[it].text == ":" } ?: return null
            val stop = (colon + 1 until end).firstOrNull {
                tokens[it].text in setOf("=", "{", "get", "set", "by")
            } ?: end
            return render(colon + 1, stop).takeIf(String::isNotBlank)
        }


        private fun extractTypeParameters(start: Int, end: Int): List<String> {
            val open = (start until end).firstOrNull { tokens[it].text == "<" } ?: return emptyList()
            val close = matching(open, "<", ">", end)
            if (close <= open) return emptyList()
            return splitTopLevel(open + 1, close, ",")
                .map { (s, e) -> render(s, e) }
                .filter(String::isNotBlank)
        }

        private fun extractSuperTypes(start: Int, end: Int): List<String> {
            var depth = 0
            val colon = (start until end).firstOrNull {
                when (tokens[it].text) {
                    "(", "<", "[" -> depth++
                    ")", ">", "]" -> depth--
                }
                depth == 0 && tokens[it].text == ":"
            } ?: return emptyList()
            val whereIndex = (colon + 1 until end).firstOrNull { tokens[it].text == "where" } ?: end
            return splitTopLevel(colon + 1, whereIndex, ",")
                .map { (s, e) -> render(s, e) }
                .filter(String::isNotBlank)
        }

        private fun extractInitializer(kind: SourceSymbolKind, start: Int, end: Int): String? {
            if (kind != SourceSymbolKind.PROPERTY) return null
            val equals = (start until end).firstOrNull { tokens[it].text == "=" } ?: return null
            return render(equals + 1, end).takeIf(String::isNotBlank)
        }

        private fun extractReceiver(funIndex: Int, nameIndex: Int): String? {
            val dot = (funIndex + 1 until nameIndex).lastOrNull { tokens[it].text == "." } ?: return null
            return render(funIndex + 1, dot).takeIf(String::isNotBlank)
        }

        private fun renderSignature(start: Int, end: Int): String? = render(start, end).takeIf(String::isNotBlank)
        private fun render(start: Int, end: Int): String = tokens.subList(start.coerceAtLeast(0), end.coerceAtMost(tokens.size)).filter { it.type != KotlinTokenType.END_OF_FILE }.joinToString(" ") { it.text }.replace(" . ", ".").replace(" < ", "<").replace(" >", ">").replace(" ( ", "(").replace(" )", ")").replace(" ,", ",").replace(" : ", ": ").trim()
        private fun skipBalanced(open: Int, left: String, right: String, end: Int): Int = matching(open, left, right, end) + 1
        private fun matching(open: Int, left: String, right: String, end: Int): Int { var d=0; for(i in open until end){ if(tokens[i].text==left)d++; if(tokens[i].text==right&&--d==0)return i }; return end-1 }
        private fun splitTopLevel(start: Int, end: Int, delimiter: String): List<Pair<Int,Int>> { val r=mutableListOf<Pair<Int,Int>>(); var s=start; var d=0; for(i in start until end){ when(tokens[i].text){"(","<","[","{"->d++; ")",">","]","}"->d--}; if(tokens[i].text==delimiter&&d==0){r+=s to i;s=i+1} }; if(s<end)r+=s to end; return r }
        private fun containsAtTopLevel(start: Int, end: Int, value: String): Boolean { var d=0; for(i in start until end){ when(tokens[i].text){"(","<","[","{"->d++; ")",">","]","}"->d--}; if(d==0&&tokens[i].text==value)return true }; return false }
        private fun visibilityOf(text: String): SourceVisibility? = when(text){"public"->SourceVisibility.PUBLIC;"internal"->SourceVisibility.INTERNAL;"protected"->SourceVisibility.PROTECTED;"private"->SourceVisibility.PRIVATE;else->null}
        private fun modifierOf(text: String): SourceModifier? = modifierMap[text]
        private data class Parsed(val symbol: SourceSymbol, val nextIndex: Int)

        companion object {
            private val classKinds = setOf(SourceSymbolKind.CLASS, SourceSymbolKind.ENUM_CLASS, SourceSymbolKind.ANNOTATION_CLASS)
            private val modifierMap = mapOf("abstract" to SourceModifier.ABSTRACT,"final" to SourceModifier.FINAL,"open" to SourceModifier.OPEN,"sealed" to SourceModifier.SEALED,"data" to SourceModifier.DATA,"value" to SourceModifier.VALUE,"inner" to SourceModifier.INNER,"companion" to SourceModifier.COMPANION,"suspend" to SourceModifier.SUSPEND,"operator" to SourceModifier.OPERATOR,"infix" to SourceModifier.INFIX,"inline" to SourceModifier.INLINE,"tailrec" to SourceModifier.TAILREC,"override" to SourceModifier.OVERRIDE,"const" to SourceModifier.CONST,"lateinit" to SourceModifier.LATEINIT,"external" to SourceModifier.EXTERNAL,"expect" to SourceModifier.EXPECT,"actual" to SourceModifier.ACTUAL)
            private val symbolComparator = compareBy<SourceSymbol>({ it.location?.lineStart ?: Int.MAX_VALUE }, { it.location?.columnStart ?: Int.MAX_VALUE }, { it.name }, { it.kind.name })
            private fun stableId(path: String, name: String, kind: SourceSymbolKind, line: Int, column: Int): String { val raw="$path|$name|${kind.name}|$line|$column"; return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(12).joinToString(""){"%02x".format(it)} }
        }
    }

    private companion object {
        val NAVIGATION_APIS = mapOf(
            "androidx.navigation.compose.composable" to ComposeNavigationRegistrationKind.COMPOSABLE,
            "androidx.navigation.compose.navigation" to ComposeNavigationRegistrationKind.NAVIGATION,
            "androidx.navigation.compose.dialog" to ComposeNavigationRegistrationKind.DIALOG,
        )
    }
}
