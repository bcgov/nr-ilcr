import { Amplify } from 'aws-amplify'
import { getAmplifyRuntimeConfig } from '@/env'

/**
 * Configure Amplify's Cognito resource server from the runtime {@code window.amplifyConfig} global.
 * Auth-code + PKCE ({@code responseType: 'code'}); the SPA sends the ID token (Story 1.0). In local
 * dev sign-in returns to the current origin ({@code http://localhost:3000/}, allow-listed on the DEV
 * client); sign-out always uses the configured value verbatim.
 *
 * @throws Error when any required setting is absent — callers only invoke this in real (non-mock)
 *     mode, so a missing config is a deployment error worth failing loudly on. Both OAuth redirect
 *     URLs are guarded too: otherwise a missing one reaches Amplify as the literal string
 *     {@code "undefined"} and the failure only surfaces mid-flow at the Hosted UI.
 */
export function configureAmplify(): void {
  const config = getAmplifyRuntimeConfig()
  if (
    !config?.userPoolId ||
    !config.userPoolClientId ||
    !config.cognitoDomain ||
    !config.redirectSignIn ||
    !config.redirectSignOut
  ) {
    throw new Error(
      '[amplify] window.amplifyConfig is missing one of: userPoolId / userPoolClientId / ' +
        'cognitoDomain / redirectSignIn / redirectSignOut',
    )
  }

  const origin = window.location.origin
  const redirectSignIn = import.meta.env.DEV ? [`${origin}/`] : [config.redirectSignIn]
  // NOT origin-overridden in dev: sign-out carries the FAM/loginproxy logout chain and must match a
  // registered Cognito sign-out URL — the bare origin is not one, so Cognito would reject it.
  const redirectSignOut = [config.redirectSignOut]

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
