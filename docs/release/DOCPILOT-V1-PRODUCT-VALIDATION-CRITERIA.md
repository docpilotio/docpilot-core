# DocPilot v1 Product Validation Criteria

## Gate decisions

```text
PRODUCT_VALIDATION_PASS
PRODUCT_VALIDATION_PASS_WITH_LIMITATIONS
PRODUCT_VALIDATION_FAIL
PRODUCT_VALIDATION_BLOCKED
```

`PASS_WITH_LIMITATIONS` cannot contain a Critical factual error, ownership
violation, determinism mismatch, or unavailable core product workflow.

## Hard gates

| Gate | Required |
| --- | --- |
| Full generation exits successfully | Yes |
| Critical factual errors | 0 |
| Unsupported material claims | 0 |
| User-owned byte corruption | 0 |
| Same-input determinism mismatches | 0 |
| Unresolved required architecture area | 0 |
| Incremental writes outside selected/required Artifacts | 0 |
| Official Reconciliation product workflow executable | Yes |
| Independent review completed | Yes |

Any hard-gate failure produces `PRODUCT_VALIDATION_FAIL`. Missing input or
environment beyond DocPilot's control may produce `BLOCKED`; a missing DocPilot
workflow is a product failure, not an environmental block.

## Quality rubric

Each category is scored from 0 to 5.

### Structural completeness

- 5: architecture views, boundaries, components, interfaces, relationships,
  runtime/deployment, decisions, risks, operations, tests, and navigation.
- 3: deterministic structural inventory with several architecture views absent.
- 1: file/symbol listing without coherent architecture.

### Accuracy

- 5: verified facts, sound parsing, no material contradiction, explicit unknowns.
- 3: mostly source-backed facts with visible parsing or classification defects.
- 1: material falsehoods or unsupported conclusions.

### Traceability

- 5: stable identities and direct source Evidence for all material facts.
- 3: broad source linkage with gaps or unwieldy Evidence.
- 1: claims cannot be traced.

### Explanation

- 5: purpose, rationale, trade-offs, causal relationships, impact, and risks.
- 3: useful descriptions but limited rationale and causal synthesis.
- 1: boilerplate declarations with no architectural explanation.

### Maintainability

- 5: navigable, scoped, compact, incrementally stable, reviewable documentation.
- 3: usable but noisy, repetitive, or partly machine-oriented.
- 1: output volume and representation prevent practical review.

Release requires:

- every category at least 4;
- total at least 21/25;
- all hard gates PASS.

## Kubernetes benchmark

The official Kubernetes Cluster Architecture document supplies a concise
component model, diagram, responsibilities, deployment variations, and
operational context:
<https://kubernetes.io/docs/concepts/architecture/>.

The official KEP process requires structured proposals and discoverable decision
history:
<https://github.com/kubernetes/enhancements/blob/master/keps/README.md>.

The official KEP template covers motivation, goals/non-goals, proposal, risks,
test plan, graduation, upgrade/downgrade, version skew, production readiness,
monitoring, dependencies, scalability, troubleshooting, alternatives, and
implementation history:
<https://github.com/kubernetes/enhancements/blob/master/keps/NNNN-kep-template/README.md>.

DocPilot need not copy the KEP format. It must demonstrate comparable discipline
for purpose, relationships, Evidence, change safety, and operational usability.

## Finding severity

- `CRITICAL`: unsafe overwrite, fabricated architecture, corrupt Evidence, or
  unrecoverable apply.
- `HIGH`: a hard gate fails or a core product workflow is unavailable.
- `MEDIUM`: important architecture information is absent or parsing is visibly
  wrong but Evidence remains inspectable.
- `LOW`: readability, naming, navigation, or compactness issue.
