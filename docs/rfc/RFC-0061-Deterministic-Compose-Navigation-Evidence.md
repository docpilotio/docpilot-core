# RFC-0061: Deterministic Compose Navigation Evidence

Status: `IMPLEMENTED_WITH_DOCUMENTED_LIMITATIONS`

DocPilot now extracts Compose navigation as additive source observations and projects
only verified route-registration-destination links into DIR 0.4. The canonical product
result remains `ProjectSpecification`; no sidecar persistence is introduced.

Supported evidence includes constant string routes, `@Serializable` object/class
routes, and `androidx.navigation.compose` `composable`, `navigation`, and `dialog`
registrations resolved by explicit import, alias, or fully qualified identity. Direct
destination calls and a uniquely resolvable deepest composable target through local
wrappers are supported. User-defined `composable` calls and plain `@Composable`
functions are not Entry Points.

A `COMPOSE_DESTINATION` Entry Point is created only when route identity, a known
registration, and one actual destination API are resolved. Its Feature uses route
identity as the boundary. A Scenario begins with an Evidence-backed `TRIGGER` Step and
may continue through RFC-0060 direct `CALLS` relationships. Ambiguous or incomplete
targets remain deterministic `UnresolvedItem` records.

Navigation evidence has a canonical SHA-256 semantic identity and rejects tampering.
Identity excludes timestamps, locale, absolute paths, filesystem order, and display
labels.

Snapshot format 1 readers, Profile IDs and hashes, Artifact IDs and paths, legacy
rendering, providers, MCP code, and Evolution Report format 1 remain unchanged.
Function-reference content, external/conditional lambdas, dynamic routes, deep links,
and runtime back-stack behavior remain out of scope. Feature Markdown is not generated.
