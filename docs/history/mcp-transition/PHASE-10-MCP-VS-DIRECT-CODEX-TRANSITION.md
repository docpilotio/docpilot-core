# Phase 10 MCP vs Direct Codex Transition

> Note: filesystem paths in the underlying experiment reflect the local environment used at execution time.

## Decision

MCP is `MAINTENANCE_ONLY` or temporarily retained. A complete transition to Direct Codex is not approved.

## Comparison

The MCP-governed path demonstrated bounded work orders, allowed/protected path enforcement, process and lock controls, independent verification, completion readiness, completion handoff, and planning synchronization.

The Direct Codex experiment demonstrated that Codex can perform Core analysis, detailed planning, implementation, tests, and correction without placing product implementation logic in MCP. Its RFC and roadmap drafts were experiment-local and were not treated as canonical planning.

## Operating Conclusion

- MCP: governance and execution control only.
- Codex: Core analysis, RFC candidate and detail planning, implementation, tests, and correction.
- People and Main Planning: vision, priority, approval, material scope/design decisions, commit, merge, and release.
- Repository documents: source of truth.
- Main chat: Sync Packet only.

## Constraints

MCP must not expand into product design authority, autonomous RFC prioritization, Core implementation logic, hidden canonical state, or automatic merge/release. Maintenance work required to keep existing governance reliable must be separately scoped and approved.

## Evidence Treatment

The Direct worktree, generated runtime output, Gradle/Kotlin error logs, prompt packages, and smoke fixture documents remain local temporary evidence. The official conclusion is preserved here without committing those machine-specific artifacts.
