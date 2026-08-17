// Local development default: mock auth (no Cognito round-trip). Combined with a localhost host,
// this activates the Mock provider and the dev role selector.
//
// Deployed environments DO NOT use this file — a per-environment ConfigMap is mounted over it with
// mockUser:false and the real Cognito pool/client/domain/redirect values (see openshift.deploy.yml).
// Never commit real client IDs or secrets here.
window.amplifyConfig = {
  mockUser: true,
}
