import type { ChangeEvent } from 'react'
import { ChevronDown } from '@carbon/icons-react'
import useAuth from '@/context/auth/useAuth'
import { ILCR_ROLES } from '@/context/auth/types'

/**
 * Local-dev-only "View as" switch for a REAL FAM session. It overrides only the role the SPA uses
 * (navigation + route guards) — the backend still enforces the real token, so admin APIs still 403.
 * Renders only when the real provider exposes {@code devRoleSwitch} (i.e. `import.meta.env.DEV`), so
 * it is absent from every deployed build; it is not the security boundary.
 */
export default function DevRoleSwitcher() {
  const { devRoleSwitch } = useAuth()

  if (!devRoleSwitch) {
    return null
  }

  const { override, realRoles, setOverride } = devRoleSwitch
  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    setOverride(event.target.value || null)
  }

  return (
    <div className="mock-user-selector">
      <span className="mock-user-selector__label">View as (dev)</span>
      <select
        aria-label="View as role"
        className="mock-user-selector__select"
        value={override ?? ''}
        onChange={handleChange}
      >
        <option value="">Real ({realRoles.join(' + ') || 'no role'})</option>
        <option value={ILCR_ROLES.admin}>{ILCR_ROLES.admin}</option>
        <option value={ILCR_ROLES.submitter}>{ILCR_ROLES.submitter}</option>
      </select>
      <ChevronDown className="mock-user-selector__caret" size={16} />
    </div>
  )
}
