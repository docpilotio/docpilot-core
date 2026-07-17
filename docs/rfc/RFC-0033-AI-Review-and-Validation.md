# RFC-0033: AI Review and Validation

## Status
Implemented

## Summary
RFC-0033 adds an evidence-first review gate between generated Markdown normalization and document staging. A section is staged only when deterministic validation and independent AI review accept it.

## Scope
- Deterministic placeholder and evidence-reference validation
- Provider-neutral AI review request mapping
- Structured review response normalization
- Review issues, scores, feedback, and decisions
- Rejection before document merge or atomic write
- Review results preserved per generation job

## Decision policy
- Any `ERROR` issue produces `REJECTED`.
- One or more `WARNING` issues without errors produces `ACCEPTED_WITH_WARNINGS`.
- No errors or warnings produces `ACCEPTED`.

## Response contract
The reviewer returns a deterministic line-oriented response containing one decision, five scores, zero or more issues, and one feedback line. This avoids adding a JSON dependency while preserving provider independence.

## Failure behavior
A rejected or invalid review fails the current generation job. Remaining jobs are skipped. No staged content is merged or written, preserving the RFC-0032 atomicity contract.

## Out of scope
- Automatic regeneration loops
- Multi-provider voting
- Human approval UI
- Cross-document global consistency analysis
