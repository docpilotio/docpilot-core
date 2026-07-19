export const RFC_HANDOFF_SCHEMA_VERSION = "1.0";

export type VerificationStatus = "NOT_RUN" | "PASSED" | "FAILED" | "BLOCKED";
export type ImplementationStatus =
  | "NOT_STARTED" | "IN_PROGRESS" | "BLOCKED" | "FAILED"
  | "PASSED_WITH_LIMITATIONS" | "PASSED";
export type AlphaReviewStatus =
  | "NOT_STARTED" | "BLOCKED" | "FAILED"
  | "PASSED_WITH_LIMITATIONS" | "PASSED";
export type CommitStatus = "NOT_CREATED" | "CREATED" | "UNKNOWN";
export type PushStatus =
  | "NOT_REQUESTED" | "PENDING_APPROVAL" | "PUSHED" | "FAILED" | "UNKNOWN";

export type RfcHandoff = {
  schemaVersion: string;
  rfcId: string;
  worker?: { type: string; executionMode?: string; version?: string };
  implementation: {
    status: ImplementationStatus;
    summary: string;
    implemented: string[];
    notImplemented: string[];
    changedFiles: string[];
    createdFiles: string[];
    deletedFiles: string[];
  };
  verification: {
    build: VerificationStatus;
    tests: VerificationStatus;
    regression: VerificationStatus;
    smoke: VerificationStatus;
    scope: VerificationStatus;
    commandsExecuted: string[];
    details: string[];
  };
  alphaReview: {
    status: AlphaReviewStatus;
    findings: string[];
    blockers: string[];
    warnings: string[];
    knownLimitations: string[];
    unresolvedItems: string[];
  };
  architectureChanges: string[];
  apiChanges: string[];
  adrCandidates: string[];
  technicalDebt: string[];
  git: {
    branch?: string;
    baseCommit?: string;
    resultingCommit?: string;
    commitStatus: CommitStatus;
    pushStatus: PushStatus;
  };
  planningUpdate: {
    summary: string[];
    releaseReadinessChanges: string[];
    warnings: string[];
  };
};
