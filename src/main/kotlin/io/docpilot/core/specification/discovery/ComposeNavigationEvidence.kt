package io.docpilot.core.specification.discovery

import io.docpilot.core.model.EntryPointKind
import io.docpilot.core.model.EntryPointSpecification
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.UnresolvedItem
import io.docpilot.core.model.source.ComposeNavigationRegistrationObservation
import io.docpilot.core.model.source.ComposeRouteDeclarationObservation
import io.docpilot.core.model.source.SourceFile
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceSymbol
import io.docpilot.core.model.source.SourceSymbolKind
import java.security.MessageDigest

public data class ComposeNavigationEvidenceResult(
    public val policyVersion: String,
    public val entryPoints: List<EntryPointSpecification>,
    public val unresolved: List<UnresolvedItem>,
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
        val componentByApi = specification.components.flatMap { component ->
            component.apis.map { it.id to component }
        }.toMap()
        val unresolved = mutableListOf<UnresolvedItem>()
        val entryPoints = files.flatMap { file ->
            file.composeNavigation.registrations.mapNotNull { registration ->
                val route = resolveRoute(file, registration, routes)
                if (route == null) {
                    unresolved += finding(registration.id, "UNRESOLVED_ROUTE_REFERENCE")
                    return@mapNotNull null
                }
                val calls = registration.destinationCalls.mapNotNull { call ->
                    apiByQualifiedName[call.calleeQualifiedName]?.let { call to it }
                }
                if (calls.isEmpty()) {
                    unresolved += finding(registration.id, "UNRESOLVED_DESTINATION_LAMBDA")
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
                val owner = componentByApi[apiId]
                if (owner == null) {
                    unresolved += finding(registration.id, "AMBIGUOUS_COMPOSE_FEATURE_BOUNDARY")
                    return@mapNotNull null
                }
                val registrationEvidence = evidenceId(
                    specification, "COMPOSE_NAVIGATION_REGISTRATION", registration.location.relativePath,
                    registration.location.lineStart,
                )
                val routeEvidence = route?.let {
                    evidenceId(
                        specification, "COMPOSE_ROUTE_DECLARATION", it.location.relativePath,
                        it.location.lineStart,
                    )
                }
                val destinationEvidence = owner.apis.single { it.id == apiId }.evidenceRefs
                val evidence = (listOfNotNull(registrationEvidence, routeEvidence) + destinationEvidence)
                    .toSortedSet()
                if (registrationEvidence == null || evidence.isEmpty()) {
                    unresolved += finding(registration.id, "INSUFFICIENT_SOURCE_EVIDENCE")
                    return@mapNotNull null
                }
                val routeId = route?.id ?: inlineRouteId(registration.routeExpression)
                EntryPointSpecification(
                    id = "entry-point:compose-destination:$routeId:$apiId",
                    name = route?.qualifiedName ?: registration.routeExpression,
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
            semanticHash = "",
        )
        return canonical.copy(semanticHash = ComposeNavigationIntegrity.semanticHash(canonical))
            .also(ComposeNavigationIntegrity::requireValid)
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
}
