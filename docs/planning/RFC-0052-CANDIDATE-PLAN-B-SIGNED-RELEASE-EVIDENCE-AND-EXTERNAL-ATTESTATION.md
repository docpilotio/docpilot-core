# RFC-0052 Candidate Plan B

# Signed Release Evidence and External Attestation

## Type

QUALITY_RELEASE

## Problem

RFC-0049 provides deterministic Release Evidence Manifest format 1, exact commit
binding, artifact integrity, and offline verification. It proves internal
consistency but not who authorized or published the evidence.

Consumers outside the producing workspace need optional cryptographic provenance
without changing the deterministic unsigned manifest.

## Proposed outcome

Define a detached, versioned signature envelope for Release Evidence Manifest
format 1 and optional external attestation export.

## Goals

- keep Release Evidence Manifest format 1 unchanged;
- detached Signature Envelope format 1;
- exact manifest payload SHA binding;
- signer/key identity metadata;
- algorithm agility with a narrowly approved initial algorithm set;
- offline signature verification;
- explicit trust policy supplied by the caller;
- deterministic attestation statement derived from verified evidence;
- no network requirement for signing or verification;
- thin CLI signing and verification adapters;
- clear unsigned, signed-untrusted, and signed-trusted results.

## Non-goals

- embedded private keys;
- automatic key generation;
- mandatory cloud transparency logs;
- organization PKI administration;
- remote build execution;
- review lifecycle signing;
- changing Release Evidence Manifest format 1;
- making MCP a trust authority.

## Architecture

Core/release tooling owns canonical signature payload construction, signature
envelope validation, trust-policy evaluation, and attestation derivation.
Pluggable key providers own private-key operations. CLI owns paths, invocation,
and result presentation.

## Expected change areas

- `docpilot-release` signature and attestation contracts
- key-provider SPI
- offline verifier
- release CLI commands and structured output
- deterministic/tamper/interoperability tests

## Contract impact

- Release Evidence Manifest format 1: unchanged
- new Signature Envelope format 1
- new Attestation Statement format 1
- no Review Bundle or lifecycle contract changes

## Risks

- premature algorithm or key-provider lock-in;
- confusing integrity with signer trust;
- accidental private-key exposure;
- nondeterministic signature metadata;
- scope growth into external PKI or hosted services.

## Verification

- standard test vectors;
- manifest, signature, identity, and policy tamper tests;
- offline verification without network;
- deterministic envelope payload tests;
- key-provider failure isolation;
- stable CLI exit codes;
- full release-gate regression.

## Priority

CONDITIONAL

This is the natural next release-security layer, but local review concurrency and
retention are more immediate operational risks after RFC-0051.
