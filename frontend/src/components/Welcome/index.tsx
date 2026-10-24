import type { FC } from 'react'
import { useState } from 'react'
import { Button, InlineNotification, Theme } from '@carbon/react'
import { Login } from '@carbon/icons-react'
import bcGovLogo from '@/assets/BCID_H_rgb_pos.png'
import forestPhoto from '@/assets/welcome-forest.webp'
import type { LoginProvider } from '@/context/auth/types'
import './index.scss'

type Notice = {
  kind: 'error' | 'info'
  title: string
  subtitle: string
}

type Props = {
  /** Starts the CSS sign-in redirect; rejects when the redirect could not start. */
  onSignIn: (provider: LoginProvider) => Promise<void>
}

/**
 * The public landing page a signed-out visitor sees, as in CSP. CSS shows only the identity
 * providers on ILCR's client and has no chooser of its own like Cognito's hosted page, so the choice
 * between IDIR and Business BCeID is made here and sent as a provider hint.
 *
 * Always light (g10): it is reached before the header's theme toggle, so a stored dark theme would
 * otherwise put light text on the design's white panel.
 */
const Welcome: FC<Props> = ({ onSignIn }) => {
  const [notice, setNotice] = useState<Notice | null>(null)
  const [starting, setStarting] = useState(false)

  const startLogin = (provider: LoginProvider) => {
    setNotice(null)
    setStarting(true)
    void onSignIn(provider).catch(() => {
      setStarting(false)
      setNotice({ kind: 'error', title: 'Could not start sign-in', subtitle: 'Please try again.' })
    })
  }

  return (
    <Theme theme="g10" as="main" id="main-content" className="welcome-page">
      <div className="welcome-page__panel">
        <img className="welcome-page__logo" src={bcGovLogo} alt="Government of British Columbia" />
        <h1 className="welcome-page__heading">Welcome to ILCR</h1>
        <p className="welcome-page__subheading">Interior Logging Cost Report</p>
        {notice && (
          <InlineNotification
            className="welcome-page__notice"
            kind={notice.kind}
            role={notice.kind === 'error' ? 'alert' : 'status'}
            title={notice.title}
            subtitle={notice.subtitle}
            lowContrast
            onClose={() => setNotice(null)}
          />
        )}
        <div className="welcome-page__actions">
          <Button
            kind="primary"
            size="lg"
            renderIcon={Login}
            className="welcome-page__action"
            disabled={starting}
            onClick={() => startLogin('idir')}
          >
            Log in with IDIR
          </Button>
          {/* Business BCeID is not provisioned on ILCR's CSS clients yet, and CSS answers a
              bceidbusiness hint by signing the user in with IDIR. Restore
              startLogin('business-bceid') here once it is provisioned. */}
          <Button
            kind="tertiary"
            size="lg"
            renderIcon={Login}
            className="welcome-page__action"
            disabled={starting}
            onClick={() =>
              setNotice({
                kind: 'info',
                title: 'Business BCeID is not provisioned yet',
                subtitle:
                  "BCeID infra is set up, but not provisioned in CSS yet. Once it's provisioned, enable this button. -Alex",
              })
            }
          >
            Log in with Business BCeID
          </Button>
        </div>
      </div>
      <div className="welcome-page__photo">
        <img src={forestPhoto} alt="" fetchPriority="high" />
      </div>
    </Theme>
  )
}

export default Welcome
