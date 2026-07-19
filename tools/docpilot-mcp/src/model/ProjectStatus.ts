export type ReleaseReadinessState = "pending" | "passed" | "failed";

import type { RfcLifecycleEvent } from "./RfcLifecycleEvent.js";
import type { RfcHandoff } from "./RfcHandoff.js";
import type { ImplementationExecutionRecord, ImplementationWorkOrder } from "./ImplementationOrchestration.js";

export type ReleaseReadiness = {
  coreBuild: ReleaseReadinessState;
  coreTests: ReleaseReadinessState;
  cli: ReleaseReadinessState;
  incremental: ReleaseReadinessState;
  reviewWorkflow: ReleaseReadinessState;
  architectureSamplesValidation: ReleaseReadinessState;
  documentationSync: ReleaseReadinessState;
  releaseCandidate: ReleaseReadinessState;
};

export function createDefaultReleaseReadiness(): ReleaseReadiness {
  return {
    coreBuild: "pending",
    coreTests: "pending",
    cli: "pending",
    incremental: "pending",
    reviewWorkflow: "pending",
    architectureSamplesValidation: "pending",
    documentationSync: "pending",
    releaseCandidate: "pending",
  };
}

export type ProjectStatus = {
  [key: string]: unknown;

  project: string;
  phase: string;
  currentRfc: string;
  release: string;
  completedRfcs: string[];
  releaseReadiness: ReleaseReadiness;
  lifecycleHistory: readonly RfcLifecycleEvent[];
  pendingRfcHandoff?: RfcHandoff;
  pendingImplementationWorkOrder?: ImplementationWorkOrder;
  implementationExecutionRecord?: ImplementationExecutionRecord;
};
