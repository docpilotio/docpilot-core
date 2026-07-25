# RFC-0050 Candidate Plan B: Signed Release Evidence and External Attestation

## Status

DEFERRED - NOT SELECTED AS RFC-0050

## Type

QUALITY_RELEASE / ARCHITECTURE_ENABLER

## Problem

RFC-0049 proves deterministic evidence integrity with SHA-256, but a checksum does
not prove who approved or produced the evidence. A party able to replace a
manifest can also recompute its checksum.

Projects distributing release evidence outside the repository may eventually
need signer authenticity, key identity, and a portable attestation envelope.

## Product outcome

A consumer can verify offline that one unchanged RFC-0049 manifest was signed by
an explicitly trusted release identity and optionally exported as an external
attestation.

## Goals

1. Preserve Release Evidence Manifest format 1 unchanged.
2. Define a versioned detached signature envelope.
3. Bind signature to manifest payload SHA-256 and release/candidate identity.
4. Support explicit trusted-key configuration.
5. Verify signatures offline and fail closed.
6. Define key rotation and revoked-key behavior.
7. Produce deterministic signature metadata apart from algorithm-required bytes.
8. Permit an optional standards-aligned attestation projection.

## Candidate contract

```text
releaseSignatureFormatVersion
releaseId
coreCommit
manifestPayloadSha256
algorithm
keyId
signature
```

Candidate algorithms and external formats require a security review during
detailed specification. No algorithm is selected by this plan.

## Architecture value

- adds authenticity to RFC-0049 integrity;
- supports detached verification without changing Core runtime;
- prepares evidence for external supply-chain systems;
- keeps signing keys outside manifests and runtime code.

## Non-goals

- automatic tag, push, publication, or release;
- hosted transparency service;
- mandatory network access;
- credential management product;
- Review Bundle signing or reviewer identity;
- Review Bundle Lifecycle or Apply Receipt;
- MCP orchestration;
- CI vendor migration.

## Expected change areas

```text
docpilot-release signature envelope and verifier
signing/verifying key adapter boundary
detached signature repository
release verifier exit/error extensions
security and interoperability tests
Canonical security documentation
```

## Risks

- premature algorithm selection;
- private-key leakage through process arguments or logs;
- platform keystore differences;
- confusing checksum integrity with signature authenticity;
- creating a custom attestation format instead of using a mature standard.

## Verification

- valid signature and trusted key;
- modified manifest and signature;
- unknown, rotated, and revoked keys;
- wrong release ID or candidate commit;
- deterministic envelope;
- offline verification;
- no secret material in evidence or diagnostics;
- RFC-0049 regression.

## Priority

RECOMMENDED AFTER Review Bundle Lifecycle and Apply Receipt, unless external
distribution or compliance requirements make release signer authenticity urgent.

Plan B strengthens the release track but contributes less immediate product value
than closing the durable review audit chain.
