export type RfcLifecycleEventType =
  | "started"
  | "completed"
  | "planningSynced"
  | "rollbackCompleted";

export type RfcLifecycleEvent = {
  readonly id: string;
  readonly type: RfcLifecycleEventType;
  readonly rfc: string;
  readonly fromRfc?: string;
  readonly phase: string;
  readonly release: string;
  readonly timestamp: string;
};
