/**
 * Runtime environment access. Cognito settings are injected at runtime via a `window.amplifyConfig`
 * global (a static `amplify-config.js` served before the bundle and overridden per-environment by a
 * ConfigMap in OpenShift), so the same image ships everywhere and no secrets are baked into the
 * build.
 */
export type AmplifyRuntimeConfig = {
  mockUser?: boolean
  userPoolId?: string
  userPoolClientId?: string
  cognitoDomain?: string
  redirectSignIn?: string
  redirectSignOut?: string
  oauthScopes?: string[]
}

declare global {
  interface Window {
    amplifyConfig?: AmplifyRuntimeConfig
  }
}

export function getAmplifyRuntimeConfig(): AmplifyRuntimeConfig | undefined {
  return typeof window === 'undefined' ? undefined : window.amplifyConfig
}

function isLocalHost(): boolean {
  if (typeof window === 'undefined') {
    return false
  }
  const host = window.location.hostname
  return (
    host === 'localhost' ||
    host === '127.0.0.1' ||
    host === '0.0.0.0' ||
    host === '::1' ||
    host.endsWith('.localhost')
  )
}

/**
 * Whether the mock auth provider is active. Double-gated (matching the nr-csp pattern): the runtime
 * config must opt in AND the app must be on a local host. A deployed environment sets
 * {@code mockUser: false} in its ConfigMap and is never on localhost, so a live role-switcher can
 * never surface in DEV/TEST/PROD even if a stray config slipped through.
 */
export function isMockAuth(): boolean {
  return getAmplifyRuntimeConfig()?.mockUser === true && isLocalHost()
}
