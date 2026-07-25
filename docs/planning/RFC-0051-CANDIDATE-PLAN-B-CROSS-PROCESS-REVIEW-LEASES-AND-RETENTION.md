# RFC-0051 Candidate Plan B

# Cross-process Review Leases and Audit-safe Retention

## Type

ARCHITECTURE_ENABLER

## Problem

RFC-0050 uses immutable generation compare-and-swap as its durable conflict
boundary, but simultaneous processes can still perform redundant work before one
loses publication. Generations and completed transaction artifacts are retained
without bound. Long-running repositories need explicit concurrency ownership and
retention rules that never weaken auditability.

## Proposed outcome

Add a Core-owned, proposal-scoped lease protocol and an audit-safe retention
policy for lifecycle control generations and completed transaction staging.

## Goals

- deterministic proposal-scoped lease identity;
- exclusive mutation ownership with bounded expiry and safe takeover;
- stale-owner detection without relying on wall-clock ordering alone;
- fail-closed behavior when lease ownership is ambiguous;
- retention classes for current generation, Receipt-bearing APPLIED generation,
  recovery evidence, and superseded historical generations;
- mark-and-sweep planning separated from deletion execution;
- offline retention verification and dry-run report;
- no loss of the authoritative Receipt or recovery evidence.

## Non-goals

- distributed consensus;
- remote locks;
- database-backed review storage;
- CLI UI beyond an optional thin invocation;
- MCP ownership;
- automatic deletion enabled by default;
- cryptographic signing.

## Dependencies

- RFC-0050 immutable generation and transaction repository.
- Stable filesystem identity and atomic same-directory replacement.

## Expected change areas

- Core lifecycle repository and concurrency ports
- lease and retention policy models/codecs
- filesystem lease adapter
- concurrency/failure-injection tests
- optional thin CLI dry-run adapter

## Risks

- unsafe time-based lease stealing;
- platform-specific file-lock behavior;
- retention deleting evidence needed for recovery or audit;
- excessive complexity before real multi-process demand is demonstrated.

## Verification

- two-process contention and stale-owner simulations;
- lease acquisition crash matrix;
- deterministic retention plans;
- proof that current lifecycle and authoritative Receipts are never selected;
- Windows filesystem behavior;
- full regression and offline verification.

## Priority

CONDITIONAL

This is valuable for sustained v1.0 operation, but should follow Plan A unless
multi-process automation or repository growth is already an immediate deployment
constraint.
