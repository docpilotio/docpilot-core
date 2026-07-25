# RFC-0053 Candidate Plan B: Documentation Quality Validation

## Status

PROPOSED ALTERNATIVE — user approval required.

## Purpose

Introduce a deterministic Core quality report and gate for generated
documentation before expanding relationship extraction.

## Proposed scope

- versioned Documentation Quality Report contract;
- required artifact and section coverage;
- DIR entity-to-document coverage;
- Evidence reference integrity and minimum Evidence expectations;
- unresolved critical-item reporting;
- stale or contradictory relationship-reference detection;
- severity levels and stable rule IDs;
- deterministic report ordering and SHA-256 identity;
- warn/fail quality policy owned by Core;
- RFC-0052 artifact IDs and paths in diagnostics;
- offline verification of a persisted report.

## Architecture

The validator consumes the current DIR, RFC-0052 artifact catalog, rendered
artifact inventory, and configured Core policy. It returns structured findings;
renderers and adapters present the result without reproducing rule logic.

## Goals

- make documentation completeness measurable;
- prevent missing expected generated artifacts from being release-ready;
- provide stable machine-readable quality evidence;
- establish a gate usable by later CLI and Release workflows.

## Non-goals

- add new relationship kinds;
- infer truth with an AI provider;
- reconcile or merge arbitrary existing documents;
- auto-fix quality findings;
- CLI/UI/MCP policy implementation;
- signed evidence or external attestation.

## Expected changes

- Core quality rule and report models
- deterministic validator and policy
- artifact/Evidence coverage analyzers
- JSON/Markdown report codecs
- offline verifier
- tests and representative fixtures

## Compatibility

- validation is additive and does not alter DIR;
- RFC-0052 planning and rendering remain the source of artifact identity;
- initial policy may run in report-only mode before becoming a release gate;
- CLI and MCP remain thin or unchanged.

## Risks

- rules may encode shallow formatting rather than product quality;
- thresholds can block valid small projects;
- relationship consistency is limited by currently narrow relationship kinds;
- premature report versioning can constrain RFC-0053 semantic expansion.

Mitigation requires stable rule IDs, explicit policy profiles, structured
Evidence, and report-only rollout.

## Verification

- deterministic finding and report SHA tests;
- missing artifact/entity/Evidence fixtures;
- policy threshold boundary tests;
- offline verification and tamper tests;
- RFC-0052 multi-artifact integration;
- full regression.

## Product value

HIGH, but strongest after richer Semantic Relationships exist.

## Complexity

MEDIUM.

## Recommendation

RECOMMENDED AS FOLLOW-UP. Prefer this as RFC-0054 after Plan A so relationship
coverage and consistency rules have sufficiently rich semantic inputs.
