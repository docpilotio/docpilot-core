import type { ReleaseReadinessState } from "./ProjectStatus.js";
import type { RfcLifecycleEventType } from "./RfcLifecycleEvent.js";

export type PlanningSynchronizationState =
  | "neverSynced"
  | "current"
  | "stale";

export type PlanningSynchronizationAction =
  | "none"
  | "generateMainPlanningSync";

export type PlanningSynchronizationStatus = {
  readonly state: PlanningSynchronizationState;
  readonly synchronized: boolean;
  readonly currentRfc: string;
  readonly lastPlanningSyncEventId?: string;
  readonly lastPlanningSyncRfc?: string;
  readonly latestRelevantEventId?: string;
  readonly latestRelevantEventType?: RfcLifecycleEventType;
  readonly reason: string;
  readonly recommendedAction: PlanningSynchronizationAction;
  readonly expectedDocumentationSync: Exclude<ReleaseReadinessState, "failed">;
  readonly documentationSyncConsistent: boolean;
  readonly documentationSyncReason?: string;
};
