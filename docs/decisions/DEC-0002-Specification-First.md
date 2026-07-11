# DEC-0002 — Specification First

Status: Accepted  
Priority: Critical

## Decision

DocPilot must create or update a structured Specification Model before generating documentation.

## Required Flow

```text
Source Code
    ↓
Analysis
    ↓
Knowledge Model
    ↓
Specification Model
    ↓
Documentation
```

## Consequences

- Renderers consume specifications rather than source code.
- Documentation formats remain replaceable.
- Specification changes can be reviewed independently from rendered outputs.
