# DocPilot Roadmap

## Current milestone

### v0.5 MVP / POC - RFC-0001 through RFC-0046

Status: technical runtime gates passed on July 25, 2026. Specification incremental
execution, snapshot persistence, AI incremental generation, documentation
diff/review, deterministic Relationship Semantics, and relationship-aware
incremental diff/review, and review-gated managed-block removal are implemented.
RFC-0046 focused verification, full regression verification, isolated
architecture-samples smoke, canonical completion handoff, and local main
integration are complete. Remote synchronization is the remaining Git action.
Artifact-version policy remains pending.

Delivered baseline:

- source scanning
- knowledge construction
- DIR 0.3 specification building
- deterministic Markdown rendering
- Stable-ID specification incremental planning
- specification snapshot persistence and CLI workflow
- provider-independent AI incremental patch generation
- deterministic documentation diff and complete-review-before-merge
- deterministic INTERNAL, EXTERNAL, and UNRESOLVED relationship endpoint semantics
- direct `DEPENDS_ON` component dependency projection and validation
- relationship-aware incremental diff, planning, AI context, and review Evidence
- explicit review-gated managed-block removal with reviewed-base conflict safety
- prompt-package generation
- AI Provider SPI
- verified Ollama architecture generation

## Next

RFC-0046 implementation, verification, isolated smoke, Main Planning update,
Completion Handoff, and local main integration are complete. RFC-0047 Auditable
Review Persistence and Resumable Conflict-safe Apply is implemented, locally
verified, and integrated into local main. RFC-0048 is selected as the Official CLI
Review Bundle Workflow; it is implemented, locally verified, and integrated into
local main. RFC-0049 has two candidate plans and neither is selected. The v0.5 Release
Provenance plan remains a later release-track candidate. Future work must preserve Clean
Architecture, Evidence First, deterministic core outputs, the separation between
Snapshot Incremental and Specification Incremental, and complete-review-before-
merge. The primary POC target remains `C:\WorkSpace\architecture-samples`.

## Release validation policy

Each release should retain a versioned snapshot containing build, test, CLI,
provider, and error-handling evidence. OpenAI runtime validation is not implied
unless explicitly included in the release scope.
