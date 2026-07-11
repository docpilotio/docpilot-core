# DSD-0001 — DocPilot Specification Language

Status: Baseline  
Version: 0.2

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
