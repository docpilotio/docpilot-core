# DocPilot Roadmap

## Current milestone

### v0.5 MVP / POC — RFC-0001 through RFC-0043

Status: technical runtime gates passed on July 17, 2026; specification incremental execution, snapshot persistence, AI incremental generation, and documentation diff/review are implemented. Artifact-version policy remains pending.

Delivered baseline:

- source scanning
- knowledge construction
- DIR 0.3 specification building
- deterministic Markdown rendering
- Stable-ID specification incremental planning
- specification snapshot persistence and CLI workflow
- provider-independent AI incremental patch generation
- deterministic documentation diff and complete-review-before-merge
- prompt-package generation
- AI Provider SPI
- verified Ollama architecture generation

## Next

RFC-0044 is to be confirmed by Main Planning. Future work must preserve Clean Architecture, Evidence First, deterministic core outputs, the separation between Snapshot Incremental and Specification Incremental, and the RFC-0043 complete-review-before-merge invariant. The primary POC target remains `C:\WorkSpace\architecture-samples`.

## Release validation policy

Each release should retain a versioned snapshot containing build, test, CLI, provider, and error-handling evidence. OpenAI runtime validation is not implied unless explicitly included in the release scope.
