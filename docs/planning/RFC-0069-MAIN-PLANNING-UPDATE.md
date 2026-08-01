# RFC-0069 Main Planning Update

RFC-0069 adds Documentation Bundle Format 1 and Generation Receipt Format 1 on top of RFC-0068. Canonical authority remains the Project Specification, renderer catalog, Profile resolution, Artifact Plan, and Snapshot Format 3. Ownership state remains separate.

Implemented scope: deterministic identities/hashes, canonical artifact ordering, exact-byte file integrity, snapshot/profile/plan binding, preview computation, apply persistence/rollback participation, `NO_CHANGES` non-rewrite, additive text/JSON output, and offline `bundle verify` with stable exit codes.

RFC-0070 prerequisite remediation completed the former follow-up items: parser-backed link/resource/fragment checks, unexpected-managed-file rejection, selective complete-index merging, Receipt verification, and registry-backed Profile binding verification. This is technical completion only. Product Validation remains `PRODUCT_VALIDATION_FAIL / NOT_APPROVED`; PV-009 remains `PENDING`.
