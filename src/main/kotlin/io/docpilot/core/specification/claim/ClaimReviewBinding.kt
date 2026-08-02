package io.docpilot.core.specification.claim

/**
 * Binds a [Claim] into the existing Review Bundle lifecycle (format 1) with zero changes to
 * `DocumentationReviewModels.kt`/`ReviewBundleModels.kt`/`ReviewLifecycleModels.kt` or their codec:
 * `DocumentationReviewDecision.targetId`/`DocumentationReviewEntry.targetId` are already plain
 * non-blank `String`, so a Claim's stable ID is a drop-in valid review target identity.
 */
public object ClaimReviewBinding {
    public fun decisionTargetId(claim: Claim): String = claim.id.value
}
