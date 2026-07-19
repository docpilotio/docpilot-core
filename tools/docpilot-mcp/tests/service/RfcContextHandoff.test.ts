import { readFile } from "node:fs/promises";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createProjectStatus, createRfcHandoff, createTemporaryState, type TemporaryState,
} from "../support/testState.js";

describe("RFC Context and Handoff service", () => {
  let state: TemporaryState | undefined;
  afterEach(async () => { await state?.cleanup(); state = undefined; });

  it("loads deterministic current RFC Context without writes or events", async () => {
    state = await createTemporaryState(createProjectStatus());
    const before = await readFile(state.stateFilePath, "utf-8");
    const saveSpy = vi.spyOn(state.repository, "save");
    const first = await state.service.loadRfcContext();
    const second = await state.service.loadRfcContext("RFC-0039");
    expect(first).toEqual(second);
    expect(first).toMatchObject({
      schemaVersion: "1.0",
      project: { name: "DocPilot", phase: expect.any(String), release: "v0.6 MVP" },
      rfc: { id: "RFC-0039" },
      completedRfcs: ["RFC-0037", "RFC-0038"],
      scope: { inScope: [], outOfScope: [] },
      acceptanceCriteria: [],
      verification: { buildCommands: ["npm.cmd run build"], testCommands: ["npm.cmd test"] },
    });
    expect(first.operatingRules.length).toBeGreaterThan(0);
    expect(first.alphaCriteria.map(({ id }) => id)).toEqual(["A1", "A2", "A3", "A4", "A5", "A6"]);
    expect(first.warnings).toHaveLength(1);
    expect(saveSpy).not.toHaveBeenCalled();
    await expect(readFile(state.stateFilePath, "utf-8")).resolves.toBe(before);
  });

  it.each([
    ["RFC-39", "rfcId must use the exact format"],
    ["RFC-0040", "available only for the current RFC RFC-0039"],
  ])("rejects invalid Context request %s", async (rfcId, message) => {
    state = await createTemporaryState(createProjectStatus());
    await expect(state.service.loadRfcContext(rfcId)).rejects.toThrow(message);
  });

  it("rejects Context loading from malformed Project State", async () => {
    state = await createTemporaryState(createProjectStatus({ currentRfc: "RFC-39" }));
    await expect(state.service.loadRfcContext()).rejects.toThrow(
      "current RFC must use the exact format",
    );
  });

  it("submits a normalized Pending Handoff without advancing project state", async () => {
    const initial = createProjectStatus();
    state = await createTemporaryState(initial);
    const result = await state.service.submitRfcHandoff(createRfcHandoff());
    expect(result.handoff.implementation.changedFiles).toEqual(["src/a.ts", "src/z.ts"]);
    expect(result.markdown).toContain("## Known Limitations");
    const persisted = await state.repository.load();
    expect(persisted).toMatchObject({
      project: initial.project, phase: initial.phase, currentRfc: initial.currentRfc,
      release: initial.release, completedRfcs: initial.completedRfcs,
      lifecycleHistory: initial.lifecycleHistory,
    });
    expect(persisted.pendingRfcHandoff).toEqual(result.handoff);
  });

  it("restores the Pending Handoff through a new Repository and rejects duplicates", async () => {
    state = await createTemporaryState(createProjectStatus());
    await state.service.submitRfcHandoff(createRfcHandoff());
    const restarted = new (await import("../../src/repository/ProjectStateRepository.js")).ProjectStateRepository(state.stateFilePath);
    expect((await restarted.load()).pendingRfcHandoff?.rfcId).toBe("RFC-0039");
    await expect(state.service.submitRfcHandoff(createRfcHandoff())).rejects.toThrow("already exists");
  });

  it.each([
    [createRfcHandoff({ rfcId: "RFC-0040" }), "does not match current RFC"],
    [createRfcHandoff({ schemaVersion: "2.0" }), "Unsupported Handoff schemaVersion"],
  ])("rejects invalid Handoff business state", async (handoff, message) => {
    state = await createTemporaryState(createProjectStatus());
    await expect(state.service.submitRfcHandoff(handoff)).rejects.toThrow(message);
  });

  it("returns empty and populated Pending results without writes", async () => {
    state = await createTemporaryState(createProjectStatus());
    const saveSpy = vi.spyOn(state.repository, "save");
    await expect(state.service.getPendingRfcHandoff()).resolves.toEqual({ found: false, rfcId: "RFC-0039" });
    expect(saveSpy).not.toHaveBeenCalled();
    await state.service.submitRfcHandoff(createRfcHandoff());
    saveSpy.mockClear();
    const first = await state.service.getPendingRfcHandoff();
    const second = await state.service.getPendingRfcHandoff();
    expect(second).toEqual(first);
    expect(first).toMatchObject({ found: true, rfcId: "RFC-0039", handoff: { schemaVersion: "1.0" } });
    expect(saveSpy).not.toHaveBeenCalled();
  });

  it("detects a Pending Handoff for a different current RFC", async () => {
    state = await createTemporaryState(createProjectStatus({ pendingRfcHandoff: createRfcHandoff({ rfcId: "RFC-0040" }) }));
    await expect(state.service.getPendingRfcHandoff()).rejects.toThrow("does not match current RFC");
  });

  it("preserves the existing file when saving fails", async () => {
    state = await createTemporaryState(createProjectStatus());
    const before = await readFile(state.stateFilePath, "utf-8");
    vi.spyOn(state.repository, "save").mockRejectedValue(new Error("save failed"));
    await expect(state.service.submitRfcHandoff(createRfcHandoff())).rejects.toThrow("save failed");
    await expect(readFile(state.stateFilePath, "utf-8")).resolves.toBe(before);
  });

  it("renders all required Markdown sections deterministically", async () => {
    state = await createTemporaryState(createProjectStatus());
    const first = state.service.renderRfcHandoff(createRfcHandoff());
    const second = state.service.renderRfcHandoff(createRfcHandoff());
    expect(second).toBe(first);
    for (const section of [
      "RFC", "Implementation Summary", "Implemented", "Not Implemented",
      "Changed Files", "Verification", "Alpha Review", "Known Limitations",
      "Architecture Changes", "API Changes", "Git Status", "ADR Candidates",
      "Technical Debt", "Planning Update",
    ]) expect(first).toContain(`## ${section}`);
    expect(first).toContain("No orchestration.");
    expect(first).toContain("- None");
  });

  it("includes the structured Pending Handoff in Main Planning Markdown", async () => {
    state = await createTemporaryState(createProjectStatus());
    await state.service.submitRfcHandoff(createRfcHandoff());
    const planning = await state.service.generateMainPlanningSync();
    expect(planning.markdown).toContain("## Pending RFC Handoff");
    expect(planning.markdown).toContain("# RFC Handoff: RFC-0039");
  });
});
