# RFC Verification Checklist

## Identity

- [ ] `<RFC_ID>` and `<RFC_TITLE>` match the approved RFC.
- [ ] Baseline is `<BASELINE_COMMIT>`.
- [ ] Work occurs on `<FEATURE_BRANCH>` in `<FEATURE_WORKTREE>`.

## Scope

- [ ] All changed files are within `<ALLOWED_PATHS>`.
- [ ] `<PROTECTED_PATHS>` are unchanged.
- [ ] Goals are implemented and non-goals remain excluded.
- [ ] Public API, schema, persistence, and workflow constraints are checked.

## Verification

- [ ] `<VERIFICATION_COMMANDS>` completed with recorded results.
- [ ] Build passed.
- [ ] Full tests passed with count and failure/skip totals recorded.
- [ ] Focused regressions passed.
- [ ] Smoke validation passed or is explicitly not applicable.
- [ ] `git diff --check` passed.
- [ ] Candidate integrity and unexpected files were checked.

## Completion

- [ ] `<COMPLETION_CRITERIA>` have evidence.
- [ ] Limitations and technical debt are explicit.
- [ ] Independent verification is recorded.
- [ ] Completion handoff is ready.
- [ ] Planning and release-readiness changes are identified.
