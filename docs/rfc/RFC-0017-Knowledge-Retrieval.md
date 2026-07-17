# RFC-0017 — Knowledge Retrieval

Status: Accepted

## Problem

DocPilot builds a Knowledge Graph and Evidence collection, but has no common
way to select only the information needed for a document-generation task.
Sending the complete graph to every prompt is wasteful and obscures which
facts were selected.

## Decision

Add a deterministic retrieval layer:

```text
KnowledgeQuery
      ↓
KnowledgeRetriever
      ↓
KnowledgeResult
```

The first version filters by node name, node kind, source-relative path, and a
maximum node count. Results include matching nodes, incident edges, and all
evidence referenced by those nodes and edges.

## Scope

- simple case-insensitive name matching,
- node-kind filtering,
- relative-path filtering,
- bounded results,
- stable ordering,
- associated edges and evidence.

## Out of Scope

- vector search,
- embeddings,
- semantic similarity,
- AI ranking,
- query DSLs,
- graph databases,
- incremental indexes.
