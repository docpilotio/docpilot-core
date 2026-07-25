# DocPilot v1 Product Validation Plan

## Status

Executed against the `v1.0.0` technical baseline.

## Baseline

- Technical tag: `v1.0.0`
- Tag commit: `4e050181ed349dc1c1389387f297e60886d86cba`
- Validation main: `5ef2abf09c1323af66d0aecea44b013a806261a8`
- Sample repository commit: `ee66e1526b84c026615df032c705842b7d2a521f`
- Original sample checkout: `C:\WorkSpace\architecture-samples`
- Isolated runtime:
  `C:\WorkSpace\docpilot-rfc-0045-discovery\product-validation-runtime`

The original sample checkout is read-only input. Its existing untracked
`docs/` and `prompt-package/` directories are excluded from clean fixtures.

## Validation questions

1. Can DocPilot generate useful design documentation from a real repository?
2. Does a semantic source change update only required Artifacts?
3. Can generated documentation coexist safely with existing user content?
4. Is output structurally and explanatorily comparable with mature Kubernetes
   architecture and design documentation?
5. Are material facts traceable to source Evidence?
6. Are factual gaps explicit rather than silently invented?
7. Is regeneration byte-deterministic for identical semantic input?
8. Is the result independently reviewable and release-decidable?

## Workloads

### W1: Legacy Core analysis

```powershell
.\gradlew.bat :run --args="analyze <isolated-fixture>"
```

Collect all generated `docs/` and `prompt-package/` Artifacts and SHA-256.

### W2: Official specification generation

```powershell
.\gradlew.bat :docpilot-cli:run `
  --args="generate specification --project <fixture> --output <fixture>"
```

Collect execution mode, Snapshot validation, artifact catalog, size, SHA-256,
and unresolved Evidence.

### W3: Determinism

- run W1 twice on identical path and input;
- compare exact relative paths and bytes;
- run W2 with valid Snapshot and unchanged source;
- compare hashes and last-write state.

### W4: Incremental selection

Add one deterministic property to `Task.kt`, execute W2, and compare all
Artifact hashes. Only the owning Component and required project summary should
change.

### W5: Existing-document reconciliation

Use the official product workflow, if present, to Preview and Apply
Reconciliation against a user-owned Markdown fixture. Verify unknown ownership,
byte preservation, conflict behavior, stale checks, and restart recovery.

If no official product workflow exposes RFC-0055, record the gate as
non-executable rather than substituting unit tests for product validation.

### W6: Kubernetes comparison

Use only official primary references:

- Kubernetes Cluster Architecture;
- Kubernetes Enhancement Proposal process;
- current KEP template and Production Readiness Review sections.

Compare structure, accuracy, traceability, explanation, and maintainability.
The comparison is a quality benchmark, not a claim that a small Android sample
requires Kubernetes' organizational scale.

## Evidence policy

- Every result binds command, input commit, relative Artifact path, and SHA.
- Runtime output is not committed.
- Repository documents contain compact, reproducible evidence and findings.
- No failed or unavailable check is reported as PASS.
- Existing `v1.0.0` tag is not moved or deleted.

## Outputs

- `DOCPILOT-V1-PRODUCT-VALIDATION-CRITERIA.md`
- `DOCPILOT-V1-PRODUCT-VALIDATION-REPORT.md`
- `DOCPILOT-V1-RELEASE-DECISION.md`
