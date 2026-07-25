# RFC-0055: Existing Documentation Reconciliation

## Status

Approved direction; detailed implementation contract proposed.

Implementation, commit, main integration, push, tag, and release are not
recorded by this document.

## 1. Purpose

DocPilot can deterministically describe generated artifacts, render only
impacted artifacts, review managed-block updates, and apply approved changes
with stale-base protection. It does not yet have one official Core contract for
reconciling current generated documentation with pre-existing user documents.

RFC-0055 defines Evidence-based ownership, preview-first three-way
reconciliation, deterministic conflict detection, incremental reconciliation,
and fail-closed apply.

AI may propose content alignment. Core alone decides ownership, allowed
operations, conflicts, final merge output, and whether apply is permitted.

## 2. Product outcome

Given:

- a previously reviewed generated base;
- current files on disk;
- current RFC-0052 generated candidates;
- artifact and managed-block identity;
- review/apply Evidence;

DocPilot can explain before mutation:

- which content is DocPilot-owned;
- which content is user-owned;
- which content is shared through managed blocks;
- what would be created, retained, updated, removed, or left conflicted;
- why each operation is safe;
- which Evidence proves ownership and base identity;
- which files require user decision;
- whether incremental apply can proceed atomically.

No user-authored content is overwritten merely because its path matches a
generated artifact path.

## 3. Baseline

- Main baseline: `3c1223d96496ab0ad029ad116c7592b50e491249`
- DIR schema: `0.3`
- Specification Snapshot format: `1`
- RFC-0053 verification: 100 XML / 312 tests / 0 failures

Prerequisites:

- RFC-0043 complete-review-before-merge;
- RFC-0046 explicit managed-block removal approval;
- RFC-0047 durable Review Bundle and stale apply protection;
- RFC-0050 Lifecycle, Receipt, Journal, idempotency, and recovery;
- RFC-0052 stable Artifact ID/catalog and selective planning;
- RFC-0053 stable semantic Relationship identity and Evidence.

RFC-0054 Documentation Quality Validation remains a separate proposed
capability. RFC-0055 can be implemented independently, while a later integration
may use quality findings as reconciliation Evidence.

## 4. Goals

1. Define Core-owned documentation ownership.
2. Prove ownership with deterministic Evidence.
3. Reconcile reviewed base, current user document, and generated candidate.
4. Make preview/dry-run the default.
5. Detect content, ownership, path, identity, and stale-base conflicts.
6. Preserve user-owned content byte-for-byte unless explicitly approved.
7. Reconcile only RFC-0052 impacted or drifted artifacts.
8. Require complete user decisions for ambiguous operations.
9. Apply approved plans atomically and idempotently.
10. Allow AI proposals without delegating merge authority.
11. Produce versioned reconciliation Plan and Result Evidence.
12. Explain every Ownership, Conflict, Operation, and Merge decision with
    structured Core Evidence.
13. Prepare Evidence for Documentation Evolution Intelligence.

## 5. Non-goals

RFC-0055 does not:

- infer ownership from writing style or filenames alone;
- adopt arbitrary Markdown automatically;
- allow AI to decide ownership or conflicts;
- let AI directly edit files;
- merge binary files;
- delete whole files;
- bypass RFC-0046 managed-block removal approval;
- add interactive UI/TUI;
- put reconciliation rules in CLI or MCP;
- change Review Bundle v1;
- add semantic diff explanations;
- sign reconciliation evidence;
- implement cross-process leases or retention.

Whole-file deletion remains excluded. Managed-block removal is allowed only
through existing explicit review-gated semantics.

## 6. Core ownership model

### 6.1 Ownership states

```kotlin
enum class DocumentationOwnership {
    DOCPILOT_OWNED,
    USER_OWNED,
    SHARED_MANAGED,
    UNKNOWN,
    CONFLICTED,
}
```

Definitions:

- `DOCPILOT_OWNED`: the complete artifact is generated and its identity/base are
  proven by Core Evidence.
- `USER_OWNED`: Core has no ownership claim; content is immutable to automatic
  reconciliation.
- `SHARED_MANAGED`: user-owned document containing individually identified
  DocPilot managed blocks.
- `UNKNOWN`: available Evidence cannot establish safe ownership.
- `CONFLICTED`: Evidence claims are inconsistent or stale.

`UNKNOWN` is not equivalent to USER_OWNED for reporting, but both prohibit
automatic overwrite.

### 6.2 Ownership unit

Ownership is evaluated separately for:

- artifact path;
- complete artifact;
- each managed block;
- unmanaged prefix/suffix/inter-block region.

A SHARED_MANAGED document never transfers ownership of unmanaged regions to
DocPilot.

### 6.3 Ownership transitions

Allowed transitions:

```text
UNKNOWN -> USER_OWNED
UNKNOWN -> DOCPILOT_OWNED
USER_OWNED -> SHARED_MANAGED
DOCPILOT_OWNED -> SHARED_MANAGED
SHARED_MANAGED -> USER_OWNED
SHARED_MANAGED -> DOCPILOT_OWNED
```

Every transition requires:

- Preview;
- explicit transition reason;
- complete user approval;
- base content SHA binding;
- resulting ownership manifest;
- Apply Receipt.

Core never infers a transition from content similarity alone.

## 7. Ownership Evidence

### 7.1 Accepted Evidence

Ownership may be proven by a deterministic combination of:

- RFC-0052 Artifact ID and descriptor;
- Core-managed ownership manifest;
- managed-block begin/end markers and stable block ID;
- prior reviewed base SHA-256;
- prior Review Bundle proposal ID and payload SHA;
- prior Apply Receipt and Lifecycle Metadata;
- current file SHA-256;
- renderer ID/version and media type;
- repository-relative normalized path.

### 7.2 Insufficient Evidence

The following never proves ownership alone:

- matching filename;
- matching heading;
- content similarity;
- generated-sounding prose;
- Git author/commit message;
- AI classification;
- location under `docs/`;
- presence in an RFC-0052 catalog without a prior ownership record.

### 7.3 Evidence precedence

Core evaluates:

1. cryptographic/hash and Receipt consistency;
2. ownership manifest identity;
3. managed-block marker validity;
4. Artifact ID/path compatibility;
5. reviewed-base availability;
6. current content state.

Contradictory higher-priority Evidence produces `CONFLICTED`; lower-priority
Evidence cannot override it.

## 8. Ownership Manifest

RFC-0055 introduces a separate versioned contract:

```kotlin
data class DocumentationOwnershipManifest(
    val formatVersion: Int = 1,
    val artifactId: DocumentationArtifactId,
    val relativePath: String,
    val mediaType: String,
    val ownership: DocumentationOwnership,
    val reviewedBaseSha256: String?,
    val managedBlocks: List<ManagedBlockOwnership>,
    val rendererIdentity: String,
    val evidenceRefs: List<String>,
    val manifestSha256: String,
)
```

Managed block ownership includes:

```text
blockId
target stable ID
reviewed base content SHA
last applied content SHA
proposal/receipt references
```

Manifest rules:

- relative paths only;
- sorted unique blocks and Evidence;
- no timestamp in semantic hash;
- stable canonical JSON;
- atomic storage;
- offline integrity verification;
- format/version fail closed;
- path and Artifact ID must match the current catalog or produce migration
  conflict.

The manifest is separate from user Markdown and DIR schema.

## 9. Reconciliation inputs

Conceptual request:

```kotlin
data class DocumentationReconciliationRequest(
    val previousArtifactCatalog: List<DocumentationArtifactDescriptor>,
    val currentArtifactCatalog: List<DocumentationArtifactDescriptor>,
    val incrementalArtifactPlan: DocumentationArtifactPlan,
    val reviewedBases: List<ReviewedDocumentationBase>,
    val currentDocuments: List<CurrentDocumentationState>,
    val generatedCandidates: List<RenderedArtifact>,
    val ownershipManifests: List<DocumentationOwnershipManifest>,
    val priorReceipts: List<ApplyReceiptReference>,
    val aiProposals: List<AiReconciliationProposal> = emptyList(),
)
```

Inputs are immutable and content-addressed.

Absolute paths, current time, process IDs, locale, and filesystem enumeration
order are excluded from semantic decisions.

## 10. Three-way reconciliation

### 10.1 Base model

For each owned unit:

```text
BASE      = last completely reviewed/applied content
CURRENT   = current user-visible content
CANDIDATE = current deterministic generated content
```

Core compares all three.

### 10.2 Safe cases

| Base vs Current | Base vs Candidate | Result |
| --- | --- | --- |
| same | same | KEEP |
| same | changed | UPDATE_GENERATED |
| changed | same | KEEP_USER_CHANGE |
| changed | changed identically | KEEP |
| changed | changed differently | CONFLICT |

For a DOCPILOT_OWNED complete artifact, `KEEP_USER_CHANGE` does not silently
transfer ownership. It records drift and requires an explicit ownership or
content decision before future generated updates.

For SHARED_MANAGED documents, the table applies independently to each managed
block. Unmanaged regions are copied from CURRENT without normalization.

### 10.3 Missing base

No reviewed base means Core cannot perform automatic three-way merge.

Possible plan outcomes:

- `CREATE` when no current path exists;
- `OWNERSHIP_ADOPTION_REQUIRED` when current content exists;
- `UNKNOWN_OWNERSHIP_CONFLICT`;
- user-approved insertion of a new managed block at an explicitly identified
  anchor.

Two-way similarity is advisory Evidence only.

## 11. Reconciliation operations

Stable operations:

```text
CREATE_ARTIFACT
UPDATE_OWNED_ARTIFACT
INSERT_MANAGED_BLOCK
UPDATE_MANAGED_BLOCK
REMOVE_MANAGED_BLOCK
KEEP
KEEP_USER_CONTENT
ADOPT_AS_USER_OWNED
ADOPT_AS_DOCPILOT_OWNED
CONVERT_TO_SHARED_MANAGED
RELOCATE_ARTIFACT
CONFLICT
```

Rules:

- removal requires RFC-0046 explicit REMOVE approval;
- relocation never deletes the previous whole file in RFC-0055;
- old paths become retained orphan/migration findings;
- adoption operations always require user approval;
- conflict is not an executable operation;
- all operations bind expected current SHA and resulting SHA.

## 12. Preview and dry-run

Preview is the default and has no write capability.

Conceptual result:

```kotlin
data class DocumentationReconciliationPlan(
    val formatVersion: Int = 1,
    val planId: String,
    val operations: List<ReconciliationOperation>,
    val conflicts: List<ReconciliationConflict>,
    val ownershipTransitions: List<OwnershipTransition>,
    val evidence: List<ReconciliationEvidence>,
    val planSha256: String,
)
```

Preview includes:

- exact affected paths and Artifact IDs;
- ownership before/after;
- base/current/candidate SHA;
- operation and reason;
- managed block IDs;
- unified/structured diff;
- conflicts;
- required decisions;
- ignored AI proposal fields;
- no-op/KEEP evidence.

`planSha256` binds every semantic input and output. Reordering inputs produces
the same plan.

## 13. Conflict detection

Stable conflict kinds:

```text
UNKNOWN_OWNERSHIP
OWNERSHIP_EVIDENCE_MISMATCH
MANIFEST_TAMPERED
ARTIFACT_ID_MISMATCH
PATH_COLLISION
MEDIA_TYPE_MISMATCH
MANAGED_BLOCK_MARKER_INVALID
DUPLICATE_MANAGED_BLOCK_ID
REVIEWED_BASE_MISSING
CURRENT_CHANGED_FROM_BASE
OVERLAPPING_USER_AND_GENERATED_EDIT
STALE_PLAN
STALE_REVIEW_BUNDLE
RENDERER_IDENTITY_CHANGED
UNSUPPORTED_BINARY_CONTENT
ORPHAN_RELOCATION_REQUIRED
```

Conflict ordering is deterministic by path, ownership unit, conflict kind, and
stable ID.

Any unresolved conflict makes the plan non-applicable.

Core does not use line-oriented auto-merge when semantic ownership regions
overlap. Non-overlapping managed blocks can reconcile independently.

## 14. Incremental reconciliation

RFC-0055 consumes RFC-0052 `DocumentationArtifactPlan`.

Reconciliation candidates are the union of:

- RFC-0052 CREATE/UPDATE Artifact IDs;
- current owned artifacts whose SHA differs from reviewed base;
- manifest/path migration findings;
- explicitly requested ownership-transition targets;
- retained orphan paths requiring reporting.

Unchanged, non-drifted KEEP artifacts are not parsed, proposed to AI, rendered,
or written.

Dependency refresh follows RFC-0052. Reconciliation does not invent additional
specification impact.

An ownership manifest change can select its artifact without selecting unrelated
artifact content.

## 15. AI boundary

### 15.1 Allowed

AI may propose:

- candidate wording for a conflicted managed block;
- a user-facing explanation of differences;
- suggested mapping between old and new headings;
- suggested anchor for managed-block insertion;
- suggested keep/generated/user preference.

Every proposal is structured, target-scoped, and Evidence-linked.

### 15.2 Forbidden

AI must not:

- set ownership;
- approve adoption;
- suppress a conflict;
- alter expected SHA values;
- select an operation;
- mark review complete;
- apply or write content;
- delete files or blocks;
- change manifest/receipt data;
- classify unknown content as generated.

### 15.3 Proposal handling

Core validates:

- proposal target is authorized by the Plan;
- referenced base/current/candidate SHA values match;
- proposal does not expand target scope;
- proposed content is treated as a fourth candidate, never as the merge result;
- all accepted proposal content enters the existing complete review workflow.

AI absence or failure does not prevent deterministic Preview.

## 16. User decisions

Every non-automatic operation has a stable decision ID.

Decision options are bounded by Core:

```text
ACCEPT_GENERATED
KEEP_CURRENT
ACCEPT_AI_PROPOSAL
ACCEPT_OWNERSHIP_TRANSITION
REJECT
```

The user cannot approve an operation that was not in the Plan.

Apply requires:

- all required decisions present;
- exact Plan SHA;
- exact current document SHA;
- exact manifest SHA;
- existing Review Bundle complete;
- no unresolved conflicts.

Partial decisions never modify documents.

## 17. Apply semantics

Apply is an explicit separate operation from Preview.

Core rechecks immediately before mutation:

- plan integrity;
- current file hashes;
- ownership manifests;
- reviewed base;
- Review Bundle/Lifecycle state;
- expected renderer/catalog identity;
- conflict absence.

Apply uses RFC-0050 transaction semantics:

```text
PREPARE journal
-> atomically write document set and manifests
-> store Apply Receipt / reconciliation result
-> mark APPLIED
```

If atomic multi-file replacement is not available, a recoverable transaction
journal records every intended before/after hash and supports deterministic
roll-forward or rollback.

No document may become visible as reconciled without its corresponding manifest
and result Evidence becoming recoverably committed.

## 18. Idempotency and recovery

Repeated apply with the same Plan SHA and already-observed result hashes returns
the original reconciliation result and performs no writes.

Crash recovery:

- detects prepared but incomplete transactions;
- verifies each document and manifest hash;
- completes or rolls back using Core rules;
- never invokes AI;
- never recomputes ownership heuristically;
- produces recovery Evidence.

A changed current document after Preview returns `STALE_PLAN`.

## 19. Reconciliation Result

Separate versioned contract:

```kotlin
data class DocumentationReconciliationResult(
    val formatVersion: Int = 1,
    val planSha256: String,
    val appliedOperationIds: List<String>,
    val retainedOperationIds: List<String>,
    val beforeDocumentShaByPath: Map<String, String?>,
    val afterDocumentShaByPath: Map<String, String>,
    val beforeManifestShaByArtifact: Map<String, String?>,
    val afterManifestShaByArtifact: Map<String, String>,
    val decisionEvidence: List<DecisionReference>,
    val applyReceiptId: String,
    val resultSha256: String,
)
```

The result is deterministic apart from external receipt metadata explicitly
excluded from semantic identity.

It supports offline verification against documents, manifests, Plan, Review
Bundle, and Apply Receipt.

### 19.1 Decision Explanation Report

Reconciliation must record not only what Core decided, but why.

RFC-0055 introduces a separate versioned Core contract:

```kotlin
data class ReconciliationExplanationReport(
    val formatVersion: Int = 1,
    val planSha256: String,
    val resultSha256: String?,
    val ownershipExplanations: List<OwnershipDecisionExplanation>,
    val operationExplanations: List<OperationDecisionExplanation>,
    val conflictExplanations: List<ConflictDecisionExplanation>,
    val mergeExplanations: List<MergeDecisionExplanation>,
    val evidenceGraph: ReconciliationEvidenceGraph,
    val reportSha256: String,
)
```

Every explanation includes:

```text
decisionId
subject Artifact/Path/Block ID
decision or outcome
stable Core rule IDs
Evidence references
accepted/rejected alternatives
base/current/candidate SHA references
required user decision, when applicable
caused downstream operation IDs
```

Stable rule examples:

```text
OWNERSHIP_MANIFEST_AND_RECEIPT_MATCH
UNKNOWN_PATH_HAS_NO_OWNERSHIP_EVIDENCE
CURRENT_MATCHES_REVIEWED_BASE
CURRENT_AND_CANDIDATE_DIVERGED
UNMANAGED_REGION_BYTE_PRESERVED
MANAGED_BLOCK_UPDATE_ALLOWED
MANAGED_BLOCK_REMOVE_REQUIRES_APPROVAL
STALE_CURRENT_SHA_BLOCKED_APPLY
AI_PROPOSAL_NOT_AUTHORITY
```

The Evidence graph contains typed deterministic edges:

```text
Evidence -> supports -> Ownership Decision
Ownership Decision -> permits/prohibits -> Operation
Base/Current/Candidate Diff -> causes -> Conflict
User Decision -> authorizes -> Merge Operation
Operation -> produces -> Result Artifact/Manifest
```

Rules:

- Core creates the structured explanation from actual evaluated rules;
- every non-KEEP operation and every conflict requires an explanation;
- KEEP decisions are explainable and may be compactly grouped by identical rule;
- a decision without its required Evidence makes the Plan invalid;
- the report is available at Preview time with `resultSha256 = null`;
- Apply binds the final Result SHA and produces the final report;
- report SHA excludes timestamps, absolute paths, locale, and prose formatting;
- offline verification replays rule/Evidence references, not AI reasoning.

AI may render a user-friendly narrative from the structured report. AI may not
add a cause, remove an Evidence reference, change rule IDs, or reinterpret the
Core outcome. AI narrative is optional and excluded from the authoritative
report SHA.

## 20. Path and content safety

- paths are normalized repository-relative paths;
- `..`, absolute paths, alternate separators, and symlink escapes are rejected;
- duplicate normalized paths fail;
- encoding must be UTF-8 for initial Markdown support;
- line-ending normalization is explicit and never used to hide user changes;
- malformed managed markers fail closed;
- user content outside managed regions remains byte-preserved;
- no whole-file delete;
- no glob-based apply target.

## 21. Determinism

Semantically identical inputs produce identical:

- ownership classification;
- operations and conflicts;
- required decisions;
- diff content;
- Evidence ordering;
- Plan ID/SHA;
- applicable/non-applicable state;
- resulting document bytes;
- manifest bytes;
- result SHA.

Determinism tests shuffle artifacts, manifests, blocks, receipts, Evidence,
decisions, and AI proposals.

## 22. Public API impact

Expected Core contracts:

- `DocumentationOwnership`
- `DocumentationOwnershipManifest`
- `ManagedBlockOwnership`
- `DocumentationReconciliationRequest`
- `DocumentationReconciliationPlan`
- `ReconciliationOperation`
- `ReconciliationConflict`
- `OwnershipTransition`
- `DocumentationReconciler`
- `AiReconciliationProposal`
- `DocumentationReconciliationDecision`
- `DocumentationReconciliationResult`
- `ReconciliationExplanationReport`
- `ReconciliationEvidenceGraph`
- stable reconciliation rule IDs
- repositories/codecs/verifiers for manifest, Plan, and result

Existing formats remain unchanged:

- DIR schema `0.3`;
- Snapshot format `1`;
- Review Bundle format `1`;
- Lifecycle/Receipt/Journal formats `1`;
- Relationship Projection Report format `1`.

RFC-0055 adds separate format-1 contracts rather than changing those models.

## 23. CLI and MCP boundary

No CLI or MCP feature is required by RFC-0055.

Future adapters may expose:

```text
reconcile preview
reconcile decide
reconcile apply
reconcile verify
```

Adapters must call Core and may not:

- infer ownership;
- classify conflicts;
- implement merge rules;
- mutate without Core confirmation;
- weaken dry-run defaults.

Core remains independent from MCP.

## 24. Expected implementation areas

```text
src/main/kotlin/io/docpilot/core/reconciliation/**
src/main/kotlin/io/docpilot/core/incremental/execution/**
src/main/kotlin/io/docpilot/core/incremental/specification/review/**
src/main/kotlin/io/docpilot/core/api/**
```

Existing Review/Lifecycle code changes are limited to reusable ports and
transaction integration.

Protected:

```text
tools/docpilot-mcp/src/**
tools/docpilot-mcp/tests/**
docpilot-cli relationship/ownership rules
Review Bundle v1 schema
whole-file deletion
```

## 25. Testing

### 25.1 Ownership

- complete DocPilot-owned Evidence;
- user-owned path collision;
- valid shared managed blocks;
- unknown and contradictory Evidence;
- manifest tampering;
- path/Artifact ID migration;
- ownership transition approval.

### 25.2 Three-way merge

- all five base/current/candidate cases;
- non-overlapping managed blocks;
- overlapping managed/user edits;
- byte preservation outside blocks;
- missing base;
- malformed/duplicate markers;
- renderer identity change.

### 25.3 Preview

- no filesystem writes;
- exact operation/reason/Evidence;
- deterministic structured diff;
- Plan SHA input-order independence;
- required decisions;
- conflicts make plan non-applicable.
- every Ownership/Operation/Conflict has structured decision reasons;
- Preview Explanation Report verifies without an Apply result.

### 25.4 Incremental

- one changed Component reconciles its artifact and summaries only;
- drifted KEEP artifact is included;
- unchanged KEEP artifact is not parsed/rendered/written;
- relationship-only artifact selection;
- orphan retained without file deletion.

### 25.5 AI

- no-provider Preview;
- authorized target-scoped proposal;
- unauthorized target rejection;
- proposal SHA mismatch;
- proposal cannot change ownership/operation;
- accepted proposal still requires complete review.

### 25.6 Apply/recovery

- stale current document;
- stale manifest;
- incomplete decisions;
- atomic document+manifest visibility;
- idempotent reapply;
- crash after journal, document, manifest, or receipt phase;
- offline result verification.
- final Explanation Report binds the Result SHA;
- Evidence graph and stable rule replay verification;
- optional AI narrative cannot change authoritative facts.

### 25.7 Full verification

- targeted suites;
- clean build/test;
- XML aggregation;
- isolated fixture with existing user Markdown;
- protected-path check;
- `git diff --check`.

## 26. Completion criteria

RFC-0055 is complete when:

- Ownership is Evidence-based and Core-owned;
- unknown ownership never overwrites content;
- Preview is write-free and default;
- conflicts are deterministic and fail closed;
- three-way managed reconciliation preserves user regions;
- only impacted/drifted artifacts reconcile;
- AI is proposal-only;
- complete decisions and stale checks gate Apply;
- document, manifest, and result are atomically/recoverably committed;
- apply is idempotent and recoverable;
- offline verification passes;
- every material decision has a verifiable Explanation Report;
- no whole-file deletion, CLI rules, or MCP changes enter scope;
- targeted, full, and isolated reconciliation tests pass.

## 27. Documentation Evolution Intelligence

RFC-0055 completes the primary v1.0 Product Capability sequence:

```text
generate -> validate facts -> coexist safely with existing documentation
```

After v1.0, add the next Product Capability RFC:

**Documentation Evolution Intelligence**

It should explain with Evidence:

- why documentation changed;
- affected Artifact, Module, Package, Component, API, Property, and Relationship;
- added and removed entities;
- added, removed, and modified relationships;
- ownership/reconciliation decisions;
- quality or projection findings that caused updates;
- before/after stable IDs and source Evidence;
- expected downstream impact.

The evolution explanation consumes:

- specification diff and RFC-0045 relationship diff;
- RFC-0052 Artifact Plan;
- RFC-0053 Projection Report;
- future RFC-0054 Quality Report when available;
- RFC-0055 Reconciliation Plan/Result.
- RFC-0055 Reconciliation Explanation Report and Evidence graph.

AI may improve prose, but Core owns the structured change facts, causal graph,
scope, Evidence, and completeness.

Proposed sequencing:

```text
RFC-0054 Documentation Quality Validation (pending approval)
RFC-0055 Existing Documentation Reconciliation (selected; v1.0 final RFC)
RFC-0056 candidate Documentation Evolution Intelligence
RFC-0057+ Hardening
```

Numbers after RFC-0055 remain provisional until separately approved.

## 28. Canonical sources

- `docs/rfc/RFC-0055-Existing-Documentation-Reconciliation.md`
- `docs/planning/RFC-0055-MAIN-PLANNING-UPDATE.md`
- `docs/roadmap/ROADMAP.md`
