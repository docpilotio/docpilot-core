# Phase 10 Direct Replay Correction Result

## Result

The Direct replay was corrected in its isolated Worktree only. The original failure was caused by the new validator rejecting the legacy snapshot fixture's unknown relationship target. The fixture was updated to use a valid external target; no Snapshot format or production snapshot codec change was made.

## Verification

- Snapshot targeted tests: PASS
- Full Gradle test task: PASS, 77 XML files / 231 tests / 0 failures / 0 skipped
- Non-clean Gradle build: PASS
- CLI smoke against isolated architecture-samples fixture: PASS, exit code 0
- `git diff --check`: PASS
- Candidate scope: RFC-allowed paths only, aside from ignored Kotlin compiler error logs
- Commit/merge/push: none

## Limitation

`clean build` could not delete the Gradle problems report because a Windows file handle remained open. The subsequent non-clean build and full test task passed. This is recorded as an environment limitation, not converted to a Clean Build PASS.

## Transition impact

Direct implementation now has functional and smoke evidence, but the replay required a correction cycle and its Codex process originally timed out. MCP remains recommended as temporarily retained until a clean-build environment and repeatable Direct procedure are demonstrated. No MCP Candidate or Main Worktree was modified.
