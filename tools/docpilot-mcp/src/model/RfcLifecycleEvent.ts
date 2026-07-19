export type RfcLifecycleEventType =
  | "started"
  | "completed"
  | "planningSynced";

export type RfcLifecycleEvent = {
  readonly id: string;
  readonly type: RfcLifecycleEventType;
  readonly rfc: string;
  readonly phase: string;
  readonly release: string;
  readonly timestamp: string;
};
