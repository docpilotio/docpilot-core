# RFC-0057 Canonical Baseline Report

## Baseline decision

`CANONICAL_SOURCE_TREE_BASELINE_ESTABLISHED_WITH_ENVIRONMENT_LIMITATION`

## Repository identity

| Item | Result |
|---|---|
| Input | Whole-project ZIP based on RFC-0056 completion |
| `.git` | Not present |
| Branch / HEAD / origin divergence | `UNAVAILABLE_NO_DOT_GIT` |
| Working-tree cleanliness | `UNAVAILABLE_NO_DOT_GIT` |
| Gradle Wrapper | 9.3.0 |
| Kotlin | 2.4.0 |
| Java toolchain | 21 |
| Artifact version | `0.1.0-SNAPSHOT` |
| Root project | `docpilot-core` |
| Gradle submodules | CLI, Ollama provider, OpenAI provider, Release Evidence |

## Contract baseline

| Contract | Value | Source authority |
|---|---:|---|
| Manual `ProjectSpecification` default | DIR 0.2 | `ProjectSpecification.kt` |
| Builder output | DIR 0.3 | `DefaultSpecificationBuilder.CURRENT_SCHEMA_VERSION` |
| Specification Snapshot | 1 | `SpecificationSnapshotFormat.CURRENT_VERSION` |
| Snapshot-supported DIR | 0.3 | `SpecificationSnapshotFormat.SUPPORTED_DIR_SCHEMA_VERSION` |
| Review Bundle | 1 | `ReviewBundleFormat.CURRENT_VERSION` |
| Relationship Projection Report | 1 | `RelationshipProjectionReport` contract |
| Evolution Report | 1 | `DocumentationEvolutionFormat.CURRENT_VERSION` |

## RFC inventory

- Implemented sequence: RFC-0001 through RFC-0053
- RFC-0054: proposed; no approved detailed RFC, Main Planning completion update, or completion handoff
- Implemented additional RFCs: RFC-0055 and RFC-0056
- Active RFC: RFC-0057
- Next planned RFC: RFC-0058

`DocumentationQualityValidator` and its tests exist and are used by the product-validation script. Source presence alone is not treated as proof that proposed RFC-0054 was approved or completed.

## RFC-0056 synchronization

The stored RFC-0056 handoff matches the delivered implementation structure:

- Evolution production package exists
- RFC-0052 and RFC-0053 integrity bridges exist
- seven Evolution test files exist
- no Evolution CLI, provider, or MCP semantics were added
- DIR and Snapshot formats remain unchanged

RFC-0056 status remains `IMPLEMENTATION_COMPLETED_WITH_VERIFICATION_LIMITATION`.

## Verification

| Check | Result |
|---|---|
| Source/document static inspection | PASS |
| RFC-0056 handoff/file inventory consistency | PASS |
| Baseline manifest creation | PASS |
| Production code unchanged | PASS |
| Canonical Gradle `clean test` | `NOT_EXECUTED_ENVIRONMENT_LIMITATION` |
| Exact XML test totals | NOT_EXECUTED |
| architecture-samples Evolution E2E | `NOT_EXECUTED_MISSING_OFFICIAL_FIXTURE` |
| Git diff/clean-tree evidence | `UNAVAILABLE_NO_DOT_GIT` |
| Independent PV-009 | PENDING |

## Release state

- Public v1.0 Product Validation: `PRODUCT_VALIDATION_FAIL`
- Public v1.0 release: `NOT_APPROVED`
- PV-009: `PENDING`
- Technical v1 baseline: historical documents report `v1.0.0`; current ZIP cannot verify the tag
- v1.1 Release Candidate: not declared
