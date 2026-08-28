import type { ComponentType } from 'react'
import { Home, Printer, Report, Settings, Table, TaskView, UserMultiple } from '@carbon/icons-react'

export const ROUTES = {
  dashboard: '/',
  scheduleOne: '/schedule-1',
  scheduleTwo: '/schedule-2',
  scheduleThree: '/schedule-3',
  scheduleFour: '/schedule-4',
  scheduleFive: '/schedule-5',
  scheduleSix: '/schedule-6',
  scheduleSevenA: '/schedule-7a',
  scheduleSevenB: '/schedule-7b',
  scheduleEight: '/schedule-8',
  scheduleNine: '/schedule-9',
  scheduleTen: '/schedule-10',
  scheduleEleven: '/schedule-11',
  checkStatus: '/check-status',
  codeTables: '/code-tables',
  openReportingYear: '/open-reporting-year',
  homeContent: '/home-content',
  millAssociations: '/mill-associations',
  millInformationReport: '/mill-information-report',
  printSchedules: '/print-schedules',
  submissions: '/submissions',
} as const

type RoutePath = (typeof ROUTES)[keyof typeof ROUTES]

type NavIcon = ComponentType<{ size?: number | string }>

/** A single top-level navigation link. `adminOnly` hides it from non-administrators. */
export type NavigationLink = {
  icon: NavIcon
  name: string
  path: RoutePath
  adminOnly?: boolean
}

/** An expandable top-level menu with one or more sub-items (e.g. Schedules → Schedule 1). */
export type NavigationMenu = {
  icon: NavIcon
  name: string
  items: { name: string; path: RoutePath }[]
  adminOnly?: boolean
}

export type NavigationItem = NavigationLink | NavigationMenu

/** True when the item is an expandable menu (has sub-items) rather than a direct link. */
export const isNavigationMenu = (item: NavigationItem): item is NavigationMenu => 'items' in item

// Top-level information architecture, aligned to the legacy menu (menu.xhtml): Home, Schedules,
// Check Status, Administration, Generate Reports, Print Schedules — plus Submissions (the modern
// file-upload area, not in legacy). Administration is admin-gated (LayoutSideNav filters on the
// ILCR_ADMIN role), as is Generate Reports. Check Status and Print Schedules are scaffolded
// placeholders until their modernization slices land.
export const NAVIGATION_ITEMS: NavigationItem[] = [
  {
    // Story 1.3: '/' renders the Home (Mill and Reporting Year) page — legacy has no Dashboard
    // concept. The ROUTES.dashboard key is internal only; the label is what users see.
    icon: Home,
    name: 'Home',
    path: ROUTES.dashboard,
  },
  {
    // Schedules is a parent menu; each schedule is a sub-item.
    icon: Table,
    name: 'Schedules',
    items: [
      { name: 'Schedule 1', path: ROUTES.scheduleOne },
      { name: 'Schedule 2', path: ROUTES.scheduleTwo },
      { name: 'Schedule 3', path: ROUTES.scheduleThree },
      { name: 'Schedule 4', path: ROUTES.scheduleFour },
      { name: 'Schedule 5', path: ROUTES.scheduleFive },
      { name: 'Schedule 6', path: ROUTES.scheduleSix },
      { name: 'Schedule 7A', path: ROUTES.scheduleSevenA },
      { name: 'Schedule 7B', path: ROUTES.scheduleSevenB },
      { name: 'Schedule 8', path: ROUTES.scheduleEight },
      { name: 'Schedule 9', path: ROUTES.scheduleNine },
      { name: 'Schedule 10', path: ROUTES.scheduleTen },
      { name: 'Schedule 11', path: ROUTES.scheduleEleven },
    ],
  },
  {
    icon: TaskView,
    name: 'Check Status',
    path: ROUTES.checkStatus,
  },
  {
    // Administration (UC-CODE-001, and the mill/user admin surfaces): visible only to ILCR_ADMIN.
    icon: Settings,
    name: 'Administration',
    adminOnly: true,
    items: [
      { name: 'Open Reporting Year', path: ROUTES.openReportingYear },
      { name: 'Home Content', path: ROUTES.homeContent },
      { name: 'Table Maintenance', path: ROUTES.codeTables },
      { name: 'Mill Associations', path: ROUTES.millAssociations },
    ],
  },
  {
    // Generate Reports is the ministry reporting area and is administrator-only: legacy required both
    // the generateReports and millReport actions to render it, and a Licensee held neither.
    icon: Report,
    name: 'Generate Reports',
    adminOnly: true,
    items: [{ name: 'Mill Information Report', path: ROUTES.millInformationReport }],
  },
  {
    icon: Printer,
    name: 'Print Schedules',
    path: ROUTES.printSchedules,
  },
  {
    icon: UserMultiple,
    name: 'Submissions',
    path: ROUTES.submissions,
  },
]

/**
 * The navigation items a user may see. Admin-only items (Administration) are dropped for non-admins.
 * This is a UX affordance only — the backend independently enforces the 403 (MAINTAIN_CODE_TABLES),
 * so hiding the menu never stands in for the real authorization boundary.
 */
export const visibleNavigationItems = (isAdmin: boolean): NavigationItem[] =>
  NAVIGATION_ITEMS.filter((item) => !item.adminOnly || isAdmin)

/**
 * The paths only an ILCR_ADMIN may open, derived from the same `adminOnly` flag the nav filter uses —
 * so the route guard and the hidden-menu affordance can never drift apart. The backend independently
 * 403s these; the guard is the matching UX so a bookmarked admin link doesn't render for a submitter.
 */
export const ADMIN_ONLY_PATHS: readonly RoutePath[] = NAVIGATION_ITEMS.flatMap((item) =>
  item.adminOnly ? (isNavigationMenu(item) ? item.items.map((sub) => sub.path) : [item.path]) : [],
)

export const isAdminOnlyPath = (path: string): boolean => {
  // Match the admin path itself and anything nested under it, ignoring a trailing slash — so a
  // sub-route (e.g. /code-tables/edit) or /code-tables/ can't slip past the guard on an exact miss.
  const normalized = path.replace(/\/+$/, '')
  return (ADMIN_ONLY_PATHS as readonly string[]).some(
    (adminPath) => normalized === adminPath || normalized.startsWith(`${adminPath}/`),
  )
}
