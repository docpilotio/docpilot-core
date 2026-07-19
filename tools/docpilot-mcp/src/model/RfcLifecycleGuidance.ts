export type RfcLifecycleState =
  | "in_progress"
  | "completed_waiting_next"
  | "inconsistent";

export type RfcLifecycleAction =
  | "markCurrentRfcCompleted"
  | "startNextRfc"
  | "manualReview";

export type RfcLifecycleGuidance = {
  state: RfcLifecycleState;
  nextAction: RfcLifecycleAction;
  reason: string;
};
