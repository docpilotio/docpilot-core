# RFC-0029: Incremental Knowledge Impact Analysis

- Status: Proposed
- Target: Sprint 0.2
- Depends on: RFC-0027, RFC-0028
- Enables: RFC-0030 Section Impact Planning

## 1. Summary

DocPilot shall identify the bounded knowledge-graph region affected by a deterministic project change set. The analysis is provider-independent and does not generate documentation or map changes to document sections.

## 2. Motivation

RFC-0027 detects changed files and RFC-0028 persists snapshots. The existing knowledge model can describe source structure, but DocPilot still lacks a deterministic bridge from changed files to affected knowledge nodes, edges, and evidence. Without that bridge, later generation stages must reconsider the entire graph.

## 3. Decision

Introduce an incremental knowledge analyzer that:

1. ignores unchanged files;
2. resolves changed paths to evidence and file nodes;
3. identifies directly affected knowledge nodes;
4. expands the result through graph relationships by a configurable, bounded depth;
5. returns sorted and unique identifiers for deterministic downstream processing.

The default expansion depth is one hop.

## 4. API

```kotlin
fun interface IncrementalKnowledgeAnalyzer {
    fun analyze(
        knowledge: KnowledgeBuildResult,
        changes: ProjectChangeSet,
    ): IncrementalKnowledgeImpact
}
```

`IncrementalKnowledgeImpact` contains changed paths, direct node IDs, expanded node IDs, edge IDs, and evidence IDs.

## 5. Path resolution

Direct impact is resolved using:

- `Evidence.location.relativePath`; and
- the conventional `relativePath` knowledge-node attribute.

Paths are normalized to `/` separators before comparison.

## 6. Determinism

Every result collection is unique and lexicographically sorted. No AI provider, filesystem timestamp, hash-map iteration order, or graph traversal order may affect the output.

## 7. Boundaries

RFC-0029 does not:

- mutate or persist a knowledge graph;
- rebuild a partial `SourceIndex`;
- select architecture-document sections;
- invoke an AI provider;
- generate or merge document text.

Section selection belongs to RFC-0030.

## 8. Consequences

The implementation reuses the existing knowledge and evidence models rather than introducing a second graph representation. This keeps RFC-0029 small, compatible, and suitable as the deterministic input to the next planning stage.
