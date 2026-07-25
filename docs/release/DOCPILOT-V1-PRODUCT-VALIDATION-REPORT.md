# DocPilot v1 Product Validation Report

## Executive result

```text
Decision: PRODUCT_VALIDATION_FAIL
Technical baseline: v1.0.0 remains immutable
Public v1.0 readiness: NOT_READY
RFC-0056 implementation: DEFERRED
```

DocPilot demonstrates strong deterministic specification generation,
source-level traceability, and selective incremental updates. It does not yet
demonstrate the complete product promise. The legacy analysis path is not
same-input deterministic after its own outputs appear, RFC-0055 has no official
end-user Reconciliation workflow, and generated documentation remains a large
fact inventory rather than a Kubernetes-quality architecture explanation.

## Generation evidence

### Legacy Core analysis

- Command: `.\gradlew.bat :run --args="analyze <fixture>"`
- Exit: PASS
- Artifacts: 7
- Notable outputs:
  - `docs/knowledge-graph.json`: 1,355,001 bytes
  - `docs/source-index.md`: 48,068 bytes
  - `docs/project-summary.md`: 415 bytes on first run
- All outputs were non-empty.

### Official specification workflow

- Command: `.\gradlew.bat :docpilot-cli:run --args="generate specification ..."`
- First mode: `FULL_REGENERATION`
- Snapshot: `NOT_FOUND`
- Generated Markdown Artifacts: 69
- Generated Markdown bytes: 2,814,576
- Snapshot bytes: 1,340,419
- Evidence-reference occurrences: 6,639
- Relationship entries: 501
- Text occurrences of `unresolved`: 574

The output includes project, module, package, component, relationship, and
Evidence Artifacts with stable hashed filenames and source references.

## Determinism

### Finding PV-001 — legacy analyze self-contaminates inventory

- Severity: HIGH
- Result: FAIL

Running `analyze` again on the exact same fixture path changed only
`docs/project-summary.md`. Generated Markdown increased the scanned inventory:

```text
First run Markdown files: 2
Second run Markdown files: 6
First run directories: 90
Second run directories: 92
First run files: 119
Second run files: 126
```

The command writes under the analyzed root and subsequently counts its own
generated output. Same-path byte determinism therefore fails.

Different fixture directory names also change the project name in
`project-summary.md`; that is an input-identity difference and is not classified
as nondeterminism.

### Specification no-change replay

- Snapshot validation: `VALID`
- Reported mode: `INCREMENTAL_UPDATE`
- Artifact hash or last-write changes: 0
- Result: PASS_WITH_OBSERVATION

The bytes and write state were stable, though a no-change run being labeled
`INCREMENTAL_UPDATE` rather than `NO_CHANGES` is a semantic reporting issue.

## Incremental validation

### Fixture change

One property was added to the existing `Task` component:

```kotlin
val displayLabel: String
    get() = titleForList
```

### Result

- Total Markdown Artifacts before change: 69
- Changed Artifacts: 2
- Changed:
  - `docs/specification/project.md`
  - owning `Task` Component Artifact
- Unrelated Component, Package, Module, Relationship, and Evidence Artifacts:
  byte-identical
- Result: PASS

This is strong product Evidence for RFC-0052 selective planning.

## Reconciliation validation

### Finding PV-002 — no official product workflow

- Severity: HIGH
- Result: FAIL

RFC-0055 provides Core models, preview/apply services, file persistence,
recovery, and tests. Neither the root CLI nor distributable CLI exposes an
official Reconciliation command. Product Validation could not perform
architecture-samples Preview, decision, Apply, restart, and offline verification
through a supported end-user workflow.

Core unit and isolated repository tests remain valid implementation Evidence,
but they cannot substitute for the requested product-level validation.

No user document was modified and no preservation PASS is claimed for the
architecture-samples product workflow.

## Documentation quality

| Category | Score | Evidence |
| --- | ---: | --- |
| Structural completeness | 3/5 | Rich project/module/package/component/API/property/relationship inventory; lacks coherent runtime, deployment, decision, risk, and operational views |
| Accuracy | 3/5 | Source-backed, but examples include `Type: String get()`, escaped `| |`, unspecified project metadata, and 574 unresolved occurrences |
| Traceability | 5/5 | Stable IDs and 6,639 Evidence references provide unusually strong source linkage |
| Explanation | 1/5 | Most purpose text is declarative boilerplate; little rationale, causal flow, trade-off, or system behavior synthesis |
| Maintainability | 2/5 | 2.8 MB Markdown, very large raw Evidence sections, hashed navigation names, and high unresolved volume impede human review |
| **Total** | **14/25** | Required: 21/25 and every category at least 4 |

## Kubernetes comparison

Kubernetes' official Cluster Architecture page explains a concise control-plane
and node model, component responsibilities, an architecture diagram, deployment
variations, workload placement, extensibility, and operational context
(<https://kubernetes.io/docs/concepts/architecture/>).

The official Kubernetes Enhancement Proposal process preserves a structured,
reviewable decision record
(<https://github.com/kubernetes/enhancements/blob/master/keps/README.md>).
Its current template explicitly covers motivation, risks, tests, graduation,
upgrade/downgrade, production readiness, monitoring, dependencies, scalability,
troubleshooting, and alternatives
(<https://github.com/kubernetes/enhancements/blob/master/keps/NNNN-kep-template/README.md>).

DocPilot exceeds these references in automatic source-line traceability for the
sample. It falls materially short in synthesis, rationale, operational views,
progressive disclosure, and human-review scale. The comparison therefore does
not support a “Kubernetes-level design document” claim.

## Fact and omission inventory

| ID | Severity | Kind | Finding |
| --- | --- | --- | --- |
| PV-001 | HIGH | Determinism | legacy `analyze` counts its own generated outputs |
| PV-002 | HIGH | Product workflow | RFC-0055 Reconciliation has no supported CLI/product entry point |
| PV-003 | MEDIUM | Accuracy | Kotlin accessor types can render as `String get()` |
| PV-004 | MEDIUM | Accuracy | expression token rendering can show `| |` instead of `||` |
| PV-005 | MEDIUM | Completeness | project platform/language/build metadata is `None` in specification output despite Android/Kotlin/Gradle input |
| PV-006 | MEDIUM | Explanation | generated purposes largely restate declarations |
| PV-007 | MEDIUM | Maintainability | raw Evidence and unresolved sections dominate document volume |
| PV-008 | LOW | Status semantics | unchanged specification execution reports `INCREMENTAL_UPDATE` |
| PV-009 | HIGH | Review | organizationally independent reviewer was not available in this execution |

No `CRITICAL` corruption or fabricated architecture claim was observed. The two
product hard-gate failures and missing independent review are sufficient for
FAIL.

## Independent review

The report uses a predeclared rubric and primary benchmark sources, but the
execution did not include a separate human or independently authorized reviewer.
It must not be labeled independent validation. A subsequent gate rerun requires
an external reviewer who did not implement the fixes.

## Recommended corrective track

1. Exclude managed output roots from legacy scanner inventory or deprecate that
   path in favor of the official specification workflow.
2. Expose RFC-0055 through an official Thin Adapter product workflow.
3. Add architecture synthesis views: system context, component responsibility,
   runtime/data flow, deployment, decisions, risks, and change impact.
4. Fix accessor/expression parsing and project metadata detection.
5. Add progressive disclosure and compact Evidence references.
6. Rerun this exact gate with an independent reviewer.
