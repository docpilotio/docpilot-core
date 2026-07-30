package io.docpilot.core.documentation.profile

import java.security.MessageDigest

public object DocumentationProfileCanonicalizer {
    public fun canonicalize(profile: DocumentationProfile): DocumentationProfile = profile.copy(
        displayName = profile.displayName.trim(),
        supportedProjectKinds = profile.supportedProjectKinds.sortedBy { it.name }.toCollection(linkedSetOf()),
        documentDefinitions = profile.documentDefinitions.map(::canonicalize).sortedBy { it.stableKey.value },
    )

    public fun canonicalize(definition: DocumentDefinition): DocumentDefinition = definition.copy(
        purpose = definition.purpose.trim(),
        primaryAudiences = definition.primaryAudiences.sortedBy { it.name }.toCollection(linkedSetOf()),
        secondaryAudiences = definition.secondaryAudiences.sortedBy { it.name }.toCollection(linkedSetOf()),
        sections = definition.sections.map(::canonicalize).sortedWith(compareBy({ it.order }, { it.id.value })),
        rendererCapabilities = definition.rendererCapabilities.sortedBy { it.name }.toCollection(linkedSetOf()),
        dependencyRules = definition.dependencyRules.sortedWith(
            compareBy({ it.documentStableKey.value }, { it.required }),
        ).toCollection(linkedSetOf()),
        pathPolicy = when (val policy = definition.pathPolicy) {
            is DocumentPathPolicy.Fixed -> DocumentPathPolicy.Fixed(normalizeRelativePath(policy.relativePath))
            is DocumentPathPolicy.Pattern -> DocumentPathPolicy.Pattern(
                normalizeRelativePath(policy.relativePathPattern),
            )
        },
    )

    public fun canonicalize(section: SectionDefinition): SectionDefinition = section.copy(
        title = section.title.trim(),
        evidenceRequirement = section.evidenceRequirement.copy(
            allowedEvidenceClasses = section.evidenceRequirement.allowedEvidenceClasses
                .sortedBy { it.name }.toCollection(linkedSetOf()),
        ),
        requiredCapabilities = section.requiredCapabilities.sortedBy { it.name }.toCollection(linkedSetOf()),
    )

    public fun normalizeRelativePath(path: String): String = path.trim().replace('\\', '/')
        .split('/').filter(String::isNotEmpty).joinToString("/")

    public fun slug(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "document" }

    public fun scopeHash(value: String): String = sha256(value).take(24)

    public fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
