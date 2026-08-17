import { InlineNotification } from '@carbon/react'
import useAuth from '@/context/auth/useAuth'

/**
 * A persistent warning strip shown only while a local-dev "View as" override is active, so it is
 * obvious the SPA is not reflecting the real FAM role. Present only in local dev (the real provider
 * exposes {@code devRoleSwitch} only under {@code import.meta.env.DEV}); absent from deployed builds.
 */
export default function DevRoleBanner() {
  const { devRoleSwitch } = useAuth()

  // Show only when the override actually changes the effective role. An override equal to the role
  // you already hold (e.g. a stale "view as ILCR_SUBMITTER" replayed for a real submitter) is a
  // no-op, so warning about it is just noise.
  const override = devRoleSwitch?.override
  const realRoles = devRoleSwitch?.realRoles ?? []
  const isNoOp = realRoles.length === 1 && realRoles[0] === override
  if (!override || isNoOp) {
    return null
  }

  const realLabel = realRoles.join(' + ') || 'no ILCR role'
  return (
    <InlineNotification
      kind="warning"
      lowContrast
      hideCloseButton
      className="dev-role-banner"
      title={`Dev override — viewing as ${override}`}
      subtitle={`Frontend only: the backend still enforces your real role (${realLabel}), so admin APIs may return 403.`}
    />
  )
}
