# DocPilot Project Pipeline

The default specification pipeline is:

`SourceIndex → Knowledge Graph → base DIR → deterministic Feature Discovery → DIR 0.4 validation → Snapshot format 2`

Feature Discovery is AI-independent and Evidence-bounded. Projects without proven Feature
Evidence still emit valid DIR 0.4 with empty collections.

RFC-0061 inserts structured Compose Navigation Evidence before Feature Discovery.
A Compose destination requires a resolved route, a known navigation registration, and
one actual destination API. Plain composables and user-defined calls named `composable`
are not promoted.

RFC-0062 expands that stage with destination function-reference resolution, bounded immutable
external lambdas, lexical nested-graph ownership, typed-route and placeholder arguments,
`navArgument` declarations, and signature-backed argument links. The resulting Evidence is
attached to the existing DIR 0.4 Compose Entry Point and Scenario; no new persisted schema or
CLI command is introduced.

## 1. Analyze a project

The command below is the legacy analysis entry point. Its base Builder and Snapshot rows
remain DIR 0.3/format 1; the current official Feature workflow continues through the
AI-independent discovery stages shown at the top of this document and persists DIR 0.4
with Snapshot format 2.

Command:

```powershell
./gradlew :run --args="analyze C:\WorkSpace\architecture-samples"
```

Pipeline:

| Stage | Input | Output | Responsibility | Not responsible for |
|---|---|---|---|---|
| Project Loader | Project path | Loaded project context | Resolve and validate the project root | Source semantics |
| Source Scanner | Loaded project | `SourceIndex` | Discover and index supported source Evidence | Documentation prose |
| Knowledge Builder | `SourceIndex` | Knowledge graph/result | Build structured relationships and knowledge | Presentation |
| Specification Builder | Knowledge result | `ProjectSpecification` DIR 0.3 | Produce canonical specification entities | Rendering format |
| Snapshot Codec | `ProjectSpecification` | Snapshot format 1 | Persist deterministic specification identity | Migrating unsupported DIR schemas |
| Prompt Package | Analysis artifacts | Prompt inputs and Evidence | Prepare bounded AI context | Owning canonical truth |
| Output Writer | Rendered artifacts | Files | Persist generated outputs | Domain interpretation |

The legacy `analyze` command and official specification workflows are distinct. Generated Markdown must not be re-ingested as source inventory in deterministic specification validation.

## 2. Generate an AI architecture document

```powershell
./gradlew :docpilot-cli:run --args="generate architecture --project C:\WorkSpace\architecture-samples --provider ollama --model qwen3:8b --output C:\WorkSpace\architecture-samples\docs\ai-architecture.md"
```

```text
Analysis Evidence
→ Prompt orchestration
→ AI Provider SPI
→ Provider adapter
→ AI model
→ Proposed Markdown
→ Output Writer
```

Ollama `qwen3:8b` was historically verified for the v0.5 smoke scope. OpenAI real API invocation was outside that validation scope.

## 3. Specification incremental planning

```text
Previous ProjectSpecification
+
Current ProjectSpecification
  -> optional explicit DIR 0.3 to 0.4 migration
  -> Snapshot format 2 for DIR 0.4 (format 1 remains DIR 0.3)
  -> Stable-ID Feature / Entry Point / Scenario / Step diff and planning
→ Stable-ID diff
→ Specification changes
→ Deterministic IncrementalUpdatePlan
```

Nested API and Property changes propagate to owning Type and Package scopes. Ownership moves preserve both previous and current affected scopes.

## 4. Documentation Profile resolution

```text
Documentation Profile
+
Current ProjectSpecification
+
Renderer capabilities
+
Artifact Catalog and Ownership Manifests
→ deterministic Document Contract Resolution
→ READY / PARTIAL / DEFERRED / BLOCKED / UNSUPPORTED
→ compatibility binding to RFC-0052 Artifacts
```

RFC-0058 Profile Resolution is policy evaluation, not rendering. `kotlin-android@1` defines nine document contracts. Feature documents remain `DEFERRED` under DIR 0.3 and may become `READY` or `PARTIAL` with canonical DIR 0.4 Feature Evidence. Contract documents remain `DEFERRED`. User-owned or unknown path collisions are `BLOCKED`; shared-managed paths require RFC-0055 Reconciliation. Profile paths are not automatically written.

## 5. Selective documentation artifacts

```text
Current ProjectSpecification
+
Previous and current Artifact Catalogs
+
Existing Artifact inventory
→ RFC-0052 DocumentationArtifactPlan
→ CREATE / UPDATE / RETAIN
→ selective deterministic rendering
```

The Plan semantic hash binds specification, catalogs, inventory, operation, dependency, and selection inputs. Unchanged artifacts are not rewritten.

## 6. AI incremental documentation review

```text
IncrementalUpdatePlan / Artifact Plan
+
AI target-scoped patches
+
Existing managed documentation blocks
→ deterministic documentation diff
→ DocumentationReviewProposal
→ Review Bundle format 1
→ complete human decisions
→ accepted patches only
→ managed-block merge
```

Safety rules:

- targets outside the plan are rejected;
- missing patches keep the proposal incomplete;
- partial decisions do not modify documentation;
- rejected patches never reach the merger;
- accepted `NO_CHANGE` entries do not rewrite content;
- Evidence references and Stable IDs remain visible;
- stale reviewed bases and managed-block conflicts fail closed.

## 7. Review lifecycle and recovery

```text
Stored Review Bundle
→ status / verify / recover / supersede / archive
→ deterministic dry-run Plan
→ explicit confirmation
→ Lifecycle Metadata / Receipt / Journal
```

Core owns state transitions. CLI commands are thin adapters. Every mutation is preview-first and integrity-bound.

## 8. Existing-document reconciliation

```text
Artifact Plan
+
Existing documentation
+
Ownership Manifests
+
User Decisions
→ RFC-0055 Reconciliation Plan
→ conflict and retained-content review
→ atomic/recoverable Result
```

The current source baseline exposes Core APIs but no official Reconciliation CLI command. Product-level E2E validation must not claim CLI support that does not exist.

## 9. Documentation evolution intelligence

```text
Verified before/after Specification Snapshots
+
Artifact Catalogs and verified Artifact Plan
+
Optional Relationship / Ownership / Reconciliation Evidence
→ deterministic change extraction
→ Artifact impact binding
→ acyclic causal graph
→ coverage classification
→ Evolution Report format 1
→ offline verification
```

The current source baseline exposes Core APIs and strict codecs but no official Evolution CLI or MCP adapter. AI may render narrative only after Report verification.

## 10. Release and Product Validation

Technical Release Evidence and public Product Validation are separate gates.

```text
Build/Test/Git/Artifact Evidence
→ Release Evidence Manifest
→ offline technical gate

Independent product criteria and reviewer Evidence
→ Product Validation
→ public release decision
```

The canonical public v1.0 decision remains `PRODUCT_VALIDATION_FAIL` / `NOT_APPROVED`, and PV-009 remains `PENDING` until independently reproduced.
