# RFC-0034: Source Scanner Enhancement

## Status
Implemented.

## Decision
RFC-0034 extends the existing language-neutral source model and token-based Kotlin extraction pipeline. It does not introduce a duplicate project/source tree.

## Scope
- Deterministic symbol identifiers
- Qualified names and parent-child relationships
- Nested declarations and constructors
- Visibility, modifiers, annotations, signatures, parameters, receiver and declared type text
- Full source ranges when token evidence is available
- Deterministic import and symbol ordering

## Compatibility
Existing constructors remain source-compatible through default values. CLI and provider modules are unchanged.

## Deferred
Semantic type resolution, call/inheritance graphs, data-flow analysis, documentation generation, and AI-provider integration remain outside RFC-0034.
