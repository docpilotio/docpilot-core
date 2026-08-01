# RFC-0062 — Compose Function References, Nested Graphs, and Arguments

Status: `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS`

## Decision

Extend RFC-0061 Compose Navigation observations additively. Kotlin source extraction now
records destination function references, bounded external-lambda references, nested graph
scope, typed-route arguments, route placeholders, and `navArgument` declarations. Resolution
is deterministic and produces unresolved findings instead of selecting the first candidate.

`navigation(...)` represents a graph rather than a destination. A destination registration
inherits its smallest structurally enclosing graph. Typed-route arguments link to destination
parameters only when both the parameter name and normalized declared type match uniquely.

## Stable identities

- `compose-function-reference:<registration>:<slot>:<expression>`
- `compose-graph:<graph-registration-id>`
- `compose-argument:<owner>:<source-kind>:<name>`
- `compose-argument-link:<argument-id>:<destination-symbol-id>:<parameter>`

Identities exclude timestamps, locale, absolute paths, filesystem order, and AI output.

## Evidence and integrity

The Knowledge Builder emits `COMPOSE_FUNCTION_REFERENCE`, `COMPOSE_NAVIGATION_GRAPH`, and
`COMPOSE_NAVIGATION_ARGUMENT` Evidence. These references are projected into the existing
DIR 0.4 `COMPOSE_DESTINATION` Entry Point and its trigger-first Scenario. Canonical records
for references, graphs, arguments, links, Entry Points, and unresolved findings participate
in the Compose semantic SHA-256.

## Compatibility

DIR remains 0.4. Specification Snapshot format 2 and the format 1/DIR 0.3 reader remain
unchanged. No CLI command, Feature Markdown renderer, MCP production behavior, Profile
identity, RFC-0052 Artifact identity, or Evolution Report format is introduced or changed.

## Bounded limitations

External lambdas are supported only for one immutable property with one initializer and one
resolvable destination. Runtime branches, whole-program data flow, runtime back stacks,
dynamic routes, deep links, bound receivers without canonical receiver identity, and business
meaning inference remain unsupported. `architecture-samples` passes read-only DIR, Snapshot,
and file-order regression checks but currently contains no RFC-0062-specific syntax.
