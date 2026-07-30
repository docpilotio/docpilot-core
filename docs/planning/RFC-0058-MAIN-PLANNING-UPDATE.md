# RFC-0058 Main Planning Update

## Dashboard

| Item | State |
|---|---|
| Current track | v1.1 Product Capability |
| Completed RFC | RFC-0058 — Documentation Profiles and Document Contracts |
| Next proposed RFC | RFC-0059 — Feature, Entry Point, and Scenario Specification Foundation |
| Profile runtime | `kotlin-android@1` |
| DIR | 0.3 Builder output; 0.2 manual legacy default |
| Specification Snapshot | format 1; unchanged |
| Review Bundle | format 1; unchanged |
| Reconciliation / Ownership | format 1; reused |
| Evolution Report | format 1; unchanged |
| Public v1.0 Product Validation | `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED` |
| PV-009 | `PENDING` |
| v1.1 Release Candidate | not declared |

## RFC sequence

- Implemented: RFC-0001 through RFC-0053, RFC-0055 through RFC-0058
- Proposed but not completed: RFC-0054
- Next proposed: RFC-0059

## RFC-0058 decisions

- Candidate A selected and implemented.
- Profile policy and project-specific Resolution are separate from RFC-0052 Artifact operations.
- Existing Artifact descriptors, paths, Plan SHA-256, and Markdown output remain unchanged.
- `kotlin-android@1` is immutable and registered deterministically.
- Profile, Document, Section, and resolved document identities are stable and content-addressed.
- Feature and Contract documents defer under DIR 0.3 rather than inventing production entities.
- Renderer capability declarations are explicit and additive.
- RFC-0055 ownership is reused; user-owned and unknown collisions block.
- Profile and Resolution persistence is deferred.
- DIR, Snapshot, Review, Reconciliation, Projection, and Evolution formats remain unchanged.

## Release Readiness

| Item | State | Evidence |
|---|---|---|
| Core Build | ⏳ | Canonical Gradle distribution unavailable; changed subset compilation PASS |
| Core Tests | ⏳ | 18 RFC-0058 targeted + 4 Renderer regression methods PASS; full Gradle not executed |
| CLI | ✅ | No command or argument contract changed |
| Incremental | ✅ | RFC-0052 model and semantic hash source unchanged; compatibility binding is additive |
| Review Workflow | ✅ | Review Bundle and Lifecycle formats unchanged |
| architecture-samples Validation | ⏳ | Official Profile Resolution fixture not included in supplied ZIP |
| Documentation Sync | ✅ | RFC, Architecture, Pipeline, Roadmap, DSD, Main Planning, and Handoff updated |
| Release Candidate | ❌ | v1.1 RC not declared; public v1.0 remains not approved |

## Technical debt and follow-up

- run `./gradlew clean test` with Gradle 9.3.0 available;
- capture XML test totals and JDK 21 evidence;
- verify Profile Resolution against the official architecture-samples corpus;
- capture Git branch, HEAD, divergence, diff, and clean-tree evidence in a real worktree;
- add a persisted Profile/Resolution format only when a workflow requires cross-process replay;
- project Profile contracts into actual Artifact descriptors only through a separately approved RFC;
- add Feature, Entry Point, and Scenario production models before Feature documents become READY.
