# RFC-0054 Candidate Plan B: Scanner Relationship Observation Coverage

## Status

PROPOSED ALTERNATIVE — user approval required.

## Purpose

Expand source scanners so supported languages automatically populate the
RFC-0053 `SourceCall` and explicit `SourceSuperTypeReference` contracts with
reliable source ranges.

## Problem

RFC-0053 implements deterministic Core projection for CALLS and explicit
supertypes, but the current simple Kotlin extractor does not parse call sites.
Legacy supertype text is promoted only when an in-project declaration proves the
target kind. External and ambiguous type semantics remain intentionally absent.

## Proposed scope

- Kotlin direct call-site extraction for statically recognizable calls;
- callable qualified-name/signature observations where provable;
- explicit Kotlin superclass/interface classification where symbol evidence
  permits;
- import-alias-aware target references;
- exact source locations;
- scanner capability/coverage report;
- deterministic ambiguity and unsupported-syntax findings;
- scale limits before Knowledge construction;
- fixtures for overloads, extension functions, constructors, and nested types.

## Architecture

Scanner output remains language-neutral `SourceCall` and
`SourceSuperTypeReference`. Scanner code observes syntax; Core Knowledge,
resolution, identity, aggregation, threshold, and quality rules remain unchanged.

## Goals

- make RFC-0053 relationships available in normal Kotlin analysis;
- measure scanner coverage rather than silently missing observations;
- improve inputs to Documentation Quality Validation.

## Non-goals

- full compiler frontend or semantic type checker;
- dynamic dispatch expansion;
- runtime tracing;
- Java/Kotlin cross-build resolution beyond available source evidence;
- relationship policy in CLI or MCP;
- quality pass/fail rules.

## Risks

- a simple token extractor cannot resolve all overloads safely;
- false-positive CALLS are worse than explicit unsupported findings;
- compiler-grade resolution could make the RFC too large;
- prioritizing extraction delays the quality contract.

Mitigations:

- emit only proven observations;
- represent ambiguity explicitly;
- publish scanner capability/coverage facts;
- keep compiler integration out of scope.

## Verification

- token/parser fixtures for supported calls;
- negative ambiguity tests;
- input-order and whitespace determinism;
- exact source location Evidence;
- RFC-0053 aggregation/threshold integration;
- large Kotlin fixture performance;
- full regression.

## Product value

HIGH for immediately visible relationship coverage, but narrower than a general
quality contract.

## Complexity

HIGH because safe call resolution exceeds simple syntax extraction quickly.

## Recommendation

CONDITIONAL. Retain as a focused follow-up or include only bounded scanner
coverage work after RFC-0054 quality policy can measure missing prerequisites.
