import type { ChangeEvent } from 'react'
import { ChevronDown } from '@carbon/icons-react'
import useMockAuth from '@/context/auth/useMockAuth'

export default function MockUserSelector() {
  const { user, users, setUserId } = useMockAuth()

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    setUserId(event.target.value)
  }

  return (
    <div className="mock-user-selector">
      <span className="mock-user-selector__label">Mock user</span>
      <select
        aria-label="Mock user"
        className="mock-user-selector__select"
        value={user.id}
        onChange={handleChange}
      >
        {users.map((mockUser) => (
          <option key={mockUser.id} value={mockUser.id}>
            {mockUser.displayName} ({mockUser.roles.join(' + ')})
          </option>
        ))}
      </select>
      <ChevronDown className="mock-user-selector__caret" size={16} />
    </div>
  )
}
