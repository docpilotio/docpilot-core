package io.docpilot.core.evolution

import io.docpilot.core.model.ApiSpecification
import io.docpilot.core.model.ComponentSpecification
import io.docpilot.core.model.Evidence
import io.docpilot.core.model.ModuleSpecification
import io.docpilot.core.model.PackageSpecification
import io.docpilot.core.model.ProjectDescriptor
import io.docpilot.core.model.PropertySpecification
import io.docpilot.core.model.RelationshipSpecification
import io.docpilot.core.model.ContractSpecification
import io.docpilot.core.specification.ContractCanonicalizer
import java.security.MessageDigest

public object EvolutionCanonicalizer {
    public fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    public fun stableChangeId(
        subjectId: String,
        subjectKind: EvolutionSubjectKind,
        kind: EvolutionChangeKind,
        beforeSha256: String?,
        afterSha256: String?,
        previousParentId: String?,
        currentParentId: String?,
        changedFields: List<String>,
    ): String = "evolution-change:${sha256(record(
        "change",
        subjectKind.name,
        subjectId,
        kind.name,
        beforeSha256.orEmpty(),
        afterSha256.orEmpty(),
        previousParentId.orEmpty(),
        currentParentId.orEmpty(),
        changedFields.distinct().sorted().joinToString(","),
    ))}"

    public fun graphSha256(nodes: List<DocumentationEvolutionGraphNode>, edges: List<DocumentationEvolutionGraphEdge>): String =
        sha256(buildString {
            nodes.sortedBy { it.nodeId }.forEach { node ->
                append(record("node", node.nodeId, node.kind.name, node.subjectId, node.evidenceRefs.distinct().sorted().joinToString(",")))
            }
            edges.sortedWith(compareBy({ it.sourceNodeId }, { it.targetNodeId }, { it.kind.name })).forEach { edge ->
                append(record("edge", edge.sourceNodeId, edge.targetNodeId, edge.kind.name))
            }
        })

    public fun reportSha256(report: DocumentationEvolutionReport): String = sha256(reportPayload(report))

    public fun reportPayload(report: DocumentationEvolutionReport): String = buildString {
        append(record("format", report.formatVersion.toString()))
        append(record("project", report.projectId))
        append(record("states", report.beforeStateSha256, report.afterStateSha256))
        report.changes.sortedBy { it.changeId }.forEach { change ->
            append(record(
                "change",
                change.changeId,
                change.subjectId,
                change.subjectKind.name,
                change.kind.name,
                change.beforeSha256.orEmpty(),
                change.afterSha256.orEmpty(),
                change.previousParentId.orEmpty(),
                change.currentParentId.orEmpty(),
                change.changedFields.joinToString(","),
                change.evidenceRefs.joinToString(","),
                change.causalPredecessorIds.joinToString(","),
                change.affectedArtifactIds.joinToString(","),
                change.confidenceClass.name,
                change.coverageState.name,
            ))
        }
        append(record("graph", report.causalGraph.graphSha256))
        report.causalGraph.nodes.sortedBy { it.nodeId }.forEach { node ->
            append(record("node", node.nodeId, node.kind.name, node.subjectId, node.evidenceRefs.joinToString(",")))
        }
        report.causalGraph.edges.sortedWith(compareBy({ it.sourceNodeId }, { it.targetNodeId }, { it.kind.name })).forEach { edge ->
            append(record("edge", edge.sourceNodeId, edge.targetNodeId, edge.kind.name))
        }
        report.impactedArtifacts.sortedBy { it.artifactId }.forEach { impact ->
            append(record(
                "impact",
                impact.artifactId,
                impact.relativePath,
                impact.operation.name,
                impact.selectionReasons.joinToString(","),
                impact.sourceChangeIds.joinToString(","),
                impact.causalChangeIds.joinToString(","),
                impact.beforeArtifactSha256.orEmpty(),
                impact.afterArtifactSha256.orEmpty(),
            ))
        }
        append(record("coverage", report.coverage.state.name))
        report.coverage.findings.sortedWith(compareBy({ it.kind.name }, { it.subjectId }, { it.message })).forEach { finding ->
            append(record("finding", finding.kind.name, finding.subjectId, finding.message, finding.required.toString()))
        }
        report.evidenceRefs.distinct().sorted().forEach { append(record("evidence", it)) }
    }

    public fun project(value: ProjectDescriptor): String = record(
        value.id,
        value.name,
        value.description.orEmpty(),
        value.platforms.sorted().joinToString(","),
        value.languages.sorted().joinToString(","),
        value.buildSystems.sorted().joinToString(","),
    )

    public fun module(value: ModuleSpecification): String = record(
        value.id, value.name, value.path.orEmpty(), value.description.orEmpty(),
        value.sourceSets.sorted().joinToString(","), value.evidenceRefs.sorted().joinToString(","),
    )

    public fun packageSpecification(value: PackageSpecification): String = record(
        value.id, value.name, value.qualifiedName, value.moduleId, value.description.orEmpty(),
        value.evidenceRefs.sorted().joinToString(","),
    )

    public fun component(value: ComponentSpecification): String = record(
        value.id, value.name, value.moduleId, value.kind, value.role, value.packageId.orEmpty(),
        value.qualifiedName.orEmpty(), value.visibility.orEmpty(), value.modifiers.sorted().joinToString(","),
        value.annotations.joinToString(","), value.typeParameters.joinToString(","), value.superTypes.joinToString(","),
        value.responsibilities.joinToString("\u001f"), value.dependencyIds.sorted().joinToString(","),
        value.evidenceRefs.sorted().joinToString(","),
    )

    public fun api(value: ApiSpecification): String = record(
        value.id, value.name, value.kind, value.signature.orEmpty(), value.visibility.orEmpty(),
        value.receiverType.orEmpty(), value.returnType.orEmpty(),
        value.parameters.joinToString("\u001f") { record(it.name, it.type.orEmpty(), it.hasDefaultValue.toString()) },
        value.modifiers.sorted().joinToString(","), value.annotations.joinToString(","),
        value.purpose.orEmpty(), value.evidenceRefs.sorted().joinToString(","),
    )

    public fun property(value: PropertySpecification): String = record(
        value.id, value.name, value.type.orEmpty(), value.visibility.orEmpty(), value.mutable?.toString().orEmpty(),
        value.hasInitializer?.toString().orEmpty(), value.modifiers.sorted().joinToString(","),
        value.annotations.joinToString(","), value.purpose.orEmpty(), value.evidenceRefs.sorted().joinToString(","),
    )

    public fun relationship(value: RelationshipSpecification): String = record(
        value.id, value.type, value.sourceId, value.targetId, value.description.orEmpty(),
        value.evidenceRefs.sorted().joinToString(","),
    )

    public fun contract(value: ContractSpecification): String = ContractCanonicalizer.canonical(value)

    public fun evidence(value: Evidence): String = record(
        value.id, value.type, value.file.orEmpty(), value.symbol.orEmpty(), value.lineStart?.toString().orEmpty(),
        value.lineEnd?.toString().orEmpty(), value.summary, value.confidence.name,
    )

    public fun record(vararg values: String): String = buildString {
        values.forEach { value ->
            append(value.toByteArray(Charsets.UTF_8).size).append(':').append(value)
        }
        append('\n')
    }

    public fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') &&
            path.split('/').none { it == ".." || it.isBlank() }
}
