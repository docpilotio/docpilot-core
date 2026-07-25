# RFC-0052 Completion Handoff

## RFC identity

- ID: RFC-0052
- Title: Selective Documentation Artifact Planning and Rendering
- Track: Product Capability
- Feature branch: `feature/rfc-0052-incremental-planning-quality`
- Baseline: `12128beb7c9696a57dd6787fd4e83c429aeb8db6`

## Implementation summary

RFC-0052 moves artifact selection before content rendering. Core now owns stable
artifact descriptors, deterministic impact planning, dependency refresh, Plan
SHA-256, selective rendering, ownership protection, and orphan reporting.

The official specification Markdown renderer exposes deterministic project,
module, package, component, relationship, evidence, and index artifacts. Its
full-render contract remains available, while the incremental executor invokes
only planned CREATE and UPDATE artifact IDs.

## Changed production areas

- `io.docpilot.core.api`: selective renderer and artifact descriptor contracts
- `io.docpilot.core.incremental.execution`: planner, Plan SHA, and executor
- `io.docpilot.core.render`: official deterministic multi-artifact renderer
- `docpilot-cli`: thin inventory adapter over the Core renderer catalog

## Verification

- Targeted Renderer/Planner/Executor tests: PASS
- Clean full test: PASS
- XML files: 98
- Tests: 306
- Failures: 0
- Errors: 0
- Diff check: PASS

## Compatibility

- DIR schema remains `0.3`.
- Specification Snapshot format remains `1`.
- Review Bundle, Lifecycle, Receipt, and Journal formats remain `1`.
- `SpecificationRenderer.render(specification)` remains available.
- Review and Apply semantics are unchanged.
- MCP source and runtime dependencies are unchanged.

## Safety

- Unknown-owned selected paths fail closed.
- Orphaned generated paths are reported and retained.
- Selective output must exactly match planned IDs, paths, and media types.
- KEEP artifacts are neither rendered nor written.

## Known limitations

- Arbitrary existing Markdown is not reconciled or adopted.
- Orphan disposition and ownership reconciliation remain RFC-0055 candidates.
- Relationship kinds remain limited to current RFC-0044 semantics.
- Quality completeness gating remains a proposed RFC-0054 capability.

## Deferred hardening

- Cross-process Review Leases and Audit-safe Retention: RFC-0056+ or v1.1.
- Signed Release Evidence and External Attestation: RFC-0057+ or v1.1.

## Git integration

- Feature commit: NOT CREATED
- Main merge: NOT PERFORMED
- Push: NOT PERFORMED
- Release: NOT PERFORMED

## Completion readiness

Implementation and local verification are complete. Exact commit evidence and
main integration require explicit follow-up approval.
