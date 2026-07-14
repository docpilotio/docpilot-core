package io.docpilot.core.model

/**
 * Minimal v0.1 representation of an analyzed software project.
 *
 * The model will expand as DSD-0001 becomes executable code.
 */
public data class ProjectSpecification(
    public val schemaVersion: String = "0.2",
    public val project: ProjectDescriptor,
    public val modules: List<ModuleSpecification> = emptyList(),
    public val components: List<ComponentSpecification> = emptyList(),
    public val relationships: List<RelationshipSpecification> = emptyList(),
    public val evidence: List<Evidence> = emptyList(),
    public val unresolved: List<UnresolvedItem> = emptyList(),
)

public data class ProjectDescriptor(
    public val id: String,
    public val name: String,
    public val description: String? = null,
    public val platforms: Set<String> = emptySet(),
    public val languages: Set<String> = emptySet(),
    public val buildSystems: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "Project id must not be blank." }
        require(name.isNotBlank()) { "Project name must not be blank." }
    }
}

public data class ModuleSpecification(
    public val id: String,
    public val name: String,
    public val path: String? = null,
    public val description: String? = null,
)

public data class ComponentSpecification(
    public val id: String,
    public val name: String,
    public val moduleId: String,
    public val kind: String,
    public val role: String,
    public val responsibilities: List<String> = emptyList(),
    public val dependencyIds: Set<String> = emptySet(),
    public val evidenceRefs: Set<String> = emptySet(),
)

public data class RelationshipSpecification(
    public val id: String,
    public val type: String,
    public val sourceId: String,
    public val targetId: String,
    public val description: String? = null,
    public val evidenceRefs: Set<String> = emptySet(),
)

public data class Evidence(
    public val id: String,
    public val type: String,
    public val file: String? = null,
    public val symbol: String? = null,
    public val lineStart: Int? = null,
    public val lineEnd: Int? = null,
    public val summary: String,
    public val confidence: EvidenceConfidence,
)

public enum class EvidenceConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

public data class UnresolvedItem(
    public val id: String,
    public val subject: String,
    public val question: String,
    public val requiredAction: String? = null,
)
