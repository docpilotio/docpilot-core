import { z } from "zod";

const commandSchema = z.object({
  id: z.string().min(1), executable: z.string().min(1), args: z.array(z.string()), workingDirectory: z.string().optional(),
  timeoutSeconds: z.number().int().positive(), required: z.boolean(),
  category: z.enum(["TARGETED_TEST", "MODULE_TEST", "BUILD", "REGRESSION_TEST", "SMOKE"]),
}).strict();

export const prepareWorkOrderInputSchema = z.object({
  mode: z.enum(["ANALYSIS", "IMPLEMENTATION"]).optional(),
  repositoryRoot: z.string().min(1), baselineBranch: z.string().min(1).optional(), baselineCommit: z.string().min(1).optional(),
  approvedPlan: z.array(z.string().min(1)).min(1), allowedPaths: z.array(z.string().min(1)).min(1), forbiddenPaths: z.array(z.string().min(1)).optional(),
  verification: z.object({ targetedCommands: z.array(commandSchema).optional(), moduleCommands: z.array(commandSchema).optional(), buildCommands: z.array(commandSchema).optional(), regressionCommands: z.array(commandSchema).optional(), smokeCommands: z.array(commandSchema).optional() }).strict().optional(),
  gitPolicy: z.object({ allowCommit: z.boolean().optional() }).strict().optional(),
}).strict();
