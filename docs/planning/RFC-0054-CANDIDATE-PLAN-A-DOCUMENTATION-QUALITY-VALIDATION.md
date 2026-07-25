# RFC-0054 Candidate Plan A: Documentation Quality Validation

## Status

PROPOSED — user approval required.

## Purpose

Add a deterministic Core-owned quality model that decides whether generated
documentation is sufficiently complete, evidence-backed, internally consistent,
and safe to treat as release-ready.

## Problem

RFC-0052 can identify and selectively generate exact artifacts. RFC-0053 can
report relationship aggregation, omission, unresolved endpoints, and threshold
overflow. DocPilot still lacks one structured contract that converts these facts
into stable quality findings and a pass/fail result.

## Proposed scope

- `DocumentationQualityRuleId` stable identifiers;
- severity: INFO, WARNING, ERROR;
- Core-owned versioned quality policy;
- versioned Documentation Quality Report;
- deterministic report SHA-256;
- offline report verification;
- DIR entity-to-artifact coverage;
- required artifact and section coverage;
- Evidence reference integrity and minimum coverage;
- unresolved critical-item findings;
- Relationship Projection Report consumption;
- relationship omission, fallback, overflow, and inconsistency findings;
- stale or missing RFC-0052 artifact inventory findings;
- deterministic quality gate result;
- report-only and enforce policy modes.

## Architecture

```text
ProjectSpecification
RFC-0052 Artifact Catalog / Inventory
RFC-0053 Relationship Projection Report
Core Quality Policy
        |
        v
DocumentationQualityValidator
        |
        v
Versioned Findings + Report SHA + PASS/FAIL
```

All rules and gate semantics remain in Core. CLI, future UI, Release tooling, and
MCP may only present or transport the result.

## Initial rule families

- artifact presence and ownership;
- entity documentation coverage;
- Evidence existence and traceability;
- unresolved endpoint/item severity;
- relationship-kind coverage;
- projection overflow and omission;
- threshold source fallback;
- dependency/relationship consistency;
- deterministic artifact/report integrity.

## Goals

- make documentation quality measurable and automation-safe;
- expose bounded relationship loss as explicit quality evidence;
- give release workflows a deterministic gate;
- provide stable diagnostics before existing-document reconciliation.

## Non-goals

- infer documentation truth with AI;
- auto-fix findings;
- add new relationship extraction;
- reconcile or adopt arbitrary Markdown;
- add CLI-specific validation rules;
- change MCP;
- sign quality reports.

## Compatibility

- DIR schema remains `0.3`;
- Snapshot format remains `1`;
- Relationship Projection Report remains a separate format-1 input;
- initial adoption may default to report-only;
- existing rendering and review contracts remain unchanged.

## Risks

- shallow rules can reward formatting rather than useful documentation;
- strict defaults can block small or partially indexed projects;
- scanner limitations can create misleading missing-relationship findings;
- rule evolution can destabilize automation.

Mitigations:

- stable rule IDs;
- versioned policy profiles;
- explicit NOT_EVALUATED outcomes when prerequisites are missing;
- report-only rollout;
- deterministic fixtures and policy-bound report identity.

## Verification

- rule-level positive/negative fixtures;
- policy boundary tests;
- input-order determinism;
- report SHA and tamper verification;
- RFC-0052 artifact coverage integration;
- RFC-0053 overflow/unresolved integration;
- report-only versus enforce behavior;
- full regression and architecture-samples smoke.

## Product value

VERY HIGH. Converts existing generation and semantic evidence into a reliable
product quality contract.

## Complexity

MEDIUM-HIGH.

## Recommendation

STRONGLY_RECOMMENDED for RFC-0054. It follows the Product Roadmap and directly
consumes the facts introduced by RFC-0052 and RFC-0053.
