export const COMPLETION_READINESS_SCHEMA_VERSION = "1.0";

export type CompletionReadinessStatus =
  | "NOT_READY" | "BLOCKED" | "READY_WITH_WARNINGS" | "READY";
export type CompletionCheckStatus =
  | "NOT_AVAILABLE" | "NOT_SATISFIED" | "SATISFIED" | "BLOCKED" | "WARNING";

export type CompletionCheck = {
  id: string;
  label: string;
  required: boolean;
  status: CompletionCheckStatus;
  evidence: string[];
  warnings: string[];
};

export type CompletionReadiness = {
  schemaVersion: string;
  rfcId: string;
  status: CompletionReadinessStatus;
  checks: CompletionCheck[];
  blockers: string[];
  warnings: string[];
};
