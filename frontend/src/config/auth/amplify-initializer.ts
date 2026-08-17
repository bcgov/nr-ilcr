import { Amplify } from 'aws-amplify'
import { getAmplifyRuntimeConfig } from '@/env'

/**
 * Configure Amplify's Cognito resource server from the runtime {@code window.amplifyConfig} global.
 * Auth-code + PKCE ({@code responseType: 'code'}); the SPA sends the ID token (Story 1.0). In local
 * dev the redirect URLs are forced to the current origin so any localhost port round-trips.
 *
 * @throws Error when the required pool/client settings are absent — callers only invoke this in real
 *     (non-mock) mode, so a missing config is a deployment error worth failing loudly on.
 */
export function configureAmplify(): void {
  const config = getAmplifyRuntimeConfig()
  if (!config?.userPoolId || !config.userPoolClientId || !config.cognitoDomain) {
    throw new Error(
      '[amplify] window.amplifyConfig is missing userPoolId / userPoolClientId / cognitoDomain',
    )
  }

  const origin = window.location.origin
  const redirectSignIn = import.meta.env.DEV ? [`${origin}/`] : [String(config.redirectSignIn)]
  const redirectSignOut = import.meta.env.DEV ? [`${origin}/`] : [String(config.redirectSignOut)]

  Amplify.configure({
    Auth: {
      Cognito: {
        userPoolId: config.userPoolId,
        userPoolClientId: config.userPoolClientId,
        loginWith: {
          oauth: {
            domain: config.cognitoDomain,
            scopes: config.oauthScopes ?? ['openid'],
            redirectSignIn,
            redirectSignOut,
            responseType: 'code',
          },
        },
      },
    },
  })
}
