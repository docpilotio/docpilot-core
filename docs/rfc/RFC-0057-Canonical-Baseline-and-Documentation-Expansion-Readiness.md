# RFC-0057 — Canonical Baseline and Documentation Expansion Readiness

## Status

`IMPLEMENTED_WITH_ENVIRONMENT_VERIFICATION_LIMITATION`

## Track

v1.1 Product Capability

## Purpose

RFC-0057 introduces no new product capability. It aligns the delivered source tree, current architecture documents, RFC inventory, version lines, verification evidence, release state, and future DIR migration rules into one canonical baseline before Documentation Profiles and richer specification concepts are added.

## Decisions

1. Production runtime code and public contracts are unchanged.
2. Canonical baseline data is stored in `docs/planning/DOCPILOT-CANONICAL-BASELINE.properties` rather than a new runtime aggregate or global registry.
3. A test binds the manifest to source constants and build settings.
4. DIR 0.2 remains the manual construction default; `DefaultSpecificationBuilder` remains DIR 0.3.
5. Specification Snapshot remains format 1 and supports DIR 0.3.
6. Review Bundle, Relationship Projection, and Evolution Report remain format 1.
7. RFC-0054 remains proposed and is not included in the completed sequence. The presence of `DocumentationQualityValidator` does not establish RFC completion.
8. RFC-0056 remains `IMPLEMENTATION_COMPLETED_WITH_VERIFICATION_LIMITATION`.
9. Public v1.0 remains `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED`; PV-009 remains `PENDING`.
10. DIR 0.4, profile models, Feature/Scenario models, Diagram IR, new CLI commands, and MCP changes are out of scope.

## Canonical source identity

The delivered ZIP contains no `.git`. Branch, HEAD, origin divergence, tag presence, and clean-tree status are `UNAVAILABLE_NO_DOT_GIT` and must not be inferred from historical documents.

Build metadata in the source tree:

- Gradle Wrapper: 9.3.0
- Kotlin: 2.4.0
- Java toolchain: 21
- Artifact version: `0.1.0-SNAPSHOT`
- Gradle modules: root, `docpilot-cli`, `docpilot-provider-ollama`, `docpilot-provider-openai`, `docpilot-release`

## Implementation

### Added

- canonical baseline properties
- canonical baseline report
- code/document consistency report
- DIR 0.4 migration-readiness note
- RFC-0057 Main Planning update
- RFC-0057 completion handoff
- RFC-0058 starting prompt
- canonical baseline contract test

### Updated

- README
- Architecture
- Project Pipeline
- Roadmap
- Vision
- DSD runtime version and migration guidance

### Not changed

- production Kotlin source
- public APIs
- Gradle module structure
- DIR runtime schema
- Snapshot, Review Bundle, Projection, Evolution, lifecycle, receipt, journal, reconciliation, or ownership formats
- public v1.0 Product Validation documents and decision

## Verification policy

Validation states are `PASS`, `FAIL`, `NOT_EXECUTED`, `NOT_EXECUTED_ENVIRONMENT_LIMITATION`, and `PASS_WITH_LIMITATIONS`.

`./gradlew clean test` requires the Gradle 9.3.0 distribution. The execution environment could not resolve `services.gradle.org`, so the Gradle suite was not started. This is `NOT_EXECUTED_ENVIRONMENT_LIMITATION`, not a code PASS or FAIL.

Static and non-Gradle checks may verify manifest consistency, expected files, document references, archive hygiene, and Kotlin compilation where an appropriate compiler is available, but they do not replace the canonical Gradle suite.

## Completion criteria

RFC-0057 is complete when:

- source identity limitations are explicit;
- canonical versions are machine-readable and source-bound;
- RFC-0054 is not falsely completed;
- README, Architecture, Pipeline, Roadmap, Vision, and DSD agree with the source tree;
- RFC-0056 limitations remain unchanged;
- DIR 0.4 migration requirements are documented without implementation;
- public v1.0 and PV-009 decisions remain unchanged;
- no product capability or out-of-scope refactor is introduced;
- final delivery excludes `.idea`, `local.properties`, build outputs, and machine-local state.
