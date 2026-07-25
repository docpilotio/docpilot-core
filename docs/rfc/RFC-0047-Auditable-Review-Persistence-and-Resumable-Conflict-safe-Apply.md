# RFC-0047: Auditable Review Persistence and Resumable Conflict-safe Apply

## Status

Implemented, locally verified, and integrated into local main. Remote push is not
part of this integration handoff.

Verification evidence:

- focused Review Bundle persistence tests: PASS;
- `clean build`: PASS;
- `clean test`: PASS;
- 88 test XML files, 270 tests, 0 failures, 0 errors, 0 skipped;
- isolated architecture-samples CLI smoke: PASS;
- DIR schema 0.3 and specification snapshot format 1 unchanged.

## 1. Purpose

RFC-0043 introduced complete review before managed-block merge. RFC-0046 added
explicit review-gated removal and bound an in-memory proposal to the exact reviewed
documentation using SHA-256. That safety boundary currently ends with the process:
proposal, decisions, identity, and integrity state are lost after restart.

RFC-0047 defines Review Bundle as an official, Core-owned, versioned long-term data
contract. It is not a CLI exchange convenience and is not owned by MCP.

```text
Incremental review preparation
        ->
Canonical Review Proposal
        ->
Versioned Review Bundle v1
        ->
Integrity-checked atomic persistence
        ->
Process restart
        ->
Validated load and decision continuation
        ->
Exact reviewed-base check
        ->
Complete conflict-safe apply
```

The contract must remain readable and diagnosable across supported DocPilot
versions. Unknown versions, corruption, identity mismatch, stale documentation,
and concurrent bundle updates fail explicitly.

## 2. Product outcome

A developer or future adapter can:

- persist a prepared review without losing its exact proposal;
- record partial decisions over multiple process executions;
- load and validate the same review later;
- prove which project, specifications, documentation base, targets, operations,
  Evidence, and decisions belong to the review;
- detect tampering, truncation, incompatible versions, and lost updates;
- resume apply only after every target has an explicit decision;
- and block apply when the documentation changed after proposal preparation.

No CLI or UI is required for the Core capability to be complete.

## 3. Scope

RFC-0047 includes:

1. Review Bundle format version 1 as a public long-term Core data contract.
2. Canonical deterministic JSON encoding.
3. Envelope and payload integrity using SHA-256.
4. Deterministic proposal identity.
5. Project and previous/current specification identity binding.
6. Persistence of proposal entries, missing targets, and partial/complete decisions.
7. Core codec and repository ports.
8. A local filesystem repository adapter.
9. Atomic validated save and expected-integrity update protection.
10. Structured load/save failure results.
11. Restart-safe resume and conflict-safe apply orchestration.
12. Exact reviewed-document stale-apply prevention.
13. Backward compatibility policy for the official format.

## 4. Non-goals

RFC-0047 does not:

- add CLI commands, flags, prompts, TUI, GUI, or web UI;
- define a human-editable interchange format as the primary contract;
- make MCP own or interpret Review Bundles;
- make Core depend on MCP;
- add remote review, synchronization, locking, or collaboration services;
- add reviewer authentication, authorization, signatures, certificates, or trust chains;
- invent reviewer identity from operating-system or Git metadata;
- persist AI credentials, prompts, raw provider responses, or provider secrets;
- persist full previous/current `ProjectSpecification` objects;
- persist the complete reviewed documentation body;
- delete files or artifacts;
- change managed-block removal semantics;
- change DIR schema 0.3;
- change specification snapshot format 1;
- automatically approve decisions;
- automatically merge divergent review bundles;
- publish, tag, release, or push Git state.

## 5. Baseline and prerequisites

Baseline:

- `main` and `origin/main`: `084f1c2ac8a7efbed5a2c3837d9e76848a274149`;
- RFC-0046 feature commit: `ae0d6d35c97ddf27cd9a5f1e05a7c2dc165d588b`;
- RFC-0046 merge commit: `d3c50ce98442bb8e823041e19f12345cb9d5d63e`;
- DIR schema: `0.3`;
- specification snapshot format: `1`;
- baseline verification: 87 XML files, 265 tests, 0 failures, 0 errors, 0 skipped.

Prerequisites already present:

- deterministic update plans and stable target IDs;
- explicit UPSERT and REMOVE patch operations;
- deterministic `DocumentationReviewProposal` entries;
- exact reviewed-document SHA-256;
- complete-review-before-merge;
- fail-closed accepted-only transformation;
- canonical specification snapshot encoding and validated atomic repository pattern.

## 6. Ownership and compatibility commitment

Review Bundle is owned by DocPilot Core.

Core owns:

- model semantics;
- format version;
- canonical field encoding and ordering;
- proposal identity;
- integrity calculation;
- load validation;
- repository ports;
- local repository behavior;
- resume state transitions;
- stale-apply validation.

Adapters may invoke these contracts but may not redefine them. CLI, UI, MCP, or a
provider-specific module must not become the canonical codec or source of truth.

Once Review Bundle format v1 is released:

- supported readers must continue to read valid v1 bundles;
- incompatible changes require a new integer format version;
- fields cannot silently change meaning;
- enum values cannot be repurposed;
- unknown versions fail as `UNSUPPORTED_VERSION`;
- migrations require a separately approved compatibility policy.

## 7. Format identity

Add:

```kotlin
public object ReviewBundleFormat {
    public const val CURRENT_VERSION: Int = 1
    public const val DEFAULT_DIRECTORY: String = ".docpilot/reviews"
}
```

Default file name:

```text
<proposalId>.json
```

`proposalId` has the exact form:

```text
review:<64 lowercase hexadecimal SHA-256 characters>
```

The filesystem adapter must map the identifier to a safe file name without
accepting path separators, traversal, alternate roots, or caller-provided paths.
A recommended file name is:

```text
review-<64 hex>.json
```

The logical identifier remains `review:<hash>`.

## 8. Bundle envelope

Format v1 envelope:

```json
{
  "reviewBundleFormatVersion": 1,
  "projectIdentity": {
    "projectId": "project-id"
  },
  "proposalId": "review:<sha256>",
  "payload": {
    "...": "canonical payload"
  },
  "integrity": {
    "algorithm": "SHA-256",
    "payloadSha256": "<64 lowercase hex>"
  }
}
```

Envelope field order is exactly:

1. `reviewBundleFormatVersion`
2. `projectIdentity`
3. `proposalId`
4. `payload`
5. `integrity`

Unknown top-level or payload fields in v1 are rejected. The format does not use
unspecified extension maps.

## 9. Canonical payload

The v1 payload contains:

```text
previousSpecificationSha256
currentSpecificationSha256
reviewedDocumentationSha256
proposal
decisions
```

Canonical JSON field order:

1. `previousSpecificationSha256`
2. `currentSpecificationSha256`
3. `reviewedDocumentationSha256`
4. `proposal`
5. `decisions`

The payload does not contain:

- wall-clock creation/update timestamps;
- random UUIDs;
- filesystem paths;
- machine or user names;
- provider identifiers;
- transient metrics;
- raw documents or specifications.

This exclusion keeps canonical identity stable and avoids unnecessary sensitive data.

## 10. Project and specification identity

### 10.1 Project identity

`projectIdentity.projectId` is non-blank and equals both previous and current
specification project IDs at bundle preparation.

Load requires an expected project ID. Mismatch returns `PROJECT_MISMATCH`.

### 10.2 Specification identity

The bundle stores:

```text
previousSpecificationSha256
currentSpecificationSha256
```

Each is lowercase SHA-256 of the canonical specification payload used by the
official Core specification snapshot canonicalizer.

The identity excludes the snapshot envelope and snapshot integrity field. It binds
the review to semantic DIR content, not to repository path or snapshot file layout.

The implementation must expose or extract one shared Core canonical specification
encoder. It must not copy two subtly different encoders into review and snapshot
packages.

Both hashes are required even when equal.

## 11. Proposal identity

`proposalId` is deterministic:

```text
"review:" + SHA-256(canonicalProposalIdentityPayload UTF-8 bytes)
```

The proposal identity payload contains:

1. format-domain string `docpilot-review-proposal-v1`;
2. project ID;
3. previous specification SHA-256;
4. current specification SHA-256;
5. reviewed documentation SHA-256;
6. canonical proposal entries;
7. canonical missing patch target IDs.

It excludes:

- decisions;
- comments added after preparation;
- envelope payload checksum;
- timestamps;
- storage path;
- mutable lifecycle state.

Therefore:

- the same semantic proposal produces the same proposal ID;
- adding or changing decisions does not change proposal ID;
- changing any proposal entry, operation, Evidence, target, Markdown, specification
  identity, or reviewed base changes proposal ID.

Decode recomputes and verifies proposal ID. Mismatch is `PROPOSAL_ID_MISMATCH`.

## 12. Persisted proposal

The persisted proposal contains all fields necessary for review and apply:

```text
entries[]
missingPatchTargetIds[]
```

Each entry contains:

```text
targetId
parentId
target
specificationChangeKind
documentationChangeKind
operation
existingMarkdown
proposedMarkdown
evidenceIds[]
```

Rules:

- entries use `DocumentationReviewProposal.ENTRY_ORDER`;
- target IDs are unique;
- Evidence IDs are unique and lexical;
- missing IDs are unique and lexical;
- UPSERT/REMOVE consistency rules from RFC-0046 apply unchanged;
- REMOVE retains previous existing Markdown and empty proposed Markdown;
- no entry can appear in `missingPatchTargetIds`;
- `reviewedDocumentationSha256` in the payload equals the proposal value used by
  the runtime review contract.

The bundle may persist an incomplete proposal. An incomplete proposal can be
inspected and updated only by replacing it with a newly prepared proposal and new
proposal ID. It can never be applied.

## 13. Persisted decisions

Each decision contains:

```text
targetId
disposition
comment
```

Rules:

- target IDs are unique and lexical in canonical storage;
- every decision target exists in proposal entries;
- disposition is `ACCEPTED` or `REJECTED`;
- comment is null or non-blank;
- comments are preserved exactly as UTF-8 strings;
- comments participate in payload integrity;
- no implicit decisions are synthesized;
- missing decisions remain pending.

Decisions can be saved incrementally.

Decision updates use target replacement semantics:

- a new target decision is added;
- a decision for an already decided target replaces that target's disposition and comment;
- the resulting complete decision list is canonicalized and saved as one new bundle payload;
- proposal ID remains unchanged;
- payload SHA-256 changes when decision content changes.

Whether adapters allow a human to revise a decision is an adapter policy. Core
supports deterministic replacement until apply is requested.

## 14. Integrity contract

Envelope integrity:

```text
algorithm = "SHA-256"
payloadSha256 = SHA-256(exact canonical payload JSON UTF-8 bytes)
```

The checksum covers:

- specification identities;
- reviewed-document identity;
- proposal;
- all decisions and comments.

It does not cover envelope whitespace because decode re-encodes the validated
semantic payload canonically before verification.

Validation order:

1. parse strict JSON;
2. validate format version;
3. validate project identity;
4. decode payload fields and enum values;
5. validate domain invariants and ordering;
6. recompute specification/proposal relationships;
7. recompute proposal ID;
8. recompute canonical payload SHA-256;
9. compare integrity.

No invalid result is returned as a usable bundle.

## 15. Canonical JSON rules

Format v1 uses:

- UTF-8;
- LF (`\n`) newlines;
- one final newline in the envelope;
- fixed object field order defined by this RFC;
- arrays in contract-defined canonical order;
- JSON strings with deterministic escaping;
- integers in base-10 without leading zeros;
- explicit JSON `null` for nullable fields;
- no insignificant fields;
- no duplicate object keys;
- no trailing JSON content;
- no locale-sensitive formatting;
- no Unicode normalization.

Equivalent valid model inputs produce byte-identical encoded bundles independent
of collection order, operating system, timezone, locale, or process execution.

The codec must not depend on general map iteration order.

## 16. Public Core models

Expected contracts:

```kotlin
public data class ReviewBundleProjectIdentity(
    public val projectId: String,
)

public data class ReviewSpecificationIdentity(
    public val previousSpecificationSha256: String,
    public val currentSpecificationSha256: String,
)

public data class ReviewBundleIntegrity(
    public val algorithm: String = "SHA-256",
    public val payloadSha256: String,
)

public data class StoredReviewBundle(
    public val formatVersion: Int,
    public val projectIdentity: ReviewBundleProjectIdentity,
    public val proposalId: String,
    public val specificationIdentity: ReviewSpecificationIdentity,
    public val proposal: DocumentationReviewProposal,
    public val decisions: List<DocumentationReviewDecision>,
    public val integrity: ReviewBundleIntegrity,
)
```

Names may be adjusted for established package conventions, but the semantic fields
and invariants are mandatory.

No mutable collection is exposed.

## 17. Load results

Add structured failure reasons:

```kotlin
public enum class ReviewBundleValidationFailure {
    CORRUPTED,
    UNSUPPORTED_VERSION,
    PROJECT_MISMATCH,
    PROPOSAL_ID_MISMATCH,
    INTEGRITY_MISMATCH,
    INVALID_PROPOSAL,
    INVALID_DECISIONS,
    SPECIFICATION_IDENTITY_MISMATCH,
}

public sealed interface ReviewBundleLoadResult {
    public data object NotFound : ReviewBundleLoadResult
    public data class Valid(
        public val bundle: StoredReviewBundle,
    ) : ReviewBundleLoadResult
    public data class Invalid(
        public val reason: ReviewBundleValidationFailure,
        public val message: String,
    ) : ReviewBundleLoadResult
}
```

Expected identity mismatches are not parser exceptions exposed to adapters.
Messages must not include persisted comments or Markdown content.

## 18. Repository port

Core defines:

```kotlin
public interface ReviewBundleRepository {
    public fun load(
        expectedProjectId: String,
        proposalId: String,
    ): ReviewBundleLoadResult

    public fun saveNew(
        bundle: StoredReviewBundle,
    ): ReviewBundleSaveResult

    public fun replace(
        bundle: StoredReviewBundle,
        expectedPayloadSha256: String,
    ): ReviewBundleSaveResult
}
```

`saveNew` fails if the proposal already exists.

`replace` is optimistic concurrency control:

- load current stored envelope;
- validate it;
- require current payload checksum equals `expectedPayloadSha256`;
- validate the replacement has the same project ID and proposal ID;
- write the replacement atomically;
- otherwise return a structured conflict and preserve the existing file.

No unconditional overwrite method is part of the public contract.

## 19. Save results

Expected results:

```kotlin
public sealed interface ReviewBundleSaveResult {
    public data class Saved(
        public val bundle: StoredReviewBundle,
    ) : ReviewBundleSaveResult

    public data object AlreadyExists : ReviewBundleSaveResult

    public data class Conflict(
        public val expectedPayloadSha256: String,
        public val actualPayloadSha256: String,
    ) : ReviewBundleSaveResult

    public data class Invalid(
        public val message: String,
    ) : ReviewBundleSaveResult

    public data class Failed(
        public val message: String,
    ) : ReviewBundleSaveResult
}
```

Repository conflicts are bundle-update conflicts. They are distinct from the
reviewed-document stale-apply conflict.

## 20. Local filesystem repository

The default repository stores bundles under:

```text
<project-root>/.docpilot/reviews/review-<proposal-hash>.json
```

Save algorithm:

1. validate model invariants;
2. encode canonical envelope;
3. create the target directory;
4. create a temporary file in the same directory;
5. write exact UTF-8 bytes;
6. read and decode the temporary file;
7. require a valid bundle with matching project/proposal/integrity;
8. for replace, revalidate expected current integrity immediately before move;
9. atomically move with replace where supported;
10. use documented same-filesystem replace fallback when atomic move is unavailable;
11. delete only the repository-created temporary file in `finally`.

The repository must not:

- follow a proposal-controlled path;
- delete unrelated files;
- scan or rewrite all bundles for one update;
- treat a directory as a valid bundle;
- replace a valid file with an invalid temporary file.

## 21. Preparation and persistence

Add a Core orchestration boundary, conceptually:

```kotlin
public interface PersistentDocumentationReviewWorkflow {
    public fun prepareAndSave(
        request: AiIncrementalGenerationRequest,
    ): PersistentReviewPreparationResult

    public fun recordDecisions(
        expectedProjectId: String,
        proposalId: String,
        expectedPayloadSha256: String,
        decisions: List<DocumentationReviewDecision>,
    ): PersistentReviewUpdateResult

    public fun resumeApply(
        request: ResumableReviewApplyRequest,
    ): ResumableReviewApplyResult
}
```

Preparation:

1. run existing AI incremental review preparation;
2. require `READY_FOR_REVIEW`;
3. calculate canonical previous/current specification hashes;
4. construct proposal identity;
5. construct and validate bundle with no decisions;
6. `saveNew`;
7. return proposal ID and stored payload checksum.

`NO_CHANGES` and `FAILED` preparation do not create a bundle.

## 22. Decision recording

`recordDecisions`:

1. load by expected project and proposal ID;
2. require valid bundle;
3. require loaded integrity equals caller's expected checksum;
4. validate every incoming decision against proposal entries;
5. merge by target ID using replacement semantics;
6. canonicalize decisions;
7. create new integrity;
8. call repository `replace` with the original expected checksum;
9. return the new checksum on success.

Unknown, duplicate, or conflicting input decisions fail before repository mutation.

Empty decision updates are rejected as invalid rather than performing a no-op write.

## 23. Restart and resume

Resume input contains:

```text
expectedProjectId
proposalId
expectedPayloadSha256 (optional but recommended)
currentDocumentation
```

Resume does not require previous/current full specifications because their
canonical identities and complete review entries are persisted.

Resume validation:

1. load exact bundle;
2. require supported version and valid integrity;
3. require expected project ID;
4. if supplied, require expected payload checksum;
5. require proposal completeness;
6. require every entry has exactly one decision;
7. require no unknown decisions;
8. require SHA-256 of exact current documentation equals
   `reviewedDocumentationSha256`;
9. reconstruct accepted UPSERT/REMOVE operations from persisted entries;
10. invoke the existing fail-closed reviewer/merger;
11. return one deterministic result.

The loaded bundle is the source of review truth. Adapters must not provide a second
proposal or patch list during resume.

## 24. Stale apply prevention

The stale apply check remains byte-exact:

```text
SHA-256(currentDocumentation UTF-8 bytes)
    ==
bundle.reviewedDocumentationSha256
```

No trimming, line-ending normalization, Unicode normalization, managed-block-only
projection, rebase, or partial apply occurs.

A mismatch returns a structured `STALE_DOCUMENTATION` resume failure and the input
document remains unchanged.

This check is independent from:

- bundle payload integrity;
- expected bundle update checksum;
- proposal identity;
- specification identities.

All four identity layers must pass.

## 25. Resume result

Expected result:

```kotlin
public sealed interface ResumableReviewApplyResult {
    public data class Applied(
        public val proposalId: String,
        public val sourcePayloadSha256: String,
        public val mergedDocumentation: String,
        public val mergedDocumentationSha256: String,
        public val acceptedTargetIds: List<String>,
        public val rejectedTargetIds: List<String>,
    ) : ResumableReviewApplyResult

    public data class Pending(
        public val proposalId: String,
        public val pendingTargetIds: List<String>,
        public val missingPatchTargetIds: List<String>,
    ) : ResumableReviewApplyResult

    public data class InvalidBundle(
        public val reason: ReviewBundleValidationFailure,
        public val message: String,
    ) : ResumableReviewApplyResult

    public data class Conflict(
        public val reason: ResumableReviewConflict,
        public val message: String,
    ) : ResumableReviewApplyResult
}
```

Conflict reasons include:

```text
BUNDLE_CHANGED
STALE_DOCUMENTATION
```

`Applied` means Core produced an approved in-memory document. It does not claim a
file writer committed that document.

## 26. Apply replay and lifecycle

Review Bundle v1 does not persist a mutable `APPLIED` flag.

Rationale:

- Core does not own the final artifact writer transaction;
- recording APPLIED before a write can be false;
- recording APPLIED after a write cannot be atomic with an arbitrary adapter;
- the exact reviewed-base check naturally blocks reapplying to already changed
  documentation.

The `Applied` result includes source and result hashes for a future receipt/audit
contract. Durable apply receipts are deferred.

## 27. Fail-closed behavior

Fail explicitly for:

- malformed, truncated, or trailing JSON;
- duplicate or unknown fields;
- unsupported format version;
- blank or unsafe project/proposal identity;
- project mismatch;
- invalid SHA-256 values;
- invalid proposal entry ordering or invariants;
- invalid UPSERT/REMOVE combinations;
- duplicate or unknown decisions;
- proposal ID mismatch;
- payload integrity mismatch;
- specification identity inconsistency;
- existing-file collision on `saveNew`;
- expected-integrity mismatch on replace;
- incomplete proposal;
- partial decisions at apply;
- stale current documentation;
- malformed managed blocks during final transformation.

No invalid load becomes a valid empty review. No conflict becomes overwrite. No
pending state becomes automatic rejection or acceptance.

## 28. Determinism

Equivalent semantic input produces identical:

- canonical specification identities;
- canonical proposal JSON;
- proposal ID;
- bundle payload JSON;
- payload SHA-256;
- encoded envelope bytes;
- load result;
- decision order;
- resumed accepted/rejected/pending lists;
- merged documentation.

Determinism is independent of:

- input list/set order;
- machine;
- process restart;
- filesystem enumeration order;
- locale;
- timezone;
- current time.

## 29. Privacy and audit limitations

The bundle intentionally stores:

- existing managed-block Markdown;
- proposed Markdown;
- Evidence IDs;
- reviewer comments.

These values may contain sensitive project information. The local adapter uses the
project filesystem's existing access controls. RFC-0047 does not add encryption,
redaction, access control, or secret scanning.

Documentation must warn adapters not to expose bundles unintentionally.

The bundle is auditable by integrity and identity, not by authenticated authorship.
It proves content consistency, not who made a decision.

## 30. Public API and compatibility

Expected additive Core API:

- `ReviewBundleFormat`
- Review Bundle identity/integrity models
- `StoredReviewBundle`
- validation and load/save result types
- canonical Review Bundle codec
- `ReviewBundleRepository`
- local filesystem repository
- persistent preparation/update workflow
- resumable apply request/result

Existing APIs remain:

- transient `AiIncrementalDocumentationReviewWorkflow`;
- `DocumentationDiffReviewer`;
- `DocumentationReviewProposal`;
- `DocumentationReviewDecision`;
- managed-block merger;
- specification snapshot repository.

Adding Review Bundle persistence does not make the transient workflow invalid.

## 31. Architecture boundaries

Dependency direction:

```text
Review domain models
        <-
Review persistence ports and orchestration
        <-
Canonical codec / local repository adapter
        <-
Future CLI or other adapters
```

Forbidden:

```text
Core -> CLI
Core -> MCP
Core -> provider implementation
Review Bundle codec -> MCP state
Review domain -> filesystem
```

The local repository is a Core-provided adapter comparable to the existing local
specification snapshot repository. Domain models do not import filesystem APIs.

## 32. Expected implementation areas

Production candidates:

```text
src/main/kotlin/io/docpilot/core/incremental/specification/review/
  ReviewBundleModels.kt
  ReviewBundleCodec.kt
  ReviewBundleRepository.kt
  FileReviewBundleRepository.kt
  PersistentDocumentationReviewWorkflow.kt
  existing review models only where identity integration requires it

src/main/kotlin/io/docpilot/core/incremental/specification/snapshot/
  shared canonical specification identity extraction only
```

Test candidates:

```text
src/test/kotlin/io/docpilot/core/incremental/specification/review/
  JsonReviewBundleCodecTest.kt
  FileReviewBundleRepositoryTest.kt
  PersistentDocumentationReviewWorkflowTest.kt
  ResumableDocumentationReviewWorkflowTest.kt
```

No CLI or MCP path is authorized.

## 33. Verification plan

### 33.1 Canonical codec

- byte-identical repeated encode;
- shuffled entries, Evidence, missing IDs, and decisions canonicalize identically;
- UPSERT and REMOVE round-trip;
- null and non-null comments round-trip;
- Unicode and JSON escape round-trip;
- exact final newline;
- duplicate/unknown field rejection;
- truncated/trailing JSON rejection.

### 33.2 Version and identity

- version 1 accepted;
- unknown lower/higher version rejected;
- project mismatch rejected;
- previous/current specification identities stable;
- proposal ID stable across decision changes;
- proposal mutation changes proposal ID;
- forged proposal ID rejected.

### 33.3 Integrity

- proposal change detected;
- decision/disposition/comment change detected;
- reviewed hash change detected;
- specification identity change detected;
- unsupported algorithm rejected;
- uppercase or malformed checksum rejected.

### 33.4 Repository

- not-found result;
- `saveNew` success;
- existing proposal collision;
- validated atomic replace;
- expected checksum conflict;
- invalid temporary bundle never replaces valid bundle;
- directory-at-file-path invalid;
- traversal and unsafe ID rejection;
- concurrent stale replace loses without overwrite;
- temporary file cleanup limited to owned temp file.

### 33.5 Resume

- restart simulation with new workflow/repository instances;
- partial decisions persist and remain pending;
- complete decisions resume and apply;
- accepted/rejected mix preserved;
- REMOVE resumes correctly;
- missing patch prevents apply;
- stale documentation blocks apply;
- line-ending-only and whitespace-only document changes block apply;
- tampered bundle blocks apply;
- stale expected bundle checksum blocks apply;
- equivalent resume produces identical merged output.

### 33.6 Regression

- RFC-0043 complete review;
- RFC-0045 relationship-aware review;
- RFC-0046 explicit removal and conflict safety;
- specification snapshot compatibility;
- full `clean build`;
- full `clean test`;
- isolated architecture-samples CLI smoke;
- `git diff --check`;
- protected path review.

## 34. Completion criteria

RFC-0047 implementation is complete only when:

1. Review Bundle v1 is defined and implemented as a Core-owned official contract;
2. valid v1 bundles round-trip byte-deterministically;
3. proposal ID is stable and decision-independent;
4. payload integrity covers proposal and decisions;
5. project and specification identities are verified;
6. partial decisions survive process restart;
7. repository updates prevent lost writes with expected integrity;
8. resume uses only the validated stored proposal and decisions;
9. incomplete review cannot apply;
10. exact stale-document conflicts cannot apply;
11. UPSERT and REMOVE resume with RFC-0046 invariants intact;
12. corrupt/unknown/mismatched bundles never become usable state;
13. local save validates before atomic replacement;
14. DIR 0.3 and specification snapshot format 1 remain compatible;
15. no CLI/UI or MCP dependency is introduced;
16. targeted, full regression, build, and smoke verification pass;
17. Canonical RFC, Planning, Handoff, and Roadmap match evidence.

## 35. Known risks

- A long-term format creates an ongoing reader compatibility obligation.
- Canonicalization errors can make proposal IDs unstable across versions.
- Persisted Markdown/comments may contain sensitive information.
- Expected-integrity replacement prevents lost writes but is not distributed locking.
- Exact stale checks intentionally reject benign document formatting changes.
- Applying returns an in-memory result; final artifact-write transactionality remains external.
- Sharing canonical specification encoding with snapshots must not accidentally
  change snapshot format 1 bytes.

Each risk requires explicit tests or a recorded limitation.

## 36. Deferred follow-up

Not approved by RFC-0047:

- CLI review prepare/list/decide/apply commands;
- UI/TUI review;
- authenticated reviewer identity;
- cryptographic signatures;
- durable apply receipts;
- remote/multi-user review synchronization;
- automatic bundle migration;
- encrypted Review Bundles;
- retention and garbage-collection policy;
- MCP orchestration features;
- release provenance automation.

## 37. Decision record

Approved candidate:

```text
RFC-0047
Auditable Review Persistence and Resumable Conflict-safe Apply
```

Approved scope:

- Review Bundle as an official Core-owned versioned long-term data contract;
- deterministic persistence;
- integrity and identity validation;
- partial/complete decision persistence;
- restart-safe resume;
- exact stale-apply prevention;
- local validated atomic repository;
- optimistic bundle update conflict protection.

Approved non-goals:

- CLI/UI;
- MCP dependency;
- remote collaboration;
- authenticated signatures;
- file deletion;
- release publication.

This decision approves the detailed specification. It does not by itself approve
implementation, commit, merge, push, tag, or release.
