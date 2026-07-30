# RFC-0059 Main Planning Update

RFC-0059 is implemented as the approved additive DIR 0.4 production design.
Manual `ProjectSpecification` construction still defaults to DIR 0.2 and
`DefaultSpecificationBuilder` still emits DIR 0.3 with empty additive
collections. Snapshot format 1/DIR 0.3 remains readable; format 2 is reserved
strictly for DIR 0.4, with an explicit 0.3-to-0.4 migration.

Delivered scope: canonical Feature/Entry Point/Scenario/Step contracts and
validation, deterministic persistence and integrity, stable-ID diff/planning,
and Feature-model Profile Resolution. Deferred: source projection, Feature
Markdown, runtime call-path extraction, AI inference, and Evolution Report
integration.
