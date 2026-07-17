# DocPilot Roadmap

## Current milestone

### v0.5 MVP — RFC-0001 through RFC-0038

Status: technical runtime gates passed on July 17, 2026; artifact-version policy pending.

Delivered baseline:

- source scanning
- knowledge construction
- DIR 0.3 specification building
- deterministic Markdown rendering
- Stable-ID specification incremental planning
- prompt-package generation
- AI Provider SPI
- verified Ollama architecture generation

## Next

RFC-0039 begins from the stabilized v0.5 baseline. Future work must preserve Clean Architecture, Evidence First, deterministic core outputs, and the separation between Snapshot Incremental and Specification Incremental.

## Release validation policy

Each release should retain a versioned snapshot containing build, test, CLI, provider, and error-handling evidence. OpenAI runtime validation is not implied unless explicitly included in the release scope.
