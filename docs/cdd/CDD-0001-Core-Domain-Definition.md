# CDD-0001 — Core Domain Definition
Status: Active domain baseline through RFC-0066

## DIR 0.5 Contract Extraction Domain

RFC-0066 adds an AI-independent Contract Extraction Engine after Feature Discovery. It owns role selection, owner and type resolution, shape projection, Evidence and unresolved binding, duplicate policy, and Contract Stable IDs. Scanner observations remain syntax facts; generated prose is never an extraction input. The default pipeline emits DIR 0.5 and Snapshot format 3 while legacy DIR/Snapshot readers remain available.

## DIR 0.4 Feature and Navigation Domain

The canonical Feature workflow additively extends the base specification with Feature, Entry Point, Scenario, ordered Scenario Step, and deterministic UnresolvedItem entities. Compose route, registration, destination, function-reference, graph, argument, and argument-link observations remain source Evidence rather than inferred business semantics.

The base Builder remains DIR 0.3. AI-independent discovery emits DIR 0.4, Snapshot format 2 stores DIR 0.4, and Snapshot format 1/DIR 0.3 compatibility remains available. Names are not business semantics, and AI output never creates canonical Feature or navigation identity.


Status: Baseline  
Version: 0.1

## Purpose

This document defines the core domain model of DocPilot.

The domain model represents concepts that exist independently of any programming language, framework, AI vendor, or renderer.

It is the foundation for the Specification Language, Architecture Decisions, and future implementations.

---

## Core Principle

DocPilot does not treat source code as documentation.

DocPilot interprets a project through a structured domain model.

```text
Source Code
      ↓
Analyzer
      ↓
Knowledge Model
      ↓
Core Domain Model
      ↓
Specification Model
      ↓
Renderers
```

---

## Root Object

The root object is `Project`.

```text
Project
 ├── Module
 ├── Component
 ├── Relationship
 ├── Decision
 └── Configuration
```

---

## Project

Represents one software project.

Typical attributes:

- name
- platform
- languages
- build systems
- repositories
- modules

---

## Module

Represents a logical or build boundary inside a project.

Examples:

- `app`
- `core`
- `data`
- `feature:settings`

A Module owns Components.

---

## Component

Represents the smallest meaningful engineering unit.

A Component is not required to map one-to-one to a class.

Examples include:

- Android Service
- Activity
- Fragment
- ViewModel
- Repository
- Manager
- Controller
- Worker
- Receiver
- Facade
- Adapter

Every Component may define:

- Role
- Responsibilities
- Lifecycle
- APIs
- Callbacks
- Events
- Dependencies
- States
- Thread Model
- Evidence

---

## Relationship

Defines how domain entities interact.

Initial relationship types include:

- owns
- creates
- depends_on
- observes
- calls
- publishes
- subscribes_to
- implements
- extends
- configures
- communicates_with

---

## Decision

Represents an accepted engineering or product decision.

Examples:

- primary user,
- initial validation target,
- architecture choice,
- design rule.

---

## Configuration

Represents external configuration that influences build-time or runtime behavior.

Examples:

- Gradle configuration,
- AndroidManifest declarations,
- build variants,
- feature flags,
- plugin configuration.

---

## Design Rules

1. Every Component belongs to exactly one Module.
2. Every API belongs to one Component.
3. Every Callback belongs to one Component.
4. Relationships must reference existing entities.
5. Accepted facts must be traceable to Evidence.
6. Specifications are generated from the domain model, not directly from source text.
7. Android-specific details belong in Android extensions or profiles.
