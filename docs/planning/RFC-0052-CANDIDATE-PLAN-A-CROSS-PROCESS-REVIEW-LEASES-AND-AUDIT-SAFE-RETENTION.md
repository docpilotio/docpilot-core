# RFC-0052 Candidate Plan A

# Cross-process Review Leases and Audit-safe Retention

## Type

ARCHITECTURE_ENABLER

## Problem

RFC-0050 and RFC-0051 use immutable lifecycle generations, compare-and-swap
publication, deterministic operation Plans, and confirm-time revalidation. Those
contracts prevent stale publication, but two processes can still begin the same
mutation concurrently and perform redundant work before one loses the final
generation check.

Lifecycle generations and completed transaction artifacts are also retained
without bound. Long-running projects require explicit, auditable retention
planning that never deletes the current generation, authoritative Receipt, or
recovery evidence.

## Proposed outcome

Add a Core-owned proposal-scoped lease with fencing identity, then add
deterministic audit-safe retention planning and explicitly confirmed execution.

## Goals

- exclusive proposal mutation ownership across local processes;
- deterministic lease identity and monotonically fenced ownership;
- stale owner rejection at every durable publication point;
- bounded expiry with conservative takeover;
- no safety dependence on wall-clock ordering alone;
- recovery-safe handling of a process that dies while holding a lease;
- deterministic retention Plan with Plan SHA;
- dry-run by default and explicit confirmation for retention;
- protected evidence classes for CURRENT, APPLIED Receipt, unresolved recovery,
  supersession lineage, and configured minimum history;
- no deletion before a fresh Core revalidation;
- offline lease/retention verification;
- thin CLI adapter if operational commands are included.

## Non-goals

- distributed consensus;
- network or database locks;
- remote review synchronization;
- automatic deletion enabled by default;
- deletion of Review Bundles or user documentation;
- MCP-owned leases or retention;
- changing Bundle, Lifecycle, Receipt, or Journal formats;
- cryptographic signing.

## Architecture

Core owns:

- lease acquisition eligibility;
- lease/fencing token generation;
- renewal and conservative takeover rules;
- mutation authorization against fencing identity;
- retention classification and protected evidence;
- deterministic retention Plan;
- confirm-time revalidation;
- deletion order and failure handling.

Filesystem adapters own only atomic local persistence and platform-specific lock
primitives. CLI remains an invocation and presentation adapter.

## Expected change areas

- Core lifecycle mutation boundary and repository
- new lease and retention models/codecs
- filesystem lease adapter
- RFC-0051 operations integration
- optional `review lifecycle lease status` and `retention plan|apply` thin commands
- multi-process, crash, fencing, and retention tests

## Public API impact

New Core lease and retention ports/results. Existing lifecycle operations remain
source compatible but require a valid mutation guard internally.

## Contract impact

- Review Bundle format 1: unchanged
- Lifecycle Metadata format 1: unchanged
- Apply Receipt format 1: unchanged
- Apply Journal format 1: unchanged
- new Lease contract: version 1
- new Retention Plan contract: version 1

## Risks

- unsafe lease stealing caused by clock skew;
- platform-specific file-lock semantics;
- fencing checks applied inconsistently across mutation paths;
- retention selecting evidence required for later audit or recovery;
- partial deletion leaving misleading state.

## Verification

- competing-process acquisition and fencing tests;
- owner crash at every mutation boundary;
- expired lease takeover with stale owner publication rejection;
- clock skew simulation;
- deterministic retention Plan golden tests;
- proof that CURRENT, authoritative Receipt, and recovery evidence are protected;
- partial deletion recovery;
- Windows filesystem integration;
- full regression and isolated CLI smoke.

## Priority

RECOMMENDED

RFC-0051 makes lifecycle operations automation-ready. Plan A closes the most
important remaining local concurrency and long-term storage risks before adding
external trust mechanisms.
