import { z } from "zod";

export const completionReadinessSchema = z.object({
  schemaVersion: z.string(),
  rfcId: z.string(),
  status: z.enum(["NOT_READY", "BLOCKED", "READY_WITH_WARNINGS", "READY"]),
  checks: z.array(z.object({
    id: z.string(),
    label: z.string(),
    required: z.boolean(),
    status: z.enum(["NOT_AVAILABLE", "NOT_SATISFIED", "SATISFIED", "BLOCKED", "WARNING"]),
    evidence: z.array(z.string()),
    warnings: z.array(z.string()),
  })),
  blockers: z.array(z.string()),
  warnings: z.array(z.string()),
});
