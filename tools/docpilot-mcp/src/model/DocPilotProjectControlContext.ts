import type { CompletionReadiness } from "./CompletionReadiness.js";
import type { PlanningSynchronizationStatus } from "./PlanningSynchronizationStatus.js";
import type { ProjectControlCapabilityManifest } from "./ProjectControlCapabilityManifest.js";
import type { ReleaseReadiness } from "./ProjectStatus.js";
import type { RfcExecutionContext } from "./RfcExecutionContext.js";

export const PROJECT_CONTROL_CONTEXT_SCHEMA_VERSION = "1.0";

export type DocPilotProjectControlContext = {
  schemaVersion: string;
  project: { name: string; phase: string; release: string };
  lifecycle: { currentRfc: string; completedRfcs: string[]; nextRfc?: string; status?: string };
  rfcExecution: RfcExecutionContext;
  handoff: {
    pending: boolean; rfcId: string; summary?: string;
    implementationStatus?: string; alphaStatus?: string;
  };
  completionReadiness: CompletionReadiness;
  capabilities: ProjectControlCapabilityManifest;
  policies: {
    lifecycleAutoAdvance: boolean; automaticCommit: boolean;
    automaticPush: boolean; automaticMerge: boolean;
    pushRequiresUserApproval: boolean;
  };
  planningSynchronization?: PlanningSynchronizationStatus;
  releaseReadiness?: ReleaseReadiness;
  warnings: string[];
};
