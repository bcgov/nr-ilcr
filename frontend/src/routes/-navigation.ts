import type { ComponentType } from 'react'
import { Document, Home, UserMultiple } from '@carbon/icons-react'

export const ROUTES = {
  dashboard: '/',
  millAssociations: '/mill-associations',
  submissions: '/submissions',
} as const

type NavigationItem = {
  icon: ComponentType<{ size?: number | string }>
  name: string
  path: (typeof ROUTES)[keyof typeof ROUTES]
}

export const NAVIGATION_ITEMS: NavigationItem[] = [
  {
    icon: Home,
    name: 'Dashboard',
    path: ROUTES.dashboard,
  },
  {
    icon: Document,
    name: 'Submissions',
    path: ROUTES.submissions,
  },
  {
    icon: UserMultiple,
    name: 'Mill Associations',
    path: ROUTES.millAssociations,
  },
]
