import type { ReleaseReadiness } from "./ProjectStatus.js";

export type RfcRollbackPreview = {
  readonly eligible: boolean;
  readonly currentRfc: string;
  readonly targetRfc?: string;
  readonly targetPhase?: string;
  readonly targetRelease?: string;
  readonly readinessAfterRollback?: ReleaseReadiness;
  readonly blockingReason?: string;
};
