# RFC-0066 Contract Extraction Validation Report

Status: `IMPLEMENTED_WITH_VALIDATION_LIMITATIONS`

## Automated validation

- Nine-role extraction and DIR 0.5 validator: PASS
- No name/simple-annotation inference: PASS
- Ambiguous type unresolved binding: PASS
- file/symbol/annotation/API/property/Evidence ordering permutation: PASS
- Stable IDs and canonical Contract ordering: PASS
- Snapshot format 3 deterministic round-trip: PASS
- Contract incremental diff/no-change: PASS
- Core regression suite: PASS

## Isolated architecture-samples validation

Source: isolated copy of `C:\WorkSpace\sample projects\architecture-samples`.

- Kotlin files: 55
- first execution: `FULL_REGENERATION`, previous snapshot `NOT_FOUND`
- Snapshot: format 3 / DIR 0.5
- Contracts: 72 (`PUBLIC_API` 69, `CALLBACK` 3)
- existing unresolved items: 60
- second execution: `NO_CHANGES`
- second Snapshot validation: `VALID`
- original checkout: unchanged by validation; its pre-existing untracked `docs/` and `prompt-package/` remained unchanged

The target did not provide qualified Evidence for repository, data-model, DTO, event, navigation-argument, Room schema, or Retrofit boundary coverage. Those roles are fixture-validated and are not claimed as real-project PASS.

