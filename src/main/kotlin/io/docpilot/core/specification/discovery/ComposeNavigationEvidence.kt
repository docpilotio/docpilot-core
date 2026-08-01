package io.docpilot.core.specification.discovery

import io.docpilot.core.model.EntryPointKind
import io.docpilot.core.model.EntryPointSpecification
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.UnresolvedItem
import io.docpilot.core.model.source.ComposeNavigationRegistrationObservation
import io.docpilot.core.model.source.ComposeRouteDeclarationObservation
import io.docpilot.core.model.source.ComposeFunctionReferenceObservation
import io.docpilot.core.model.source.ComposeNavigationArgumentObservation
import io.docpilot.core.model.source.ComposeNavigationGraphObservation
import io.docpilot.core.model.source.ComposeNavigationArgumentLinkObservation
import io.docpilot.core.model.source.ComposeNavigationArgumentLinkEvidenceKind
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import java.security.MessageDigest

public data class ComposeNavigationEvidenceResult(
    public val policyVersion: String,
    public val entryPoints: List<EntryPointSpecification>,
    public val unresolved: List<UnresolvedItem>,
    public val functionReferenceIds: List<String> = emptyList(),
    public val graphIds: List<String> = emptyList(),
    public val argumentIds: List<String> = emptyList(),
    public val argumentLinkIds: List<String> = emptyList(),
    public val canonicalRecords: List<String> = emptyList(),
    public val semanticHash: String,
)

public object ComposeNavigationIntegrity {
    public fun semanticHash(result: ComposeNavigationEvidenceResult): String {
        val payload = buildString {
            append("policy=").append(result.policyVersion).append('\n')
            result.entryPoints.sortedBy { it.id }.forEach {
                append(it.id).append('|').append(it.ownerComponentId).append('|')
                    .append(it.apiId.orEmpty()).append('|')
                    .append(it.evidenceRefs.sorted().joinToString(",")).append('\n')
            }
            result.unresolved.sortedBy { it.id }.forEach {
                append(it.id).append('|').append(it.subject).append('|')
                    .append(it.requiredAction.orEmpty()).append('\n')
            }
            result.functionReferenceIds.sorted().forEach { append("function-reference=").append(it).append('\n') }
            result.graphIds.sorted().forEach { append("graph=").append(it).append('\n') }
            result.argumentIds.sorted().forEach { append("argument=").append(it).append('\n') }
            result.argumentLinkIds.sorted().forEach { append("argument-link=").append(it).append('\n') }
            result.canonicalRecords.sorted().forEach { append("record=").append(it).append('\n') }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    public fun requireValid(result: ComposeNavigationEvidenceResult) {
        require(result.semanticHash == semanticHash(result)) {
            "Compose Navigation Evidence semantic integrity verification failed."
        }
    }
}

public class ComposeNavigationEvidenceResolver {
    public fun resolve(
        sourceIndex: SourceIndex?,
        specification: ProjectSpecification,
    ): ComposeNavigationEvidenceResult {
        val files = sourceIndex?.files.orEmpty()
            .filter { it.sourceSetName == null || it.sourceSetName == "main" }
        val routes = files.flatMap { it.composeNavigation.routes }.associateBy { it.qualifiedName }
        val apiByQualifiedName = buildApiIndex(files, specification)
        val apiBySimpleName = apiByQualifiedName.entries.groupBy({ it.key.substringAfterLast('.') }, { it })
        val symbolByApiId = files.flatMap { it.symbols.flatMap(::flatten) }
            .associateBy { "symbol:${it.id}" }
        val componentByApi = specification.components.flatMap { component ->
            component.apis.map { it.id to component }
        }.toMap()
        val unresolved = mutableListOf<UnresolvedItem>()
        val argumentLinks = mutableListOf<ComposeNavigationArgumentLinkObservation>()
        val entryPoints = files.flatMap { file ->
            file.composeNavigation.registrations.filter {
                it.apiKind != io.docpilot.core.model.source.ComposeNavigationRegistrationKind.NAVIGATION
            }.mapNotNull { registration ->
                val route = resolveRoute(file, registration, routes)
                if (route == null) {
                    unresolved += finding(registration.id, "UNRESOLVED_ROUTE_REFERENCE")
                    return@mapNotNull null
                }
                val calls = registration.destinationCalls.mapNotNull { call ->
                    apiByQualifiedName[call.calleeQualifiedName]?.let { call to it }
                }.toMutableList()
                registration.functionReferences.forEach { reference ->
                    val resolved = resolveFunctionReference(file, reference, apiByQualifiedName, apiBySimpleName)
                    when {
                        resolved.size == 1 -> calls += io.docpilot.core.model.source.ComposeDestinationCallObservation(
                            resolved.single().key, 0, reference.location,
                        ) to resolved.single().value
                        resolved.isEmpty() -> {
                            val reason = when {
                                reference.receiverExpression?.firstOrNull()?.isLowerCase() == true ->
                                    "FUNCTION_REFERENCE_RECEIVER_UNRESOLVED"
                                hasOverloadAmbiguity(files, file, reference) ->
                                    "FUNCTION_REFERENCE_OVERLOAD_AMBIGUITY"
                                else -> "UNRESOLVED_FUNCTION_REFERENCE"
                            }
                            unresolved += finding(reference.id, reason)
                        }
                        else -> unresolved += finding(reference.id, "AMBIGUOUS_FUNCTION_REFERENCE")
                    }
                }
                registration.externalLambdaReference?.let { lambdaName ->
                    val candidates = file.symbols.flatMap(::flatten).filter {
                        it.kind == SourceSymbolKind.PROPERTY && it.name == lambdaName
                    }
                    when {
                        candidates.size > 1 -> unresolved += finding(registration.id, "EXTERNAL_LAMBDA_MULTIPLE_INITIALIZERS")
                        candidates.isEmpty() -> unresolved += finding(registration.id, "EXTERNAL_LAMBDA_UNRESOLVED")
                        candidates.single().mutable == true -> unresolved += finding(registration.id, "EXTERNAL_LAMBDA_REASSIGNED")
                        else -> {
                            val initializer = candidates.single().initializerExpression.orEmpty()
                            val targetNames = CALL_EXPRESSION.findAll(initializer).map { it.groupValues[1] }
                                .filterNot { it in NON_DESTINATION_CALLS }.distinct().toList()
                            val targets = targetNames.flatMap { name ->
                                resolveSimpleCallable(file, name, apiByQualifiedName, apiBySimpleName)
                            }.distinctBy { it.key }
                            when (targets.size) {
                                1 -> calls += io.docpilot.core.model.source.ComposeDestinationCallObservation(
                                    targets.single().key, 0, candidates.single().location ?: registration.location,
                                ) to targets.single().value
                                0 -> unresolved += finding(registration.id, "EXTERNAL_LAMBDA_UNRESOLVED")
                                else -> unresolved += finding(registration.id, "EXTERNAL_LAMBDA_TARGET_AMBIGUOUS")
                            }
                        }
                    }
                }
                if (calls.isEmpty()) {
                    if (registration.functionReferences.isEmpty() && registration.externalLambdaReference == null) {
                        unresolved += finding(registration.id, "UNRESOLVED_DESTINATION_LAMBDA")
                    }
                    return@mapNotNull null
                }
                val maximumDepth = calls.maxOf { it.first.nestingDepth }
                val deepest = calls.filter { it.first.nestingDepth == maximumDepth }
                    .distinctBy { it.second }
                if (deepest.size != 1) {
                    unresolved += finding(registration.id, "MULTIPLE_DESTINATION_TARGETS")
                    return@mapNotNull null
                }
                val apiId = deepest.single().second
                val destinationSymbol = symbolByApiId[apiId]
                if (destinationSymbol != null) {
                    val typedArguments = file.composeNavigation.routeArguments.filter { it.ownerRouteId == route.id }
                    typedArguments.forEach { argument ->
                        val matches = destinationSymbol.parameters.filter { parameter ->
                            parameter.name == argument.name && normalizeType(parameter.type) == normalizeType(argument.declaredType)
                        }
                        if (matches.size == 1) {
                            argumentLinks += ComposeNavigationArgumentLinkObservation(
                                id = "compose-argument-link:${argument.id}:$apiId:${matches.single().name}",
                                argumentId = argument.id,
                                destinationSymbolId = apiId,
                                parameterName = matches.single().name,
                                evidenceKind = ComposeNavigationArgumentLinkEvidenceKind.TYPED_ROUTE_PARAMETER_SIGNATURE,
                            )
                        } else if (matches.size > 1) {
                            unresolved += finding(argument.id, "AMBIGUOUS_DESTINATION_PARAMETER_LINK")
                        }
                    }
                }
                val owner = componentByApi[apiId]
                if (owner == null) {
                    unresolved += finding(registration.id, "AMBIGUOUS_COMPOSE_FEATURE_BOUNDARY")
                    return@mapNotNull null
                }
                val registrationEvidence = evidenceId(
                    specification, "COMPOSE_NAVIGATION_REGISTRATION", registration.location.relativePath,
                    registration.location.lineStart,
                )
                val routeEvidence = evidenceId(
                    specification, "COMPOSE_ROUTE_DECLARATION", route.location.relativePath,
                    route.location.lineStart,
                )
                val destinationEvidence = owner.apis.single { it.id == apiId }.evidenceRefs
                val functionReferenceEvidence = registration.functionReferences.flatMap { reference ->
                    evidenceIds(specification, "COMPOSE_FUNCTION_REFERENCE", reference.location.relativePath,
                        reference.location.lineStart)
                }
                val graphEvidence = registration.ownerGraphId?.let { ownerGraphId ->
                    file.composeNavigation.graphs.singleOrNull { it.id == ownerGraphId }
                }?.let { graph ->
                    evidenceIds(specification, "COMPOSE_NAVIGATION_GRAPH", graph.location.relativePath,
                        graph.location.lineStart)
                }.orEmpty()
                val argumentEvidence = (registration.arguments +
                    file.composeNavigation.routeArguments.filter { it.ownerRouteId == route.id }).flatMap { argument ->
                    evidenceIds(specification, "COMPOSE_NAVIGATION_ARGUMENT", argument.location.relativePath,
                        argument.location.lineStart)
                }
                val evidence = (listOfNotNull(registrationEvidence, routeEvidence) + destinationEvidence +
                    functionReferenceEvidence + graphEvidence + argumentEvidence)
                    .toSortedSet()
                if (registrationEvidence == null || evidence.isEmpty()) {
                    unresolved += finding(registration.id, "INSUFFICIENT_SOURCE_EVIDENCE")
                    return@mapNotNull null
                }
                val routeId = route.id
                EntryPointSpecification(
                    id = "entry-point:compose-destination:$routeId:$apiId",
                    name = route.qualifiedName,
                    kind = EntryPointKind.COMPOSE_DESTINATION.name,
                    ownerComponentId = owner.id,
                    apiId = apiId,
                    evidenceRefs = evidence,
                )
            }
        }.distinctBy { it.id }.sortedBy { it.id }
        val canonical = ComposeNavigationEvidenceResult(
            policyVersion = "1",
            entryPoints = entryPoints,
            unresolved = unresolved.distinctBy { it.id }.sortedBy { it.id },
            functionReferenceIds = files.flatMap { file ->
                file.composeNavigation.registrations.flatMap { it.functionReferences }
            }.map { it.id }.distinct().sorted(),
            graphIds = files.flatMap { it.composeNavigation.graphs }.map { it.id }.distinct().sorted(),
            argumentIds = files.flatMap { file ->
                file.composeNavigation.routeArguments + file.composeNavigation.registrations.flatMap { it.arguments }
            }.map { it.id }.distinct().sorted(),
            argumentLinkIds = argumentLinks.map { it.id }.distinct().sorted(),
            canonicalRecords = canonicalRecords(files),
            semanticHash = "",
        )
        return canonical.copy(semanticHash = ComposeNavigationIntegrity.semanticHash(canonical))
            .also(ComposeNavigationIntegrity::requireValid)
    }

    private fun resolveSimpleCallable(
        file: SourceFile,
        name: String,
        apiByQualifiedName: Map<String, String>,
        apiBySimpleName: Map<String, List<Map.Entry<String, String>>>,
    ): List<Map.Entry<String, String>> {
        val imported = file.imports.filter {
            (it.alias ?: it.qualifiedName.substringAfterLast('.')) == name
        }.map { it.qualifiedName }.distinct()
        if (imported.isNotEmpty()) return apiByQualifiedName.entries.filter { it.key in imported }
        val samePackage = file.packageName?.let { "$it.$name" }
        if (samePackage != null && samePackage in apiByQualifiedName) {
            return apiByQualifiedName.entries.filter { it.key == samePackage }
        }
        return apiBySimpleName[name].orEmpty()
    }

    private fun canonicalRecords(files: List<SourceFile>): List<String> = files.flatMap { file ->
        val references = file.composeNavigation.registrations.flatMap { it.functionReferences }.map {
            "function-reference|${it.id}|${it.kind}|${it.receiverExpression.orEmpty()}|${it.referencedName}|${it.ownerRegistrationId}"
        }
        val graphs = file.composeNavigation.graphs.map {
            "graph|${it.id}|${it.kind}|${it.routeExpression}|${it.startDestinationExpression.orEmpty()}|" +
                "${it.parentGraphId.orEmpty()}|${it.childRegistrationIds.sorted().joinToString(",")}"
        }
        val arguments = (file.composeNavigation.routeArguments +
            file.composeNavigation.registrations.flatMap { it.arguments }).map {
            "argument|${it.id}|${it.sourceKind}|${it.name}|${it.declaredType.orEmpty()}|" +
                "${it.nullable}|${it.defaultValueExpression.orEmpty()}|${it.routePlaceholder.orEmpty()}"
        }
        references + graphs + arguments
    }.distinct().sorted()

    private fun normalizeType(type: String?): String? = type?.replace(" ", "")

    private fun resolveFunctionReference(
        file: SourceFile,
        reference: ComposeFunctionReferenceObservation,
        apiByQualifiedName: Map<String, String>,
        apiBySimpleName: Map<String, List<Map.Entry<String, String>>>,
    ): List<Map.Entry<String, String>> {
        val receiver = reference.receiverExpression
        val explicit = when {
            receiver != null && receiver.contains('.') -> "$receiver.${reference.referencedName}"
            receiver != null -> {
                val importedReceiver = file.imports.filter {
                    (it.alias ?: it.qualifiedName.substringAfterLast('.')) == receiver
                }.map { it.qualifiedName }.distinct().singleOrNull()
                "${importedReceiver ?: file.packageName?.let { "$it.$receiver" } ?: receiver}.${reference.referencedName}"
            }
            else -> null
        }
        if (explicit != null) return apiByQualifiedName.entries.filter { it.key == explicit }
        val imported = file.imports.filter {
            (it.alias ?: it.qualifiedName.substringAfterLast('.')) == reference.referencedName
        }.map { it.qualifiedName }.distinct()
        if (imported.isNotEmpty()) return apiByQualifiedName.entries.filter { it.key in imported }
        val samePackage = file.packageName?.let { "${it}.${reference.referencedName}" }
        if (samePackage != null && samePackage in apiByQualifiedName) {
            return apiByQualifiedName.entries.filter { it.key == samePackage }
        }
        return apiBySimpleName[reference.referencedName].orEmpty()
    }

    private fun hasOverloadAmbiguity(
        files: List<SourceFile>,
        ownerFile: SourceFile,
        reference: ComposeFunctionReferenceObservation,
    ): Boolean {
        val candidates = files.flatMap { it.symbols.flatMap(::flatten) }.filter {
            it.kind == SourceSymbolKind.FUNCTION && it.name == reference.referencedName &&
                it.annotations.any { annotation -> annotation.substringAfterLast('.') == "Composable" }
        }
        val qualifiedTargets = when {
            reference.receiverExpression != null -> setOf(
                "${ownerFile.packageName}.${reference.receiverExpression}.${reference.referencedName}",
            )
            else -> ownerFile.imports.filter {
                (it.alias ?: it.qualifiedName.substringAfterLast('.')) == reference.referencedName
            }.map { it.qualifiedName }.toSet().ifEmpty {
                setOf("${ownerFile.packageName}.${reference.referencedName}")
            }
        }
        return candidates.count { it.qualifiedName in qualifiedTargets } > 1
    }

    private fun resolveRoute(
        file: SourceFile,
        registration: ComposeNavigationRegistrationObservation,
        routes: Map<String, ComposeRouteDeclarationObservation>,
    ): ComposeRouteDeclarationObservation? {
        val raw = registration.genericRouteType ?: registration.routeExpression
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            return ComposeRouteDeclarationObservation(
                id = inlineRouteId(raw),
                symbolId = registration.ownerSymbolId,
                qualifiedName = raw,
                kind = io.docpilot.core.model.source.ComposeRouteDeclarationKind.STRING_ROUTE,
                expression = raw,
                location = registration.location,
            )
        }
        val candidates = buildSet {
            add(raw)
            file.packageName?.let { add("$it.$raw") }
            file.imports.filter { (it.alias ?: it.qualifiedName.substringAfterLast('.')) == raw }
                .forEach { add(it.qualifiedName) }
        }.mapNotNull(routes::get).distinctBy { it.id }
        return candidates.singleOrNull()
    }

    private fun buildApiIndex(
        files: List<SourceFile>,
        specification: ProjectSpecification,
    ): Map<String, String> {
        val existingApiIds = specification.components.flatMap { it.apis }.mapTo(mutableSetOf()) { it.id }
        return files.flatMap { file -> file.symbols.flatMap(::flatten) }
            .filter {
                it.kind == SourceSymbolKind.FUNCTION &&
                    it.annotations.any { annotation -> annotation.substringAfterLast('.') == "Composable" }
            }
            .mapNotNull { symbol ->
                val qualifiedName = symbol.qualifiedName ?: return@mapNotNull null
                val apiId = "symbol:${symbol.id}"
                if (apiId in existingApiIds) qualifiedName to apiId else null
            }.groupBy({ it.first }, { it.second })
            .mapNotNull { (name, ids) -> ids.distinct().singleOrNull()?.let { name to it } }
            .toMap()
    }

    private fun evidenceId(
        specification: ProjectSpecification,
        type: String,
        file: String,
        line: Int?,
    ): String? = specification.evidence.filter {
        it.type == type && it.file == file && it.lineStart == line
    }.map { it.id }.distinct().singleOrNull()

    private fun evidenceIds(
        specification: ProjectSpecification,
        type: String,
        file: String,
        line: Int?,
    ): List<String> = specification.evidence.filter {
        it.type == type && it.file == file && it.lineStart == line
    }.map { it.id }.distinct().sorted()

    private fun finding(subject: String, reason: String) = UnresolvedItem(
        id = "compose-navigation:${reason.lowercase()}:$subject",
        subject = subject,
        question = "Compose Navigation evidence could not be resolved deterministically.",
        requiredAction = reason,
    )

    private fun inlineRouteId(expression: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(expression.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "compose-route:inline:$digest"
    }

    private fun flatten(symbol: SourceSymbol): List<SourceSymbol> =
        listOf(symbol) + symbol.children.flatMap(::flatten)

    private companion object {
        val CALL_EXPRESSION = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
        val NON_DESTINATION_CALLS = setOf("if", "when", "for", "while")
    }
}
