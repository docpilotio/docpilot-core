# RFC-0067 Contract Documentation Validation Report

Status: `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS`

## Automated validation

- Multi-module `./gradlew.bat test`: PASS (root suite: 371 tests).
- Nine canonical roles in Catalog/Detail fixtures: PASS.
- Stable-ID ordering and input permutation byte identity: PASS.
- nullable/unresolved type and repository-relative Evidence rendering: PASS.
- absolute Evidence path fail-closed: PASS.
- DIR 0.5 Profile READY and DIR 0.4 deferred diagnostic: PASS.
- existing Feature/Profile/planner regression: PASS.
- Snapshot format 3 and DIR 0.5 model code unchanged: PASS.

## Isolated architecture-samples validation

Source was copied from `C:\WorkSpace\sample projects\architecture-samples` to a system temporary directory. Generated output was written only inside that copy.

- First execution: `FULL_REGENERATION`; previous Snapshot `NOT_FOUND`.
- Second execution: `NO_CHANGES`; Snapshot `VALID`.
- Contract Catalog: 1; Contract Details: 72.
- Roles: PUBLIC_API 69, CALLBACK 3.
- Catalog SHA-256 on both runs: `801E60C81AF373B81FFD1A26C8FC51F2F09F93DEE3358F3905CEB96149E5D2E8`.
- Detail and all other generated artifact hashes were identical between runs.
- Evidence paths rendered repository-relative; generated Detail links resolve within the output tree.
- Original sample status remained its pre-existing untracked `docs/` and `prompt-package/`; no new original change appeared.

The sample provides no canonical instances for the remaining seven roles, so their real-project coverage is not claimed. Fixture tests cover them.
