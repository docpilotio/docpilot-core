# RFC-0051: Official Review Lifecycle Operations and Recovery CLI

## Status

Implemented and locally verified.

Plan A was selected by the user on July 25, 2026. Focused Core/CLI tests, clean
build, and the full 301-test regression suite pass. Main integration, push, tag,
and release are recorded separately and have not been performed by this
implementation worktree.

## 1. Purpose

RFC-0050 established Core-owned Lifecycle Metadata, Apply Receipt, Apply
Transaction Journal, idempotent apply, deterministic recovery, supersession,
archive, and offline verification.

RFC-0051 exposes those contracts through the official DocPilot CLI:

```text
docpilot review lifecycle status
docpilot review lifecycle recover
docpilot review lifecycle verify
docpilot review lifecycle supersede
docpilot review lifecycle archive
```

The CLI remains a thin adapter. It resolves user input, invokes Core, and renders
Core results. It does not interpret lifecycle state, select a recovery action,
validate a transition, calculate contract integrity, or construct a Receipt.

All state-changing commands are safe by default:

```text
no --confirm
    -> deterministic Core dry-run plan
    -> no durable mutation

--confirm
    -> Core revalidates the exact plan boundary
    -> mutation only if the boundary is unchanged
```

## 2. Product outcome

An operator or automation process can:

1. inspect the authoritative lifecycle aggregate;
2. verify Lifecycle, Bundle, Receipt, and Journal integrity offline;
3. preview exact recovery behavior without changing state;
4. explicitly confirm a recoverable transaction;
5. preview and confirm supersession;
6. preview and confirm archive;
7. consume stable text, JSON, and exit-code contracts;
8. prove which lifecycle generation and payload were inspected or changed.

No command requires an AI provider, network access, MCP, UI, or interactive
prompt.

## 3. Baseline

- Core repository: `C:\WorkSpace\docpilot-core`
- local main baseline: `60704a254f7d90a0ea9c00e9490d06bf6e917b26`
- RFC-0050 feature commit:
  `4bfa654e5318cc7a5b6bb995f30612cc445ae79c`
- RFC-0050 main merge commit: `0f6b15d`
- Review Bundle format: `1`, unchanged
- Lifecycle Metadata format: `1`, unchanged
- Apply Receipt format: `1`, unchanged
- Apply Transaction Journal format: `1`, unchanged
- CLI JSON envelope: `1`, extended compatibly
- verified baseline: 96 XML files, 291 tests, 0 failures, 0 errors, 0 skipped

`origin/main` is behind local main at specification time. Remote synchronization
is not part of this RFC.

## 4. Scope

RFC-0051 includes:

- `review lifecycle status`;
- `review lifecycle verify`;
- `review lifecycle recover`;
- `review lifecycle supersede`;
- `review lifecycle archive`;
- Core-owned lifecycle inspection result;
- Core-owned deterministic operation planning;
- Core-owned confirm-time revalidation;
- default dry-run behavior for every mutation;
- explicit `--confirm`;
- optional `--dry-run` for automation readability;
- stable text and JSON output;
- stable exit-code mapping;
- proposal and exact Bundle selection;
- lifecycle generation, Bundle SHA, Plan SHA, transaction ID, and Receipt ID
  output when applicable;
- documentation resource selection for recovery;
- focused Core and CLI tests;
- isolated filesystem smoke;
- canonical planning, roadmap, and handoff synchronization.

## 5. Non-goals

RFC-0051 does not:

- change Review Bundle, Lifecycle Metadata, Receipt, or Journal format versions;
- add new lifecycle states;
- alter apply or managed-block semantics;
- add interactive confirmation prompts;
- infer approval from stdin, TTY state, environment variables, or CI detection;
- add UI, TUI, or web interfaces;
- add MCP tools or MCP-owned state;
- add remote review synchronization;
- add cross-process leases;
- add automatic retention or deletion;
- add cryptographic identity or signatures;
- add batch lifecycle mutation;
- add automatic recovery on `status` or `verify`;
- push, merge, tag, or release.

## 6. Architecture boundary

### 6.1 CLI owns

- token and option parsing;
- required/mutually-exclusive option validation;
- path normalization;
- project identity loading through existing adapters;
- construction of Core request values;
- invocation of Core services;
- deterministic rendering;
- stable exit-code mapping;
- mapping local documentation paths to `DocumentationResource`.

### 6.2 Core owns

- lifecycle aggregate loading;
- lifecycle state interpretation;
- transition eligibility;
- recovery classification;
- input/result/neither hash classification;
- lifecycle generation conflict detection;
- Bundle payload binding;
- Journal and Receipt validation;
- dry-run operation construction;
- operation Plan identity;
- confirm-time revalidation;
- lifecycle mutation;
- document mutation during recovery;
- Receipt/APPLIED atomic publication;
- offline verification;
- supersession and archive rules;
- all failure reasons exposed to adapters.

### 6.3 Forbidden CLI behavior

The CLI must not:

```text
switch on ReviewLifecycleState to decide an operation
select roll-forward or rollback
compare documentation SHA values for lifecycle decisions
parse lifecycle.json, receipt.json, journal.json, or CURRENT
calculate lifecycle, Receipt, Journal, or Plan integrity
construct a lifecycle generation
call repository.transition directly
decide whether supersede or archive is valid
convert a Core conflict into success
mutate documentation outside a Core-confirmed recovery
write lifecycle control files
silently adopt a missing lifecycle
```

The CLI may display enum names and structured data already returned by Core. That
presentation mapping is not lifecycle interpretation.

## 7. Core operation API

RFC-0051 introduces an application-level Core boundary. Names may vary during
implementation, but responsibilities must remain equivalent:

```kotlin
interface ReviewLifecycleOperations {
    fun status(request: LifecycleStatusRequest): LifecycleStatusResult
    fun verify(request: LifecycleVerifyRequest): LifecycleVerifyResult

    fun planRecovery(request: RecoveryPlanRequest): LifecycleOperationPlanResult
    fun confirmRecovery(request: ConfirmRecoveryRequest): LifecycleOperationResult

    fun planSupersede(request: SupersedePlanRequest): LifecycleOperationPlanResult
    fun confirmSupersede(request: ConfirmSupersedeRequest): LifecycleOperationResult

    fun planArchive(request: ArchivePlanRequest): LifecycleOperationPlanResult
    fun confirmArchive(request: ConfirmArchiveRequest): LifecycleOperationResult
}
```

The CLI depends on this boundary, not on `FileReviewLifecycleRepository`
transition methods.

## 8. Lifecycle operation plan

### 8.1 Purpose

Dry-run is not a CLI-generated explanation. It is a Core result describing the
exact mutation Core would attempt against the observed aggregate.

Conceptual model:

```kotlin
data class ReviewLifecycleOperationPlan(
    val planFormatVersion: Int,
    val action: LifecycleOperationAction,
    val projectId: String,
    val proposalId: String,
    val observedBundlePayloadSha256: String,
    val observedLifecycleGeneration: Long,
    val observedLifecycleState: ReviewLifecycleState,
    val expectedResultState: ReviewLifecycleState,
    val transactionId: String?,
    val receiptId: String?,
    val replacementProposalId: String?,
    val recoveryDisposition: RecoveryDisposition?,
    val documentInputSha256: String?,
    val documentResultSha256: String?,
    val planSha256: String,
)
```

### 8.2 Action

```text
RECOVER
SUPERSEDE
ARCHIVE
```

### 8.3 Recovery disposition

Only Core can produce:

```text
ROLL_FORWARD_APPLIED
ROLL_BACK_ACTIVE
ALREADY_APPLIED
BLOCK_RECOVERY_REQUIRED
```

`BLOCK_RECOVERY_REQUIRED` is a non-executable plan. `--confirm` must return the
same fail-closed result and perform no mutation unless the observed aggregate has
changed and a new plan now permits an exact action.

### 8.4 Plan identity

`planSha256` is deterministic over all semantic fields using a Core-owned
canonical representation.

The Plan is an operation concurrency token, not a new long-term stored contract.
Plan format version `1` stabilizes calculation and CLI automation within this
command contract but does not require a new durable file.

No timestamps, absolute paths, process IDs, locale-dependent values, or map
iteration order participate in Plan identity.

## 9. Confirm-time safety

`--confirm` does not mean “execute whatever is currently possible.”

Core must:

1. create or receive the expected operation Plan boundary;
2. reload the authoritative aggregate;
3. revalidate project ID, proposal ID, Bundle SHA, lifecycle generation, state,
   transaction ID, Receipt ID, replacement proposal, and relevant document hash;
4. recompute the operation Plan;
5. require the recomputed Plan SHA to equal the expected Plan SHA when supplied;
6. execute only the exact revalidated action;
7. return a conflict without mutation if any boundary changed.

CLI invocation supports:

```text
--plan-sha256 <sha>
```

For manual use, `--confirm` may omit `--plan-sha256`; Core still plans and
revalidates within the same invocation. Automation should pass the Plan SHA from
the prior dry-run to prevent approval drift.

The CLI must not implement these comparisons.

## 10. Command grammar

```text
docpilot review lifecycle <subcommand> [options]
```

Subcommands:

```text
status
verify
recover
supersede
archive
```

Missing or unknown lifecycle subcommands return `CLI_USAGE_ERROR`.

## 11. Common selection options

Every command requires:

```text
--project <path>
```

Every command selects a proposal using exactly one:

```text
--proposal <proposal-id>
--bundle <path>
```

All commands support:

```text
--json
```

Selection behavior reuses RFC-0048:

- `--proposal` resolves the default Bundle;
- `--bundle` selects an exact Bundle;
- Bundle decoding and identity validation use Core contracts;
- lifecycle control remains rooted in the selected project;
- Bundle content cannot redirect lifecycle storage.

## 12. `review lifecycle status`

### 12.1 Syntax

```text
docpilot review lifecycle status \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  [--documentation <path>] \
  [--json]
```

### 12.2 Behavior

Read-only. It must not:

- recover an incomplete transaction;
- adopt missing lifecycle state;
- rewrite `CURRENT`;
- repair integrity;
- update a Journal;
- mutate documentation.

Core returns:

- lifecycle presence and validity;
- authoritative generation and state;
- observed Bundle SHA match;
- active transaction identity and phase when valid;
- Receipt identity and result document SHA when present;
- optional documentation relationship:
  `MATCHES_INPUT`, `MATCHES_RESULT`, `OTHER`, or `NOT_PROVIDED`;
- available operation names as Core-derived informational data.

“Available operation” output is not permission to execute without a new plan and
confirm-time revalidation.

## 13. `review lifecycle verify`

### 13.1 Syntax

```text
docpilot review lifecycle verify \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  [--documentation <path>] \
  [--json]
```

### 13.2 Verification

Core verifies offline:

- Bundle format, identity, and integrity;
- Lifecycle Metadata format and integrity;
- CURRENT pointer safety;
- selected generation identity;
- Bundle/Lifecycle payload binding;
- APPLIED/Receipt co-visibility;
- Receipt identity and integrity;
- Receipt/Bundle/document hash binding;
- Journal identity and integrity when present;
- transaction/lifecycle cross-binding;
- immutable generation naming and payload identity;
- optional current documentation relationship.

Verification is read-only and performs no repair.

## 14. `review lifecycle recover`

### 14.1 Syntax

```text
docpilot review lifecycle recover \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  --documentation <path> \
  [--dry-run | --confirm] \
  [--plan-sha256 <sha>] \
  [--json]
```

### 14.2 Default

Omitting both flags is equivalent to `--dry-run`.

`--dry-run` and `--confirm` are mutually exclusive.

`--plan-sha256` requires `--confirm`.

### 14.3 Core planning

Core alone determines:

- result bytes + valid staged Receipt: roll forward to APPLIED;
- input bytes + PREPARED Journal: roll back to ACTIVE;
- exact APPLIED result: idempotent ALREADY_APPLIED;
- neither hash or contradictory evidence: fail closed;
- missing/tampered evidence: invalid or recovery required.

Dry-run performs no durable write, including no transition to
`RECOVERY_REQUIRED`. It reports what confirm would do or why it is blocked.

### 14.4 Confirm

Confirm delegates the exact revalidated operation to Core. Successful output
includes the prior and resulting generation/state and Receipt ID when applicable.

## 15. `review lifecycle supersede`

### 15.1 Syntax

```text
docpilot review lifecycle supersede \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  --replacement-proposal <id> \
  [--dry-run | --confirm] \
  [--plan-sha256 <sha>] \
  [--json]
```

### 15.2 Rules

Core validates:

- current state is eligible;
- replacement proposal syntax and inequality;
- replacement Bundle existence, identity, and integrity if required by the final
  Core contract;
- current generation and observed Bundle SHA;
- no incomplete recovery transaction permits supersession;
- resulting metadata binding.

CLI validates only option presence and shape required to construct the request.

Default is dry-run. Confirmation revalidates the complete Plan boundary.

## 16. `review lifecycle archive`

### 16.1 Syntax

```text
docpilot review lifecycle archive \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  [--dry-run | --confirm] \
  [--plan-sha256 <sha>] \
  [--json]
```

Core determines archive eligibility and preserves `archivedFrom`.

Archive does not delete:

- Bundle;
- generations;
- Receipt;
- Journal or recovery evidence;
- documentation.

Default is dry-run. Confirmation revalidates the complete Plan boundary.

## 17. Dry-run contract

Successful executable dry-run output status:

```text
DRY_RUN_READY
```

Successful no-op dry-run output:

```text
DRY_RUN_NO_CHANGE
```

Blocked dry-run output:

```text
DRY_RUN_BLOCKED
```

All three are Core result categories.

`DRY_RUN_BLOCKED` is not a CLI usage error. It uses a stable non-zero operational
exit code.

Dry-run must be side-effect-free with respect to:

- Bundle;
- lifecycle generations;
- CURRENT pointer;
- Receipt;
- Journal;
- documentation;
- project state;
- MCP runtime.

## 18. Stable CLI output

Every lifecycle command prints when available:

```text
Command
Status
Proposal ID
Bundle Path
Bundle Payload SHA-256
Lifecycle Generation
Lifecycle State
Plan SHA-256
Transaction ID
Receipt ID
Exit Code
```

Mutation result additionally prints:

```text
Previous Generation
Previous State
Result Generation
Result State
Mutation Performed
```

Unavailable values are explicit `unavailable`, not omitted from text output.

## 19. JSON envelope

RFC-0048 envelope version remains `1`.

Conceptual success example:

```json
{
  "formatVersion": 1,
  "command": "review lifecycle recover",
  "status": "DRY_RUN_READY",
  "exitCode": 0,
  "proposalId": "review:<sha256>",
  "bundlePath": "<absolute-normalized-path>",
  "payloadSha256": "<sha256>",
  "data": {
    "lifecycleGeneration": 3,
    "lifecycleState": "APPLYING",
    "action": "RECOVER",
    "recoveryDisposition": "ROLL_FORWARD_APPLIED",
    "expectedResultState": "APPLIED",
    "planSha256": "<sha256>",
    "transactionId": "transaction:<sha256>",
    "receiptId": "receipt:<sha256>",
    "mutationPerformed": false
  },
  "error": null
}
```

Field order is deterministic. Unknown future `data` fields must not change the
meaning of existing fields.

## 20. Stable exit codes

Existing RFC-0048 meanings remain unchanged.

RFC-0051 uses:

| Code | Meaning |
| ---: | --- |
| 0 | success, verified, dry-run ready/no-change, or confirmed mutation completed |
| 2 | CLI usage error |
| 3 | operation not currently applicable or dry-run blocked |
| 4 | stale lifecycle/Bundle/document/Plan conflict |
| 5 | invalid Bundle, lifecycle, Receipt, Journal, or integrity |
| 8 | documentation or control storage I/O failure |
| 9 | recovery required or ambiguous recovery evidence |
| 70 | unexpected internal CLI failure |

Core returns semantic result reasons. CLI maps those reasons to this table
without reclassifying lifecycle facts.

## 21. Error contract

Stable status names include:

```text
LIFECYCLE_STATUS
VERIFIED
DRY_RUN_READY
DRY_RUN_NO_CHANGE
DRY_RUN_BLOCKED
RECOVERED
ALREADY_APPLIED
SUPERSEDED
ARCHIVED
STALE_PLAN
RECOVERY_REQUIRED
INVALID_LIFECYCLE
INVALID_RECEIPT
INVALID_JOURNAL
INTEGRITY_FAILURE
STORAGE_FAILURE
CLI_USAGE_ERROR
INTERNAL_ERROR
```

Failure output must retain proposal, Bundle path, payload SHA, and Plan SHA when
they were safely established.

## 22. Determinism

Given the same Bundle bytes, lifecycle generation, transaction evidence, Receipt,
optional documentation bytes, and request:

- Core Plan fields are identical;
- Plan SHA is identical;
- text output is identical except absolute normalized paths supplied by the
  environment;
- JSON field order and values are identical;
- exit code is identical.

Directory iteration order, locale, timezone, process ID, random UUID, and wall
clock must not affect results.

## 23. Security and safety

- proposal IDs must retain safe fixed syntax;
- Bundle path cannot redirect control storage;
- control paths cannot escape the project root;
- symlink/reparse-point behavior must fail closed where path identity is
  ambiguous;
- `--confirm` is a literal explicit flag and cannot be inferred;
- dry-run never writes;
- Plan SHA mismatch never falls back to a fresh unapproved action;
- invalid integrity never triggers repair;
- archive never deletes;
- recovery never overwrites third-state documentation.

## 24. Compatibility

RFC-0051 preserves:

- Review Bundle format 1;
- Lifecycle Metadata format 1;
- Apply Receipt format 1;
- Apply Transaction Journal format 1;
- DIR schema 0.3;
- Specification Snapshot format 1;
- managed-block semantics;
- apply idempotency;
- RFC-0048 existing commands and exit meanings;
- provider SPI;
- MCP independence.

New lifecycle subcommands are additive.

## 25. Testing

### 25.1 Core tests

- deterministic operation Plan and Plan SHA;
- status for every lifecycle state;
- verify valid and tampered aggregate variants;
- recovery plan input/result/neither matrix;
- dry-run proves zero writes;
- confirm revalidation and stale Plan rejection;
- supersede eligibility and stale generation;
- archive eligibility and evidence preservation;
- idempotent already-applied recovery;
- storage failure classification.

### 25.2 CLI tests

- grammar and help for all five commands;
- default dry-run;
- explicit `--dry-run`;
- `--confirm`;
- mutual-exclusion and Plan SHA usage errors;
- text and JSON golden output;
- stable exit codes;
- exact proposal/Bundle selection;
- command restart behavior;
- no direct lifecycle JSON parsing or transition invocation in CLI.

### 25.3 Smoke

Use an isolated project fixture:

1. create a valid lifecycle aggregate;
2. inspect status;
3. verify offline;
4. create an incomplete apply boundary;
5. dry-run recovery and capture Plan SHA;
6. confirm with the captured Plan SHA;
7. verify APPLIED and Receipt;
8. create another active review;
9. dry-run and confirm supersede;
10. dry-run and confirm archive;
11. prove no original architecture-samples files changed.

## 26. Completion criteria

RFC-0051 is complete only when:

- all five commands are implemented;
- Core provides inspection, planning, and confirmed execution boundaries;
- CLI contains no lifecycle state-machine decisions;
- mutation defaults to dry-run;
- explicit confirmation is required;
- stale Plan and generation conflicts fail closed;
- status and verification are read-only;
- lifecycle/Receipt/Journal formats remain unchanged;
- targeted and full tests pass;
- isolated smoke passes;
- Handoff and Roadmap reflect actual evidence;
- no MCP source or project state is changed.

## 27. Known implementation considerations

RFC-0050 currently exposes direct `recover`, `supersede`, and `archive` methods.
Implementation should introduce the planning/revalidation application boundary
without duplicating those rules. Existing methods may become internal delegates
or remain public compatibility surfaces, but the CLI must use the new operations
boundary.

Status and verification may require richer Core result models so the CLI never
loads contract files directly.

Cross-process leases and retention remain deferred to Plan B.

## 28. Canonical sources

- `docs/rfc/RFC-0051-Official-Review-Lifecycle-Operations-and-Recovery-CLI.md`
- `docs/planning/RFC-0051-CANDIDATE-PLAN-A-OFFICIAL-LIFECYCLE-OPERATIONS-CLI.md`
- `docs/planning/RFC-0051-CANDIDATE-PLAN-B-CROSS-PROCESS-REVIEW-LEASES-AND-RETENTION.md`
- `docs/planning/RFC-0051-TWO-PLAN-SYNC-PACKET.md`
- `docs/planning/RFC-0051-MAIN-PLANNING-UPDATE.md`
- `docs/roadmap/ROADMAP.md`
