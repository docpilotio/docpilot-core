import type { ReleaseReadiness } from "./ProjectStatus.js";
import type { PlanningSynchronizationStatus } from "./PlanningSynchronizationStatus.js";
import type { RfcLifecycleGuidance } from "./RfcLifecycleGuidance.js";

export const RFC_EXECUTION_CONTEXT_SCHEMA_VERSION = "1.0";

export type AlphaCriterionType =
  | "BUILD" | "TEST" | "REGRESSION" | "SMOKE" | "SCOPE" | "REVIEW";

export type AlphaCriterion = {
  id: string;
  type: AlphaCriterionType;
  required: boolean;
  description: string;
};

export type RfcExecutionContext = {
  schemaVersion: string;
  project: { name: string; phase: string; release: string };
  rfc: { id: string; title?: string; goal?: string; status?: string };
  completedRfcs: string[];
  nextRfc?: string;
  operatingRules: string[];
  scope: { inScope: string[]; outOfScope: string[] };
  acceptanceCriteria: string[];
  alphaCriteria: AlphaCriterion[];
  repository?: { path?: string; baselineBranch?: string; baselineCommit?: string };
  changePolicy?: {
    allowedPaths: string[];
    forbiddenPaths: string[];
    refactoringPolicy?: string;
    publicApiPolicy?: string;
  };
  verification?: {
    buildCommands: string[];
    testCommands: string[];
    smokeCommands: string[];
  };
  lifecycleGuidance?: RfcLifecycleGuidance;
  planningSynchronization?: PlanningSynchronizationStatus;
  releaseReadiness?: ReleaseReadiness;
  warnings: string[];
};
