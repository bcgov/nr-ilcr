export const ILCR_ROLES = {
  admin: 'ILCR_ADMIN',
  submitter: 'ILCR_SUBMITTER',
} as const

export type IlcrRole = (typeof ILCR_ROLES)[keyof typeof ILCR_ROLES]

export type MockUser = {
  id: string
  displayName: string
  userName: string
  email: string
  roles: IlcrRole[]
}

export const MOCK_USER_STORAGE_KEY = 'nr-ilcr.mock-user'

export const MOCK_USERS: MockUser[] = [
  {
    id: 'admin',
    displayName: 'Alex Admin',
    userName: 'alex.admin',
    email: 'alex.admin@gov.bc.ca',
    roles: [ILCR_ROLES.admin],
  },
  {
    id: 'submitter',
    displayName: 'Sam Submitter',
    userName: 'sam.submitter',
    email: 'sam.submitter@gov.bc.ca',
    roles: [ILCR_ROLES.submitter],
  },
  {
    id: 'admin-submitter',
    displayName: 'Casey Dual Role',
    userName: 'casey.dual',
    email: 'casey.dual@gov.bc.ca',
    roles: [ILCR_ROLES.admin, ILCR_ROLES.submitter],
  },
]

export function findMockUser(id: string | null): MockUser {
  return MOCK_USERS.find((user) => user.id === id) ?? MOCK_USERS[0]
}
