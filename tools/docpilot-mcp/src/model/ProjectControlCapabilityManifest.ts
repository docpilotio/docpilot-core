export const PROJECT_CONTROL_CAPABILITY_SCHEMA_VERSION = "1.0";

export type ProjectControlCapabilityManifest = {
  schemaVersion: string;
  rfcContext: { load: boolean };
  handoff: {
    submit: boolean; retrievePending: boolean; acknowledge: boolean;
    consume: boolean; archive: boolean; history: boolean;
  };
  validation: {
    schema: boolean; rfcIdentity: boolean; scope: boolean;
    buildEvidence: boolean; testEvidence: boolean; regressionEvidence: boolean;
    smokeEvidence: boolean; diffReviewEvidence: boolean; completionReadiness: boolean;
  };
  worker: {
    workOrderGeneration: boolean; localExecution: boolean;
    cloudExecution: boolean; resultSubmission: boolean;
  };
  git: {
    commit: boolean; push: boolean; pushApproval: boolean;
    pullRequest: boolean; merge: boolean; release: boolean;
  };
  lifecycle: { automaticCompletion: boolean; automaticAdvance: boolean };
};

export function createProjectControlCapabilityManifest(): ProjectControlCapabilityManifest {
  return {
    schemaVersion: PROJECT_CONTROL_CAPABILITY_SCHEMA_VERSION,
    rfcContext: { load: true },
    handoff: {
      submit: true, retrievePending: true, acknowledge: false,
      consume: false, archive: false, history: false,
    },
    validation: {
      schema: true, rfcIdentity: true, scope: true,
      buildEvidence: true, testEvidence: true, regressionEvidence: true,
      smokeEvidence: true, diffReviewEvidence: true, completionReadiness: true,
    },
    worker: {
      workOrderGeneration: true, localExecution: true,
      cloudExecution: false, resultSubmission: true,
    },
    git: {
      commit: true, push: false, pushApproval: true,
      pullRequest: false, merge: false, release: false,
    },
    lifecycle: { automaticCompletion: false, automaticAdvance: false },
  };
}
