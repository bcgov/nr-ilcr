import type { ChangeEvent } from 'react'
import { ChevronDown } from '@carbon/icons-react'
import useAuth from '@/context/auth/useAuth'

/**
 * Local-dev role switcher. Renders ONLY under the Mock auth provider ({@code mock} present) — the
 * real (Amplify) provider carries no role selector (role comes from the FAM JWT), so this is absent
 * from deployed DEV/TEST/PROD builds. A live role-switcher in a real environment would be a
 * privilege-escalation hole.
 */
export default function MockUserSelector() {
  const { mock } = useAuth()

  if (!mock) {
    return null
  }

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    mock!.setUserId(event.target.value)
  }

  return (
    <div className="mock-user-selector">
      <span className="mock-user-selector__label">Mock user</span>
      <select
        aria-label="Mock user"
        className="mock-user-selector__select"
        value={mock.currentUserId}
        onChange={handleChange}
      >
        {mock.users.map((mockUser) => (
          <option key={mockUser.id} value={mockUser.id}>
            {mockUser.roles.join(' + ')}
          </option>
        ))}
      </select>
      <ChevronDown className="mock-user-selector__caret" size={16} />
    </div>
  )
}
