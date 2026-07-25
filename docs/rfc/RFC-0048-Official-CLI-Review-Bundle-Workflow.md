# RFC-0048: Official CLI Review Bundle Prepare, Inspect, Status, Decide, and Apply Workflow

## Status

Implemented, locally verified, and integrated into local main. Remote delivery is
recorded separately from the implementation contract.

Verification evidence:

- focused CLI restart workflow, JSON identity, comment, and conflict tests: PASS;
- `clean build`: PASS;
- `clean test`: PASS;
- 89 test XML files, 273 tests, 0 failures, 0 errors, 0 skipped;
- isolated architecture-samples CLI smoke: PASS;
- Review Bundle format 1, DIR schema 0.3, and snapshot format 1 unchanged.

## 1. Purpose

RFC-0047 made Review Bundle format version 1 an official, Core-owned, durable
contract with deterministic identity, integrity protection, optimistic updates,
restart-safe decisions, and stale-document apply protection.

RFC-0048 exposes that capability through the official DocPilot CLI.

The CLI is a thin adapter:

```text
CLI arguments and filesystem locations
        ->
Core PersistentDocumentationReviewWorkflow
        ->
Core ReviewBundleRepository / codec / reviewer
        ->
stable CLI output and exit code
```

The CLI does not reimplement:

- Review Bundle encoding or decoding;
- proposal identity;
- payload integrity;
- project or specification identity;
- decision merge rules;
- complete-review rules;
- UPSERT/REMOVE validation;
- stale-document detection;
- managed-block transformation.

## 2. Product outcome

A developer can use independent CLI processes to:

1. prepare and persist a Review Bundle;
2. inspect the complete proposal and Evidence;
3. query automation-friendly status;
4. record or revise explicit decisions with comments;
5. apply a complete, valid, non-stale review;
6. consume stable text or JSON results and exit codes.

Every successful command identifies the exact proposal, bundle file, and payload
version used.

## 3. Scope

RFC-0048 includes:

- `review prepare`;
- `review inspect`;
- `review status`;
- `review decide`;
- `review apply`;
- default project Review Bundle discovery;
- explicit `--bundle <path>` selection;
- `--json` output for every review subcommand;
- `--comment` and `--comment-file`;
- stable automation exit codes;
- stable JSON output envelope version 1;
- deterministic text output;
- atomic documentation file replacement after Core-approved apply;
- CLI help and usage;
- CLI unit, integration, and isolated end-to-end tests.

## 4. Non-goals

RFC-0048 does not:

- change Review Bundle format version 1;
- make the CLI the owner of Review Bundle semantics;
- add interactive prompts, TUI, GUI, or web UI;
- add MCP commands, state, or dependency;
- add remote collaboration or synchronization;
- add authentication, authorization, reviewer identity, or signatures;
- add automatic approval;
- add batch decisions from an unspecified script format;
- add Review Bundle migration;
- delete documentation files;
- bypass RFC-0046 managed-block rules;
- bypass RFC-0047 integrity or stale checks;
- add Git commit, push, tag, or release behavior;
- change provider SPI, DIR schema, or specification snapshot format.

## 5. Baseline

- local main: `addb7ab39e572a5faf4758b782f5602220501087`;
- RFC-0047 feature commit: `d71ed979aff27c4a84bc5e462c7ee6f7384e463b`;
- RFC-0047 merge commit: `27ebe07fd4b5fa5484f6ab33a3b4462afc18c397`;
- Review Bundle format: `1`;
- DIR schema: `0.3`;
- specification snapshot format: `1`;
- verification baseline: 88 XML files, 270 tests, 0 failures, 0 errors, 0 skipped.

`origin/main` is behind local main at specification time. Remote synchronization
is not part of this RFC.

## 6. Thin-adapter boundary

CLI owns only:

- argument parsing;
- mutually exclusive and required option validation;
- project/document/bundle path resolution;
- provider and model selection for preparation;
- construction of Core requests;
- invocation of Core workflows and repositories;
- deterministic presentation;
- stable exit-code mapping;
- final documentation file I/O after Core returns an approved result.

Core owns:

- all Review Bundle models and invariants;
- bundle location safety contract;
- codec and format compatibility;
- integrity and proposal identity;
- decision validation and replacement;
- repository conflict handling;
- proposal completeness;
- stale-document conflict;
- accepted-only apply;
- UPSERT/REMOVE semantics;
- managed-block merger.

Forbidden CLI behavior:

```text
parse Review Bundle JSON directly
calculate or compare bundle payload checksums independently
construct proposal IDs
merge decision lists
infer pending/accepted/rejected targets
interpret REMOVE from empty Markdown
edit managed block markers
apply patches without Core resumeApply
convert invalid Core results into success
```

## 7. Command family

Top-level grammar:

```text
docpilot review <subcommand> [options]
```

Subcommands:

```text
prepare
inspect
status
decide
apply
```

Unknown or missing review subcommands return `CLI_USAGE_ERROR`.

## 8. Common options

All review commands support:

```text
--project <path>
--json
```

All commands except `prepare` support bundle selection:

```text
--proposal <proposal-id>
--bundle <path>
```

Selection rule:

- exactly one of `--proposal` or `--bundle` is required;
- `--proposal` resolves through the project default Review Bundle repository;
- `--bundle` selects one exact bundle file;
- using both fails with `CLI_USAGE_ERROR`;
- neither proposal ID nor bundle content may control an arbitrary parent directory.

`prepare` supports optional:

```text
--bundle <path>
```

When omitted, Core's default location is used:

```text
<project>/.docpilot/reviews/review-<proposal-hash>.json
```

## 9. Bundle path contract

`--bundle <path>` is an official location override, not an alternate format.

Rules:

- path is resolved to an absolute normalized path by the CLI;
- relative paths resolve against the current process directory;
- parent directories may be created only for `prepare`;
- inspect/status/decide/apply require an existing regular file;
- symlink behavior follows the filesystem adapter's explicit safe policy;
- directories, device files, or unreadable files fail;
- the selected file is still decoded and validated by Core's official codec;
- proposal ID is always read from and validated against the bundle;
- `decide` replaces the same selected file using Core expected-integrity rules;
- no command silently falls back from an explicit path to the default repository.

RFC-0048 may add a Core `ReviewBundleLocation` or exact-file repository adapter to
support this option. The adapter must reuse the Core codec and repository
validation. Implementing JSON or integrity logic in `docpilot-cli` is forbidden.

## 10. Mandatory command identity output

Every review command result contains:

```text
Proposal ID
Bundle Path
Payload SHA-256
```

This applies to:

- text output;
- JSON output;
- success;
- pending state;
- conflict or failure when the value is discoverable safely.

If a value cannot be established:

- text prints `unavailable`;
- JSON uses `null`.

Paths are absolute and normalized in output.

The payload SHA is:

- current stored payload SHA after `prepare` or successful `decide`;
- loaded source payload SHA for inspect/status/apply;
- actual current payload SHA on a bundle-update conflict when available.

## 11. `review prepare`

Candidate syntax:

```text
docpilot review prepare \
  --project <path> \
  --documentation <file> \
  --provider <id> \
  --model <model> \
  [--bundle <path>] \
  [--json]
```

Responsibilities:

1. validate arguments and paths;
2. load current project analysis using existing official Core/CLI bootstrap;
3. load the previous specification snapshot through the existing Core snapshot contract;
4. build the current specification and incremental plan;
5. read exact existing documentation bytes as UTF-8;
6. resolve provider/model using existing provider composition;
7. call Core `prepareAndSave`;
8. print proposal identity and saved location.

Rules:

- no changes returns success with status `NO_CHANGES` and no bundle;
- provider/generation failure creates no bundle;
- an existing deterministic proposal does not get overwritten;
- explicit bundle path cannot weaken `saveNew` collision behavior;
- prepare never applies documentation;
- prepare never records decisions.

Required outputs:

```text
Command: review prepare
Status: READY_FOR_REVIEW | NO_CHANGES
Proposal ID
Bundle Path
Payload SHA-256
Entry Count
Missing Patch Count
```

## 12. `review inspect`

Candidate syntax:

```text
docpilot review inspect \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  [--json]
```

This is read-only.

It loads the selected bundle through Core and renders:

- format version;
- proposal and bundle identity;
- previous/current specification SHA-256;
- reviewed-document SHA-256;
- payload SHA-256;
- proposal completeness;
- each entry in canonical order;
- target and parent;
- specification/documentation change kind;
- UPSERT/REMOVE operation;
- existing and proposed Markdown;
- Evidence IDs;
- current decision and comment;
- pending and missing targets.

Text output reuses or extends Core's deterministic review report renderer. The CLI
must not independently derive review semantics.

A valid pending bundle is successfully inspected with exit code 0. `inspect`
reports content; it does not use pending as a process failure.

## 13. `review status`

Candidate syntax:

```text
docpilot review status \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  [--documentation <file>] \
  [--json]
```

Status is read-only and summary-oriented.

Required status values:

```text
PENDING_REVIEW
READY_TO_APPLY
STALE_DOCUMENTATION
INVALID_BUNDLE
```

Derivation is provided by a Core status/query contract, not reimplemented in CLI.
RFC-0048 may add a Core `ReviewBundleStatusQuery` that consumes validated bundle
state and optional exact documentation.

Rules:

- incomplete proposal or missing decisions -> `PENDING_REVIEW`;
- complete proposal and decisions -> `READY_TO_APPLY`;
- when `--documentation` is provided, exact hash mismatch ->
  `STALE_DOCUMENTATION`;
- without `--documentation`, freshness is `NOT_CHECKED`;
- invalid/corrupt bundle -> `INVALID_BUNDLE`;
- status never applies or changes decisions.

Required summary:

```text
Proposal ID
Bundle Path
Payload SHA-256
Status
Proposal Complete
Total Entries
Accepted
Rejected
Pending
Missing Patches
Documentation Freshness: MATCH | STALE | NOT_CHECKED
```

Exit behavior:

- `READY_TO_APPLY`: 0;
- `PENDING_REVIEW`: 3;
- `STALE_DOCUMENTATION`: 4;
- invalid bundle: 5.

## 14. `review decide`

Candidate syntax:

```text
docpilot review decide \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  --target <target-id> \
  (--accept | --reject) \
  [--comment <text> | --comment-file <path>] \
  [--payload-sha256 <sha>] \
  [--json]
```

Rules:

- exactly one of `--accept` or `--reject`;
- exactly one target per invocation;
- `--comment` and `--comment-file` are mutually exclusive;
- comment is optional;
- an empty/blank comment fails;
- the target must exist in the stored proposal;
- decision replacement semantics come from Core;
- successful decision output returns the new payload SHA-256;
- a stale expected checksum fails without mutation.

`--payload-sha256`:

- optional for manual use;
- strongly recommended for automation;
- when supplied, must match the exact loaded payload;
- when omitted, the CLI loads the current bundle and passes its checksum to Core
  for the single optimistic update;
- no retry or silent rebase occurs after conflict.

Successful decision recording exits 0 even if other targets remain pending. The
command's requested mutation succeeded; status output communicates remaining work.

## 15. Comment input

### 15.1 `--comment`

- consumes one command argument;
- preserves its Unicode content;
- rejects empty or whitespace-only content;
- shell quoting is the caller's responsibility.

### 15.2 `--comment-file`

- path must identify one readable regular file;
- read as exact UTF-8;
- optional UTF-8 BOM may be rejected or stripped consistently; the detailed
  implementation decision must be tested;
- trailing CR/LF added by text editors is removed once;
- remaining content must be non-blank;
- no environment-variable or template expansion;
- file content is passed to Core as the decision comment.

The CLI must not print comments to stderr on failures involving integrity or I/O.

## 16. `review apply`

Candidate syntax:

```text
docpilot review apply \
  --project <path> \
  (--proposal <id> | --bundle <path>) \
  --documentation <file> \
  [--payload-sha256 <sha>] \
  [--json]
```

Apply flow:

1. validate arguments and exact paths;
2. load current bundle through Core;
3. read exact documentation UTF-8 content;
4. invoke Core `resumeApply`;
5. map pending/invalid/conflict without writing;
6. on `Applied`, prepare a temporary file in the documentation file's directory;
7. write Core's exact merged documentation;
8. verify temporary bytes match Core's result SHA-256;
9. immediately re-read the destination and invoke or reuse a Core freshness check;
10. if destination changed, discard temporary file and return conflict;
11. atomically replace where supported, with documented same-filesystem fallback;
12. output applied target counts and result documentation SHA-256.

The CLI never edits managed blocks itself.

No file is created when the documentation input does not exist unless a separately
approved command contract explicitly allows creation. RFC-0048 apply requires an
existing regular documentation file.

## 17. Apply atomicity and TOCTOU

RFC-0047 guarantees in-memory transformation atomicity. RFC-0048 adds adapter-level
file replacement safety.

The CLI must not:

- truncate the destination before Core approval;
- write accepted operations one at a time;
- replace the file after a stale check without a final destination verification;
- mark success when atomic/fallback replacement fails.

If the destination changes between initial read and final verification:

- no replacement;
- temporary file removed;
- exit code 4;
- status `STALE_DOCUMENTATION`.

The final write boundary must be implemented as a reusable CLI I/O adapter, not
inside argument parsing.

## 18. Text output contract

Text output is deterministic UTF-8 with LF logical lines.

Every result begins:

```text
Command: review <subcommand>
Status: <status>
Proposal ID: <id|unavailable>
Bundle Path: <absolute-path|unavailable>
Payload SHA-256: <sha|unavailable>
```

Additional fields follow in documented fixed order.

Human diagnostics must not include:

- raw bundle JSON;
- full comments on errors;
- provider secrets;
- stack traces by default.

## 19. JSON output contract

`--json` writes exactly one JSON object to stdout and no human prose.

Envelope version 1:

```json
{
  "outputFormatVersion": 1,
  "command": "review status",
  "status": "READY_TO_APPLY",
  "exitCode": 0,
  "proposalId": "review:...",
  "bundlePath": "C:\\absolute\\review-....json",
  "payloadSha256": "...",
  "data": {},
  "error": null
}
```

Field order is deterministic:

1. `outputFormatVersion`
2. `command`
3. `status`
4. `exitCode`
5. `proposalId`
6. `bundlePath`
7. `payloadSha256`
8. `data`
9. `error`

Rules:

- `outputFormatVersion` is integer 1;
- unavailable identity values are JSON null;
- `data` is always an object;
- `error` is null on non-error results;
- error object contains stable `code` and sanitized `message`;
- stderr is reserved for unexpected launcher/runtime diagnostics;
- JSON escaping and field ordering use one CLI output codec;
- absolute Windows paths are valid escaped JSON strings.

Changes that remove fields, change meaning, or repurpose status/exit values require
a new output format version.

## 20. Common JSON data fields

Where applicable:

```text
proposalComplete
entryCount
acceptedCount
rejectedCount
pendingCount
missingPatchCount
documentationFreshness
reviewedDocumentationSha256
resultDocumentationSha256
```

`inspect` additionally returns canonical entries and decisions. JSON review entry
semantics mirror Core models; the CLI does not invent a competing domain model.

## 21. Stable exit-code contract

Exit codes are public automation contracts:

| Code | Symbol | Meaning |
| ---: | --- | --- |
| 0 | `SUCCESS` | Command completed successfully |
| 2 | `CLI_USAGE_ERROR` | Invalid command or arguments |
| 3 | `REVIEW_PENDING` | Apply/status cannot proceed because review is incomplete |
| 4 | `REVIEW_CONFLICT` | Bundle changed or documentation is stale |
| 5 | `INVALID_BUNDLE` | Missing, corrupt, incompatible, or identity-invalid bundle |
| 6 | `GENERATION_FAILED` | Provider or incremental preparation failed |
| 7 | `REPOSITORY_IO_FAILED` | Bundle read/write/replace failure |
| 8 | `DOCUMENT_WRITE_FAILED` | Approved result could not be safely written |
| 70 | `INTERNAL_ERROR` | Unexpected uncategorized failure |

Rules:

- code 1 is intentionally unused;
- help and version output return 0;
- unknown command/option returns 2;
- successful `prepare`, `inspect`, and `decide` return 0;
- successful `decide` remains 0 even if the overall review remains pending;
- `status PENDING_REVIEW` returns 3;
- `apply` pending returns 3;
- stale expected payload or stale documentation returns 4;
- not-found bundle is 5, not 7;
- filesystem permission/read/write failure is 7 or 8 according to target;
- unexpected exceptions are caught at the CLI boundary and return 70.

Existing non-review command exit codes are not changed by RFC-0048.

## 22. Stable status and error codes

JSON/text statuses include:

```text
READY_FOR_REVIEW
NO_CHANGES
INSPECTED
PENDING_REVIEW
READY_TO_APPLY
DECISION_RECORDED
APPLIED
STALE_DOCUMENTATION
BUNDLE_CHANGED
INVALID_BUNDLE
GENERATION_FAILED
REPOSITORY_IO_FAILED
DOCUMENT_WRITE_FAILED
CLI_USAGE_ERROR
INTERNAL_ERROR
```

JSON error codes use these or more specific documented values without changing
their exit-code category.

## 23. Core API additions permitted

RFC-0048 may add Core contracts only when required to avoid duplication:

- exact-file Review Bundle repository/location;
- status/query result derived from validated bundle;
- safe public access to resolved bundle location;
- documentation freshness helper;
- richer structured repository failure needed for exit mapping.

These additions remain Core-owned and provider/CLI-neutral.

The CLI may not add a second Review Bundle model to avoid a small Core change.

## 24. CLI architecture

Expected adapter structure:

```text
docpilot-cli/
  command/review/
    ReviewCommand
    ReviewArguments
    ReviewPrepareCommand
    ReviewInspectCommand
    ReviewStatusCommand
    ReviewDecideCommand
    ReviewApplyCommand
  bootstrap/
    ReviewWorkflowBootstrap
  io/
    ReviewCliResultRenderer
    AtomicDocumentationFileWriter
```

Exact file names may follow current conventions.

`Main.runCli` dispatches `review` without embedding subcommand business logic.

## 25. Argument validation

Fail before Core/provider/filesystem mutation for:

- missing subcommand;
- unknown options;
- repeated scalar options;
- mutually exclusive options used together;
- blank project/proposal/target/provider/model values;
- malformed proposal ID or SHA-256;
- both/neither bundle selectors where one is required;
- both/neither accept/reject;
- both comment inputs;
- comment options without `decide`;
- provider/model outside `prepare`;
- documentation outside prepare/status/apply;
- output mode repeated inconsistently.

Option parsing must not depend on map overwrites that silently accept duplicates.

## 26. Path safety

- project path must be an existing readable directory;
- documentation must be an existing readable regular file;
- bundle input must be an existing readable regular file except prepare output;
- apply destination equals the documented input path;
- temporary files are created in the target directory;
- cleanup targets only CLI-created temporary files;
- normalized output paths do not imply symlink trust;
- no recursive delete or broad cleanup occurs.

## 27. Determinism

Equivalent command, Core state, and files produce identical:

- exit code;
- status;
- text field order;
- JSON field order and values;
- proposal/bundle/checksum output;
- inspect entry order;
- status counts;
- decision result;
- applied documentation bytes.

Exceptions:

- absolute paths differ when callers intentionally select different locations;
- provider-generated content remains governed by existing provider determinism limits.

No timestamp is included in stable CLI identity output.

## 28. Error mapping

CLI maps structured Core results exhaustively.

Examples:

| Core result | CLI status | Exit |
| --- | --- | ---: |
| `PersistentReviewPreparationResult.Saved` | `READY_FOR_REVIEW` | 0 |
| `NoChanges` | `NO_CHANGES` | 0 |
| `PersistentReviewUpdateResult.Saved` | `DECISION_RECORDED` | 0 |
| update `Conflict` | `BUNDLE_CHANGED` | 4 |
| resume `Pending` | `PENDING_REVIEW` | 3 |
| resume stale conflict | `STALE_DOCUMENTATION` | 4 |
| invalid load | `INVALID_BUNDLE` | 5 |
| provider failure | `GENERATION_FAILED` | 6 |

The CLI must not parse exception message text to determine semantic categories
when a structured Core result exists.

## 29. Compatibility

- Review Bundle format remains 1.
- Existing valid bundles remain readable and operable.
- DIR schema remains 0.3.
- Specification snapshot format remains 1.
- Provider SPI remains unchanged.
- Existing `generate` commands remain unchanged.
- New review exit codes and JSON format become stable public contracts.
- MCP remains independent.

## 30. Expected implementation areas

Core, only if required for thin adaptation:

```text
src/main/kotlin/io/docpilot/core/incremental/specification/review/
  exact-file repository/location
  status/query result
  structured failure refinements
```

CLI:

```text
docpilot-cli/src/main/kotlin/io/docpilot/cli/
  Main.kt
  command/review/**
  bootstrap/**
  io/**
```

Tests:

```text
docpilot-cli/src/test/kotlin/io/docpilot/cli/
  review argument tests
  text/JSON output contract tests
  exit-code mapping tests
  restart end-to-end workflow tests
  atomic apply tests
```

No MCP source/test path is authorized.

## 31. Verification plan

### 31.1 Parsing

- every subcommand and required option;
- `--bundle` and `--proposal` exclusivity;
- accept/reject exclusivity;
- comment/comment-file exclusivity;
- duplicate and unknown options;
- malformed SHA and proposal ID;
- paths with spaces;
- help output.

### 31.2 Common identity output

For all five commands and both output modes:

- proposal ID;
- absolute bundle path;
- payload SHA-256;
- null/unavailable behavior on early failures.

### 31.3 JSON

- one JSON object and no prose;
- format version 1;
- deterministic field order;
- valid escaping of Windows paths, Unicode comments, and Markdown;
- stable error object;
- stdout/stderr separation;
- byte-identical repeated output.

### 31.4 Prepare

- default and explicit bundle location;
- no-change without bundle;
- provider failure without bundle;
- deterministic existing proposal collision;
- valid saved bundle through Core codec.

### 31.5 Inspect and status

- inspect valid pending and complete bundles;
- inspect is read-only;
- status pending exit 3;
- status ready exit 0;
- status optional freshness match/stale/not-checked;
- corrupt and unsupported bundle exit 5.

### 31.6 Decide

- accept and reject;
- decision replacement;
- inline comment;
- UTF-8 comment file;
- blank comment rejection;
- partial review success exit 0;
- expected checksum conflict exit 4;
- current payload SHA output after save;
- no mutation on failure.

### 31.7 Apply

- complete accepted/rejected mixed review;
- accepted REMOVE;
- pending exit 3 and unchanged file;
- stale document exit 4 and unchanged file;
- stale payload exit 4;
- invalid bundle exit 5;
- temporary validation;
- destination change before replace;
- atomic replace and fallback;
- writer failure exit 8;
- exact result SHA output.

### 31.8 End-to-end

Use a temporary isolated project:

```text
prepare process
  -> inspect process
  -> status process (pending)
  -> decide process(es)
  -> status process (ready)
  -> apply process
```

Each step uses a fresh CLI invocation and loads the persisted RFC-0047 bundle.

### 31.9 Regression

- Core RFC-0047 tests;
- RFC-0046 removal tests;
- existing CLI generate tests;
- full clean build/test;
- isolated architecture-samples smoke;
- `git diff --check`;
- protected path review.

## 32. Completion criteria

RFC-0048 implementation is complete only when:

1. all five review subcommands are available;
2. CLI delegates all Review Bundle semantics to Core;
3. default and explicit bundle paths work through Core validation;
4. every command emits proposal ID, bundle path, and payload SHA;
5. text and JSON outputs are deterministic;
6. JSON format version 1 is documented and tested;
7. stable exit codes are exhaustively mapped and tested;
8. partial decisions persist across separate CLI processes;
9. status accurately reports pending/ready/stale through Core query semantics;
10. comments work inline and from UTF-8 files;
11. apply never writes for pending, invalid, or conflict results;
12. approved output is safely atomically replaced;
13. RFC-0047 integrity and stale rules are not duplicated or weakened;
14. existing CLI commands and Core APIs remain compatible;
15. no CLI/UI interactivity or MCP dependency is introduced;
16. targeted, end-to-end, full build/test, and smoke verification pass;
17. Canonical RFC, Planning, Handoff, Roadmap, help, and exit-code documentation match evidence.

## 33. Known risks

- CLI apply adds a filesystem write race beyond Core's in-memory apply.
- Stable exit codes and JSON fields create long-term compatibility obligations.
- Shell quoting can alter inline comments.
- Explicit bundle paths can broaden filesystem access if normalization is weak.
- Provider-dependent prepare can fail before a proposal exists.
- Adding convenience logic in CLI can accidentally duplicate Core semantics.

Each risk requires an explicit adapter boundary and test.

## 34. Deferred follow-up

Not approved by RFC-0048:

- interactive review UI/TUI;
- batch decision manifest;
- remote/multi-user review;
- authenticated reviewer identity;
- Review Bundle migration;
- durable apply receipt;
- bundle retention/cleanup;
- MCP review commands;
- Release Evidence Manifest;
- automatic Git or release actions.

## 35. Decision record

Approved candidate:

```text
RFC-0048
Official CLI Review Bundle Prepare, Decide, Inspect, Status, and Apply Workflow
```

Approved additions:

- `review status`;
- proposal ID, bundle path, and payload SHA on every command;
- `--json`;
- `--bundle <path>`;
- `--comment`;
- `--comment-file`;
- stable automation exit codes.

Approved architecture:

- CLI remains a thin adapter;
- Core Review Bundle, integrity, decision, and apply contracts are reused;
- CLI owns only orchestration, path handling, output, exit mapping, and safe final file I/O.

Approved non-goals:

- interactive UI;
- MCP dependency;
- duplicated Review Bundle semantics;
- remote review;
- file deletion outside managed-block behavior.

This decision approves the detailed specification. It does not by itself approve
implementation, commit, merge, push, tag, or release.
