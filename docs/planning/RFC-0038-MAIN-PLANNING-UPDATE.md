# Main Planning Update — RFC-0038

## Project Dashboard

- Current Phase: Phase 1 — MVP
- Current RFC: RFC-0039
- Previous RFC: RFC-0038 — Completed
- Completed RFC: RFC-0001 through RFC-0038
- Planned RFC: RFC-0039 through RFC-0064
- Next Release: v0.5 MVP
- Release status: Technical runtime gates passed; artifact-version policy pending

## RFC completion summary

1. Stabilized Specification Incremental owner propagation without changing public APIs.
2. Added previous/current Type propagation for moved APIs and Properties.
3. Added previous/current Package propagation for moved Types.
4. Strengthened DIR ID and Package-reference validation.
5. Added deterministic incremental regression coverage.
6. Clarified DIR 0.2 legacy and DIR 0.3 builder-output policy.
7. Preserved renderer-only presentation responsibility.
8. Completed clean build, test, CLI smoke, Ollama generation, and invalid-provider gates.
9. Recorded a v0.5 MVP release evidence snapshot.

## Phase progress

| Capability | Status |
|---|---|
| Source Scanner | Complete |
| Knowledge Builder | Complete |
| Specification Builder / DIR 0.3 | Complete |
| Markdown Renderer | Complete |
| Specification Incremental | Stabilized |
| Validation | MVP baseline strengthened |
| Full Regression Evidence | Passed |
| Core CLI Smoke | Passed |
| Ollama Provider Smoke | Passed |
| v0.5 Release | Technically ready; version policy pending |

## Release-gate evidence

- Clean build: PASS
- Test task: PASS
- `architecture-samples` core analysis: PASS
- Prompt-package generation: PASS
- Ollama `qwen3:8b`: PASS, HTTP 200
- AI architecture output: PASS
- Invalid-provider handling: PASS
- OpenAI real API invocation: N/A, explicitly out of scope

## ADR candidates

- Incremental affected scopes include both previous and current owners when a Stable-ID entity changes ownership.
- DIR 0.2 remains a compatibility baseline while the canonical builder output is DIR 0.3.
- Stabilization validation should reject structural contradictions but remain permissive for potentially external symbolic references.
- Product milestone version and Gradle artifact version require an explicit versioning policy.

## Technical debt

- Resolve artifact version versus product milestone version policy.
- Define Relationship and dependency endpoint semantics in a future RFC.
- Add a dedicated successful help command instead of using usage-error output.
- Consider a documented root-task alias to avoid ambiguous multi-project `run` selection.

## RFC-0039 start prompt

```text
# DocPilot RFC-0039

This conversation is dedicated to RFC-0039 only.

Baseline:
- Phase 1 — MVP
- RFC-0001 through RFC-0038 completed
- v0.5 technical release gates passed on July 17, 2026
- Source Scanner, Knowledge Builder, DIR 0.3 Specification Builder, Markdown Renderer, and Stable-ID Specification Incremental are implemented
- RFC-0038 stabilized previous/current ownership propagation and DIR structural validation
- Core CLI analysis was verified against architecture-samples
- Ollama qwen3:8b architecture generation was verified with HTTP 200
- OpenAI real invocation is outside the v0.5 release-validation scope
- Renderer consumes ProjectSpecification only
- Snapshot Incremental and Specification Incremental remain separate subdomains

Preserve Clean Architecture, Evidence First, Constitution principles, existing public contracts, project structure, and test style. Do not change earlier RFC designs or perform unrelated refactoring. Resolve or explicitly carry forward the artifact-version policy before publishing v0.5.
```
