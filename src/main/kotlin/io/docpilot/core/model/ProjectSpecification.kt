package io.docpilot.core.model

public data class ProjectSpecification(
    public val schemaVersion: String = "0.2",
    public val project: ProjectDescriptor,
    public val modules: List<ModuleSpecification> = emptyList(),
    public val packages: List<PackageSpecification> = emptyList(),
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
    public val sourceSets: Set<String> = emptySet(),
    public val evidenceRefs: Set<String> = emptySet(),
)

public data class PackageSpecification(
    public val id: String,
    public val name: String,
    public val qualifiedName: String,
    public val moduleId: String,
    public val description: String? = null,
    public val evidenceRefs: Set<String> = emptySet(),
)

public data class ComponentSpecification(
    public val id: String,
    public val name: String,
    public val moduleId: String,
    public val kind: String,
    public val role: String,
    public val packageId: String? = null,
    public val qualifiedName: String? = null,
    public val visibility: String? = null,
    public val modifiers: Set<String> = emptySet(),
    public val annotations: List<String> = emptyList(),
    public val typeParameters: List<String> = emptyList(),
    public val superTypes: List<String> = emptyList(),
    public val responsibilities: List<String> = emptyList(),
    public val dependencyIds: Set<String> = emptySet(),
    public val apis: List<ApiSpecification> = emptyList(),
    public val properties: List<PropertySpecification> = emptyList(),
    public val evidenceRefs: Set<String> = emptySet(),
)

public data class ApiSpecification(
    public val id: String,
    public val name: String,
    public val kind: String,
    public val signature: String? = null,
    public val visibility: String? = null,
    public val receiverType: String? = null,
    public val returnType: String? = null,
    public val parameters: List<ParameterSpecification> = emptyList(),
    public val modifiers: Set<String> = emptySet(),
    public val annotations: List<String> = emptyList(),
    public val purpose: String? = null,
    public val evidenceRefs: Set<String> = emptySet(),
)

public data class ParameterSpecification(
    public val name: String,
    public val type: String? = null,
    public val hasDefaultValue: Boolean = false,
)

public data class PropertySpecification(
    public val id: String,
    public val name: String,
    public val type: String? = null,
    public val visibility: String? = null,
    public val mutable: Boolean? = null,
    public val hasInitializer: Boolean? = null,
    public val modifiers: Set<String> = emptySet(),
    public val annotations: List<String> = emptyList(),
    public val purpose: String? = null,
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

public enum class EvidenceConfidence { HIGH, MEDIUM, LOW }

public data class UnresolvedItem(
    public val id: String,
    public val subject: String,
    public val question: String,
    public val requiredAction: String? = null,
)
