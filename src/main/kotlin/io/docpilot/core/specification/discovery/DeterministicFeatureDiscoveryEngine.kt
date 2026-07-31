package io.docpilot.core.specification.discovery

import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.EntryPointKind
import io.docpilot.core.model.EntryPointSpecification
import io.docpilot.core.model.FeatureSpecification
import io.docpilot.core.model.ProjectSpecification
import io.docpilot.core.model.ScenarioSpecification
import io.docpilot.core.model.ScenarioStepKind
import io.docpilot.core.model.ScenarioStepSpecification
import io.docpilot.core.model.UnresolvedItem
import io.docpilot.core.model.knowledge.KnowledgeGraph
import io.docpilot.core.model.knowledge.RelationshipType
import io.docpilot.core.model.source.SourceIndex
import io.docpilot.core.model.source.SourceSymbol

public class DeterministicFeatureDiscoveryEngine(
    private val policy: FeatureDiscoveryPolicy = FeatureDiscoveryPolicy(),
) {
    public fun discover(
        graph: KnowledgeGraph,
        baseSpecification: ProjectSpecification,
        sourceIndex: SourceIndex? = null,
    ): FeatureDiscoveryResult {
        val componentByEntityId = buildMap {
            baseSpecification.components.forEach { component ->
                put(component.id, component)
                component.apis.forEach { put(it.id, component) }
                component.properties.forEach { put(it.id, component) }
            }
        }
        val productionComponentIds = sourceIndex?.files.orEmpty()
            .filter { it.sourceSetName == null || it.sourceSetName == "main" }
            .flatMap { file -> file.symbols.flatMap(::symbolIds) }
            .mapTo(mutableSetOf()) { "symbol:$it" }
        val nodeById = graph.nodes.associateBy { it.id }
        val graphEntryPoints = graph.edges.asSequence()
            .filter { it.relationship in setOf(RelationshipType.EXTENDS, RelationshipType.IMPLEMENTS) }
            .mapNotNull { edge ->
                val owner = componentByEntityId[edge.sourceNodeId] ?: return@mapNotNull null
                if (productionComponentIds.isNotEmpty() && owner.id !in productionComponentIds) return@mapNotNull null
                val target = nodeById[edge.targetNodeId] ?: return@mapNotNull null
                val qualifiedName = target.attributes["qualifiedName"] ?: target.name
                val kind = ENTRY_POINT_SUPERTYPES[qualifiedName] ?: return@mapNotNull null
                val evidence = (edge.evidenceRefs + owner.evidenceRefs).toSortedSet()
                if (evidence.isEmpty()) return@mapNotNull null
                EntryPointSpecification(
                    id = "entry-point:${kind.name.lowercase()}:${owner.id}",
                    name = owner.name,
                    kind = kind.name,
                    ownerComponentId = owner.id,
                    evidenceRefs = evidence,
                )
            }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toList()
        val importedFrameworkTypes = sourceIndex?.files.orEmpty()
            .flatMap { it.imports }
            .map { it.qualifiedName }
            .filter { it in ENTRY_POINT_SUPERTYPES }
            .groupBy { it.substringAfterLast('.') }
            .mapValues { (_, values) -> values.distinct() }
        val sourceEntryPoints = baseSpecification.components.mapNotNull { owner ->
            if (productionComponentIds.isNotEmpty() && owner.id !in productionComponentIds) return@mapNotNull null
            val kinds = owner.superTypes.mapNotNull { rawSuperType ->
                val typeName = rawSuperType.substringBefore('<').substringBefore('(').trim()
                val qualifiedName = when {
                    typeName in ENTRY_POINT_SUPERTYPES -> typeName
                    '.' !in typeName -> importedFrameworkTypes[typeName]?.singleOrNull()
                    else -> null
                } ?: return@mapNotNull null
                ENTRY_POINT_SUPERTYPES[qualifiedName]
            }.distinct()
            val kind = kinds.singleOrNull() ?: return@mapNotNull null
            if (owner.evidenceRefs.isEmpty()) return@mapNotNull null
            EntryPointSpecification(
                id = "entry-point:${kind.name.lowercase()}:${owner.id}",
                name = owner.name,
                kind = kind.name,
                ownerComponentId = owner.id,
                evidenceRefs = owner.evidenceRefs.toSortedSet(),
            )
        }
        val composeNavigation = ComposeNavigationEvidenceResolver().resolve(sourceIndex, baseSpecification)
        val entryPoints = (graphEntryPoints + sourceEntryPoints + composeNavigation.entryPoints)
            .distinctBy { it.id }
            .sortedBy { it.id }

        val unresolved = composeNavigation.unresolved.toMutableList()
        val scenarioDrafts = mutableMapOf<String, ScenarioSpecification>()
        val featureDrafts = entryPoints.groupBy { entryPoint ->
            if (entryPoint.kind == EntryPointKind.COMPOSE_DESTINATION.name) entryPoint.id
            else entryPoint.ownerComponentId
        }.entries.map { (_, ownedEntryPoints) ->
            val ownerId = ownedEntryPoints.map { it.ownerComponentId }.distinct().single()
            val owner = baseSpecification.components.single { it.id == ownerId }
            val traversal = resolveParticipants(owner, graph, componentByEntityId)
            unresolved += traversal.unresolved
            val composeEntryPoint = ownedEntryPoints.singleOrNull {
                it.kind == EntryPointKind.COMPOSE_DESTINATION.name
            }
            val featureId = if (composeEntryPoint == null) {
                "feature:${owner.id}"
            } else {
                "feature:${composeEntryPoint.id.removePrefix("entry-point:compose-destination:")}"
            }
            val scenarios = ownedEntryPoints.sortedBy { it.id }.mapNotNull { entryPoint ->
                projectScenario(featureId, entryPoint, traversal.components, graph, componentByEntityId)
            }
            scenarios.forEach { scenarioDrafts[it.id] = it }
            FeatureSpecification(
                id = featureId,
                name = owner.qualifiedName ?: owner.name,
                description = "Feature rooted at evidence-backed entry point ${owner.qualifiedName ?: owner.name}.",
                ownerComponentId = owner.id,
                participantComponentIds = traversal.components.mapTo(sortedSetOf()) { it.id },
                entryPointIds = ownedEntryPoints.map { it.id }.sorted(),
                scenarioIds = scenarios.map { it.id }.sorted(),
                evidenceRefs = (ownedEntryPoints.flatMap { it.evidenceRefs } + traversal.evidenceRefs).toSortedSet(),
                unresolvedRefs = traversal.unresolved.mapTo(sortedSetOf()) { it.id },
            )
        }.sortedBy { it.id }

        val canonical = FeatureDiscoveryResult(
            policyVersion = policy.version,
            features = featureDrafts,
            entryPoints = entryPoints,
            scenarios = scenarioDrafts.values.sortedBy { it.id },
            unresolved = unresolved.distinctBy { it.id }.sortedBy { it.id },
            semanticHash = "",
        )
        return canonical.copy(semanticHash = FeatureDiscoveryIntegrity.semanticHash(canonical))
            .also(FeatureDiscoveryIntegrity::requireValid)
    }

    private fun resolveParticipants(
        owner: ComponentSpecification,
        graph: KnowledgeGraph,
        componentByEntityId: Map<String, ComponentSpecification>,
    ): TraversalResult {
        val supported = setOf(
            RelationshipType.CALLS,
            RelationshipType.DEPENDS_ON,
            RelationshipType.IMPLEMENTS,
            RelationshipType.EXTENDS,
        )
        val visited = linkedSetOf(owner.id)
        val evidence = sortedSetOf<String>().apply { addAll(owner.evidenceRefs) }
        var frontier = sortedSetOf(owner.id)
        var depth = 0
        var limited = false
        while (frontier.isNotEmpty() && depth < policy.maximumTraversalDepth && !limited) {
            val next = sortedSetOf<String>()
            graph.edges.asSequence()
                .filter { it.relationship in supported }
                .filter { componentByEntityId[it.sourceNodeId]?.id in frontier }
                .sortedBy { it.id }
                .forEach { edge ->
                    val target = componentByEntityId[edge.targetNodeId] ?: return@forEach
                    evidence += edge.evidenceRefs
                    if (target.id !in visited) {
                        if (visited.size >= policy.maximumParticipantsPerFeature) {
                            limited = true
                            return@forEach
                        }
                        visited += target.id
                        next += target.id
                    }
                }
            frontier = next
            depth++
        }
        if (frontier.isNotEmpty()) limited = true
        val finding = if (limited) {
            UnresolvedItem(
                id = "feature-discovery:limit:${owner.id}",
                subject = owner.id,
                question = "Feature participant discovery exceeded its bounded traversal policy.",
                requiredAction = "DISCOVERY_LIMIT_EXCEEDED",
            )
        } else null
        return TraversalResult(
            components = visited.mapNotNull { id ->
                componentByEntityId[id]
            }.distinctBy { it.id }.sortedBy { it.id },
            evidenceRefs = evidence,
            unresolved = listOfNotNull(finding),
        )
    }

    private fun projectScenario(
        featureId: String,
        entryPoint: EntryPointSpecification,
        participants: List<ComponentSpecification>,
        graph: KnowledgeGraph,
        componentByEntityId: Map<String, ComponentSpecification>,
    ): ScenarioSpecification? {
        val participantIds = participants.mapTo(mutableSetOf()) { it.id }
        val calls = graph.edges.asSequence()
            .filter { it.relationship == RelationshipType.CALLS && it.evidenceRefs.isNotEmpty() }
            .mapNotNull { edge ->
                val source = componentByEntityId[edge.sourceNodeId] ?: return@mapNotNull null
                val target = componentByEntityId[edge.targetNodeId] ?: return@mapNotNull null
                if (source.id !in participantIds || target.id !in participantIds) return@mapNotNull null
                Triple(edge, source, target)
            }
            .distinctBy { it.first.id }
            .sortedBy { it.first.id }
            .toList()
        val scenarioId = "scenario:$featureId:${entryPoint.id}"
        val triggerSteps = if (entryPoint.kind == EntryPointKind.COMPOSE_DESTINATION.name) {
            listOf(
                ScenarioStepSpecification(
                    id = "scenario-step:$scenarioId:trigger:${entryPoint.id}",
                    order = 0,
                    action = ScenarioStepKind.TRIGGER.name,
                    ownerComponentId = entryPoint.ownerComponentId,
                    targetComponentId = entryPoint.ownerComponentId,
                    apiId = entryPoint.apiId,
                    evidenceRefs = entryPoint.evidenceRefs,
                ),
            )
        } else emptyList()
        if (calls.isEmpty() && triggerSteps.isEmpty()) return null
        val callSteps = calls.mapIndexed { index, (edge, source, target) ->
            ScenarioStepSpecification(
                id = "scenario-step:$scenarioId:${edge.id}",
                order = index + triggerSteps.size,
                action = ScenarioStepKind.CALL.name,
                ownerComponentId = source.id,
                targetComponentId = target.id,
                apiId = edge.targetNodeId.takeIf { target.apis.any { api -> api.id == it } },
                evidenceRefs = edge.evidenceRefs.toSortedSet(),
            )
        }
        val steps = triggerSteps + callSteps
        return ScenarioSpecification(
            id = scenarioId,
            featureId = featureId,
            name = entryPoint.name,
            entryPointId = entryPoint.id,
            steps = steps,
            evidenceRefs = steps.flatMapTo(sortedSetOf()) { it.evidenceRefs },
        )
    }

    private data class TraversalResult(
        val components: List<ComponentSpecification>,
        val evidenceRefs: Set<String>,
        val unresolved: List<UnresolvedItem>,
    )

    private fun symbolIds(symbol: SourceSymbol): List<String> =
        listOf(symbol.id) + symbol.children.flatMap(::symbolIds)

    private companion object {
        val ENTRY_POINT_SUPERTYPES = mapOf(
            "android.app.Activity" to EntryPointKind.ANDROID_ACTIVITY,
            "androidx.activity.ComponentActivity" to EntryPointKind.ANDROID_ACTIVITY,
            "androidx.appcompat.app.AppCompatActivity" to EntryPointKind.ANDROID_ACTIVITY,
            "android.app.Fragment" to EntryPointKind.ANDROID_FRAGMENT,
            "androidx.fragment.app.Fragment" to EntryPointKind.ANDROID_FRAGMENT,
            "android.app.Service" to EntryPointKind.ANDROID_SERVICE,
            "android.content.BroadcastReceiver" to EntryPointKind.ANDROID_RECEIVER,
            "android.content.ContentProvider" to EntryPointKind.ANDROID_PROVIDER,
            "androidx.work.ListenableWorker" to EntryPointKind.BACKGROUND_WORKER,
            "androidx.work.Worker" to EntryPointKind.BACKGROUND_WORKER,
            "androidx.work.CoroutineWorker" to EntryPointKind.BACKGROUND_WORKER,
        )
    }
}
