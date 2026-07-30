# RFC-0060 — Deterministic Kotlin and Android Feature Discovery

Status: `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS`

## Decision

DocPilot derives DIR 0.4 Feature entities without AI. `DeterministicFeatureDiscoveryEngine`
consumes the Source Index, Knowledge Graph, and base Project Specification after existing
component and relationship projection.

## Supported discovery

- Activity, Fragment, Service, BroadcastReceiver, ContentProvider, and WorkManager
  contracts are recognized from an exact framework supertype.
- A simple supertype requires a unique explicit import to an allowlisted type. Class-name
  suffixes are never Evidence.
- A Feature is rooted at one evidence-backed entry-point component.
- Participant traversal supports `CALLS`, `DEPENDS_ON`, `IMPLEMENTS`, and `EXTENDS`, with
  maximum depth 4 and maximum 32 participants.
- A Scenario is emitted only for direct evidence-backed internal `CALLS` edges.
- Scenario Step identity contains relationship identity, never numeric order.

## Determinism and integrity

All discovery collections use stable-ID ordering. `FeatureDiscoveryIntegrity` computes
SHA-256 over policy version and the canonical discovery payload. Timestamps, locale,
absolute paths, display labels, and AI output are excluded. Modified results are rejected.

## Builder integration

`DefaultSpecificationBuilder.CURRENT_SCHEMA_VERSION` is `0.4`. Projects without proven
entry points produce valid DIR 0.4 with empty Feature collections. Snapshot format 2
persists Builder output; format 1 and DIR 0.3 readers remain unchanged.

## Deliberate limits

Compose destinations are not emitted because route registration arguments are not yet
preserved strongly enough to prove destination identity. Dependency edges do not establish
runtime order. Callback, coroutine, Flow, branch, and dynamic order are not inferred.
Feature Markdown and Feature Artifacts remain deferred.
