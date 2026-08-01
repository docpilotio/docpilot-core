# RFC-0063 Main Planning Update

RFC-0063 is approved as the standalone Core Release Evidence and MCP removal change.

Implementation scope is limited to deleting the temporary `tools/docpilot-mcp` tree, removing
MCP identity from release collection and verification, and advancing Release Evidence Manifest
from format 1 to format 2. Historical records remain unchanged and format 1 is rejected rather
than silently migrated.

Required gates are Release module tests, full Gradle regression, `git diff --check`, tracked MCP
path absence, format 2 canonical round-trip, explicit format 1 rejection, and canonical document
synchronization. Public Product Validation remains `PRODUCT_VALIDATION_FAIL / NOT_APPROVED` and
PV-009 remains `PENDING`.
