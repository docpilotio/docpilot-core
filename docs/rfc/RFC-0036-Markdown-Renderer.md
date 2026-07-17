# RFC-0036 — Markdown Renderer

## Status

Implemented

## Summary

RFC-0036 adds a deterministic Markdown renderer for the approved `ProjectSpecification` DIR model introduced by RFC-0035. The renderer is presentation-only and does not inspect source files, `SourceIndex`, or the Knowledge Graph.

## Scope

Included:

- `ProjectSpecificationMarkdownRenderer`
- Project, module, package, type, API, and property rendering
- Relationship, evidence, and unresolved rendering
- Deterministic ordering independent of input collection order
- Markdown escaping and safe inline code spans
- Explicit empty-section rendering
- Renderer unit tests
- Builder-to-renderer integration test

Excluded:

- Source scanning or specification construction
- CLI integration
- Output file writing
- AI providers and prompts
- Incremental rendering
- JSON or YAML rendering

## Design

The renderer implements the existing `SpecificationRenderer` API:

```kotlin
fun interface SpecificationRenderer {
    fun render(specification: ProjectSpecification): List<RenderedArtifact>
}
```

It produces one artifact:

- Path: `docs/project-specification.md`
- Media type: `text/markdown`

The document hierarchy is:

1. Project
2. Modules
3. Packages
4. Types
5. APIs
6. Properties
7. Relationships
8. Evidence
9. Unresolved

## Determinism

The renderer sorts every externally supplied collection by stable semantic keys before rendering. Sets are rendered in lexical order. Reordering equivalent DIR input therefore produces byte-identical Markdown.

## Evidence First

Evidence references are rendered on modules, packages, types, APIs, properties, and relationships. The complete evidence catalog is rendered separately with source location and confidence when available. Missing information is shown as `Unspecified`; the renderer does not infer values.

## Compatibility

No existing public API was modified. CLI, providers, builders, scanners, and writers remain unchanged.
