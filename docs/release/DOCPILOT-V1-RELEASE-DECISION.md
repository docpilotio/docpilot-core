# DocPilot v1 Release Decision

## Decision

```text
PRODUCT_VALIDATION_FAIL
```

## Release state

- `v1.0.0`: retained as an immutable technical baseline.
- Automated remediation gate: PASS.
- Public/product v1.0 release: not approved.
- RFC-0056 Documentation Evolution Intelligence: deferred until gate approval.

The existing tag is not deleted, moved, or rewritten. It must not be presented
as Product Validation approval.

## Corrected blockers

- legacy same-path generation no longer scans managed output and has zero hash
  mismatches;
- an official Reconciliation Thin Adapter CLI now exercises RFC-0055 Core
  Preview, Apply, recovery, and verification;
- accessor parsing, multi-character expression rendering, and project metadata
  detection are covered by regression tests;
- an Architecture Overview and human-readable navigation provide progressive
  entry into generated details.

## Passed verification

- clean full build;
- clean full test;
- automated architecture-samples Product Validation;
- same-input legacy and specification determinism;
- Reconciliation ownership-preservation E2E;
- incremental Architecture Overview dependency coverage;
- Git diff check.

## Remaining blockers

1. Quality score is 19/25, below the required 21/25.
2. Explanation and maintainability remain below the category minimum of 4.
3. Runtime/data-flow, deployment, and decision views remain incomplete.
4. Organizationally independent review is incomplete.

## Re-entry criteria

- add Evidence-bounded runtime/data-flow, deployment, and decision views;
- raise every quality category to at least 4 and total score to at least 21;
- preserve zero determinism mismatches and zero ownership violations;
- complete an external independent review;
- rerun all automated, incremental, reconciliation, and regression checks.

## Next planning action

Continue only the bounded Product Validation remediation track. Do not describe
the automated PASS as public release approval, and do not begin RFC-0056
implementation until the remaining release gates pass.
