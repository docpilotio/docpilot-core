# RFC-0053 Candidate Plan A: Semantic Relationship Expansion

## Status

SELECTED for RFC-0053. Detailed specification:
`docs/rfc/RFC-0053-Semantic-Relationship-Expansion.md`.

## Purpose

Extend the current deterministic relationship model beyond `DEPENDS_ON` so
generated documentation can explain structural and behavioral connections that
are already present in source code.

## Proposed scope

- add `EXTENDS`, `IMPLEMENTS`, `CALLS`, and `IMPORTS`;
- define stable relationship identity for every new kind;
- extract relationships from supported source models without provider inference;
- normalize endpoints through the existing INTERNAL, EXTERNAL, and UNRESOLVED
  semantics;
- preserve deterministic multi-module resolution;
- attach source Evidence to every emitted relationship;
- validate source/target identity and unresolved Evidence;
- propagate new relationship changes through RFC-0045 incremental planning;
- select the RFC-0052 relationship artifact and dependent summaries;
- render each kind deterministically.

## Architecture

Extraction remains inside the Core analysis/specification pipeline. The
relationship resolver owns endpoint classification, the validator owns
invariants, and the renderer only presents accepted DIR relationships.

RFC-0052 requires no planner redesign: new stable relationship IDs flow through
the existing `RELATIONSHIP` update target and artifact scope bindings.

## Goals

- make architecture relationships explicit and evidence-backed;
- improve generated relationship documentation;
- prove exact incremental selection for relationship-only changes;
- provide semantic inputs for the later Quality Validation RFC.

## Non-goals

- call-graph reachability or transitive dependency analysis;
- runtime tracing;
- framework-specific semantic inference without source Evidence;
- AI-generated relationships;
- schema migration unless unavoidable and separately approved;
- CLI/UI/MCP relationship editing;
- reconciliation of user-authored Markdown.

## Expected changes

- source/knowledge relationship extraction
- specification builder and endpoint resolver
- relationship validator
- relationship Markdown rendering
- relationship diff/planning tests
- architecture-samples fixtures

## Compatibility

- preserve current `DEPENDS_ON` behavior;
- preserve INTERNAL/EXTERNAL/UNRESOLVED endpoint rules;
- preserve Snapshot and Review contracts where possible;
- keep Core independent from CLI and MCP.

## Risks

- `CALLS` and `IMPORTS` can generate excessive volume;
- overloaded symbols may create ambiguous targets;
- language-specific syntax can leak into shared semantics;
- relationship identity changes can create noisy incremental diffs.

Mitigation requires explicit emission thresholds, deterministic unresolved
results, stable identity tests, and cross-language fixtures.

## Verification

- dedicated resolver and extractor unit tests for every kind;
- ambiguity and multi-module determinism tests;
- validator negative tests;
- snapshot round-trip and compatibility tests;
- relationship-only selective-render integration tests;
- architecture-samples smoke;
- full regression.

## Product value

HIGH. This directly improves architecture documentation content and enables
quality rules that reason about missing or contradictory relationships.

## Complexity

MEDIUM-HIGH.

## Recommendation

STRONGLY_RECOMMENDED for RFC-0053 because it is the next Product Roadmap step
and consumes RFC-0052 selective planning immediately.
