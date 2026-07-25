# RFC-0052 Product Roadmap Realignment

## Decision

RFC-0052 returns to the original Product Capability roadmap.

The previously proposed Lease/Retention candidate is not selected for RFC-0052.
Signed Release Evidence is also not selected.

## Why

DocPilot's primary product objective is accurate, evidence-backed documentation
that changes only where the project changed.

Review Workflow safety is already available through:

- complete-review-before-merge;
- managed-block removal approval;
- durable Review Bundles;
- stale apply prevention;
- Lifecycle Metadata and Apply Receipts;
- crash recovery;
- official dry-run/confirm lifecycle CLI.

The remaining Review Lease and retention risks are real but are operational
hardening concerns. They do not currently block the primary documentation-quality
roadmap.

## Product track

### RFC-0052

Selective Documentation Artifact Planning and Rendering:

- renderer-owned artifact catalog;
- exact stable-ID impact mapping;
- multi-artifact official renderer;
- render/write only CREATE and UPDATE documents;
- KEEP suppression;
- orphan reporting without deletion.

### Proposed RFC-0053

Semantic Relationship Expansion:

- `EXTENDS`;
- `IMPLEMENTS`;
- `CALLS`;
- `IMPORTS`;
- stable identity and Evidence;
- selective artifact impact through RFC-0052.

### Proposed RFC-0054

Documentation Quality Validation:

- artifact and target coverage;
- Evidence traceability;
- stale/missing relationship documentation;
- unresolved critical gaps;
- deterministic validation report and gate.

### Proposed RFC-0055

Existing Documentation Reconciliation:

- DocPilot ownership inventory;
- legacy artifact adoption;
- managed/manual content boundaries;
- drift detection;
- orphan disposition;
- conflict-safe reconciliation Plan.

## Hardening track

### Proposed RFC-0056 or v1.1

Cross-process Review Leases and Audit-safe Retention.

### Proposed RFC-0057 or v1.1

Signed Release Evidence and External Attestation.

The two subjects may be renumbered when the Product track completes. This
document does not reserve those RFC numbers.

## Guardrails

- Product RFCs must retain complete review and fail-closed apply.
- Core remains independent from MCP and provider implementations.
- Snapshot Incremental and Specification Incremental remain separate.
- Hardening work may not displace Product Capability without new evidence of an
  immediate release or data-integrity blocker.
- Existing contract versions remain unchanged unless a dedicated compatibility
  decision is approved.
