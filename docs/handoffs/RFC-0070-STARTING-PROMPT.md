# RFC-0070 — Structured AI Documentation Enrichment

Start from RFC-0069 Bundle Format 1 without changing DIR 0.5, Snapshot Format 3, stable artifact identity/path, ownership state, or deterministic non-AI output. Design additive enrichment records for provider, model, canonical input identity, prompt/template identity, target artifact/section, narrative hash, success/failure/fallback, and explicit `NOT_APPLIED`.

Before enrichment work, close RFC-0069's documented partial items: parser-backed local Markdown link/fragment/resource validation, unexpected managed-file policy, receipt-file integrity verification, selective-generation merging of the complete persisted Bundle index, and registry-assisted Profile binding verification. Preserve offline verification and ensure provider calls are never made when enrichment is not requested.
