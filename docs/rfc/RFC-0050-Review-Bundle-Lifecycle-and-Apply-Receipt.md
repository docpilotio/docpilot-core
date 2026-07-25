# RFC-0050: Review Bundle Lifecycle and Apply Receipt

## Status

Approved detailed specification. Implementation is not yet started.

## 1. Purpose

RFC-0046 introduced explicit, fully reviewed managed-block removal. RFC-0047 made
Review Bundle format 1 a durable Core-owned contract with deterministic identity,
integrity, optimistic updates, restart-safe decisions, and stale apply checks.
RFC-0048 exposed that contract through a thin official CLI. RFC-0049 established
deterministic release provenance.

The remaining audit gap begins after review approval:

- Core can produce an approved in-memory document but does not own a durable apply
  outcome;
- a Bundle has no official lifecycle after preparation;
- a successful apply has no immutable proof binding the exact Bundle, reviewed
  base, apply input, operations, and result;
- a crash between documentation replacement and audit persistence can leave an
  ambiguous state;
- retry behavior is inferred from stale-document checks instead of represented by
  an explicit idempotency contract.

RFC-0050 closes that gap with a Core-owned Review Lifecycle, immutable Apply
Receipt, recoverable apply transaction, idempotent apply semantics, and offline
verification suitable as the audit foundation for the v1.0 path.

## 2. Product outcome

For one review proposal, DocPilot can prove:

```text
proposal created
  -> decisions recorded
  -> apply transaction prepared
  -> exact documentation bytes replaced
  -> immutable receipt committed
  -> lifecycle reached APPLIED
```

After restart, the same proposal is either:

- safely retryable;
- already applied with the same Receipt;
- recoverable by deterministic roll-forward/rollback;
- blocked with an explicit recovery reason.

No adapter may declare a review applied, manufacture a Receipt, or interpret an
incomplete transaction independently.

## 3. Approved direction

RFC-0050 adopts separate versioned contracts:

```text
Review Bundle format 1       unchanged
Review Lifecycle format 1    new
Apply Receipt format 1       new
Apply Transaction Journal 1  new, recovery contract
```

The Review Bundle remains the proposal and decision payload. Lifecycle, Receipt,
and transaction state are separate Core-owned contracts with independent version
policies.

## 4. Scope

RFC-0050 includes:

- lifecycle creation with Review Bundle preparation;
- lifecycle states and legal transitions;
- Lifecycle Metadata format version 1;
- Apply Receipt format version 1;
- deterministic lifecycle, receipt, and transaction identities;
- exact binding to Review Bundle v1 payload integrity;
- accepted/rejected target and applied operation evidence;
- Core-owned apply transaction orchestration;
- atomic logical commit of Receipt and APPLIED lifecycle;
- compare-and-swap documentation replacement through a Core port;
- crash-safe transaction journal and deterministic recovery;
- exact idempotent apply;
- supersession and non-destructive archival metadata;
- optimistic concurrency for lifecycle and decisions;
- offline verification of Bundle, Lifecycle, Receipt, and Journal;
- structured Core status/query and recovery results;
- thin-adapter integration boundaries for CLI and MCP;
- deterministic local filesystem adapter;
- focused, integration, crash-injection, concurrency, and regression tests.

## 5. Non-goals

RFC-0050 does not:

- change Review Bundle format version 1;
- embed lifecycle or receipt fields into Review Bundle v1;
- delete Review Bundles, Receipts, documentation, or archive files;
- define automatic retention, cleanup, or garbage collection;
- add interactive CLI/TUI/GUI behavior;
- add remote or multi-user synchronization;
- add authentication, authorization, reviewer identity, or signatures;
- change Release Evidence Manifest format 1;
- add Git commit, merge, push, tag, or release behavior;
- make Core depend on CLI, MCP, provider implementations, or release tooling;
- add MCP persistence or MCP-owned lifecycle state;
- guarantee one hardware-level atomic write across unrelated filesystems;
- silently repair a document whose bytes match neither recorded input nor result.

## 6. Baseline

Specification baseline:

- main and origin/main:
  `d674463c078125b3d113823a90a49c26cb77b139`;
- Review Bundle format: `1`;
- DIR schema: `0.3`;
- Specification Snapshot format: `1`;
- CLI JSON output format: `1`;
- Release Evidence Manifest format: `1`;
- full regression: 95 XML files, 287 tests, 0 failures, 0 errors, 0 skipped.

Implementation must remeasure its own evidence.

## 7. Architecture principles

1. Core owns lifecycle, receipt, idempotency, transaction, and recovery rules.
2. Adapters expose I/O capabilities; they do not decide state.
3. Review Bundle v1 remains a separate unchanged contract.
4. Durable state is deterministic and integrity protected.
5. Cross-resource apply is recoverable and fail-closed.
6. APPLIED is never observable without a valid matching Receipt.
7. Receipt persistence and APPLIED lifecycle visibility use one atomic control
   generation switch.
8. A crash produces a recoverable Journal, not guessed success.
9. Offline verification requires no provider, CLI, MCP, or network.
10. Archive means retained terminal metadata, not deletion.

## 8. Aggregate boundary

The Core aggregate is:

```text
Review Aggregate
  Review Bundle v1
  Current Lifecycle Generation v1
  Optional Apply Receipt v1
  Optional active Transaction Journal v1
```

The Review Bundle stays byte-compatible. The aggregate repository validates
cross-contract identity before exposing usable state.

Conceptual dependency direction:

```text
Lifecycle/Receipt domain
        <-
Review aggregate service and transaction coordinator
        <-
Repository and DocumentationResource ports
        <-
local filesystem / CLI / MCP adapters
```

Forbidden:

```text
Core -> CLI
Core -> MCP
Core -> provider implementation
Core -> release tooling
Lifecycle codec -> CLI JSON model
Receipt codec -> MCP project state
Adapter -> independent lifecycle evaluator
```

## 9. Lifecycle states

Format 1 defines:

```text
ACTIVE
APPLYING
APPLIED
SUPERSEDED
ARCHIVED
RECOVERY_REQUIRED
```

Meaning:

- `ACTIVE`: decisions may be recorded; apply or supersession may begin.
- `APPLYING`: one durable apply transaction owns the proposal.
- `APPLIED`: one immutable valid Receipt is committed.
- `SUPERSEDED`: another proposal replaces this proposal; apply is forbidden.
- `ARCHIVED`: retained but inactive metadata; no apply or decision mutation.
- `RECOVERY_REQUIRED`: automated recovery cannot safely determine the document
  state; mutation is blocked pending explicit operator recovery input.

`RECOVERY_REQUIRED` is not a success or terminal audit outcome. It is a
fail-closed operational state.

## 10. Legal transitions

```text
create -> ACTIVE

ACTIVE -> ACTIVE             decision generation update
ACTIVE -> APPLYING           begin apply
ACTIVE -> SUPERSEDED         bind successor proposal
ACTIVE -> ARCHIVED           explicit non-destructive archive

APPLYING -> APPLIED          transaction commit
APPLYING -> ACTIVE           safe rollback before document replacement
APPLYING -> RECOVERY_REQUIRED ambiguous external document state

SUPERSEDED -> ARCHIVED
APPLIED -> ARCHIVED
RECOVERY_REQUIRED -> APPLYING explicit recovery continuation
RECOVERY_REQUIRED -> ACTIVE   proven rollback to exact input
RECOVERY_REQUIRED -> APPLIED  proven result plus valid receipt commit
```

No other transition is legal.

In particular:

- `APPLIED -> ACTIVE` is forbidden;
- `SUPERSEDED -> ACTIVE` is forbidden;
- `ARCHIVED -> *` is forbidden in format 1;
- `ACTIVE -> APPLIED` without an apply transaction is forbidden;
- a second `APPLIED` Receipt is forbidden.

## 11. Lifecycle Metadata format version 1

Top-level canonical fields:

```text
lifecycleFormatVersion
projectIdentity
proposalId
reviewBundleFormatVersion
observedBundlePayloadSha256
generation
state
activeTransactionId
applyReceiptId
supersededByProposalId
archivedFrom
integrity
```

Rules:

- format version is integer `1`;
- project and proposal identities match Review Bundle v1;
- `reviewBundleFormatVersion` is `1`;
- `observedBundlePayloadSha256` binds the exact current decision payload;
- generation begins at `1` and increases by exactly one per committed transition;
- state-specific nullable fields follow closed invariants;
- no timestamp, username, hostname, random UUID, or absolute path appears;
- unknown, missing, duplicate, or trailing JSON is rejected.

## 12. State invariants

### 12.1 ACTIVE

```text
activeTransactionId = null
applyReceiptId = null
supersededByProposalId = null
archivedFrom = null
```

### 12.2 APPLYING

```text
activeTransactionId != null
applyReceiptId = null
supersededByProposalId = null
archivedFrom = null
```

### 12.3 APPLIED

```text
activeTransactionId = null
applyReceiptId != null
supersededByProposalId = null
archivedFrom = null
```

The referenced Receipt must exist and validate.

### 12.4 SUPERSEDED

```text
activeTransactionId = null
applyReceiptId = null
supersededByProposalId != null
archivedFrom = null
```

The successor differs from the current proposal and belongs to the same project.

### 12.5 ARCHIVED

```text
activeTransactionId = null
archivedFrom in ACTIVE | APPLIED | SUPERSEDED
```

If archived from APPLIED, `applyReceiptId` remains present. If archived from
SUPERSEDED, `supersededByProposalId` remains present.

### 12.6 RECOVERY_REQUIRED

```text
activeTransactionId != null
```

The Journal contains the exact structured reason. Lifecycle metadata does not
duplicate free-form diagnostics.

## 13. Lifecycle identity and integrity

Lifecycle integrity:

```text
algorithm = SHA-256
payloadSha256
```

The payload contains every field except `integrity` in canonical order.

The lifecycle generation identity is:

```text
lifecycle:<sha256(canonical lifecycle payload)>
```

Generation number is concurrency evidence; payload SHA is content identity.

## 14. Apply Receipt format version 1

Top-level fields:

```text
applyReceiptFormatVersion
receiptId
projectIdentity
proposalId
reviewBundleFormatVersion
bundlePayloadSha256
transactionId
reviewedDocumentationSha256
applyInputDocumentationSha256
resultDocumentationSha256
acceptedTargetIds
rejectedTargetIds
operations
integrity
```

Receipt format version is integer `1`.

## 15. Receipt operation evidence

Each operation records:

```text
targetId
target
operation
existingMarkdownSha256
proposedMarkdownSha256
evidenceIds
```

Rules:

- entries use target ID lexical order;
- only accepted entries appear in `operations`;
- `operation` is `UPSERT` or `REMOVE`;
- rejected targets appear only in `rejectedTargetIds`;
- accepted and rejected sets are disjoint;
- their union equals all proposal entry IDs;
- Evidence IDs use canonical order;
- nullable Markdown is represented by the SHA-256 of the explicit contract value,
  never an omitted guess;
- the Receipt does not duplicate complete Markdown or reviewer comments.

## 16. Receipt identity

The canonical receipt identity input is:

```text
projectId
proposalId
bundlePayloadSha256
transactionId
reviewedDocumentationSha256
applyInputDocumentationSha256
resultDocumentationSha256
acceptedTargetIds
rejectedTargetIds
operations
```

Identity:

```text
receipt:<sha256(canonical identity input)>
```

The same semantic apply produces the same Receipt ID across process restarts,
machines, locales, and timezones.

Decision comments are covered indirectly by `bundlePayloadSha256`; they are not
copied into the Receipt.

## 17. Receipt integrity and immutability

Receipt integrity uses canonical payload SHA-256 and covers every field except
`integrity`.

Receipt repository contract supports:

```text
saveNew
load
verify
```

There is no replace method.

An existing exact Receipt is an idempotent collision. An existing different
Receipt at the same identity path is corruption and blocks apply.

## 18. Why Review Bundle v1 remains unchanged

Review Bundle v1 already owns:

- project and specification identities;
- proposal identity;
- complete patch/remove proposal;
- decisions and comments;
- payload integrity.

Lifecycle and Receipt have different mutation and retention semantics:

- decisions can change while ACTIVE;
- lifecycle generation changes on state transitions;
- Receipt is immutable and terminal;
- recovery Journal is operational and temporary after commit.

Embedding them in Bundle v1 would:

- break existing readers;
- change proposal update concurrency;
- mix mutable and immutable contracts;
- make lifecycle migration inseparable from proposal format migration.

Therefore no Review Bundle v2 is introduced.

## 19. Review Aggregate repository port

Core defines one aggregate-level port rather than allowing orchestration to call
three unrelated repositories:

```kotlin
public interface ReviewLifecycleRepository {
    public fun loadAggregate(
        expectedProjectId: String,
        proposalId: String,
    ): ReviewAggregateLoadResult

    public fun createReview(
        bundle: StoredReviewBundle,
        lifecycle: ReviewLifecycleMetadata,
    ): ReviewAggregateWriteResult

    public fun replaceActiveBundle(
        bundle: StoredReviewBundle,
        expectedBundlePayloadSha256: String,
        expectedLifecycleGeneration: Long,
    ): ReviewAggregateWriteResult

    public fun beginApply(
        transaction: ReviewApplyTransaction,
        applyingLifecycle: ReviewLifecycleMetadata,
        expectedBundlePayloadSha256: String,
        expectedLifecycleGeneration: Long,
    ): ReviewAggregateWriteResult

    public fun commitApply(
        transactionId: String,
        receipt: ApplyReceipt,
        appliedLifecycle: ReviewLifecycleMetadata,
        expectedApplyingGeneration: Long,
    ): ReviewAggregateWriteResult

    public fun transition(
        metadata: ReviewLifecycleMetadata,
        expectedGeneration: Long,
    ): ReviewAggregateWriteResult

    public fun recover(
        expectedProjectId: String,
        proposalId: String,
        documentation: DocumentationResource,
    ): ReviewRecoveryResult
}
```

Exact names may follow established conventions. The aggregate semantics are
mandatory.

## 20. Atomic Receipt and Lifecycle commit

RFC-0050 requires Receipt and APPLIED lifecycle visibility to be one atomic
logical transaction.

The local filesystem implementation uses immutable control generations:

```text
.docpilot/reviews/control/<proposal-hash>/
  CURRENT
  generations/
    00000001-<lifecycle-sha>/
      lifecycle.json
    00000002-<lifecycle-sha>/
      lifecycle.json
    00000003-<lifecycle-sha>/
      lifecycle.json
      receipt.json
```

Commit algorithm:

1. create a new generation directory under the same control directory;
2. write Lifecycle and Receipt temporary files;
3. read and fully validate both;
4. validate cross-identities with Bundle and Journal;
5. rename temporary files to their generation-final names;
6. fsync files and generation directory when the platform adapter supports it;
7. write a temporary `CURRENT` pointer containing the generation name;
8. validate the pointer target;
9. atomically replace `CURRENT`;
10. fsync the control directory when supported.

Only the `CURRENT` pointer determines visible lifecycle state. Therefore a reader
observes either:

- the previous APPLYING generation without a Receipt; or
- the new APPLIED generation with the matching Receipt.

It never observes APPLIED without its Receipt.

Unsupported atomic pointer replacement fails closed. A documented same-filesystem
fallback may be supported only with the Journal recovery protocol and tests.

## 21. Documentation transaction port

Core must not write through CLI-specific code. It defines:

```kotlin
public interface DocumentationResource {
    public fun readIdentity(): DocumentationResourceIdentity

    public fun prepareReplacement(
        expectedSha256: String,
        resultBytes: ByteArray,
        resultSha256: String,
        transactionId: String,
    ): DocumentationPrepareResult

    public fun commitPrepared(
        transactionId: String,
        expectedSha256: String,
        resultSha256: String,
    ): DocumentationCommitResult

    public fun discardPrepared(
        transactionId: String,
    ): DocumentationDiscardResult
}
```

The identity is adapter-neutral and contains a stable logical resource ID plus
exact content SHA-256. Canonical durable contracts do not contain absolute paths.

The local file adapter:

- prepares a temporary sibling file;
- verifies exact UTF-8 bytes and result SHA;
- compare-and-swaps against expected destination bytes;
- atomically replaces where supported;
- never truncates before Core authorization;
- scopes cleanup to the transaction-created temporary file.

CLI and MCP may provide ports. They do not implement lifecycle rules.

## 22. Cross-resource atomicity decision

A documentation file and `.docpilot` control directory may be different resources
without a shared filesystem transaction. Hardware-level simultaneous atomicity is
not portable.

RFC-0050 therefore guarantees:

```text
Receipt + APPLIED Lifecycle:
strict atomic visibility through one control-generation pointer

Documentation + audit control:
recoverable atomic transaction through durable write-ahead Journal,
exact hashes, idempotent operations, and mandatory recovery-before-read/write
```

This is a deliberate architecture decision. Claiming stronger cross-filesystem
atomicity would be false.

## 23. Apply Transaction Journal format 1

Top-level fields:

```text
applyTransactionFormatVersion
transactionId
projectIdentity
proposalId
bundlePayloadSha256
sourceLifecycleGeneration
phase
documentationResourceId
applyInputDocumentationSha256
resultDocumentationSha256
receiptId
targetLifecycleGeneration
integrity
```

Phases:

```text
PREPARED
DOCUMENT_REPLACED
CONTROL_COMMITTED
COMPLETED
RECOVERY_REQUIRED
```

The Journal is durable before documentation replacement begins.

The Journal contains no Markdown, comments, credentials, timestamp, hostname, or
absolute path.

## 24. Transaction identity

```text
transaction:<sha256(
  projectId,
  proposalId,
  bundlePayloadSha256,
  sourceLifecycleGeneration,
  documentationResourceId,
  applyInputDocumentationSha256,
  resultDocumentationSha256,
  receiptId
)>
```

The same exact apply attempt produces the same transaction identity. Random UUIDs
and current time are forbidden.

## 25. Apply protocol

Core performs:

1. acquire proposal-scoped exclusive transaction lease;
2. invoke recovery for any existing Journal;
3. load and validate Review Bundle v1 and current Lifecycle;
4. require Lifecycle `ACTIVE`;
5. require current Bundle SHA equals Lifecycle observed SHA;
6. validate expected project, proposal, Bundle SHA, and lifecycle generation;
7. require complete proposal and exactly one decision per entry;
8. read exact documentation identity;
9. require input SHA equals reviewed-documentation SHA;
10. compute merged result through existing reviewer;
11. construct and validate immutable Receipt;
12. derive deterministic transaction ID;
13. prepare documentation replacement;
14. persist Journal `PREPARED`;
15. atomically switch Lifecycle to `APPLYING`, referencing transaction ID;
16. revalidate Bundle, generation, and documentation input;
17. command DocumentationResource `commitPrepared`;
18. persist Journal `DOCUMENT_REPLACED`;
19. verify resource now has exact result SHA;
20. create the APPLIED control generation containing Lifecycle plus Receipt;
21. atomically switch `CURRENT`;
22. persist Journal `CONTROL_COMMITTED`;
23. verify Bundle, Receipt, Lifecycle, and current document result identity;
24. persist Journal `COMPLETED`;
25. release lease and remove only safe completed transient staging.

If a step fails, Core returns a structured transaction or recovery result. It does
not infer success from an exception message.

## 26. Ordering rationale

Documentation replacement occurs before APPLIED control commit.

If control were committed first and the document write failed, the audit contract
would falsely claim APPLIED. With document-first ordering:

- a crash before document replacement can roll back safely;
- a crash after document replacement can deterministically roll forward when the
  document matches the recorded result;
- APPLIED is published only after the result bytes exist.

The short intermediate state is durable `APPLYING`, never false `APPLIED`.

## 27. Idempotent apply

Apply request includes:

```text
expectedProjectId
proposalId
expectedBundlePayloadSha256
expectedLifecycleGeneration (recommended)
DocumentationResource
```

Results include:

```text
Applied
AlreadyApplied
Pending
Conflict
InvalidAggregate
RecoveryRequired
Failed
```

`AlreadyApplied` is returned only when:

- Lifecycle is APPLIED or archived-from-APPLIED;
- referenced Receipt is valid;
- Receipt proposal and Bundle SHA match the request;
- Receipt result SHA matches the current documentation resource;
- accepted/rejected/operation evidence validates against the stored Bundle.

It returns the existing exact Receipt. It never writes a second Receipt or
rewrites the document.

If Lifecycle is APPLIED but current documentation no longer matches the Receipt
result, Core returns `POST_APPLY_DOCUMENT_CHANGED`. It does not overwrite later
user changes.

## 28. Decision updates and lifecycle

Decisions may be recorded only while Lifecycle is ACTIVE.

The aggregate decision update:

1. loads Bundle and Lifecycle;
2. validates expected Bundle payload and lifecycle generation;
3. creates the updated Review Bundle v1 using its existing codec;
4. creates the next ACTIVE lifecycle generation binding the new payload SHA;
5. publishes the Bundle replacement and lifecycle generation through a durable
   aggregate update Journal;
6. recovers any crash before exposing the aggregate.

Readers never return a usable ACTIVE aggregate whose observed Bundle SHA differs
from the actual Bundle SHA.

APPLYING, APPLIED, SUPERSEDED, ARCHIVED, and RECOVERY_REQUIRED reject decisions.

## 29. Creation transaction

`prepareAndSave` becomes aggregate creation:

1. create unchanged Review Bundle v1;
2. create ACTIVE Lifecycle generation 1;
3. write both into temporary/staged locations;
4. validate cross-identities;
5. publish Bundle;
6. publish lifecycle `CURRENT`;
7. complete the creation Journal.

A crash after Bundle publication but before Lifecycle publication is recovered by
the creation Journal. A Bundle without valid Lifecycle is not exposed as an ACTIVE
aggregate.

Legacy valid Review Bundle v1 without Lifecycle is a legacy state, not corrupt.
Explicit adoption may create ACTIVE generation 1 only when:

- Bundle is valid;
- no lifecycle/control state or transaction exists;
- it has not been externally marked applied;
- the caller explicitly requests adoption.

No automatic migration write occurs on read.

## 30. Supersession

Supersession request provides:

```text
source proposal ID
successor proposal ID
expected source generation
expected source Bundle SHA
```

Rules:

- both aggregates belong to the same project;
- source is ACTIVE;
- successor is valid and ACTIVE;
- IDs differ;
- source moves to SUPERSEDED with the successor ID;
- successor is not mutated;
- supersession is deterministic and optimistic;
- superseded apply and decision updates fail;
- no Bundle or documentation file is deleted.

## 31. Archive

Archive is an explicit metadata transition:

- permitted from ACTIVE, APPLIED, or SUPERSEDED;
- retains Bundle, all Lifecycle generations, Receipt, and completed Journals
  required for verification;
- records `archivedFrom`;
- forbids future decision, apply, and supersession transitions;
- does not move or delete files in format 1;
- has no retention timer.

Archive reason text is excluded from the canonical v1 contract to avoid
non-deterministic or sensitive identity input. A future annotation contract may
be added separately.

## 32. Crash recovery

Every aggregate load and mutation first checks for an incomplete Journal.

Recovery by phase:

### 32.1 PREPARED

- if document SHA equals apply input: discard prepared temp, return Lifecycle to
  ACTIVE, mark transaction completed as rolled back;
- if document SHA equals result: advance to DOCUMENT_REPLACED and roll forward;
- otherwise transition to RECOVERY_REQUIRED.

### 32.2 DOCUMENT_REPLACED

- require document SHA equals result;
- validate staged Receipt and APPLIED Lifecycle;
- atomically publish control generation;
- advance to CONTROL_COMMITTED;
- otherwise RECOVERY_REQUIRED.

### 32.3 CONTROL_COMMITTED

- require current lifecycle and Receipt match Journal;
- require document SHA equals result;
- mark COMPLETED;
- mismatch becomes RECOVERY_REQUIRED without destructive repair.

### 32.4 COMPLETED

- verify current aggregate;
- safe staging cleanup may run;
- result is APPLIED or ALREADY_APPLIED.

### 32.5 RECOVERY_REQUIRED

- automatic mutation stops;
- return exact structured mismatch evidence;
- explicit recovery request must provide the observed documentation identity and
  expected resolution;
- Core validates whether rollback or roll-forward is safe.

## 33. Recovery safety

Recovery never:

- guesses from modification time;
- trusts lifecycle without Receipt;
- trusts Receipt without Bundle;
- overwrites document bytes matching neither input nor result;
- deletes an unknown temporary file;
- accepts a changed Bundle payload;
- retries provider generation;
- changes decisions;
- invokes CLI or MCP business logic.

## 34. Proposal-scoped concurrency

The local repository uses a proposal-scoped exclusive lease for mutations.

Lease requirements:

- bounded to one proposal control directory;
- acquired before recovery and held through transaction commit;
- does not depend only on an in-process mutex;
- stale lease handling is based on Journal identity and filesystem ownership, not
  elapsed wall-clock time alone;
- lock acquisition failure returns conflict;
- readers either recover under lease or return structured busy/recovery status.

Optimistic expected Bundle SHA and lifecycle generation remain mandatory even
with a lease.

## 35. Control generation validation

Load algorithm:

1. validate proposal ID before path resolution;
2. read `CURRENT` as strict relative generation name;
3. forbid traversal, absolute paths, symlinks outside control root, and devices;
4. load exact Lifecycle;
5. if state is APPLIED or archived-from-APPLIED, load exact Receipt in the same
   generation;
6. load unchanged Review Bundle v1;
7. validate project/proposal identities;
8. validate actual Bundle payload equals Lifecycle observed payload;
9. validate lifecycle/receipt integrity;
10. validate Receipt against Bundle proposal and decisions;
11. check active Journal and recover or return structured state.

No partially validated model is returned.

## 36. Canonical JSON rules

Lifecycle, Receipt, and Journal use:

- UTF-8;
- LF newlines;
- one final newline;
- fixed object field order;
- fixed enum spellings;
- arrays in contract-defined order;
- deterministic escaping;
- lowercase SHA-256;
- base-10 integers without leading zeros;
- explicit null for nullable fields;
- no unknown or duplicate fields;
- no trailing JSON;
- no Unicode normalization;
- no map iteration dependency.

Equivalent models produce byte-identical bytes.

## 37. Offline verification

Core exposes:

```kotlin
public interface ReviewAuditVerifier {
    public fun verify(
        bundle: StoredReviewBundle,
        lifecycle: ReviewLifecycleMetadata,
        receipt: ApplyReceipt?,
        journal: ReviewApplyTransaction?,
        documentationIdentity: DocumentationResourceIdentity? = null,
    ): ReviewAuditVerificationResult
}
```

Offline verification checks:

- each format/version and integrity;
- all project/proposal/Bundle identities;
- legal lifecycle state and generation;
- Receipt identity and operation projection;
- accepted/rejected completeness;
- Journal phase and lifecycle link;
- optional current documentation SHA;
- APPLIED always has one matching Receipt;
- no unexpected Receipt exists for non-applied state.

No provider, project analysis, CLI, MCP, Git, network, or current time is required.

## 38. Public Core results

Structured failures include:

```text
NOT_FOUND
LEGACY_BUNDLE_REQUIRES_ADOPTION
UNSUPPORTED_LIFECYCLE_VERSION
UNSUPPORTED_RECEIPT_VERSION
UNSUPPORTED_TRANSACTION_VERSION
CORRUPTED_LIFECYCLE
CORRUPTED_RECEIPT
CORRUPTED_TRANSACTION
PROJECT_MISMATCH
PROPOSAL_MISMATCH
BUNDLE_CHANGED
LIFECYCLE_CHANGED
ILLEGAL_TRANSITION
REVIEW_PENDING
STALE_DOCUMENTATION
POST_APPLY_DOCUMENT_CHANGED
TRANSACTION_BUSY
DOCUMENT_PREPARE_FAILED
DOCUMENT_COMMIT_FAILED
CONTROL_COMMIT_FAILED
RECOVERY_REQUIRED
```

Adapters map these results exhaustively. They do not parse message text.

## 39. CLI boundary

CLI remains a thin adapter.

Permitted CLI responsibilities:

- parse arguments;
- resolve project, Bundle, and documentation paths;
- create Core repository and DocumentationResource adapters;
- invoke Core lifecycle/status/apply/recover APIs;
- map structured Core results to stable output and exit codes;
- display existing Receipt fields.

Forbidden CLI responsibilities:

- choose lifecycle transitions;
- create transaction or receipt IDs;
- calculate receipt operation evidence;
- write Lifecycle/Receipt/Journal JSON directly;
- decide idempotency;
- infer crash recovery;
- mark APPLIED after its own file write;
- mutate Review Bundle after terminal state;
- repair mismatched documentation.

RFC-0050 does not require new interactive commands. Necessary additive CLI output
or status mapping may be implemented as thin exposure only.

## 40. MCP boundary

MCP remains optional orchestration, never the system of record.

MCP may in future:

- query Core lifecycle status;
- request a Core operation;
- report structured Core results.

MCP may not:

- persist duplicate lifecycle or receipt state;
- own transition policy;
- manufacture a Receipt;
- bypass the Core transaction;
- become a Core runtime dependency.

No MCP source or state change is required by RFC-0050.

## 41. Privacy and security

Lifecycle, Receipt, and Journal deliberately exclude:

- Markdown content;
- decision comments;
- environment variables;
- provider credentials;
- user identity;
- hostnames;
- absolute paths;
- timestamps.

Review Bundle v1 retains its existing Markdown/comment privacy characteristics.

SHA-256 proves integrity, not authorship. Digital signatures remain separately
deferred.

## 42. Versioning and compatibility

Independent contracts:

| Contract | Version |
| --- | ---: |
| Review Bundle | 1, unchanged |
| Lifecycle Metadata | 1 |
| Apply Receipt | 1 |
| Apply Transaction Journal | 1 |

Rules:

- each decoder rejects unsupported versions;
- one contract version change does not automatically change another;
- existing Bundle v1 remains readable;
- legacy Bundle adoption is explicit and no-write-on-read;
- Receipt v1 always references Bundle format 1;
- DIR `0.3`, Snapshot `1`, CLI JSON `1`, and Release Evidence `1` remain unchanged.

## 43. Expected implementation areas

Core production:

```text
src/main/kotlin/io/docpilot/core/incremental/specification/review/
  ReviewLifecycleModels.kt
  ReviewLifecycleCodec.kt
  ApplyReceiptModels.kt
  ApplyReceiptCodec.kt
  ReviewApplyTransactionModels.kt
  ReviewApplyTransactionCodec.kt
  ReviewLifecycleRepository.kt
  FileReviewLifecycleRepository.kt
  ReviewLifecycleService.kt
  ReviewApplyTransactionCoordinator.kt
  ReviewAuditVerifier.kt
  DocumentationResource.kt
```

Existing integration:

```text
PersistentDocumentationReviewWorkflow.kt
ReviewBundleRepository.kt only through aggregate integration
ReviewBundleCodec.kt compatibility only; format bytes unchanged
```

Thin adapter, only where required:

```text
docpilot-cli/.../AtomicDocumentationFileWriter.kt
docpilot-cli/.../ReviewCommand.kt
```

No MCP source/test change is authorized.

## 44. Test plan

### 44.1 Lifecycle model and codec

- every legal state and invariant;
- every illegal transition;
- deterministic generations and payload SHA;
- duplicate/unknown/trailing field rejection;
- unsupported version;
- Unicode and escape handling;
- exact bytes and final newline.

### 44.2 Receipt

- UPSERT, REMOVE, mixed accepted/rejected;
- deterministic ID and bytes;
- Bundle/comment change alters bound payload;
- operation hash projection;
- missing/duplicate/unknown targets;
- forged Receipt ID;
- tampered result/input SHA;
- immutable repository collision.

### 44.3 Review Bundle compatibility

- existing RFC-0047 Bundle fixtures remain byte-identical;
- Bundle decoder/encoder unchanged;
- decision updates remain v1;
- legacy Bundle explicit adoption;
- no lifecycle field accepted by Bundle codec.

### 44.4 Aggregate repository

- create Bundle plus ACTIVE lifecycle;
- decision update binds new Bundle SHA;
- stale Bundle and generation conflicts;
- atomic control generation switch;
- APPLIED never visible without Receipt;
- invalid `CURRENT` pointer;
- traversal/symlink/device rejection;
- concurrent mutation;
- owned staging cleanup only.

### 44.5 Apply transaction

- full successful apply;
- accepted REMOVE;
- pending review;
- stale input;
- Bundle changed;
- lifecycle changed;
- documentation prepare failure;
- document changes before commit;
- control generation failure;
- exact repeated apply returns AlreadyApplied;
- post-apply document change does not reapply.

### 44.6 Crash injection

Inject failure after every durable step:

```text
after document preparation
after PREPARED Journal
after APPLYING pointer
after document replacement
after DOCUMENT_REPLACED Journal
after generation files
after CURRENT pointer
after CONTROL_COMMITTED Journal
before/after COMPLETED
```

Restart with new Core/repository instances and verify deterministic recovery.

### 44.7 Recovery ambiguity

- document equals input -> safe rollback;
- document equals result -> safe roll-forward;
- document equals neither -> RECOVERY_REQUIRED;
- Receipt/lifecycle mismatch;
- Bundle mutation during recovery;
- missing staged resource;
- repeated recovery idempotency.

### 44.8 Supersession and archive

- valid successor;
- cross-project successor;
- self-supersession;
- superseded apply rejection;
- archive from ACTIVE/APPLIED/SUPERSEDED;
- archive from APPLYING rejection;
- archived mutation rejection;
- no file deletion.

### 44.9 Offline verification

- valid ACTIVE and APPLIED aggregates;
- optional document identity match/mismatch;
- corrupt each contract independently;
- unsupported independent versions;
- no provider/network dependency;
- byte-identical repeated report.

### 44.10 Regression

- RFC-0046 removal semantics;
- RFC-0047 Bundle persistence/integrity/resume;
- RFC-0048 CLI thin workflow and exit contracts;
- RFC-0049 release evidence module;
- full clean build/test;
- isolated architecture-samples smoke;
- `git diff --check`;
- MCP and protected-path review.

## 45. Completion criteria

RFC-0050 is complete only when:

1. Review Bundle format 1 bytes and semantics remain unchanged;
2. Lifecycle Metadata format 1 is implemented and deterministic;
3. Apply Receipt format 1 is immutable, deterministic, and Bundle-bound;
4. Journal format 1 and transaction IDs are deterministic;
5. legal lifecycle transitions are exclusively Core-owned;
6. decision updates are allowed only in ACTIVE;
7. APPLIED is never visible without one valid matching Receipt;
8. Receipt and APPLIED lifecycle use one atomic control generation switch;
9. documentation replacement uses a Core DocumentationResource port;
10. cross-resource apply is recoverable and fail-closed;
11. crash injection after every durable step recovers deterministically;
12. ambiguous document bytes produce RECOVERY_REQUIRED without overwrite;
13. exact repeated apply returns the same Receipt without mutation;
14. post-apply user changes are never overwritten by idempotent retry;
15. supersession and archive follow closed transition rules;
16. archive deletes nothing;
17. legacy Bundle adoption is explicit and no-write-on-read;
18. offline verification needs no provider, CLI, MCP, Git, or network;
19. CLI and MCP remain thin adapters;
20. Core has no CLI/MCP/provider/release-tool dependency;
21. DIR, Snapshot, CLI JSON, and Release Evidence versions remain compatible;
22. targeted, crash, concurrency, regression, build, test, and smoke checks pass;
23. Canonical RFC, Planning, Handoff, and Roadmap match actual evidence.

## 46. Risks

- Cross-filesystem atomicity can be overstated unless Journal semantics are clear.
- A control pointer fallback can expose torn state if not recovery-tested.
- Document-first ordering requires reliable roll-forward after replacement.
- Bundle decision updates and Lifecycle binding need aggregate-level recovery.
- Proposal-scoped leases can become stale after process death.
- Receipt operation projection can diverge from actual merger behavior.
- Legacy Bundle adoption can falsely classify externally applied reviews.
- Durable generations increase storage without retention cleanup.

Every risk requires explicit fail-closed tests or a documented limitation.

## 47. Deferred follow-up

Not approved by RFC-0050:

- digital signatures or authenticated reviewer identity;
- Signed Release Evidence and external attestation;
- remote/multi-user review;
- automatic retention or deletion;
- encrypted Review Bundles;
- UI/TUI;
- MCP lifecycle persistence;
- Git/release automation;
- Review Bundle format 2.

## 48. Decision record

Approved RFC:

```text
RFC-0050
Review Bundle Lifecycle and Apply Receipt
```

Approved contracts:

- Review Bundle format 1 unchanged;
- Lifecycle Metadata format 1, separate;
- Apply Receipt format 1, separate;
- Apply Transaction Journal format 1, separate.

Approved atomicity:

- Receipt and APPLIED Lifecycle are strictly atomically visible through one
  immutable control generation and atomic `CURRENT` pointer;
- documentation plus audit state use a durable recoverable transaction because a
  portable multi-filesystem atomic write cannot be guaranteed.

Approved architecture:

- Core owns every lifecycle, receipt, idempotency, and recovery rule;
- CLI and MCP remain thin adapters;
- offline verification is a Core capability;
- no automatic deletion, Git action, or release action.

This approves the detailed specification. It does not approve implementation,
commit, merge, push, tag, or release.
