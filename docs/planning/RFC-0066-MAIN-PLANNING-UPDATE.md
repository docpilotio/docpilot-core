# RFC-0066 Main Planning Update

RFC-0066 is implemented with real-project validation limitations. The default pipeline now emits DIR 0.5, performs deterministic Contract extraction after Feature Discovery, validates the result, and persists Snapshot format 3. DIR 0.2/0.3/0.4 readers and Snapshot formats 1/2 remain supported.

All nine roles have positive, Evidence-bound fixtures. The isolated `architecture-samples` target contains legitimate Evidence for `PUBLIC_API` and `CALLBACK`; it does not prove real-project coverage for the other seven roles. Contract Markdown and Profile readiness remain deferred to RFC-0067.

Product Validation remains `PRODUCT_VALIDATION_FAIL / NOT_APPROVED`; PV-009 remains `PENDING`.

