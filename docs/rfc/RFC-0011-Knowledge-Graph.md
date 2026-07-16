# RFC-0011 — Knowledge Graph

Status: Accepted
Version: 0.1
Target: Sprint 4

## Summary

Introduce DocPilot-owned knowledge graph models for software entities and relationships.

This commit defines models only. It does not build the graph, serialize JSON, call AI, or modify the CLI.

## Models

- `KnowledgeGraph`
- `KnowledgeNode`
- `KnowledgeEdge`
- `KnowledgeUnresolvedItem`
- `KnowledgeNodeKind`
- `RelationshipType`

## Validation

- Node IDs are unique.
- Edge IDs are unique.
- Edge endpoints reference existing nodes.
- IDs and names are non-blank.
- Confidence is between 0.0 and 1.0.
- Evidence references are non-blank.

## Acceptance Criteria

`./gradlew clean test` reports `BUILD SUCCESSFUL`.
