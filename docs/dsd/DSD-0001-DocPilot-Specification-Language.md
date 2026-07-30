# DSD-0001 — DocPilot Specification Language

Status: Active baseline
Version: 0.2 authoring baseline with DIR 0.3 runtime extension

> Version policy: DIR 0.2 remains the legacy, source-compatible baseline for manual `ProjectSpecification` construction. `DefaultSpecificationBuilder` emits DIR 0.3, which extends the runtime model with package, API, property, relationship, and deterministic rendering data. Specification Snapshot format 1 accepts DIR 0.3 only. Snapshot format and DIR schema remain independent version lines.

## Purpose

This document defines the first version of the DocPilot Specification Language.

The language provides a structured, reviewable, and AI-readable representation of software project knowledge.

It is used between the Analyzer layer and the Renderer layer.

```text
Source Code
    ↓
Analyzer
    ↓
Knowledge Model
    ↓
DocPilot Specification Language
    ↓
Renderers
    ↓
Markdown / Mermaid / JSON / HTML / PDF
```

The specification language is not tied to one Android framework, architecture style, or AI vendor.

---

## Design Goals

The language must be:

- human-readable,
- machine-readable,
- deterministic,
- versioned,
- traceable to evidence,
- extensible through profiles and plugins,
- and independent from output formats.

---

## Canonical Format

Version 0.2 uses YAML as the canonical authoring and interchange format.

Recommended file extension:

```text
.docpilot.yaml
```

---

## Root Structure

```yaml
docpilot:
  schema_version: "0.2"
  project: {}
  modules: []
  components: []
  relationships: []
  decisions: []
  evidence: []
  unresolved: []
  extensions: {}
```

---

## Project Definition

```yaml
project:
  id: sample-android-app
  name: Sample Android App
  description: Example Android project
  platforms:
    - android
  languages:
    - kotlin
  build_systems:
    - gradle
```

Required fields:

- `id`
- `name`

---

## Module Definition

```yaml
modules:
  - id: app
    name: App
    path: app
    description: Main Android application module
```

Required fields:

- `id`
- `name`

---

## Component Definition

```yaml
components:
  - id: tasks-view-model
    name: TasksViewModel
    module_id: app
    kind: view-model
    visibility: public

    role: Coordinates task-list presentation behavior

    responsibilities:
      - Load task data
      - Expose UI state
      - Process user actions

    lifecycle:
      scope: view-model

    thread_model:
      default_context: main

    dependencies:
      - task-repository

    apis: []
    callbacks: []
    events: []
    states: []
```

Required fields:

- `id`
- `name`
- `module_id`
- `kind`
- `role`

---

## Initial Component Kinds

- application
- activity
- fragment
- composable
- view-model
- service
- manager
- repository
- controller
- facade
- adapter
- client
- provider
- worker
- receiver
- data-source
- utility
- unknown

Profiles may introduce additional kinds.

---

## API Definition

```yaml
apis:
  - id: observe-tasks
    name: observeTasks
    signature: "fun observeTasks(): Flow<List<Task>>"
    visibility: public
    purpose: Exposes the current task stream

    returns:
      type: "Flow<List<Task>>"

    thread:
      caller_context: any
      execution_context: background

    effects:
      - Observes persisted task data

    evidence_refs:
      - ev-task-repository-observe
```

---

## Callback Definition

```yaml
callbacks:
  - id: on-sync-completed
    name: onSyncCompleted
    signature: "fun onSyncCompleted(result: SyncResult)"
    purpose: Reports completion of a synchronization operation
    delivery_thread: main
    evidence_refs:
      - ev-sync-callback
```

---

## Event Definition

```yaml
events:
  - id: task-updated
    name: TaskUpdated
    category: domain
    description: Indicates that a task changed
    emitted_by:
      - task-repository
    observed_by:
      - tasks-view-model
    evidence_refs:
      - ev-task-updated
```

---

## State Definition

```yaml
states:
  - id: loading
    name: Loading
  - id: content
    name: Content
  - id: error
    name: Error
```

Optional transitions:

```yaml
state_transitions:
  - from: loading
    to: content
    trigger: data-loaded
  - from: loading
    to: error
    trigger: load-failed
```

---

## Relationship Definition

```yaml
relationships:
  - id: rel-view-model-depends-on-repository
    type: depends_on
    source: tasks-view-model
    target: task-repository
    description: TasksViewModel obtains task data from TaskRepository
    evidence_refs:
      - ev-view-model-constructor
```

Initial relationship types:

- owns
- creates
- depends_on
- calls
- observes
- publishes
- subscribes_to
- implements
- extends
- configures
- communicates_with

---

## Decision Definition

```yaml
decisions:
  - id: dec-primary-user
    title: Primary user is an Android developer
    status: accepted
    rationale: The initial product is optimized for Android development workflows
```

Supported statuses:

- proposed
- accepted
- rejected
- deprecated
- superseded

---

## Evidence Definition

```yaml
evidence:
  - id: ev-view-model-constructor
    type: source
    file: app/src/main/java/com/example/tasks/TasksViewModel.kt
    symbol: TasksViewModel
    line_start: 20
    line_end: 28
    summary: TasksViewModel receives TaskRepository as a dependency
    confidence: high
```

Supported evidence types:

- source
- configuration
- test
- comment
- commit
- decision
- manual-review

Supported confidence values:

- high
- medium
- low

Low-confidence information must not be presented as implemented fact.

---

## Unknown and Unresolved Information

```yaml
unresolved:
  - id: unresolved-callback-thread
    subject: sync-callback
    question: Callback delivery thread could not be verified
    required_action: Review runtime implementation
```

---

## Extension Model

Platform-specific information is stored under profile namespaces.

```yaml
extensions:
  android:
    manifest_components:
      - com.example.SyncService
    min_sdk: 26
    target_sdk: 35

  compose:
    navigation_enabled: true
```

The Core must preserve unknown extension fields.

---

## Validation Rules

1. Every ID must be unique within the specification set.
2. Every Component must reference an existing Module.
3. Every relationship source and target must exist.
4. Every evidence reference must exist.
5. Accepted facts must not depend only on low-confidence evidence.
6. Platform-specific fields must be stored in extension namespaces.
7. Renderers must not invent missing information.
8. Unknown values must be omitted or represented as unresolved.

---

## File Organization

```text
.docpilot/
├── project.docpilot.yaml
├── modules/
├── components/
├── evidence/
└── extensions/
```

Small projects may use one specification file.

Large projects should split specifications by module or component.

---

## Human-AI Collaboration

```text
Analyzer extracts evidence
    ↓
AI proposes specification changes
    ↓
Developer reviews the proposal
    ↓
Approved specification is updated
    ↓
Renderers update documents and diagrams
```

AI-generated specification content remains a proposal until human approval.

---

## Non-Goals for Version 0.2

Version 0.2 does not define:

- executable code generation,
- automatic source-code modification,
- a custom parser grammar,
- binary serialization,
- a graph database implementation,
- or provider-specific prompts.


---

## RFC-0057 Canonical Runtime Baseline

The canonical runtime baseline is derived from source constants rather than inferred from document version labels.

| Contract | Current value |
|---|---|
| Manual `ProjectSpecification` default | DIR `0.2` |
| `DefaultSpecificationBuilder` output | DIR `0.3` |
| Specification Snapshot | format `1` |
| Snapshot-supported DIR | `0.3` |
| Review Bundle | format `1` |
| Relationship Projection Report | format `1` |
| Evolution Report | format `1` |

DIR 0.2 authoring examples in this document remain historical/source-compatible guidance. Runtime code and current generated specifications use DIR 0.3.

## DIR 0.4 Migration Readiness

RFC-0057 does not define or implement DIR 0.4. It establishes the following requirements for later RFCs:

1. Existing DIR 0.3 Snapshot data must remain readable through the format-1 reader or a retained legacy reader.
2. Stored DIR 0.3 data must not be silently rewritten in place.
3. A DIR 0.3 to 0.4 conversion, when required, must be an explicit deterministic migration operation with input and output identities.
4. Existing Project, Module, Package, Component, API, Property, Relationship, Evidence, and Unresolved Stable IDs must be preserved.
5. Feature and Scenario IDs must use deterministic namespaces that cannot collide with existing entity IDs.
6. New fields must remain absent or explicitly unresolved when Evidence is insufficient; renderers and AI must not invent values.
7. RFC-0052 Artifact Plan semantic hashes for existing DIR 0.3 fixtures must remain compatible.
8. RFC-0055 ownership, reconciliation, retained-content, and user-decision contracts must remain compatible.
9. RFC-0056 must continue to verify and compare DIR 0.3 states after DIR 0.4 support is introduced.
10. Rollback must preserve the original DIR 0.3 Snapshot and document artifacts.

The preferred direction is to retain Snapshot format 1 for DIR 0.3 and introduce Snapshot format 2 only when DIR 0.4 storage is actually required. Format 1 readers must remain available. RFC-0057 itself changes neither schema nor Snapshot format.

Planned documentation-expansion concepts include `FeatureSpecification`, `EntryPointSpecification`, `ScenarioSpecification`, `InteractionStep`, `ContractSpecification`, `DocumentationClaim`, and `DiagramSpecification`. Documentation Profiles are defined by RFC-0058; the exact fields and identities of Feature, Entry Point, Scenario, Interaction, Contract, Claim, and Diagram production concepts remain deferred to RFC-0059 and later RFCs.

## RFC-0058 Documentation Profiles and Document Contracts

RFC-0058 adds a runtime-only policy layer beside DIR 0.3. It does not change `ProjectSpecification`, Snapshot format 1, or any persisted DIR contract.

```text
DocumentationProfile
  id
  version
  documentDefinitions[]

DocumentDefinition
  stableKey
  type
  purpose
  audience
  multiplicity
  pathPolicy
  sections[]
  rendererCapabilities[]
  completenessPolicy
  ownershipPolicy
  dependencyRules[]
  requiredModel

DocumentationProfileResolution
  profileSemanticSha256
  documents[]
  artifactBindings[]
  findings[]
  resolutionSha256
```

The first built-in Profile is `kotlin-android@1`. Profile Resolution consumes DIR 0.3 but never writes back into DIR. Feature and Contract requirements remain `DEFERRED` until a later RFC introduces canonical production entities. The Profile layer may be persisted only through an explicitly versioned future format; it must not be silently embedded into Snapshot format 1.

Stable IDs preserve semantic continuity:

```text
document-definition:{profileId}:{stableKey}
document:{profileId}:{stableKey}:{scopeId}
section:{profileId}:{stableKey}:{sectionId}
```

Titles, purposes, Evidence policies, paths, capabilities, completeness, ownership, and dependencies affect semantic SHA-256. Timestamps, absolute paths, filesystem order, object identity, and AI narrative do not.

## RFC-0059 DIR 0.4 Feature, Entry Point, and Scenario Foundation

DIR 0.4 additively extends `ProjectSpecification` with canonical Feature, Entry
Point, and Scenario collections. Scenario Steps are nested in their owning
Scenario. Existing DIR 0.2 manual construction and the DIR 0.3 automatic builder
remain supported; RFC-0059 performs no Feature discovery.

Every new entity has an externally supplied deterministic Stable ID and explicit
Evidence references. Entry Point and Step kinds use fixed allowlists. Ambiguous
or unsupported facts reference existing `UnresolvedItem` records and are never
selected implicitly. Scenario Step Stable IDs are semantic and independent of
their numeric order; canonical order is `order` followed by Stable ID.

Snapshot format 2 stores DIR 0.4. Snapshot format 1 remains the DIR 0.3 format.
The explicit format-1-to-format-2 migration preserves existing entities,
Evidence, unresolved records, and Stable IDs, initializes the new collections
empty, and never calls AI or overwrites its input.