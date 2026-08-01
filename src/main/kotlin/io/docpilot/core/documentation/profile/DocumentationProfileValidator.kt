package io.docpilot.core.documentation.profile

import io.docpilot.core.reconciliation.DocumentationOwnership

public object DocumentationProfileValidator {
    private val idPattern = Regex("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
    private val stableKeyPattern = Regex("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
    private val sectionIdPattern = Regex("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$")
    private val placeholderPattern = Regex("\\{([A-Za-z][A-Za-z0-9]*)}")
    private val allowedPlaceholders = setOf(
        "scopeId", "scopeHash", "slug", "moduleId", "packageId", "componentId",
        "featureId", "externalSystemId", "contractId",
    )

    public fun validate(profile: DocumentationProfile): DocumentationProfile {
        require(idPattern.matches(profile.id.value)) { "Invalid documentation profile id: ${profile.id.value}" }
        require(profile.version.value == 1) { "Unsupported documentation profile version: ${profile.version.value}" }
        require(profile.displayName.isNotBlank()) { "Documentation profile display name must not be blank." }
        require(profile.supportedProjectKinds.isNotEmpty()) { "Documentation profile must support a project kind." }
        require(profile.documentDefinitions.isNotEmpty()) { "Documentation profile must define documents." }
        require(profile.documentDefinitions.distinctBy { it.type }.size == profile.documentDefinitions.size) {
            "Duplicate document type."
        }
        require(profile.documentDefinitions.distinctBy { it.stableKey }.size == profile.documentDefinitions.size) {
            "Duplicate document stable key."
        }
        profile.documentDefinitions.forEach(::validateDefinition)
        val fixedPaths = profile.documentDefinitions.mapNotNull {
            (it.pathPolicy as? DocumentPathPolicy.Fixed)?.relativePath?.let(
                DocumentationProfileCanonicalizer::normalizeRelativePath,
            )
        }
        require(fixedPaths.distinct().size == fixedPaths.size) { "Duplicate fixed document path." }
        val keys = profile.documentDefinitions.mapTo(hashSetOf()) { it.stableKey }
        profile.documentDefinitions.flatMap { it.dependencyRules }.forEach { rule ->
            require(rule.documentStableKey in keys) {
                "Unknown document dependency: ${rule.documentStableKey.value}"
            }
        }
        return DocumentationProfileCanonicalizer.canonicalize(profile)
    }

    private fun validateDefinition(definition: DocumentDefinition) {
        require(stableKeyPattern.matches(definition.stableKey.value)) {
            "Invalid document stable key: ${definition.stableKey.value}"
        }
        require(definition.purpose.isNotBlank()) { "Document purpose must not be blank: ${definition.stableKey.value}" }
        require(definition.primaryAudiences.isNotEmpty()) {
            "Document primary audience must not be empty: ${definition.stableKey.value}"
        }
        require(definition.sections.isNotEmpty() && definition.requiredSections.isNotEmpty()) {
            "Document must define at least one required section: ${definition.stableKey.value}"
        }
        require(definition.sections.distinctBy { it.id }.size == definition.sections.size) {
            "Duplicate section id: ${definition.stableKey.value}"
        }
        require(definition.sections.distinctBy { it.order }.size == definition.sections.size) {
            "Duplicate section order: ${definition.stableKey.value}"
        }
        definition.sections.forEach(::validateSection)
        validatePathPolicy(definition)
        validateOwnershipPolicy(definition)
    }

    private fun validateSection(section: SectionDefinition) {
        require(sectionIdPattern.matches(section.id.value)) { "Invalid section id: ${section.id.value}" }
        require(section.title.isNotBlank()) { "Section title must not be blank: ${section.id.value}" }
        require(section.order >= 0) { "Section order must not be negative: ${section.id.value}" }
        require(section.evidenceRequirement.minimumEvidenceCount >= 0) {
            "Minimum Evidence count must not be negative: ${section.id.value}"
        }
        require(
            section.evidenceRequirement.minimumEvidenceCount == 0 ||
                section.evidenceRequirement.allowedEvidenceClasses.isNotEmpty(),
        ) { "Required Evidence must allow at least one Evidence class: ${section.id.value}" }
        require(section.required || section.missingDataBehavior != MissingDataBehavior.BLOCK_DOCUMENT) {
            "Optional section cannot block the document: ${section.id.value}"
        }
        require(section.required || section.missingDataBehavior != MissingDataBehavior.DEFER_DOCUMENT) {
            "Optional section cannot defer the document: ${section.id.value}"
        }
    }

    private fun validatePathPolicy(definition: DocumentDefinition) {
        when (val policy = definition.pathPolicy) {
            is DocumentPathPolicy.Fixed -> {
                require(definition.multiplicity == DocumentMultiplicity.SINGLE) {
                    "Fixed path requires SINGLE multiplicity: ${definition.stableKey.value}"
                }
                validateSafePath(policy.relativePath, allowPlaceholders = false)
            }
            is DocumentPathPolicy.Pattern -> {
                require(definition.multiplicity != DocumentMultiplicity.SINGLE) {
                    "Pattern path requires scoped multiplicity: ${definition.stableKey.value}"
                }
                validateSafePath(policy.relativePathPattern, allowPlaceholders = true)
                val placeholders = placeholderPattern.findAll(policy.relativePathPattern).map { it.groupValues[1] }.toSet()
                require(placeholders.isNotEmpty()) { "Pattern path must contain a placeholder." }
                require(placeholders.all(allowedPlaceholders::contains)) { "Unknown path placeholder." }
                require(requiredPlaceholders(definition.multiplicity).any(placeholders::contains)) {
                    "Pattern path does not identify ${definition.multiplicity.name}."
                }
                val replacedPattern = placeholderPattern.replace(policy.relativePathPattern, "scope")
                require(!replacedPattern.contains('{') && !replacedPattern.contains('}')) {
                    "Malformed path placeholder."
                }
            }
        }
    }

    private fun validateSafePath(path: String, allowPlaceholders: Boolean) {
        val trimmed = path.trim()
        val normalized = DocumentationProfileCanonicalizer.normalizeRelativePath(trimmed)
        require(normalized.isNotBlank()) { "Document path must not be blank." }
        require(!trimmed.startsWith("/") && !trimmed.startsWith("\\") && !trimmed.startsWith("//")) {
            "Absolute document path is not allowed: $path"
        }
        require(!Regex("^[A-Za-z]:").containsMatchIn(trimmed)) {
            "Drive-qualified document path is not allowed: $path"
        }
        require(trimmed.split('/', '\\').none { it.trim() == ".." || it.trim() == "." }) {
            "Path traversal is not allowed: $path"
        }
        require(normalized.split('/').none(String::isBlank)) { "Invalid document path: $path" }
        val literalPath = placeholderPattern.replace(normalized, "scope")
        require(literalPath.none { it.code < 32 || it in PORTABLE_PATH_FORBIDDEN_CHARACTERS }) {
            "Document path contains a non-portable character: $path"
        }
        if (!allowPlaceholders) require(!normalized.contains('{') && !normalized.contains('}')) {
            "Fixed document path cannot contain placeholders: $path"
        }
    }

    private fun validateOwnershipPolicy(definition: DocumentDefinition) {
        val policy = definition.ownershipPolicy
        require(policy.defaultOwnership != DocumentationOwnership.UNKNOWN &&
            policy.defaultOwnership != DocumentationOwnership.CONFLICTED) {
            "Document ownership default must be actionable: ${definition.stableKey.value}"
        }
        require(policy.defaultOwnership != DocumentationOwnership.USER_OWNED) {
            "Generated document cannot default to USER_OWNED: ${definition.stableKey.value}"
        }
        require(
            policy.defaultOwnership != DocumentationOwnership.SHARED_MANAGED ||
                policy.conflictBehavior == OwnershipConflictBehavior.REQUIRE_RECONCILIATION,
        ) { "SHARED_MANAGED document must require reconciliation: ${definition.stableKey.value}" }
    }

    private fun requiredPlaceholders(multiplicity: DocumentMultiplicity): Set<String> = when (multiplicity) {
        DocumentMultiplicity.SINGLE -> emptySet()
        DocumentMultiplicity.PER_MODULE -> setOf("moduleId", "scopeId", "scopeHash", "slug")
        DocumentMultiplicity.PER_PACKAGE -> setOf("packageId", "scopeId", "scopeHash", "slug")
        DocumentMultiplicity.PER_COMPONENT -> setOf("componentId", "scopeId", "scopeHash", "slug")
        DocumentMultiplicity.PER_FEATURE -> setOf("featureId", "scopeId", "scopeHash", "slug")
        DocumentMultiplicity.PER_EXTERNAL_SYSTEM -> setOf("externalSystemId", "scopeId", "scopeHash", "slug")
        DocumentMultiplicity.PER_CONTRACT -> setOf("contractId", "scopeId", "scopeHash", "slug")
    }

    private val PORTABLE_PATH_FORBIDDEN_CHARACTERS: Set<Char> = setOf('<', '>', ':', '"', '|', '?', '*')
}
