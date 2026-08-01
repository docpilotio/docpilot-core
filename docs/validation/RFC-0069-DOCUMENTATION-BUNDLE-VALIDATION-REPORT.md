# RFC-0069 Documentation Bundle Validation Report

Validation date: 2026-08-02. Source: `C:\WorkSpace\sample projects\architecture-samples`. Output was isolated under the system temporary directory; the source project was not used as output.

## Results

- CLI module tests: PASS.
- Canonical permutation/identity tests: PASS.
- Preview: PASS, `PREVIEW_READY`, no output writes.
- Apply: PASS, 158 artifacts and 72 Contract Detail documents.
- Offline verification: PASS, `VALID`, zero missing/changed files.
- Second apply: PASS, `NO_CHANGES`; document state, Snapshot, Bundle Manifest, and Receipt were not rewritten.
- Changed artifact: PASS, detected as `TAMPERED`, exit 6.
- Missing artifact: PASS, detected as `INCOMPLETE`, exit 5.
- Manifest mutation/noncanonical bytes: PASS, detected as `INVALID`, exit 7.
- Original source mutation: PASS for this run; output was outside the project. Pre-existing untracked `docs/` and `prompt-package/` in the sample repository were observed and not modified or removed.
- Full repository test suite (`.\\gradlew.bat test`): PASS, 22 tasks successful/up-to-date.

## RFC-0070 prerequisite remediation

The five previously partial conditions were completed and regression-tested on 2026-08-02:

- Markdown links and image resources are parsed outside fenced code; local targets and heading fragments are verified offline.
- Markdown files under the managed `docs/` tree that are absent from the Bundle index fail as `UNEXPECTED_MANAGED_FILE`.
- the Receipt is required and its version, IDs, Manifest binding, enrichment binding, and independently recomputed receipt hash are verified.
- selective generation merges selected results with the complete persisted Bundle artifact index; the sample retained all 158 entries and 72 Contract Details.
- generation resolves the exact Profile from `BuiltInDocumentationProfiles`, verifies its semantic hash, and verifies resolved document/binding integrity.

Receipt mutation and a broken fragment both fail closed in `DocumentationBundleTest`. The full suite passes. These results complete the technical RFC-0069 conditions; they do not alter Product Validation: `PRODUCT_VALIDATION_FAIL / NOT_APPROVED`, `PV-009: PENDING`.
