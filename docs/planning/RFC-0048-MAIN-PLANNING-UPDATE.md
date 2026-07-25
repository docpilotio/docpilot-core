# RFC-0048 Main Planning Update

## Dashboard

```text
RFC
RFC-0048 - Official CLI Review Bundle Workflow

State
Implementation and local verification complete
Feature Commit and main integration pending
```

## Implementation summary

- Add `review prepare`, `inspect`, `status`, `decide`, and `apply`.
- Keep CLI as a thin adapter over RFC-0047 Core workflows.
- Support default proposal lookup and exact `--bundle` paths.
- Emit proposal ID, absolute bundle path, and payload SHA for every result.
- Provide deterministic text and JSON output envelope version 1.
- Support inline and UTF-8 file comments.
- Define stable exit codes 0/2/3/4/5/6/7/8/70.
- Add Core status evaluation and exact-file repository factory to prevent semantic duplication.
- Apply Core-approved output through a guarded atomic documentation writer.

## Verification

- Focused review CLI workflow tests: PASS.
- Separate command instances persisted and resumed status/decision/apply.
- JSON identity fields and pending exit code: PASS.
- Stale payload conflict and no mutation: PASS.
- `clean build`: PASS.
- `clean test`: PASS.
- 89 XML files, 273 tests, 0 failures, 0 errors, 0 skipped.
- Existing isolated CLI smoke: PASS.

## Compatibility

- Review Bundle format 1 unchanged.
- DIR schema 0.3 unchanged.
- Specification snapshot format 1 unchanged.
- Existing generate commands preserved.
- MCP source/tests/state unchanged.

## Known limitations

- Prepare requires a valid previous specification snapshot.
- Provider execution remains subject to provider availability.
- CLI output format and exit codes now create long-term compatibility obligations.
- Exact bundle path support uses the official Core codec and repository but does not add remote locations.
- Atomic replacement fallback is same-filesystem but not a distributed transaction.
- Interactive UI and batch decision files remain excluded.

## Git

- Feature Branch: `feature/rfc-0048-cli-review-workflow`
- Baseline: `addb7ab39e572a5faf4758b782f5602220501087`
- Feature Commit: PENDING
- Main merge: NOT PERFORMED
- Push: NOT PERFORMED
