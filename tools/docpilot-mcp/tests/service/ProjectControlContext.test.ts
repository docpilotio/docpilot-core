import { readFile } from "node:fs/promises";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { RfcHandoff } from "../../src/model/RfcHandoff.js";
import {
  createProjectStatus, createRfcHandoff, createTemporaryState, type TemporaryState,
} from "../support/testState.js";

const passingHandoff = (overrides: Partial<RfcHandoff> = {}) => createRfcHandoff({
  alphaReview: {
    ...createRfcHandoff().alphaReview,
    knownLimitations: [],
  },
  ...overrides,
});

describe("Project Control Context and Completion Readiness", () => {
  let state: TemporaryState | undefined;
  afterEach(async () => { await state?.cleanup(); state = undefined; });

  it("publishes an exact deterministic Capability Manifest", async () => {
    state = await createTemporaryState(createProjectStatus());
    const first = state.service.getProjectControlCapabilityManifest();
    const second = state.service.getProjectControlCapabilityManifest();
    expect(second).toEqual(first);
    expect(first).toEqual({
      schemaVersion: "1.0",
      rfcContext: { load: true },
      handoff: { submit: true, retrievePending: true, acknowledge: false, consume: false, archive: false, history: false },
      validation: {
        schema: true, rfcIdentity: true, scope: true, buildEvidence: true,
        testEvidence: true, regressionEvidence: true, smokeEvidence: true,
        diffReviewEvidence: true, completionReadiness: true,
      },
      worker: { workOrderGeneration: true, localExecution: true, cloudExecution: false, resultSubmission: true },
      git: { commit: true, push: false, pushApproval: true, pullRequest: false, merge: false, release: false },
      lifecycle: { automaticCompletion: false, automaticAdvance: false },
    });
  });

  it("returns NOT_READY with deterministic check order when no Handoff exists", async () => {
    state = await createTemporaryState(createProjectStatus());
    const result = await state.service.evaluateRfcCompletionReadiness();
    expect(result.status).toBe("NOT_READY");
    expect(result.checks.map(({ id }) => id)).toEqual([
      "CURRENT_RFC_MATCH", "HANDOFF_PRESENT", "HANDOFF_SCHEMA_VALID",
      "HANDOFF_RFC_MATCH", "IMPLEMENTATION_STATUS", "BUILD_VERIFICATION",
      "TEST_VERIFICATION", "REGRESSION_VERIFICATION", "SMOKE_VERIFICATION",
      "SCOPE_VERIFICATION", "ALPHA_REVIEW", "KNOWN_LIMITATIONS_RECORDED",
      "GIT_PUSH_POLICY",
    ]);
  });

  it("returns READY for complete structured evidence", async () => {
    state = await createTemporaryState(createProjectStatus({ pendingRfcHandoff: passingHandoff() }));
    const result = await state.service.evaluateRfcCompletionReadiness();
    expect(result).toMatchObject({ status: "READY", blockers: [], warnings: [] });
    expect(result.checks.find(({ id }) => id === "SCOPE_VERIFICATION")?.warnings).toHaveLength(1);
  });

  it.each([
    ["known limitations", passingHandoff({ alphaReview: { ...passingHandoff().alphaReview, knownLimitations: ["Known limitation"] } })],
    ["implementation limitations", passingHandoff({ implementation: { ...passingHandoff().implementation, status: "PASSED_WITH_LIMITATIONS" } })],
    ["alpha limitations", passingHandoff({ alphaReview: { ...passingHandoff().alphaReview, status: "PASSED_WITH_LIMITATIONS" } })],
  ])("returns READY_WITH_WARNINGS for %s", async (_name, handoff) => {
    state = await createTemporaryState(createProjectStatus({ pendingRfcHandoff: handoff }));
    const result = await state.service.evaluateRfcCompletionReadiness();
    expect(result.status).toBe("READY_WITH_WARNINGS");
    expect(result.warnings.length).toBeGreaterThan(0);
  });

  it.each([
    ["build", "FAILED"], ["regression", "BLOCKED"], ["smoke", "FAILED"], ["scope", "FAILED"],
  ] as const)("returns BLOCKED for %s=%s", async (field, value) => {
    const verification = { ...passingHandoff().verification, [field]: value };
    state = await createTemporaryState(createProjectStatus({ pendingRfcHandoff: passingHandoff({ verification }) }));
    await expect(state.service.evaluateRfcCompletionReadiness()).resolves.toMatchObject({ status: "BLOCKED" });
  });

  it("returns NOT_READY for NOT_RUN tests", async () => {
    const verification = { ...passingHandoff().verification, tests: "NOT_RUN" as const };
    state = await createTemporaryState(createProjectStatus({ pendingRfcHandoff: passingHandoff({ verification }) }));
    await expect(state.service.evaluateRfcCompletionReadiness()).resolves.toMatchObject({ status: "NOT_READY" });
  });

  it("returns BLOCKED for a Pending Handoff RFC mismatch", async () => {
    state = await createTemporaryState(createProjectStatus({
      pendingRfcHandoff: passingHandoff({ rfcId: "RFC-0040" }),
    }));
    await expect(state.service.evaluateRfcCompletionReadiness()).resolves.toMatchObject({
      status: "BLOCKED",
      blockers: [expect.stringContaining("does not match current RFC")],
    });
  });

  it("warns when a Worker reports an unsupported push", async () => {
    state = await createTemporaryState(createProjectStatus({
      pendingRfcHandoff: passingHandoff({
        git: { commitStatus: "CREATED", pushStatus: "PUSHED" },
      }),
    }));
    await expect(state.service.evaluateRfcCompletionReadiness()).resolves.toMatchObject({
      status: "READY_WITH_WARNINGS",
      warnings: [expect.stringContaining("no push approval capability")],
    });
  });

  it.each([
    ["alpha blocker", { blockers: ["Critical blocker"] }],
    ["unresolved item", { unresolvedItems: ["Must resolve"] }],
  ])("returns BLOCKED for %s", async (_name, alphaOverride) => {
    const alphaReview = { ...passingHandoff().alphaReview, ...alphaOverride };
    state = await createTemporaryState(createProjectStatus({ pendingRfcHandoff: passingHandoff({ alphaReview }) }));
    await expect(state.service.evaluateRfcCompletionReadiness()).resolves.toMatchObject({ status: "BLOCKED" });
  });

  it("rejects malformed or non-current RFC evaluation", async () => {
    state = await createTemporaryState(createProjectStatus());
    await expect(state.service.evaluateRfcCompletionReadiness("RFC-39")).rejects.toThrow("exact format");
    await expect(state.service.evaluateRfcCompletionReadiness("RFC-0040")).rejects.toThrow("only for the current RFC");
  });

  it("returns a complete deterministic Project Control Context without writes", async () => {
    const status = createProjectStatus({ pendingRfcHandoff: passingHandoff() });
    state = await createTemporaryState(status);
    const saveSpy = vi.spyOn(state.repository, "save");
    const before = await readFile(state.stateFilePath, "utf-8");
    const first = await state.service.getDocPilotProjectControlContext();
    const second = await state.service.getDocPilotProjectControlContext();
    expect(second).toEqual(first);
    expect(first).toMatchObject({
      schemaVersion: "1.0",
      project: { name: "DocPilot" },
      lifecycle: { currentRfc: "RFC-0039", completedRfcs: status.completedRfcs },
      rfcExecution: { schemaVersion: "1.0", rfc: { id: "RFC-0039" } },
      handoff: { pending: true, rfcId: "RFC-0039", implementationStatus: "PASSED" },
      completionReadiness: { status: "READY" },
      policies: {
        lifecycleAutoAdvance: false, automaticCommit: false,
        automaticPush: false, automaticMerge: false, pushRequiresUserApproval: true,
      },
      planningSynchronization: expect.any(Object),
      releaseReadiness: status.releaseReadiness,
    });
    expect(first.warnings.length).toBeGreaterThan(0);
    expect(saveSpy).not.toHaveBeenCalled();
    await expect(readFile(state.stateFilePath, "utf-8")).resolves.toBe(before);
  });

  it("adds Project Control to Main Planning without lifecycle advancement", async () => {
    state = await createTemporaryState(createProjectStatus({ pendingRfcHandoff: passingHandoff() }));
    const planning = await state.service.generateMainPlanningSync();
    expect(planning.markdown).toContain("## Project Control");
    expect(planning.markdown).toContain("- Completion Readiness: READY");
    expect((await state.repository.load()).currentRfc).toBe("RFC-0039");
  });
});
