# RFC-0052: Selective Documentation Artifact Planning and Rendering

## Status

Implemented and locally verified; Git integration pending.

The Product Capability direction was selected on July 25, 2026. Cross-process
Review Leases, Audit-safe Retention, Signed Release Evidence, and External
Attestation are moved to the v1.1 Hardening track.

Implementation and regression verification were completed on the RFC-0052
feature branch. Commit, main integration, push, tag, and release remain pending.

## 1. Purpose

DocPilot already detects stable-ID specification changes and creates
target-scoped AI review actions. Its artifact executor, however, still invokes:

```text
SpecificationRenderer.render(currentSpecification)
```

before it decides which outputs are `CREATE`, `UPDATE`, `DELETE`, or `KEEP`.
Therefore current “incremental execution” suppresses unnecessary writes but does
not suppress unnecessary document rendering or generation.

The official `ProjectSpecificationMarkdownRenderer` also emits one monolithic
artifact:

```text
specification.md
```

With one artifact, a change to one API makes the entire specification document
the update unit.

RFC-0052 introduces deterministic artifact discovery, change-to-artifact impact
planning, and selective rendering so DocPilot creates or updates only the
documents that are actually required.

## 2. Product outcome

Given previous/current DIR specifications, a stable-ID update plan, and existing
generated-document inventory, DocPilot can answer before rendering:

- which documents must be created;
- which documents must be updated;
- which documents must remain untouched;
- which specification changes caused each decision;
- which dependent summary/index documents must be refreshed;
- whether a full-render fallback is required.

Only selected `CREATE` and `UPDATE` artifacts are rendered. `KEEP` artifacts are
not regenerated and are not rewritten.

## 3. Baseline

- Local main: `12128beb7c9696a57dd6787fd4e83c429aeb8db6`
- Verified baseline: 97 XML files / 301 tests / 0 failures
- DIR schema: `0.3`
- Specification Snapshot format: `1`
- Review Bundle format: `1`
- Lifecycle/Receipt/Journal formats: `1`

Relevant completed RFCs:

- RFC-0037: stable-ID Specification Incremental
- RFC-0039: artifact execution and write suppression
- RFC-0040/0041: snapshot and CLI execution
- RFC-0044/0045: relationship semantics and relationship-aware planning
- RFC-0046 through RFC-0051: review safety, persistence, lifecycle, and CLI

## 4. Current gap

### 4.1 Render-before-plan

`DefaultIncrementalDocumentationExecutor` currently:

1. calls the full renderer;
2. compares every rendered artifact with existing content;
3. plans `CREATE`, `UPDATE`, `DELETE`, and `KEEP`;
4. writes only changed outputs.

The plan is too late to avoid full rendering.

### 4.2 Monolithic official artifact

`ProjectSpecificationMarkdownRenderer` returns one artifact. Package, component,
API, property, and relationship changes cannot select independent document
outputs.

### 4.3 Change scopes are not artifact scopes

`IncrementalUpdatePlan` identifies changed package/type/target IDs, but no
contract maps those IDs to renderer-owned artifact identities.

### 4.4 Existing artifact state is content-only

`ExistingDocumentationArtifact` carries path, media type, and content. It does
not distinguish DocPilot-owned outputs from user-authored files and is not an
artifact catalog.

## 5. Scope

RFC-0052 includes:

- renderer-owned deterministic artifact descriptors;
- stable artifact identity distinct from path;
- artifact-to-specification scope bindings;
- artifact dependency declarations;
- deterministic artifact impact planning;
- `CREATE`, `UPDATE`, and `KEEP` decisions before rendering;
- explicit orphan reporting without deletion;
- selective renderer contract;
- official renderer multi-artifact layout;
- selective execution using only planned artifact IDs;
- unchanged artifact render/write suppression;
- deterministic Plan identity;
- full-render fallback only for explicit compatibility conditions;
- Core tests and isolated filesystem smoke;
- canonical Roadmap realignment.

## 6. Non-goals

RFC-0052 does not:

- add `EXTENDS`, `IMPLEMENTS`, `CALLS`, or `IMPORTS` extraction;
- add semantic transitive-impact rules beyond existing relationships;
- create the full Documentation Quality Validation gate;
- reconcile arbitrary legacy or user-authored Markdown;
- adopt unmanaged files into DocPilot ownership;
- delete orphaned documents;
- change managed-block review or approval semantics;
- merge Snapshot Incremental with Specification Incremental;
- change DIR schema or Snapshot format;
- invoke an AI provider from the planner;
- add Review Lease, retention, signature, or attestation behavior;
- add MCP dependencies or state;
- create UI/TUI.

Semantic Relationship expansion is planned for RFC-0053, Quality Validation for
RFC-0054, and full Existing Documentation Reconciliation for RFC-0055.

## 7. Architecture

```text
Previous / Current ProjectSpecification
                +
IncrementalUpdatePlan
                +
Previous / Current Artifact Catalog
                +
Existing Generated Artifact Inventory
                |
                v
SelectiveDocumentationArtifactPlanner
                |
                v
DocumentationArtifactPlan
  - CREATE artifact IDs
  - UPDATE artifact IDs
  - KEEP artifact IDs
  - ORPHAN_RETAINED paths
  - deterministic reasons / Plan SHA
                |
                v
SelectiveSpecificationRenderer.render(selected IDs only)
                |
                v
DocumentationArtifactWriter
```

The planner does not render Markdown. The renderer does not calculate change
impact. The writer does not interpret artifact dependencies.

## 8. Artifact descriptor contract

Conceptual model:

```kotlin
data class DocumentationArtifactDescriptor(
    val artifactId: DocumentationArtifactId,
    val relativePath: String,
    val mediaType: String,
    val kind: DocumentationArtifactKind,
    val scopeIds: List<String>,
    val dependencyArtifactIds: List<DocumentationArtifactId>,
)
```

### 8.1 Artifact identity

`artifactId` is stable and independent from display names and collection order.
It must not contain absolute paths.

Conceptual identities:

```text
project:<project-id>
module:<module-id>
package:<package-id>
component:<component-id>
relationships:<project-id>
evidence:<project-id>
index:<project-id>
```

The exact encoding is owned by Core and must be deterministic.

### 8.2 Path

`relativePath` is renderer-owned but must:

- be normalized and relative;
- reject `..`, absolute paths, and ambiguous separators;
- remain stable for the same artifact ID;
- avoid using untrusted names directly;
- be unique within a catalog.

Path changes for the same artifact ID are reported as a migration/orphan
condition. RFC-0052 does not silently delete the old path.

### 8.3 Scope bindings

`scopeIds` contains the stable DIR IDs whose content is directly rendered into
the artifact.

Examples:

- a component artifact binds the component, its APIs, and properties;
- a package artifact binds the package and summary references to owned
  components;
- a relationship artifact binds relationship IDs;
- project/index artifacts bind only their direct project-level content and
  declare dependencies for derived summaries.

Lists are sorted and unique.

### 8.4 Dependencies

Dependencies express derived-document refresh:

```text
component changed -> package summary
package changed   -> module summary
module changed    -> project/index summary
relationship changed -> relationship view and dependent overview/index
```

Dependencies are explicit descriptor edges, not keyword inference.

The catalog must be acyclic. Cycles fail closed.

## 9. Artifact catalog

The renderer provides descriptors without rendering content:

```kotlin
interface SelectiveSpecificationRenderer : SpecificationRenderer {
    fun describe(specification: ProjectSpecification):
        List<DocumentationArtifactDescriptor>

    fun render(
        specification: ProjectSpecification,
        artifactIds: Set<DocumentationArtifactId>,
    ): List<RenderedArtifact>
}
```

`describe` must be:

- provider-independent;
- side-effect-free;
- deterministic;
- materially cheaper than full Markdown rendering;
- complete for every artifact the renderer can emit.

The inherited full `render(specification)` remains for compatibility and returns
all described artifacts in deterministic order.

## 10. Official artifact layout

The official specification renderer becomes multi-artifact.

Required logical artifact kinds:

```text
PROJECT_OVERVIEW
MODULE
PACKAGE
COMPONENT
RELATIONSHIP
EVIDENCE
INDEX
```

Exact filenames are implementation-defined within the following contract:

- paths are deterministic from stable IDs;
- user-facing names may appear inside content, not as unsafe path identity;
- one component change must not require rendering unrelated component files;
- relationship-only change must select the relationship artifact and declared
  dependent summaries only;
- evidence-only changes must select artifacts that directly render the changed
  evidence or derived evidence summaries;
- index/overview refresh occurs through explicit dependencies.

The monolithic `specification.md` path requires an explicit compatibility
strategy:

- retain it as an index/overview artifact, or
- report it as an orphan/migration candidate.

RFC-0052 must not silently delete or overwrite unrelated user content during
layout migration.

## 11. Planner input

Conceptual request:

```kotlin
data class DocumentationArtifactPlanningRequest(
    val previousSpecification: ProjectSpecification?,
    val currentSpecification: ProjectSpecification,
    val updatePlan: IncrementalUpdatePlan,
    val previousCatalog: List<DocumentationArtifactDescriptor>,
    val currentCatalog: List<DocumentationArtifactDescriptor>,
    val existingArtifacts: List<ExistingDocumentationArtifactState>,
)
```

Existing state includes at minimum:

```text
relativePath
mediaType
contentSha256
ownership = DOCPILOT | UNKNOWN
```

RFC-0052 uses `UNKNOWN` only to prevent unsafe overwrite/delete. Full adoption
and reconciliation remain RFC-0055.

## 12. Planner output

```kotlin
data class DocumentationArtifactPlan(
    val actions: List<DocumentationArtifactPlanAction>,
    val orphanedArtifacts: List<OrphanedDocumentationArtifact>,
    val fallbackReason: SelectivePlanningFallbackReason?,
    val planSha256: String,
)
```

Action:

```kotlin
data class DocumentationArtifactPlanAction(
    val artifactId: DocumentationArtifactId,
    val relativePath: String,
    val operation: CREATE | UPDATE | KEEP,
    val reasons: List<DocumentationArtifactReason>,
    val sourceChangeIds: List<String>,
)
```

All lists are sorted and unique.

## 13. Planning rules

### 13.1 No specification change

When:

- previous specification exists;
- `updatePlan.requiresUpdate == false`;
- catalogs are compatible;
- expected artifacts exist;

all current artifacts are `KEEP`; renderer and writer are not invoked.

### 13.2 Create

An artifact is `CREATE` when it exists in the current catalog, is expected to be
DocPilot-owned, and no owned artifact exists at its current path.

Missing expected artifacts are created even when the underlying specification
scope did not change. Reason:

```text
MISSING_EXPECTED_ARTIFACT
```

This repairs incomplete generated output without adopting arbitrary files.

### 13.3 Direct update

An artifact is `UPDATE` when any `IncrementalUpdateAction.id` is in its current or
previous `scopeIds`.

Both catalogs are considered so removed/moved entities still select the old
owning document and the new owning document where applicable.

### 13.4 Dependency update

If artifact A is selected for CREATE/UPDATE, every transitive dependent artifact
is selected for UPDATE or CREATE in topological order.

Reason:

```text
DEPENDENCY_REFRESH
```

The dependency path is included in evidence for the plan.

### 13.5 Removed scope

If a scope is removed:

- the previous containing artifact is selected;
- a still-existing containing artifact becomes `UPDATE`;
- an artifact absent from the current catalog becomes `ORPHAN_RETAINED`.

RFC-0052 does not delete the orphan.

### 13.6 Unaffected artifact

Artifacts not selected directly, through dependencies, or because they are
missing are `KEEP`.

`KEEP` artifacts must not be passed to selective render.

### 13.7 Unknown ownership

If a selected path exists with `UNKNOWN` ownership, planning fails closed or
reports a reconciliation-required conflict. It must not overwrite the file.

RFC-0055 defines explicit adoption and merge behavior.

## 14. Plan reasons

Minimum stable reasons:

```text
DIRECT_SPECIFICATION_CHANGE
DEPENDENCY_REFRESH
MISSING_EXPECTED_ARTIFACT
ARTIFACT_ADDED_TO_CATALOG
SCOPE_MOVED
FULL_RENDER_FALLBACK
```

Orphan reasons:

```text
ARTIFACT_REMOVED_FROM_CATALOG
ARTIFACT_PATH_CHANGED
REMOVED_SCOPE
UNKNOWN_OWNERSHIP
```

## 15. Plan identity

`planSha256` binds:

- current schema/project identity;
- previous/current catalog semantic fields;
- existing owned artifact identity/hash;
- every action semantic field;
- every orphan semantic field;
- fallback reason.

It excludes:

- timestamps;
- absolute workspace paths;
- locale;
- process IDs;
- random values;
- iteration order.

## 16. Selective execution

The executor changes from:

```text
render all -> compare -> write changed
```

to:

```text
describe -> plan -> render CREATE/UPDATE IDs only -> verify exact outputs
         -> write CREATE/UPDATE only
```

Execution must verify:

- every selected artifact produces exactly one output;
- no unselected artifact is returned;
- returned path/media type match its descriptor;
- no planned artifact is missing;
- rendered artifact identities are unique;
- writer receives only selected CREATE/UPDATE artifacts.

## 17. Full-render fallback

Fallback is explicit, not silent.

Allowed initial reasons:

```text
PREVIOUS_SPECIFICATION_MISSING
SCHEMA_VERSION_MISMATCH
RENDERER_NOT_SELECTIVE
CATALOG_INCOMPATIBLE
OWNED_INVENTORY_MISSING
```

Even full rendering must not delete unknown or orphaned artifacts in RFC-0052.

`RENDERER_NOT_SELECTIVE` preserves third-party `SpecificationRenderer`
compatibility. Completion of RFC-0052 requires the official renderer to support
selective mode.

## 18. Existing documentation boundary

RFC-0052 performs minimal inventory checks only:

- expected owned artifact present/missing;
- owned content hash;
- path/media type;
- unknown ownership conflict;
- orphan reporting.

It does not:

- parse arbitrary Markdown structure;
- merge manual edits;
- infer ownership;
- adopt legacy documents;
- compare semantic statements;
- delete obsolete documents.

Those behaviors belong to RFC-0055 Existing Documentation Reconciliation.

## 19. Relationship boundary

RFC-0052 consumes existing `RELATIONSHIP` update actions and current relationship
stable IDs.

It does not expand extraction or semantics. RFC-0053 will add semantic
relationships such as:

```text
EXTENDS
IMPLEMENTS
CALLS
IMPORTS
```

Those new stable relationship IDs will flow through the RFC-0052 artifact
planning contract without changing the planner architecture.

## 20. Quality boundary

RFC-0052 verifies plan and renderer contract correctness but does not decide
whether generated documentation is semantically complete or high quality.

RFC-0054 will add Quality Validation for:

- required coverage;
- Evidence linkage;
- stale claims;
- unresolved critical items;
- relationship documentation consistency;
- deterministic release/readiness gates.

## 21. Determinism

For semantically identical inputs, the following are identical:

- descriptor order;
- artifact identity and paths;
- scope/dependency bindings;
- plan actions and reasons;
- source change IDs;
- orphan reports;
- fallback;
- Plan SHA;
- selective renderer invocation order;
- execution result ordering.

## 22. Compatibility

RFC-0052 preserves:

- `SpecificationRenderer.render(specification)` compatibility;
- DIR schema `0.3`;
- Snapshot format `1`;
- Review Bundle/Lifecycle/Receipt/Journal format `1`;
- complete-review-before-merge;
- CLI lifecycle contracts;
- provider SPI;
- MCP independence.

The official renderer output layout changes additively/migrationally and requires
explicit fixture/golden updates. Existing monolithic output is never silently
deleted.

## 23. Public API impact

Expected new public contracts:

- `DocumentationArtifactId`
- `DocumentationArtifactKind`
- `DocumentationArtifactDescriptor`
- `SelectiveSpecificationRenderer`
- `DocumentationArtifactPlanningRequest`
- `DocumentationArtifactPlan`
- `DocumentationArtifactPlanAction`
- `DocumentationArtifactReason`
- `OrphanedDocumentationArtifact`
- `SelectiveDocumentationArtifactPlanner`

Existing executor request/result may be extended or a v2 selective executor may
be introduced while retaining source compatibility.

## 24. Testing

### 24.1 Planner tests

- one API change selects one component plus declared summaries;
- unrelated component artifacts remain KEEP;
- relationship-only change selects relationship outputs;
- moved scope selects previous and current containers;
- missing expected owned artifact becomes CREATE;
- unknown-owned selected path fails closed;
- removed artifact becomes ORPHAN_RETAINED;
- dependency closure and topological order;
- cycle and duplicate descriptor rejection;
- input order independence;
- deterministic Plan SHA.

### 24.2 Renderer tests

- descriptor determinism;
- stable safe paths;
- selective render returns only selected IDs;
- full render equals union of all selective outputs;
- no unselected content work in instrumentation tests;
- artifact golden Markdown.

### 24.3 Executor tests

- KEEP never rendered or written;
- CREATE/UPDATE exact selection;
- unexpected/missing render output fails;
- fallback reasons;
- unknown ownership protection;
- no deletion;
- writer failure remains fail closed.

### 24.4 Integration smoke

On an isolated architecture-samples fixture:

1. generate the official multi-artifact baseline;
2. change one component API;
3. build specification diff and artifact plan;
4. prove only that component and declared summaries render/write;
5. prove unrelated artifact hashes and modification times remain unchanged;
6. apply relationship-only change and verify exact relationship selection;
7. remove one scope and verify orphan reporting without deletion.

## 25. Completion criteria

RFC-0052 is complete only when:

- official renderer describes deterministic multi-artifact output;
- planner maps stable-ID actions to exact artifact IDs;
- only CREATE/UPDATE artifacts render;
- KEEP artifacts do not render or write;
- dependency refresh is explicit and deterministic;
- missing expected owned artifacts are created;
- unknown ownership fails closed;
- orphaned documents are reported but retained;
- full-render fallback is explicit;
- targeted, full, and isolated smoke tests pass;
- Roadmap and Completion Handoff reflect actual evidence;
- no Lease/Retention/Signature/MCP scope enters implementation.

## 26. Follow-up Product Capability roadmap

```text
RFC-0052  Selective Documentation Artifact Planning and Rendering
    ->
RFC-0053  Semantic Relationship Expansion
    ->
RFC-0054  Documentation Quality Validation
    ->
RFC-0055  Existing Documentation Reconciliation
```

Hardening begins at RFC-0056 or v1.1:

```text
RFC-0056+ Cross-process Review Leases and Audit-safe Retention
RFC-0057+ Signed Release Evidence and External Attestation
```

Numbers after RFC-0052 remain proposals until individually approved.

## 27. Canonical sources

- `docs/rfc/RFC-0052-Selective-Documentation-Artifact-Planning-and-Rendering.md`
- `docs/planning/RFC-0052-PRODUCT-ROADMAP-REALIGNMENT.md`
- `docs/planning/RFC-0052-MAIN-PLANNING-UPDATE.md`
- `docs/roadmap/ROADMAP.md`
