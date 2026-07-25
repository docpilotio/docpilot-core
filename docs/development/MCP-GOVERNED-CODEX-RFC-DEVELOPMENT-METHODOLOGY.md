# MCP-Governed Codex RFC Development Methodology

## 1. Purpose

This document defines the official pre-Core workflow for turning a proposed DocPilot change into an approved, implemented, independently verified RFC.

Core principle:

> Codex proposes. People decide. MCP governs. Codex implements. Evidence proves completion.

## 2. Scope

The workflow applies to Core architecture discovery, RFC candidates, approved RFC delivery, verification, completion handoff, planning synchronization, and Git integration. It does not delegate product authority or release authority to automation.

## 3. Source of Truth

Versioned repository documents are the source of truth. Runtime state, prompts, chat transcripts, logs, and local worktrees are evidence or execution aids, not canonical project state. Main chat receives a compact Sync Packet that links to canonical documents.

## 4. People and Main Planning

People and Main Planning own vision, priority, RFC approval, material scope or architecture decisions, exceptions, commit/merge/release approval, and selection of the next RFC.

## 5. MCP

MCP is `MAINTENANCE_ONLY` (temporarily retained) as a governance boundary. It manages work orders, allowed and protected paths, process locks, independent verification, correction cycles, completion readiness, completion handoff, and planning synchronization.

## 6. Codex

Codex performs Core code analysis, architecture discovery, RFC candidate drafting, detailed RFC planning, implementation, test implementation, evidence collection, and correction work inside an approved scope.

## 7. RFC Candidate vs Official RFC

An RFC candidate is an analysis-backed proposal with an unresolved priority and approval state. An official RFC has explicit human approval, a stable scope, non-goals, acceptance criteria, and a repository document. Codex must not treat a candidate as approved work.

## 8. Core Architecture Discovery

Codex inspects the actual code, tests, build graph, APIs, invariants, and prior decisions. Findings cite repository paths and distinguish observed facts from proposals.

## 9. RFC Candidate Backlog

Candidates record motivation, evidence, dependencies, risks, estimated impact, and open decisions. Ordering remains a Main Planning decision.

## 10. RFC Approval

Human approval fixes the RFC identity, goals, non-goals, risk class, compatibility constraints, and completion criteria. Material deviations return to approval.

## 11. RFC Detail Planning

Codex expands an approved RFC into change areas, test strategy, migration or compatibility requirements, verification commands, allowed paths, protected paths, and rollback considerations.

## 12. MCP Work Order

MCP converts the approved plan into a bounded work order containing `<RFC_ID>`, `<BASELINE_COMMIT>`, `<FEATURE_BRANCH>`, `<FEATURE_WORKTREE>`, `<ALLOWED_PATHS>`, `<PROTECTED_PATHS>`, `<VERIFICATION_COMMANDS>`, and `<COMPLETION_CRITERIA>`.

## 13. Codex Implementation

Codex implements only the approved scope, preserves protected paths, adds proportionate tests, and reports deviations. Repository state is inspected before edits.

## 14. Independent Verification

Verification is independent of the implementation narrative. It covers build, tests, focused regression, smoke behavior where applicable, scope, protected paths, and candidate integrity.

## 15. Correction Cycle

Failed criteria produce a bounded correction order. The correction records the finding, affected requirement, allowed files, required verification, and evidence. Repeated failure is escalated rather than hidden.

## 16. Completion Readiness

Readiness requires all approved criteria to have evidence, no unresolved blocker, clean scope, and an explicit account of limitations and technical debt.

## 17. Completion Handoff

The handoff records implementation status, changed files, verification, limitations, debt, Git state, planning updates, and decisions still required.

## 18. Planning Synchronization

Canonical planning, RFC status, completed-RFC lists, release readiness, and roadmap state are synchronized after verification. Historical evidence is not rewritten to match later conclusions.

## 19. Main Chat Sync Packet

Main chat receives only the reusable Sync Packet format. It summarizes status and links to the RFC, planning update, handoff, and report; it does not replace those documents.

## 20. Git Integration Approval

Commit, push, merge, tag, and release are separate authorities. Automation may perform only the actions explicitly authorized for the task. Force pushes and unapproved main-branch integration are prohibited.

## 21. RFC Risk Classes

- Low: isolated internal behavior with narrow compatibility impact.
- Medium: cross-module behavior, persisted output, CLI workflow, or public semantics.
- High: public API/schema changes, migration, security, destructive behavior, or release architecture.

Higher risk requires broader review, stronger rollback planning, and more independent evidence.

## 22. MCP Maintenance-Only Policy

MCP remains available for Core delivery governance while the workflow is evaluated. Fixes needed to preserve existing governance reliability may be proposed separately, but MCP is not a product implementation surface for Core RFC features.

## 23. Prohibited MCP Expansion

Do not expand MCP into product design authority, Core implementation logic, autonomous priority selection, automatic approval, hidden source-of-truth state, automatic merge/release, or a replacement for repository planning.

## 24. Failure and Recovery

Preserve evidence, stop unsafe mutations, inspect locks and Git state, and resume from a verified baseline. Do not bypass hooks, erase user changes, rewrite historical reports, or report a blocked push as success.

## 25. Evidence Requirements

Evidence identifies the baseline, branch, commands, results, test counts when available, smoke target, scope result, protected-path result, limitations, and timestamp or phase. A check becomes passed only when supported by actual evidence.

Current operating conclusion:

- Core delivery: MCP governance plus Codex analysis, planning, implementation, and tests.
- Direct Codex-only transition: not approved.
- Next RFC: unconfirmed until Main Planning approval.
