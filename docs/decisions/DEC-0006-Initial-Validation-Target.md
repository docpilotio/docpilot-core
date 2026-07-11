# DEC-0006 — Initial Validation Target

Status: Accepted  
Date: 2026-07-12  
Priority: High

## Decision

DocPilot's first validation target will be publicly available Android projects hosted on GitHub.

The initial validation scope is not limited to Wear OS projects.

Wear OS, Bluetooth, Wi-Fi, background services, and commercial Android code remain supported use cases, but they are not required for the first validation cycle.

## Context

The original motivating project is commercial code.

That code cannot be freely published, shared, or used as a public reproducible test target.

Public Android repositories provide:

- reproducible source access,
- repeatable analysis,
- visible expected behavior,
- diverse project structures,
- community-verifiable results,
- and safe examples for documentation and demonstrations.

## Constraints

- Only repositories with compatible open-source licenses may be included in the official validation corpus.
- Each test repository must be pinned to a specific commit.
- Generated documentation must preserve source attribution.
- Repository code must not be redistributed when its license does not permit redistribution.
- Large or unstable repositories should not be used in the first validation set.

## Initial Repository Selection Criteria

A repository should preferably:

1. Be an Android project with a recognized open-source license.
2. Use Kotlin as a primary or significant language.
3. Build with Gradle.
4. Have a clear multi-file or multi-module structure.
5. Include enough architecture to test components, APIs, dependencies, states, callbacks, or data flow.
6. Have a stable commit that can be pinned.
7. Be small or medium-sized for the first validation cycle.
8. Avoid requiring private credentials or proprietary SDKs for basic source analysis.

## Initial Validation Categories

- simple single-module application,
- multi-module application,
- MVVM or layered architecture,
- background service or worker,
- networking and persistence,
- Jetpack Compose or traditional View system.

Wear OS may be added as a later specialized validation category.
