import { afterEach, describe, expect, it, vi } from 'vitest'

const configure = vi.hoisted(() => vi.fn())
vi.mock('aws-amplify', () => ({ Amplify: { configure } }))

import { configureAmplify } from './amplify-initializer'

describe('configureAmplify', () => {
  afterEach(() => {
    window.amplifyConfig = { mockUser: true }
    configure.mockReset()
  })

  it('configures the Cognito resource server (auth-code/PKCE) from the runtime config', () => {
    window.amplifyConfig = {
      userPoolId: 'ca-central-1_UpeAqsYt4',
      userPoolClientId: '352pis0ark86dam7ht1jlp9uj5',
      cognitoDomain: 'example.auth.ca-central-1.amazoncognito.com',
      redirectSignIn: 'https://app.example/',
      redirectSignOut: 'https://app.example/logout',
    }

    configureAmplify()

    expect(configure).toHaveBeenCalledTimes(1)
    const cognito = configure.mock.calls[0][0].Auth.Cognito
    expect(cognito.userPoolId).toBe('ca-central-1_UpeAqsYt4')
    expect(cognito.userPoolClientId).toBe('352pis0ark86dam7ht1jlp9uj5')
    expect(cognito.loginWith.oauth.responseType).toBe('code')
    expect(cognito.loginWith.oauth.scopes).toContain('openid')
    // Sign-out uses the configured value verbatim (carries the FAM logout chain), never the origin.
    expect(cognito.loginWith.oauth.redirectSignOut).toContain('https://app.example/logout')
  })

  it('throws when the required Cognito settings are absent', () => {
    window.amplifyConfig = { mockUser: false }
    expect(() => configureAmplify()).toThrow(/amplifyConfig/)
  })

  it('throws when an OAuth redirect URL is missing, so a deployment misconfig fails loudly up front', () => {
    const base = {
      userPoolId: 'ca-central-1_UpeAqsYt4',
      userPoolClientId: '352pis0ark86dam7ht1jlp9uj5',
      cognitoDomain: 'example.auth.ca-central-1.amazoncognito.com',
      redirectSignIn: 'https://app.example/',
      redirectSignOut: 'https://app.example/logout',
    }

    // A missing redirectSignIn / redirectSignOut would otherwise reach Amplify as the literal
    // "undefined" and only fail mid-flow at the Hosted UI — guard both up front.
    window.amplifyConfig = { ...base, redirectSignIn: undefined }
    expect(() => configureAmplify()).toThrow(/redirectSignIn/)
    expect(configure).not.toHaveBeenCalled()

    window.amplifyConfig = { ...base, redirectSignOut: undefined }
    expect(() => configureAmplify()).toThrow(/redirectSignOut/)
    expect(configure).not.toHaveBeenCalled()
  })
})
