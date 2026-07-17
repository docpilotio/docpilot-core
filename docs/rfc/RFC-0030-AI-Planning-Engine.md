# RFC-0030: AI Planning Engine

- Status: Proposed
- Target: Sprint 0.3
- Depends on: RFC-0026, RFC-0027, RFC-0029

## Summary

RFC-0030 introduces a deterministic, provider-independent planning engine that
turns project changes and incremental knowledge impact into an ordered queue of
architecture-document generation jobs.

The engine decides:

- which architecture sections need regeneration;
- why each section is included;
- execution priority;
- dependency-safe job ordering;
- bounded prompt-context token allocation.

It does not render prompts and does not invoke an AI provider. Those concerns
remain in RFC-0031 and RFC-0032.

## Data flow

```text
ProjectChangeSet
        +
IncrementalKnowledgeImpact
        +
ArchitectureGenerationPlan
        +
PlanningConstraints
        |
        v
DefaultIncrementalGenerationPlanner
        |
        v
IncrementalGenerationPlan
        |
        +-- GenerationJob[]
        +-- dependency order
        +-- context token budgets
```

## Determinism

For identical inputs, the planner produces the same selected sections, reasons,
priorities, dependency order, and budgets. No timestamps, provider state, or
randomness are included in the domain result.

## Section selection

The default policy uses stable keyword rules over changed relative paths and
affected knowledge-node identifiers. If no specific rule matches, the planner
falls back to `components-and-responsibilities`. `executive-summary` is refreshed
whenever any architecture section changes.

## Dependency ordering

The execution queue is topologically ordered. Examples:

- data/control flow follows components;
- quality attributes follow components;
- the executive summary follows every selected detail section.

This execution order is independent from final document presentation order.

## Token budgeting

Every job receives a minimum context budget. Remaining context is distributed
in deterministic 128-token increments, favoring higher-priority jobs and
respecting per-job limits. Output-token limits remain owned by
`ArchitectureSection`.

## Non-goals

- prompt rendering;
- AI-provider selection or invocation;
- generated Markdown persistence;
- semantic embeddings;
- model-specific token counting.

## Follow-up

RFC-0031 consumes `GenerationJob` to build provider-neutral prompt plans.
