# DocPilot Constitution

This document defines the non-negotiable principles of the DocPilot
project.

All architecture decisions, implementations, workflows, and
contributions must remain consistent with these principles.

------------------------------------------------------------------------

## Article 1 --- Human Authority

AI assists developers but does not replace human judgment.

AI may analyze, propose, generate, review, and explain.

Humans remain responsible for:

-   approving architecture,
-   reviewing generated code,
-   applying changes,
-   validating behavior,
-   and deciding what is merged or released.

No AI-generated change is considered accepted until a human reviews and
approves it.

------------------------------------------------------------------------

## Article 2 --- Code Is the Primary Evidence

Source code is the primary evidence of implemented behavior.

DocPilot must not invent behavior that cannot be supported by:

-   source code,
-   configuration,
-   tests,
-   comments,
-   commit history,
-   or approved project decisions.

When implementation and documentation disagree, the inconsistency must
be reported.

DocPilot must not silently assume that either side is correct.

------------------------------------------------------------------------

## Article 3 --- Specification First

DocPilot does not generate documentation directly from source code.

The required transformation is:

``` text
Source Code
    ↓
Project Analysis
    ↓
Knowledge Model
    ↓
Specification Model
    ↓
Documentation and Diagrams
```

The Specification Model is the structured representation of the project.

Markdown, Mermaid, HTML, JSON, and other outputs are renderings of that
specification.

------------------------------------------------------------------------

## Article 4 --- Understanding Before Generation

DocPilot must understand project structure and relationships before
producing documentation.

Analysis may include:

-   modules
-   components
-   roles
-   responsibilities
-   dependencies
-   APIs
-   callbacks
-   events
-   states
-   lifecycles
-   threads
-   communication paths
-   architectural decisions

A list of classes and functions alone is not considered sufficient
project understanding.

------------------------------------------------------------------------

## Article 5 --- Evidence Before Assumption

Every significant specification statement must be traceable to evidence.

Statements with insufficient evidence must be:

-   excluded
-   marked as unresolved
-   explicitly presented as a proposal

Unverified assumptions must never be presented as implemented facts.

------------------------------------------------------------------------

## Article 6 --- Living Specifications

Specifications must evolve with the source code.

When code changes, DocPilot must identify:

-   affected components
-   affected APIs
-   affected relationships
-   affected behaviors
-   affected diagrams
-   affected documents

Only impacted specifications and outputs should be updated unless a
complete rebuild is explicitly requested.

------------------------------------------------------------------------

## Article 7 --- Explainable Changes

Every update should identify:

-   what changed
-   why it changed
-   supporting evidence
-   affected specifications
-   affected generated documents

------------------------------------------------------------------------

## Article 8 --- Platform-Independent Core

DocPilot Core remains independent from programming languages, platforms,
and AI vendors.

Platform-specific behavior belongs in analyzers or plugins.

------------------------------------------------------------------------

## Article 9 --- AI Vendor Independence

Prompt implementations may change.

The durable assets are:

-   Knowledge Model
-   Specification Model
-   Rules
-   Schemas
-   Decisions
-   Evidence

------------------------------------------------------------------------

## Article 10 --- Documentation Is an Engineering Artifact

Documentation is version-controlled and reviewed with the same rigor as
source code.

------------------------------------------------------------------------

## Article 11 --- Decisions Must Be Recorded

The project records:

-   Decisions
-   ADRs
-   DSDs
-   RFCs

Each important decision includes context, rationale, status, and
consequences.

------------------------------------------------------------------------

## Article 12 --- Safe Collaboration

Default workflow:

``` text
AI analyzes
    ↓
AI proposes
    ↓
Human reviews
    ↓
Human applies
    ↓
Human validates
```

Automation must remain reviewable and reversible.

------------------------------------------------------------------------

## Amendments

This Constitution may be amended only after documentation, review,
Product Owner approval, and recording in the decision history.

------------------------------------------------------------------------

## Status

-   Status: Accepted
-   Scope: DocPilot Ecosystem
-   Version: 0.1