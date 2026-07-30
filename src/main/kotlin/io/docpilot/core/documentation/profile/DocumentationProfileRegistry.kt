package io.docpilot.core.documentation.profile

public interface DocumentationProfileRegistry {
    public fun profiles(): List<DocumentationProfile>

    public fun resolve(
        id: DocumentationProfileId,
        version: DocumentationProfileVersion,
    ): DocumentationProfile
}

public class ImmutableDocumentationProfileRegistry(
    profiles: List<DocumentationProfile>,
) : DocumentationProfileRegistry {
    private val canonicalProfiles: List<DocumentationProfile> = profiles
        .map(DocumentationProfileValidator::validate)
        .sortedWith(compareBy({ it.id.value }, { it.version.value }))
        .also { validated ->
            require(validated.distinctBy { it.id to it.version }.size == validated.size) {
                "Duplicate documentation profile identity."
            }
        }

    override fun profiles(): List<DocumentationProfile> = canonicalProfiles.toList()

    override fun resolve(
        id: DocumentationProfileId,
        version: DocumentationProfileVersion,
    ): DocumentationProfile = canonicalProfiles.singleOrNull { it.id == id && it.version == version }
        ?: throw IllegalArgumentException("Unknown documentation profile: ${id.value}@${version.value}")
}

public object BuiltInDocumentationProfiles : DocumentationProfileRegistry by ImmutableDocumentationProfileRegistry(
    listOf(KotlinAndroidDocumentationProfile.profile),
)
