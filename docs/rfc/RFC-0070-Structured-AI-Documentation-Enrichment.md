# RFC-0070 — Structured AI Documentation Enrichment

## Status

Implemented and technically validated. Product Validation remains `PRODUCT_VALIDATION_FAIL / NOT_APPROVED`; `PV-009: PENDING`.

## Decision

DocPilot may enrich only explicit narrative sections of deterministic documentation. AI is not an authority for stable IDs, evidence, unresolved items, source bindings, relationships, ownership, types, paths, or manifest metadata. Failure or rejection preserves the deterministic document.

Supported section contracts are `architecture-description`, `feature-summary`, `feature-description`, `scenario-flow`, and `contract-description`. Output is inserted only inside `docpilot:enrichment` managed markers before Evidence or Unresolved. Responses containing headings, lists, links, resources, code fences, canonical-field redefinitions, absolute paths, secrets, or protected references are rejected.

`DocumentationEnrichmentRequest`, target, result, record, status, prompt, engine, and managed-section types are provider-independent Core models. Records include provider/model, canonical input and template identities, target identity, source/evidence/unresolved references, narrative SHA-256, status, invocation/cache flags, and masked diagnostics. Statuses are `APPLIED`, `FAILED`, `FALLBACK`, `SKIPPED`, `NOT_APPLIED`, `STALE`, and `REJECTED`.

CLI enrichment requires `--enrich --provider <id> --model <model> --confirm`. `--enrichment-target` is repeatable. Without `--enrich`, no provider is resolved or invoked and eligible artifacts receive `NOT_APPLIED` provenance. Providers are explicit; there is no automatic fallback. Fixture success/failure providers support deterministic validation, while Ollama and OpenAI continue through the existing `AiProvider` SPI.

The cache identity binds provider, model, canonical input SHA-256, prompt template identity, artifact, and section. A matching persisted record and narrative are reused without invocation. Semantic changes make the record stale by identity mismatch. Bundle/Manifest/Receipt serialization is additive and never stores full prompts or raw provider responses.
