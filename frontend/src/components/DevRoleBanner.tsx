import { InlineNotification } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'

/**
 * A persistent warning strip shown only while a local-dev "View as" override is active, so it is
 * obvious the SPA is not reflecting the real FAM role. Present only in local dev (the real provider
 * exposes {@code devRoleSwitch} only under {@code import.meta.env.DEV}); absent from deployed builds.
 */
export default function DevRoleBanner() {
  const { devRoleSwitch } = useAuth()

  if (!devRoleSwitch?.override) {
    return null
  }

  const realRoles = devRoleSwitch.realRoles.join(' + ') || 'no ILCR role'
  return (
    <InlineNotification
      kind="warning"
      lowContrast
      hideCloseButton
      className="dev-role-banner"
      title={`Dev override — viewing as ${devRoleSwitch.override}`}
      subtitle={`Frontend only: the backend still enforces your real role (${realRoles}), so admin APIs may return 403.`}
    />
  )
}
