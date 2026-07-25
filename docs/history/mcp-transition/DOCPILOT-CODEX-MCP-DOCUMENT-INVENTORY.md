# DocPilot Codex and MCP Document Inventory

## Audit Scope

Audit date: 2026-07-25 (Asia/Seoul)

The audit inspected 346 physical candidate files:

| Source | Candidates |
|---|---:|
| Main repository `docs/` | 67 |
| Main repository `tools/docpilot-mcp/docs/` | 8 |
| RFC-0044 worktree | 93 |
| Phase 10 Direct Codex worktree | 93 |
| MCP runtime | 62 |
| Direct Codex runtime | 14 |
| Current integration worktree Phase 7–10 reports | 9 |

Candidate formats were Markdown, text, AsciiDoc, reStructuredText, JSON, YAML, and YML. Build output, dependency directories, Git internals, raw logs, JSONL, locks, PIDs, and process dumps were excluded by policy. The two experiment worktrees contain duplicated baseline repository documents; physical counts intentionally retain those duplicates.

## Decisions

Each entry below records path/purpose, phase or RFC, state, duplication/latest status, future use, sensitivity, classification, preservation path, and rationale. No secret value is reproduced.

| Source path or group | Purpose / phase | State; duplicate; latest | Future use / sensitivity | Decision | Preservation path and rationale |
|---|---|---|---|---|---|
| Main `docs/vision`, `cdd`, `dsd`, `decisions`, existing RFC-0002–0043, planning-0038–0043, release, provider, plugin, roadmap | Existing canonical product and planning record | Tracked baseline; canonical; current for its recorded scope | High; no secret found | `KEEP_CANONICAL` (existing, unchanged except roadmap) | Existing paths; repository documents remain source of truth |
| Main empty `docs/adr/ADR-0001...` and `docs/manifesto/MANIFESTO.md` | Reserved canonical files | Empty baseline; not changed by this audit | Requires human authorship; no secret | `NEEDS_HUMAN_DECISION` | Existing paths; retained untouched |
| Main `tools/docpilot-mcp/docs/architecture.md` and Phase 2–6 verification reports (8 files) | MCP architecture and earlier verification | Tracked; historical/operational baseline; predates Phase 10 | Useful maintenance history; machine paths present | `KEEP_HISTORICAL` (existing, unchanged) | Existing MCP docs paths; moving would rewrite history and links |
| RFC worktree `docs/rfc/RFC-0044-Relationship-Semantics.md` | Approved RFC implementation contract | Final MCP candidate; newer than main; overlaps Direct draft | High; no secret | `CONSOLIDATE` | `docs/rfc/RFC-0044-Relationship-Semantics.md`; completion status and verified compatibility added |
| RFC worktree `docs/roadmap/ROADMAP.md` | Mark implementation in progress | Superseded by completion state | High; no secret | `CONSOLIDATE` | `docs/roadmap/ROADMAP.md`; completion and next-RFC uncertainty recorded |
| RFC worktree baseline docs (91 candidates) | Repository baseline and generated snapshot evidence | Mostly duplicate of main; some line-ending/checkout-size differences | Existing canonical value; no new audit value | `SKIP_DUPLICATE` | Canonical copies already in main |
| Direct worktree RFC-0044 and roadmap drafts | Direct Codex Phase 10 experiment | Experiment-local; conflicts in some endpoint details; not approved canonical | Comparison evidence only; no secret | `SKIP_OBSOLETE` / `CONSOLIDATE` | Operational conclusion preserved in Phase 10 report; drafts not copied |
| Direct worktree baseline docs (91 candidates) | Repository baseline and snapshot files | Duplicate of main/RFC worktree | No additional value | `SKIP_DUPLICATE` | Existing canonical copies retained |
| MCP runtime Phase 7 state | Work order and execution metadata | Historical intermediate; final Structured Result unavailable | Evidence summarized; contains machine paths | `CONSOLIDATE` | Phase 8 report and completion handoff record the limitation |
| MCP runtime Phase 8 evidence embedded in Phase 9 states | Independent re-verification | Final evidence; 254 tests; latest for independent verification | High; machine paths only | `KEEP_HISTORICAL` | `PHASE-8-RFC-0044-INDEPENDENT-REVERIFICATION.md` |
| MCP runtime Phase 9 final/smoke states | Completion, handoff, smoke, readiness | Final; overlapping state snapshots | High; machine paths only | `CONSOLIDATE` | Phase 9 report plus canonical handoff/planning update |
| MCP runtime result, diagnostics, schemas, project-state snapshots (remaining JSON candidates) | Orchestration execution and state | Intermediate or raw; duplicates final conclusions | Low after consolidation; may contain local paths/session metadata | `SKIP_TEMPORARY` | Kept only in local runtime |
| MCP runtime smoke fixture and generated docs/prompt packages | Phase 9 CLI smoke output | Generated, machine/run specific | Reproducible; no canonical role | `SKIP_TEMPORARY` | Final smoke result summarized in Markdown |
| Direct runtime smoke fixture docs and prompt packages (14 candidates) | Phase 10 Direct smoke output | Generated and experiment-local | Comparison only | `SKIP_TEMPORARY` | Phase 10 conclusion preserved without raw output |
| Phase 10 comparison conclusion | Decide future MCP/Codex roles | Latest operating conclusion | High; no secret | `KEEP_HISTORICAL` | `PHASE-10-MCP-VS-DIRECT-CODEX-TRANSITION.md` |
| Integration worktree Phase 7 implementation report | Initial governed implementation and interrupted-worker limitation | Final Phase 7 report; unique execution evidence | High; local paths present | `KEEP_HISTORICAL` | `source-reports/DOCPILOT-MCP-PHASE-7.0-RFC-0044-IMPLEMENTATION-REPORT.md` |
| Integration worktree initial Phase 8 verification | Independent failure with three contract findings and 252-test snapshot | Superseded as a gate result but important correction evidence | High; local paths present | `KEEP_HISTORICAL` | `source-reports/DOCPILOT-MCP-PHASE-8.0-RFC-0044-INDEPENDENT-VERIFICATION-REPORT.md` |
| Integration worktree Phase 8 re-verification | Final independent re-verification with 254 tests | Final; overlaps consolidated summary | High; local paths present | `KEEP_HISTORICAL` | `source-reports/DOCPILOT-MCP-PHASE-8-REVERIFICATION-REPORT.md` |
| Integration worktree Phase 9 smoke result | Final smoke/readiness evidence | Final; overlaps consolidated summary | High; local paths present | `KEEP_HISTORICAL` | `source-reports/DOCPILOT-MCP-PHASE-9-RFC-0044-COMPLETION-SMOKE-RESULT.md` |
| Integration worktree Phase 10 transition and Direct correction | Comparison gate, failure, recovery, and environment limitation | Final paired evidence; Direct-only replacement not approved | High; local paths present | `KEEP_HISTORICAL` | Two Phase 10 reports under `source-reports/` |
| Integration worktree Phase 7 correction and two intermediate Phase 9 synchronization reports | Correction and blocked intermediate gates | Conclusions fully captured by selected final reports and canonical handoff | Moderate; local paths present | `SKIP_DUPLICATE` | Not copied; inventory retains the decision |
| Long prompts, logs, JSONL, locks, manifests, diagnostics, Kotlin error logs | One-time execution support | Raw, duplicated, or failed-attempt output | Low; elevated sensitivity risk | `SKIP_TEMPORARY` | Not committed by policy |

## Selected New or Updated Documents

### KEEP_CANONICAL

- RFC-0044 Relationship Semantics
- RFC-0044 Main Planning Update
- Roadmap
- RFC-0044 Completion Handoff

### KEEP_OPERATIONAL

- MCP-Governed Codex RFC Development Methodology
- RFC Candidate Backlog Template
- RFC Detail Planning Template
- RFC Verification Checklist
- RFC Completion Handoff Template
- Main Chat Sync Packet Template

### KEEP_HISTORICAL

- MCP transition history index
- Phase 8 RFC-0044 Independent Re-verification
- Phase 9 RFC-0044 Completion and Smoke
- Phase 10 MCP vs Direct Codex Transition
- Six selected original Phase 7–10 source reports
- This inventory
- Pre-Core Document Consolidation Report

## Sensitive Information Review

The selected documents were scanned for API keys, access/session tokens, passwords, authorization headers, private keys, private repository credentials, personal email addresses, and secret environment variables. No secret value requiring redaction was found. Machine-specific paths were generalized in canonical and operational documents; historical reports carry a local-environment note.
