# RFC-0058 Completion Handoff

## Status

`IMPLEMENTED_WITH_ENVIRONMENT_VERIFICATION_LIMITATION`

## Outcome

RFC-0058 implements Candidate A: immutable Documentation Profiles and deterministic project-specific Document Contract Resolution are now explicit Core contracts, while the existing RFC-0052 Artifact Catalog, Artifact Plan, legacy Markdown paths, and persisted formats remain unchanged.

## Production changes

### Added

```text
src/main/kotlin/io/docpilot/core/documentation/profile/
  DocumentationProfileModels.kt
  DocumentationProfileCanonicalizer.kt
  DocumentationProfileValidator.kt
  DocumentationProfileIntegrity.kt
  DocumentationProfileRegistry.kt
  DocumentationProfileResolver.kt
  KotlinAndroidDocumentationProfile.kt
  ProfileArtifactCompatibility.kt
```

### Updated

```text
src/main/kotlin/io/docpilot/core/render/ProjectSpecificationMarkdownRenderer.kt
```

The Renderer update only declares existing capabilities:

- Markdown section rendering;
- Evidence reference rendering;
- UNKNOWN finding rendering.

Its Artifact inventory, paths, content, rendering order, and Stable IDs were not changed.

## Test changes

Added:

```text
src/test/kotlin/io/docpilot/core/documentation/profile/
  DocumentationProfileTestFixtures.kt
  DocumentationProfileContractTest.kt
  DocumentationProfileValidatorTest.kt
  DocumentationProfileIntegrityTest.kt
  DocumentationProfileResolverTest.kt
  ProfileArtifactCompatibilityTest.kt
```

Coverage includes:

- built-in Profile construction and canonical ordering;
- Profile ID and version validation;
- duplicate type, key, path, Section ID, and Section order rejection;
- absolute, drive-qualified, traversal, malformed-pattern, and unknown-placeholder rejection;
- unknown Profile failure;
- input-order-independent Profile and Resolution hashes;
- Stable ID continuity with semantic title changes;
- semantic tamper detection;
- SINGLE and PER_MODULE Resolution;
- Feature and Contract model deferral;
- required Evidence partial state;
- required and optional Renderer capability behavior;
- deterministic safe pattern paths;
- RFC-0055 ownership conflict and reconciliation behavior;
- explicit RFC-0052 legacy compatibility binding;
- unchanged legacy Renderer output behavior.

## Built-in Profile

```text
kotlin-android@1
```

Definitions:

- Project Overview
- Feature Catalog
- Architecture Overview
- Module Architecture
- Feature Specification
- Domain Model
- Database Schema
- External API Contract
- Test Strategy

Under DIR 0.3, Feature and Contract documents remain `DEFERRED`. Project, Architecture, Module Architecture, and Test Strategy can be `READY` or `PARTIAL` according to Evidence and capability availability.

## Profile and Resolution contracts

Profile policy includes purpose, audience, multiplicity, safe path policy, Section order and required state, section-level Evidence requirements, Renderer capability requirements, completeness, ownership, dependencies, and required production model.

Resolution includes:

- Profile ID/version and Profile SHA-256;
- deterministic document and Section contracts;
- source specification IDs;
- resolved relative paths;
- Evidence availability;
- required/available Renderer capabilities;
- ownership policy;
- `READY`, `PARTIAL`, `DEFERRED`, `BLOCKED`, or `UNSUPPORTED` state;
- structured findings;
- RFC-0052 compatibility bindings;
- Resolution SHA-256.

## Legacy coexistence

- Profile-free workflows behave as before.
- Existing Artifact descriptors and RFC-0052 Plan source were not modified.
- Existing Markdown paths remain under `docs/...`.
- New Profile paths are contracts only and are not automatically written.
- Project and Architecture contracts may bind to compatible legacy kinds without moving them.
- Other Profile documents remain explicitly unbound until a later Profile-aware rendering RFC.
- User-owned content is never overwritten by Profile policy.

## Format compatibility

Unchanged:

- DIR Builder 0.3
- manual DIR default 0.2
- Specification Snapshot format 1
- Review Bundle format 1
- Relationship Projection Report format 1
- Ownership/Reconciliation format 1
- Evolution Report format 1

No Profile codec or Resolution codec was introduced.

## Verification

| Check | Result |
|---|---|
| Changed Profile + Renderer subset compilation | PASS |
| RFC-0058 targeted test methods | 18 PASS |
| Existing Renderer regression methods | 4 PASS |
| Actual RFC-0055 Reconciliation model contract subset compilation | PASS |
| Deterministic Profile Resolution smoke | PASS |
| Profile semantic tamper smoke | PASS |
| Legacy Renderer output behavior | PASS |
| Canonical `./gradlew clean test` | `NOT_EXECUTED_ENVIRONMENT_LIMITATION` |
| Git identity / clean tree | `UNAVAILABLE_NO_DOT_GIT` |
| architecture-samples Profile E2E | `NOT_EXECUTED_MISSING_OFFICIAL_FIXTURE` |

Gradle Wrapper resolution failed before task execution because `services.gradle.org` could not be resolved. The local targeted compiler is Kotlin 1.9.0, not the canonical Kotlin 2.4.0 plugin. These targeted results do not replace the canonical Gradle suite.

## Known limitations

- Profile and Resolution are runtime-only.
- Profile documents are not yet rendered or persisted.
- Feature, Entry Point, Scenario, and Contract production models do not exist.
- Diagram capabilities are declarations only.
- Profile changes are not emitted as Evolution Report format 1 events.
- no Profile CLI or MCP workflow exists.
- supplied ZIP contains no `.git` metadata.
- canonical Gradle/Kotlin 2.4.0 verification must be repeated in the actual repository before commit.

## Release Readiness

| Item | State |
|---|---|
| Core Build | ⏳ |
| Core Tests | ⏳ |
| CLI | ✅ |
| Incremental | ✅ |
| Review Workflow | ✅ |
| architecture-samples Validation | ⏳ |
| Documentation Sync | ✅ |
| Release Candidate | ❌ |

## Suggested branch

```text
feature/rfc-0058-documentation-profiles
```

## Suggested commit message

```text
feat(profile): implement RFC-0058 document contracts

- add immutable documentation profile, document, section, evidence, path, and ownership contracts
- add deterministic profile validation, canonicalization, Stable IDs, and semantic hashes
- add kotlin-android profile version 1 with explicit deferred Feature and Contract documents
- resolve profiles against DIR 0.3, renderer capabilities, artifact catalogs, and ownership manifests
- bind compatible legacy RFC-0052 artifacts without changing existing paths or plan hashes
- declare existing Markdown renderer capabilities without changing rendered output
- add profile contract, validation, determinism, resolution, ownership, and compatibility tests
- synchronize RFC, architecture, pipeline, roadmap, DSD, planning, handoff, and RFC-0059 proposal
```

## Commit-before verification

```bash
./gradlew clean test
git diff --check
git status --short
```

Run in the actual JDK 21 Git worktree with Gradle 9.3.0 available. Commit, push, merge, tag, and release remain user-approved operations only.

## Main Planning synchronization packet

```text
RFC-0058 Documentation Profiles and Document Contracts is implemented with an
environment verification limitation.

Candidate A was implemented. kotlin-android@1, DocumentDefinition,
SectionDefinition, section-level Evidence requirements, Renderer capability
requirements, completeness states, ownership policy, deterministic Profile and
Resolution SHA-256, and RFC-0052 compatibility binding now exist in Core.

The existing ProjectSpecificationMarkdownRenderer, Artifact paths, RFC-0052
DocumentationArtifactPlan, Plan SHA-256, DIR 0.3, Snapshot format 1, Review Bundle
format 1, Reconciliation format 1, and Evolution Report format 1 remain unchanged.

DIR 0.3 has no Feature or canonical Contract production model. Feature Catalog,
Feature Specification, Domain Model, Database Schema, and External API Contract
therefore resolve as DEFERRED rather than being invented.

Targeted verification passed: changed-source subset compilation, 18 RFC-0058 test
methods, and 4 legacy Renderer regression methods. Canonical Gradle clean test was
not executed because Gradle 9.3.0 could not be downloaded in the environment.

Public v1.0 remains PRODUCT_VALIDATION_FAIL / NOT_APPROVED. PV-009 remains PENDING.
No v1.1 RC is declared.

Next proposed: RFC-0059 Feature, Entry Point, and Scenario Specification Foundation.
```
