# DEC-0003 — Intermediate Representation

Status: Accepted  
Priority: Critical

## Decision

DocPilot will use a versioned intermediate representation called DIR (DocPilot Intermediate Representation).

## Context

A stable intermediate representation is needed to separate analyzers from renderers and AI providers.

## Consequences

- Analyzers produce or propose DIR-compatible data.
- Renderers consume DIR.
- Markdown, Mermaid, JSON, HTML, and PDF outputs can evolve independently.
- DIR must support evidence and unresolved information.
