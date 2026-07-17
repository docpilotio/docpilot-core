# RFC-0026 — Multi-Step Architecture Generation

Status: Proposed  
Version: 0.1  
Depends on: RFC-0025

## Summary

Introduce a deterministic architecture-generation plan that divides one large
architecture-document request into a bounded sequence of focused sections.

RFC-0026 begins with a provider-independent planning layer. It does not yet
replace `DefaultArchitectureGenerator`; instead, it establishes the stable
section model and deterministic default plan required for the multi-step
orchestrator.

## Motivation

A single AI request must simultaneously:

- understand retrieved project knowledge,
- decide the document structure,
- cover multiple architectural concerns,
- maintain consistent terminology,
- and fit within the provider output limit.

Even with RFC-0025 input budgeting, a single response may become incomplete,
shallow, or truncated. Section-by-section generation provides a predictable
output shape and bounded output per request.

## Target Flow

```text
ArchitectureGenerationRequest
    ↓
ArchitectureGenerationPlanner
    ↓
ArchitectureGenerationPlan
    ↓
One bounded generation request per section
    ↓
Deterministic assembly
    ↓
Architecture Document
```

## Scope of This Patch

This patch introduces:

- `ArchitectureSectionId`
- `ArchitectureSection`
- `ArchitectureGenerationPlan`
- `ArchitectureGenerationPlanner`
- `DefaultArchitectureGenerationPlanner`
- planner validation tests

A follow-up implementation step will add the orchestration and final assembly
without changing the planning contract.

## Default Architecture Sections

The default plan contains:

1. Executive Summary
2. System Context
3. Components and Responsibilities
4. Data and Control Flow
5. Dependencies and Integrations
6. Quality Attributes and Constraints
7. Risks and Recommendations

Each section includes:

- a stable identifier,
- a title,
- a focused generation instruction,
- an explicit order,
- and an output-token budget.

## Design Principles

1. Deterministic ordering
2. Stable section identifiers
3. Provider independence
4. Explicit output budgets
5. Immutable plan objects
6. Validation at construction time
7. No AI-based planning in the default implementation

## Proposed API

```kotlin
fun interface ArchitectureGenerationPlanner {
    fun plan(
        request: ArchitectureGenerationRequest,
    ): ArchitectureGenerationPlan
}
```

The default planner currently does not inspect provider details or execute AI
calls. The request argument is retained so future planners may use deterministic
request fields such as requested format or caller variables.

## Compatibility

No existing class is replaced in this patch. Existing architecture generation
continues to use `DefaultArchitectureGenerator`.

## Acceptance Criteria

1. The default plan is deterministic.
2. Section IDs are unique.
3. Section orders are unique and strictly positive.
4. Output-token budgets are positive.
5. The plan exposes sections in ascending order.
6. Existing tests continue to pass.
7. `./gradlew clean test` reports `BUILD SUCCESSFUL`.

## Next Implementation Step

The next RFC-0026 patch will add:

- `MultiStepArchitectureGenerator`
- one AI generation request per planned section,
- shared terminology/context variables,
- deterministic Markdown assembly,
- partial-failure reporting,
- and CLI selection between single-step and multi-step modes.
