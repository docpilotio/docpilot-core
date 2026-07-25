# DocPilot Phase 8.0 — RFC-0044 Independent Diff, Build and Test Verification

## Decision

`PHASE_8_VERIFICATION_FAILED`

Independent Build and tests passed, and the preserved Candidate remained uncommitted and within the declared paths. However, independent code review found RFC-0044 contract gaps that are not covered by the current tests. The Feature Worktree is preserved for a Phase 7 correction cycle; no code was modified in Phase 8.

## Baseline and Candidate

- Feature Worktree: `C:\WorkSpace\docpilot-rfc-0044`
- Branch: `feature/rfc-0044-relationship-semantics`
- Baseline and current HEAD: `c62965cda3aef7f2d69165c545c5e1f11696f242`
- Index: clean; Candidate remains uncommitted.
- Main and user changes were not touched.
- Phase 7 Worker completion: interrupted; final Structured Result unavailable.

Candidate paths remained limited to the RFC-0044 implementation, tests, RFC document, and minimal roadmap update. `git diff --check` passed. No protected MCP, planning, ADR, build configuration, or user file was changed.

## Independent verification

- `./gradlew.bat clean build`: PASS, `BUILD SUCCESSFUL`.
- Targeted Builder, Validator, Renderer, and Snapshot Gradle tests: all four commands exit `0`.
- `./gradlew.bat clean test`: PASS, `BUILD SUCCESSFUL`.
- Test XML aggregation: 85 XML files, 252 tests, 0 failures, 0 skipped.
- Candidate status and paths remained unchanged after Build/Test.

## Blocking contract findings

### P8-001 — Validator does not enforce INTERNAL source endpoints

`ProjectSpecificationValidator.kt` classifies both endpoints but accepts any valid EXTERNAL or UNRESOLVED endpoint for the source. RFC-0044 requires every relationship source to be INTERNAL and EXTERNAL source endpoints to be rejected. The current implementation therefore permits an invalid `external:* -> target` relationship.

Classification: `RFC_REQUIREMENT_MISSING` / `TEST_COVERAGE_GAP`.

### P8-002 — Validator does not verify UNRESOLVED evidence

The validator checks the unresolved prefix and non-empty suffix but does not require a corresponding `UnresolvedItem` in `ProjectSpecification.unresolved`. RFC-0044 explicitly requires that evidence mapping.

Classification: `RFC_REQUIREMENT_MISSING` / `TEST_COVERAGE_GAP`.

### P8-003 — Ambiguous multi-module package fallback is nondeterministic by contract

`RelationshipEndpointResolver.resolve()` returns `candidates.firstOrNull()?.id` when multiple package candidates remain unresolved after counterpart-module selection. RFC-0044 requires `UNRESOLVED` when the package cannot be determined safely; arbitrary first-candidate selection is prohibited.

Classification: `RFC_REQUIREMENT_MISSING` / `DETERMINISM_COVERAGE_GAP`.

## Non-blocking observations

The independent Gradle runner output did not provide a single human-readable total for all tests, but XML reports provided the aggregate above. Relationship-specific resolver coverage is not represented by a dedicated resolver test file; the contract gaps above remain blocking regardless of the passing aggregate suite.

## Required Phase 7 correction

Do not modify code in Phase 8. Return the preserved Feature Worktree to Phase 7 to:

1. enforce INTERNAL-only sources;
2. require matching `UnresolvedItem` evidence for UNRESOLVED endpoints;
3. replace ambiguous `firstOrNull()` package fallback with UNRESOLVED;
4. add focused tests for these cases and deterministic input-order behavior;
5. rerun the full independent Phase 8 verification.

**Ready for Phase 9:** NO.
