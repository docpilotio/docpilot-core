import { z } from "zod";

const stringArray = z.array(z.string());
const verificationStatus = z.enum(["NOT_RUN", "PASSED", "FAILED", "BLOCKED"]);

export const rfcHandoffSchema = z.object({
  schemaVersion: z.string(),
  rfcId: z.string(),
  worker: z.object({
    type: z.string(),
    executionMode: z.string().optional(),
    version: z.string().optional(),
  }).strict().optional(),
  implementation: z.object({
    status: z.enum(["NOT_STARTED", "IN_PROGRESS", "BLOCKED", "FAILED", "PASSED_WITH_LIMITATIONS", "PASSED"]),
    summary: z.string(),
    implemented: stringArray,
    notImplemented: stringArray,
    changedFiles: stringArray,
    createdFiles: stringArray,
    deletedFiles: stringArray,
  }).strict(),
  verification: z.object({
    build: verificationStatus,
    tests: verificationStatus,
    regression: verificationStatus,
    smoke: verificationStatus,
    scope: verificationStatus,
    commandsExecuted: stringArray,
    details: stringArray,
  }).strict(),
  alphaReview: z.object({
    status: z.enum(["NOT_STARTED", "BLOCKED", "FAILED", "PASSED_WITH_LIMITATIONS", "PASSED"]),
    findings: stringArray,
    blockers: stringArray,
    warnings: stringArray,
    knownLimitations: stringArray,
    unresolvedItems: stringArray,
  }).strict(),
  architectureChanges: stringArray,
  apiChanges: stringArray,
  adrCandidates: stringArray,
  technicalDebt: stringArray,
  git: z.object({
    branch: z.string().optional(),
    baseCommit: z.string().optional(),
    resultingCommit: z.string().optional(),
    commitStatus: z.enum(["NOT_CREATED", "CREATED", "UNKNOWN"]),
    pushStatus: z.enum(["NOT_REQUESTED", "PENDING_APPROVAL", "PUSHED", "FAILED", "UNKNOWN"]),
  }).strict(),
  planningUpdate: z.object({
    summary: stringArray,
    releaseReadinessChanges: stringArray,
    warnings: stringArray,
  }).strict(),
}).strict();
