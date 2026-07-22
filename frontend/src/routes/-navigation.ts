import type { ComponentType } from 'react'
import { Document, Home, Table, UserMultiple } from '@carbon/icons-react'

export const ROUTES = {
  dashboard: '/',
  scheduleOne: '/schedule-1',
  scheduleTwo: '/schedule-2',
  scheduleFour: '/schedule-4',
  scheduleEight: '/schedule-8',
  millAssociations: '/mill-associations',
  submissions: '/submissions',
} as const

type RoutePath = (typeof ROUTES)[keyof typeof ROUTES]

type NavIcon = ComponentType<{ size?: number | string }>

/** A single top-level navigation link. */
export type NavigationLink = {
  icon: NavIcon
  name: string
  path: RoutePath
}

/** An expandable top-level menu with one or more sub-items (e.g. Schedules → Schedule 1). */
export type NavigationMenu = {
  icon: NavIcon
  name: string
  items: { name: string; path: RoutePath }[]
}

export type NavigationItem = NavigationLink | NavigationMenu

/** True when the item is an expandable menu (has sub-items) rather than a direct link. */
export const isNavigationMenu = (item: NavigationItem): item is NavigationMenu => 'items' in item

export const NAVIGATION_ITEMS: NavigationItem[] = [
  {
    icon: Home,
    name: 'Dashboard',
    path: ROUTES.dashboard,
  },
  {
    // Schedules is a parent menu; each schedule is a sub-item (Schedule 2–11 will be added here).
    icon: Table,
    name: 'Schedules',
    items: [
      { name: 'Schedule 1', path: ROUTES.scheduleOne },
      { name: 'Schedule 2', path: ROUTES.scheduleTwo },
      { name: 'Schedule 4', path: ROUTES.scheduleFour },
      { name: 'Schedule 8', path: ROUTES.scheduleEight },
    ],
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
