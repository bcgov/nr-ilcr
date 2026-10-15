export type AppRuntimeConfig = {
  mockUser?: boolean
  appEnv?: string
  oidcIssuerUri?: string
  oidcClientId?: string
  oidcSiteminderLogoutUrl?: string
}

declare global {
  interface Window {
    appConfig?: AppRuntimeConfig
  }
}

function getAppRuntimeConfig(): AppRuntimeConfig | undefined {
  return typeof window === 'undefined' ? undefined : window.appConfig
}

function isLocalHost(): boolean {
  if (typeof window === 'undefined') return false
  const host = window.location.hostname
  return (
    ['localhost', '127.0.0.1', '0.0.0.0', '::1', '[::1]'].includes(host) ||
    host.endsWith('.localhost')
  )
}

// Mock authentication needs both a runtime opt-in and a local browser origin.
export function isMockAuth(): boolean {
  return getAppRuntimeConfig()?.mockUser === true && isLocalHost()
}

const readOidcSetting = (field: keyof AppRuntimeConfig, viteKey: string): string | undefined => {
  const runtime = getAppRuntimeConfig()?.[field]
  return typeof runtime === 'string' && runtime.trim() ? runtime.trim() : import.meta.env[viteKey]
}

export const env = {
  get VITE_OIDC_ISSUER_URI() {
    return readOidcSetting('oidcIssuerUri', 'VITE_OIDC_ISSUER_URI')
  },
  get VITE_OIDC_CLIENT_ID() {
    return readOidcSetting('oidcClientId', 'VITE_OIDC_CLIENT_ID')
  },
  get VITE_OIDC_SITEMINDER_LOGOUT_URL() {
    return (
      readOidcSetting('oidcSiteminderLogoutUrl', 'VITE_OIDC_SITEMINDER_LOGOUT_URL') ??
      (getAppRuntimeConfig()?.appEnv === 'prod'
        ? 'https://logon7.gov.bc.ca/clp-cgi/logoff.cgi'
        : 'https://logontest7.gov.bc.ca/clp-cgi/logoff.cgi')
    )
  },
}
