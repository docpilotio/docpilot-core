# RFC-0053: Semantic Relationship Expansion

## Status

Implemented and locally verified with scanner limitations; Git integration
pending.

Plan A was selected. Core implementation and regression verification are
complete. Commit, main integration, push, tag, and release remain pending.

## 1. Purpose

DocPilot currently has deterministic DIR relationship endpoint semantics,
relationship-aware incremental diff/review, and selective relationship artifact
rendering. The official DIR projection, however, does not yet define complete
product contracts for:

```text
EXTENDS
IMPLEMENTS
CALLS
IMPORTS
```

RFC-0053 adds those types to the same Evidence-backed deterministic relationship
model as `DEPENDS_ON`. It also defines bounded projection for high-cardinality
`CALLS` and `IMPORTS` data without moving semantic rules into CLI or MCP.

## 2. Product outcome

Generated architecture documentation can explain:

- inheritance and interface implementation;
- direct source-observed calls;
- direct source imports;
- existing direct dependencies;
- exact Evidence supporting every emitted relationship;
- whether high-volume relationships were aggregated or omitted by policy.

A relationship-only change continues through RFC-0045 and RFC-0052 so only the
relationship artifact and its declared summaries are regenerated.

## 3. Baseline

- Main baseline: `fde1700f9a71b8aa5da2ac08928323ab380ef42d`
- DIR schema: `0.3`
- Specification Snapshot format: `1`
- RFC-0052 verification: 98 XML / 306 tests / 0 failures

Existing capabilities:

- Knowledge `RelationshipType` already includes `IMPORTS`, `DEPENDS_ON`, and
  `CALLS`.
- import declarations already produce Knowledge Evidence and edges.
- `SourceSymbol.superTypes` contains scanner-level inheritance references.
- DIR Builder normalizes every Knowledge edge through RFC-0044 endpoints.
- DIR relationship identity is currently type plus normalized endpoints.
- RFC-0045 diffs relationships by stable ID.
- RFC-0052 binds relationship IDs to a selective relationship artifact.

Current gaps:

- `EXTENDS` and `IMPLEMENTS` are not separated into official DIR semantics;
- scanner/Knowledge support for call sites is incomplete;
- type references do not have a complete deterministic resolver contract;
- all graph edge kinds may currently flow into DIR without an allowlist;
- there is no versioned cardinality/aggregation policy or projection report;
- high-volume relationship output cannot be assessed by RFC-0054.

## 4. Goals

1. Define `EXTENDS`, `IMPLEMENTS`, `CALLS`, and `IMPORTS` semantics.
2. Preserve `DEPENDS_ON` semantics and dependency projection.
3. Give every logical relationship stable identity independent of source order.
4. Require source-derived Evidence for every emitted relationship.
5. Resolve endpoints as INTERNAL, EXTERNAL, or UNRESOLVED without guessing.
6. Collapse repeated call/import occurrences into one logical relationship.
7. Bound high-cardinality output with deterministic Core policy.
8. Report aggregation and omission without hiding loss.
9. Feed RFC-0045 incremental planning and RFC-0052 artifact planning unchanged.
10. Establish structured inputs for RFC-0054 Documentation Quality Validation.

## 5. Non-goals

RFC-0053 does not:

- compute transitive inheritance, dependency, call, or import closure;
- perform runtime tracing or bytecode call analysis;
- infer relationships with an AI provider;
- guess overloaded or dynamically dispatched call targets;
- model reflection, generated code, or dependency injection runtime wiring;
- add reverse relationships;
- treat imports as proof of calls or dependencies;
- add CLI flags, CLI relationship policy, UI, or MCP rules;
- reconcile existing user-authored documentation;
- add Documentation Quality pass/fail rules;
- add cross-process leases, retention, signatures, or attestation;
- change Review Bundle, Lifecycle, Receipt, or Journal formats.

## 6. Core ownership

Core owns:

- relationship kind semantics;
- raw relationship observation models;
- symbol and endpoint resolution;
- canonical identity;
- aggregation;
- threshold policy;
- projection report;
- validation;
- incremental mapping;
- rendering order.

CLI may invoke existing analysis/generation workflows and display Core output.
It must not classify, aggregate, filter, cap, or reinterpret relationships.

MCP remains unchanged and Core has no MCP dependency.

## 7. Canonical relationship kinds

The official emitted DIR allowlist is:

```text
DEPENDS_ON
EXTENDS
IMPLEMENTS
CALLS
IMPORTS
```

Knowledge-only structural edges such as `CONTAINS` and `DECLARES` are not DIR
relationships. Unknown Knowledge relationship types fail closed at the
projection boundary unless a later RFC explicitly adds them.

### 7.1 DEPENDS_ON

RFC-0044 remains authoritative.

- source: INTERNAL Component;
- target: INTERNAL or EXTERNAL; UNRESOLVED permitted with matching evidence;
- direct only;
- contributes to `ComponentSpecification.dependencyIds`;
- no new threshold is introduced.

### 7.2 EXTENDS

Represents a direct declared superclass relationship.

- source: INTERNAL class, object, enum, annotation class, or supported type;
- target: INTERNAL or EXTERNAL type, or explicit UNRESOLVED;
- Evidence: the exact type declaration/supertype reference;
- one direct superclass is expected for languages that enforce that rule;
- no transitive ancestor is emitted.

Interface inheritance is also `EXTENDS` when the source language models it as
interface-to-interface inheritance.

### 7.3 IMPLEMENTS

Represents a direct declared conformance/implementation relationship.

- source: INTERNAL Component;
- target: INTERNAL or EXTERNAL interface/protocol, or explicit UNRESOLVED;
- Evidence: the exact type declaration/supertype reference;
- no inherited implementation is synthesized.

For languages such as Kotlin where syntax does not distinguish superclass and
interface entries, classification uses the resolved target declaration kind.
If the target kind cannot be proven:

- a known class-like target becomes `EXTENDS`;
- a known interface-like target becomes `IMPLEMENTS`;
- an external target with scanner-provided declaration-kind Evidence may use
  that proven kind;
- otherwise the observation is UNRESOLVED and no arbitrary kind is selected.

### 7.4 CALLS

Represents a direct statically observed call site.

- preferred source: INTERNAL API or constructor ID;
- fallback source: owning INTERNAL Component only when the scanner cannot
  represent the executable member but can prove component ownership;
- target: INTERNAL API/constructor, EXTERNAL callable identity, or UNRESOLVED;
- Evidence: source call-site file and range plus observed callable text;
- repeated call sites with the same logical endpoints aggregate;
- dynamic dispatch targets are not expanded to possible runtime implementations;
- ambiguous overload resolution produces UNRESOLVED, never `firstOrNull()`.

### 7.5 IMPORTS

Represents a direct source import declaration.

- source: the RFC-0044 package belonging to the importing file and module;
- target: INTERNAL Component/package when uniquely resolvable, otherwise
  EXTERNAL qualified name or UNRESOLVED;
- Evidence: the exact import declaration;
- wildcard imports remain the wildcard qualified target unless expansion is
  uniquely and deterministically proven;
- alias spelling is Evidence metadata and does not change the resolved target
  identity;
- imports do not contribute to `dependencyIds`.

## 8. Observation contract

High-cardinality relationships must be represented before DIR projection as
source-derived observations.

Conceptual Core model:

```kotlin
data class RelationshipObservation(
    val kind: SemanticRelationshipKind,
    val sourceReference: RelationshipEndpointReference,
    val targetReference: RelationshipEndpointReference,
    val evidenceRefs: List<String>,
    val sourceOrderKey: SourceOrderKey,
)
```

Rules:

- observations are immutable;
- Evidence refs are non-empty, sorted, and unique;
- absolute paths, timestamps, process IDs, and collection indexes are forbidden
  from semantic identity;
- source location is an ordering/evidence input, not logical identity;
- scanners may emit multiple observations for repeated sites.

Scanner-specific syntax is normalized into this Core model before semantic
projection. Provider output is never an observation source.

## 9. Endpoint resolution

RFC-0044 remains the base endpoint contract:

```text
INTERNAL   = actual DIR entity ID
EXTERNAL   = external:<qualified-name>
UNRESOLVED = unresolved:<relationship-reference>:source|target
```

Additional rules:

- sources must resolve to INTERNAL;
- `EXTENDS`/`IMPLEMENTS` targets resolve against type declarations;
- `CALLS` targets resolve against callable signature and owning type;
- `IMPORTS` targets resolve against qualified import identity;
- module and package context are considered before global candidates;
- a unique candidate is required;
- ambiguous candidates are UNRESOLVED;
- resolution is completed before relationship identity or aggregation.

The resolver must not depend on filesystem enumeration order, map iteration
order, locale, or source scan concurrency.

## 10. Relationship identity

### 10.1 Logical key

The logical key is:

```text
(relationship kind, normalized source endpoint ID, normalized target endpoint ID)
```

Evidence, description, location, alias, occurrence count, and threshold policy
are not identity fields.

### 10.2 Canonical ID

```text
relationship:<KIND>:<SOURCE_ID>-><TARGET_ID>
```

Examples:

```text
relationship:EXTENDS:type:child->type:base
relationship:IMPLEMENTS:type:service->external:java.lang.Runnable
relationship:CALLS:api:checkout->api:reserve-stock
relationship:IMPORTS:module:app:package:sample->external:kotlin.collections.List
```

The encoding must escape or length-frame reserved delimiters if endpoint IDs can
contain ambiguous `->` sequences. Canonical ID construction is a single Core
utility used by Knowledge projection, DIR Builder, tests, and validation.

### 10.3 Stability

- repeated observations collapse to one ID;
- source input order does not affect ID;
- adding/removing Evidence with unchanged endpoints yields `MODIFIED`;
- changing kind or endpoint yields `REMOVED` plus `ADDED`;
- aggregation and threshold changes never rewrite retained relationship IDs.

Duplicate canonical IDs merge Evidence. Conflicting semantic fields for the same
ID fail closed.

## 11. Evidence contract

Every emitted relationship has at least one valid Evidence reference.

Aggregation uses the sorted unique union of all contributing Evidence refs.
Evidence retains individual source locations so one logical `CALLS` relationship
can be traced to multiple call sites.

The validator rejects:

- an empty Evidence set;
- a missing Evidence ID;
- Evidence whose source cannot support the relationship kind;
- synthesized Evidence text without a source observation;
- relationship Evidence discarded merely to meet output thresholds.

Evidence cardinality may be large. RFC-0053 may store all Evidence records while
renderers show a bounded preview plus total count. Rendering bounds do not alter
the DIR relationship Evidence set.

## 12. Aggregation policy

Aggregation occurs after endpoint resolution and before thresholds.

### 12.1 Common rule

Observations with the same canonical relationship ID become one
`RelationshipSpecification`:

- ID/type/source/target from the logical key;
- Evidence refs as sorted unique union;
- deterministic description derived from kind and endpoints;
- occurrence count recorded in the projection report.

### 12.2 CALLS

All repeated call sites from one logical source endpoint to one logical target
endpoint aggregate.

No aggregation may:

- merge different source APIs solely because they share a Component;
- merge overloads with distinct resolved API IDs;
- replace an unresolved target with a guessed target;
- convert call count into multiple relationships.

When source API identity is unavailable but Component ownership is proven, the
Component fallback is explicit in the projection report.

### 12.3 IMPORTS

Repeated identical imports within the same normalized package and target
aggregate. Multiple files in the same module/package contribute Evidence to the
same relationship.

The following remain distinct:

- the same qualified import from different module/package source IDs;
- different resolved targets;
- wildcard and non-wildcard external targets unless resolution proves equality.

## 13. Threshold policy

### 13.1 Purpose

Thresholds protect deterministic build time, memory, snapshot size, and
documentation usability. They are not quality judgments.

### 13.2 Versioned Core policy

Conceptual contract:

```kotlin
data class RelationshipProjectionPolicy(
    val formatVersion: Int = 1,
    val policyId: String,
    val maxCallsPerSource: Int,
    val maxCallsPerProject: Int,
    val maxImportsPerSourcePackage: Int,
    val maxImportsPerProject: Int,
    val overflowBehavior: OverflowBehavior,
)
```

Initial recommended defaults:

```text
CALLS per source API/Component: 128
CALLS per project:              50,000
IMPORTS per source package:     512
IMPORTS per project:            20,000
overflow behavior:              TRUNCATE_WITH_REPORT
```

The implementation may adjust these numeric defaults only with benchmark
Evidence recorded in the Completion Handoff. The semantic contract is fixed.

`DEPENDS_ON`, `EXTENDS`, and `IMPLEMENTS` are not normally truncated. A separate
high safety ceiling may fail the build, but must not silently omit structural
relationships.

### 13.3 Application order

```text
observe
-> resolve
-> canonical identity
-> aggregate identical identity
-> validate
-> per-source threshold
-> project threshold
-> deterministic output
```

Thresholds count aggregated logical relationships, not raw occurrences.

### 13.4 Deterministic retention

Within a threshold scope, candidates are ordered by:

1. normalized source endpoint ID;
2. normalized target endpoint kind (`INTERNAL`, `EXTERNAL`, `UNRESOLVED`);
3. normalized target endpoint ID;
4. canonical relationship ID.

The first `N` are retained. No input-order or hash-order sampling is permitted.
The policy report makes this deterministic truncation visible; it is not
presented as semantic importance ranking.

### 13.5 Overflow behavior

`TRUNCATE_WITH_REPORT`:

- retains deterministic first `N`;
- emits no synthetic relationship;
- records exact counts and digests;
- produces a non-fatal projection warning;
- gives RFC-0054 enough data to apply a quality policy.

`FAIL_CLOSED`:

- emits no partial ProjectSpecification;
- returns a typed policy-limit error;
- reports the threshold scope and counts.

CLI cannot choose a weaker behavior. Any future configuration adapter passes a
Core policy object and does not implement the rule.

## 14. Relationship Projection Report

Threshold and aggregation loss must not be hidden in Markdown or unstructured
logs.

RFC-0053 introduces a separate versioned Core contract:

```kotlin
data class RelationshipProjectionReport(
    val formatVersion: Int = 1,
    val policyId: String,
    val policySha256: String,
    val observationCountByKind: Map<String, Long>,
    val logicalCountByKind: Map<String, Long>,
    val emittedCountByKind: Map<String, Long>,
    val omittedCountByKind: Map<String, Long>,
    val aggregatedOccurrenceCountByKind: Map<String, Long>,
    val sourceFallbackCountByKind: Map<String, Long>,
    val overflowScopes: List<RelationshipOverflowScope>,
    val omittedIdentitySha256ByKind: Map<String, String>,
    val reportSha256: String,
)
```

The report is:

- Core-owned;
- provider-independent;
- deterministic;
- ordered and canonicalized;
- free of timestamps and absolute paths;
- returned with the build result;
- suitable as an input to RFC-0054.

It is not added to `RelationshipSpecification` and does not change relationship
identity.

The omitted identity digest binds sorted omitted canonical IDs without storing
all omitted entries in the report. Tests must prove digest stability.

## 15. Builder result and compatibility

Core adds an enriched build entry point:

```kotlin
data class SpecificationBuildResult(
    val specification: ProjectSpecification,
    val relationshipProjectionReport: RelationshipProjectionReport,
)
```

The existing `SpecificationBuilder.build(request): ProjectSpecification`
remains source-compatible and delegates to the enriched path using the default
Core policy.

DIR schema remains `0.3` because emitted relationship shape is unchanged.
Specification Snapshot format remains `1`.

The projection report is a separate format-1 contract. A future persistence RFC
may store it alongside snapshots without embedding it into DIR schema 0.3.

## 16. Validation

The validator enforces:

- relationship type belongs to the official allowlist;
- source is INTERNAL;
- target follows RFC-0044 endpoint rules;
- type-specific endpoint kinds are valid;
- Evidence refs are non-empty and exist;
- no structural self-relationship;
- canonical ID exactly matches kind/source/target;
- duplicate IDs are rejected after aggregation boundary;
- `dependencyIds` equals direct `DEPENDS_ON` targets only;
- `EXTENDS`, `IMPLEMENTS`, `CALLS`, and `IMPORTS` never enter dependencyIds;
- report counts are non-negative and arithmetically consistent;
- report and policy SHA values verify;
- overflow ordering/digests are reproducible.

General `CALLS` self-recursion is permitted. Structural self-relationships for
`DEPENDS_ON`, `EXTENDS`, and `IMPLEMENTS` remain rejected. Self `IMPORTS` created
by normalization is suppressed and counted in the report.

## 17. Incremental planning

RFC-0045 behavior is reused:

- canonical relationship ID is the diff key;
- additions/removals/modifications produce `RELATIONSHIP` actions;
- Evidence-only change is `MODIFIED`;
- type/endpoint change is remove plus add;
- previous/current endpoint scopes are unioned.

RFC-0052 behavior is reused:

- relationship IDs are bound to the relationship artifact;
- relationship artifact changes refresh declared project/index summaries;
- unrelated Component/Package artifacts remain `KEEP`;
- only `CREATE`/`UPDATE` artifact IDs render;
- no CLI relationship selection rule is added.

Threshold-driven omission can produce removal diffs when policy or input volume
changes. The projection report must distinguish `POLICY_OMITTED` from a source
relationship truly disappearing so RFC-0054 can flag quality risk.

## 18. Rendering

The relationship artifact renders emitted relationships in deterministic order:

1. source endpoint ID;
2. type;
3. target endpoint ID;
4. relationship ID.

Each entry includes:

- kind;
- endpoint IDs and kinds;
- bounded Evidence preview and total Evidence count;
- aggregation occurrence count when greater than one.

Summary artifacts may show report counts by kind, but they must consume the Core
report and may not recompute thresholds.

Omitted relationship identities are not rendered individually.

## 19. Error handling

Fail explicitly for:

- unsupported relationship type at DIR projection;
- missing Evidence;
- ambiguous endpoint chosen as INTERNAL;
- identity mismatch;
- conflicting observations with one canonical ID;
- invalid policy values or unknown policy format;
- arithmetic/report SHA mismatch;
- threshold overflow under `FAIL_CLOSED`;
- non-deterministic duplicate candidates.

Do not:

- choose `firstOrNull()` for ambiguous type/call/import resolution;
- silently drop relationships;
- treat truncated output as complete;
- infer relationships from names or Markdown;
- downgrade structural relationship failures to warnings.

## 20. Determinism

Semantically identical input produces identical:

- observations after normalization;
- endpoint resolution;
- canonical IDs;
- aggregated Evidence;
- retained/omitted identity sets;
- output ordering;
- projection counts and digests;
- projection report SHA;
- DIR relationship artifact;
- RFC-0045 diff and RFC-0052 artifact plan.

Required shuffle tests cover files, symbols, imports, call sites, graph nodes,
graph edges, Evidence refs, modules, packages, and components.

## 21. Public API impact

Expected additive Core contracts:

- `SemanticRelationshipKind`
- `RelationshipObservation`
- `RelationshipIdentity`
- `RelationshipProjectionPolicy`
- `RelationshipProjectionReport`
- `RelationshipOverflowScope`
- `SpecificationBuildResult`
- enriched Specification Builder entry point

Expected source/scanner additions:

- language-neutral call-site observation
- resolvable supertype declaration reference/kind evidence

Existing contracts retained:

- `RelationshipSpecification`
- `ProjectSpecification`
- `SpecificationRenderer`
- `IncrementalUpdateTarget.RELATIONSHIP`
- Snapshot format 1
- Review and Apply contracts

## 22. Expected implementation areas

```text
src/main/kotlin/io/docpilot/core/model/source/**
src/main/kotlin/io/docpilot/core/model/knowledge/**
src/main/kotlin/io/docpilot/core/knowledge/**
src/main/kotlin/io/docpilot/core/specification/**
src/main/kotlin/io/docpilot/core/incremental/specification/**
src/main/kotlin/io/docpilot/core/render/**
```

CLI changes are limited to compilation-compatible consumption of the existing
Core build workflow if required. No relationship policy or interpretation is
implemented there.

Protected:

```text
tools/docpilot-mcp/src/**
tools/docpilot-mcp/tests/**
Review Bundle/Lifecycle/Receipt formats
release signing/hardening
```

## 23. Testing

### 23.1 Extraction and Knowledge

- direct superclass and interface/protocol cases;
- Kotlin external supertype ambiguity;
- direct internal/external/unresolved calls;
- overloaded and dynamic call ambiguity;
- explicit, wildcard, and alias imports;
- Evidence location and input-order determinism.

### 23.2 Identity and resolution

- all five official kinds;
- repeated occurrence identity stability;
- reserved delimiter encoding;
- multi-module package/type candidates;
- ambiguous candidates become UNRESOLVED;
- kind/endpoint change becomes new identity.

### 23.3 Aggregation and threshold

- repeated CALLS Evidence union;
- package-level IMPORTS aggregation;
- thresholds count logical relationships;
- exact boundary, boundary plus one, and project cap;
- deterministic retained set under shuffled input;
- omission digest and report SHA;
- `TRUNCATE_WITH_REPORT` and `FAIL_CLOSED`;
- structural relationships never silently truncate.

### 23.4 DIR and validation

- official allowlist;
- canonical ID enforcement;
- Evidence required;
- recursive CALLS allowed;
- structural self-edge rejection;
- dependencyIds remains DEPENDS_ON-only;
- schema 0.3 and Snapshot format 1 round trip.

### 23.5 Incremental and rendering

- relationship-only add/remove/modify;
- Evidence-only modification;
- policy omission distinguished in report;
- relationship artifact plus summaries selected;
- unrelated artifacts remain KEEP and are not rendered/written;
- deterministic Markdown and full/selected render equivalence.

### 23.6 Scale

Synthetic fixtures above both project thresholds must prove:

- bounded emitted relationship count;
- stable memory/time trend;
- no stack overflow;
- byte-identical output across shuffled and parallel scan input;
- exact projection report arithmetic.

### 23.7 Full verification

- targeted suites;
- `clean build`;
- `clean test`;
- XML aggregation;
- isolated architecture-samples smoke;
- `git diff --check`;
- MCP protected-path check.

## 24. Completion criteria

RFC-0053 is complete when:

- all four new kinds have explicit Core semantics;
- all emitted relationships have canonical identity and Evidence;
- ambiguous resolution never selects an arbitrary candidate;
- aggregation is deterministic;
- CALLS/IMPORTS thresholds are bounded and reported;
- projection report format 1 verifies offline in Core;
- dependencyIds remains DEPENDS_ON-only;
- relationship-only changes select only relationship artifact and summaries;
- CLI and MCP contain no relationship interpretation rules;
- DIR schema 0.3 and Snapshot format 1 remain compatible;
- targeted, full, scale, and isolated smoke tests pass;
- canonical Planning, Roadmap, and Completion Handoff match actual evidence.

## 25. RFC-0054 foundation

RFC-0054 Documentation Quality Validation can consume:

- emitted relationship counts by kind;
- aggregation occurrence counts;
- source fallback counts;
- omitted counts and overflow scopes;
- policy identity/SHA;
- omission identity digests;
- unresolved endpoints;
- missing/invalid Evidence findings;
- RFC-0052 artifact coverage.

RFC-0053 records facts and deterministic loss. RFC-0054 decides whether those
facts pass a project quality policy.

## 26. Follow-up roadmap

```text
RFC-0053 Semantic Relationship Expansion
    ->
RFC-0054 Documentation Quality Validation
    ->
RFC-0055 Existing Documentation Reconciliation
```

Lease/Retention and Signed Release Evidence remain in the RFC-0056+/v1.1
Hardening track.

## 27. Canonical sources

- `docs/rfc/RFC-0053-Semantic-Relationship-Expansion.md`
- `docs/planning/RFC-0053-MAIN-PLANNING-UPDATE.md`
- `docs/planning/RFC-0053-TWO-PLAN-SYNC-PACKET.md`
- `docs/roadmap/ROADMAP.md`
