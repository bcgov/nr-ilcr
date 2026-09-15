import {
  ADMIN_ONLY_PATHS,
  isAdminOnlyPath,
  NAVIGATION_ITEMS,
  visibleNavigationItems,
} from '@/routes/-navigation'

const names = (isAdmin: boolean) => visibleNavigationItems(isAdmin).map((item) => item.name)

describe('visibleNavigationItems', () => {
  test('the six top-level areas plus Submissions are present for an admin', () => {
    expect(names(true)).toEqual([
      'Home',
      'Schedules',
      'Check Status',
      'Administration',
      'Generate Reports',
      'Print Schedules',
      'Submissions',
    ])
  })

  test('the admin-only areas are hidden from non-admins; everything else stays', () => {
    expect(names(false)).not.toContain('Administration')
    // Generate Reports holds the ministry mill reports, which a Licensee never had access to.
    expect(names(false)).not.toContain('Generate Reports')
    expect(names(false)).toEqual([
      'Home',
      'Schedules',
      'Check Status',
      'Print Schedules',
      'Submissions',
    ])
  })

  test('exactly two items are admin-gated: Administration and Generate Reports', () => {
    const gated = NAVIGATION_ITEMS.filter((item) => item.adminOnly)
    expect(gated.map((item) => item.name)).toEqual(['Administration', 'Generate Reports'])
  })

  test('Generate Reports lists the two mill reports, in the legacy menu order', () => {
    // menu.xhtml:40-41 — Mill Information Report then Mill Status Report. (Data Extract is its own
    // later epic and is deliberately not here yet.)
    const reports = NAVIGATION_ITEMS.find((item) => item.name === 'Generate Reports')
    expect(reports?.items).toEqual([
      { name: 'Mill Information Report', path: '/mill-information-report' },
      { name: 'Mill Status Report', path: '/mill-status-report' },
    ])
  })

  test('Administration lists Mills directly after Mill Associations, as legacy did', () => {
    // menu.xhtml:32-36 ran Users → Mills → Content Editing → Report Year → Table Maintenance. The
    // absolute order here already diverges, but Mills' place relative to the users screen is
    // legacy's and the two screens cross-navigate to each other (UC-MILL-001 S10).
    const administration = NAVIGATION_ITEMS.find((item) => item.name === 'Administration')
    const names = administration?.items?.map((child) => child.name) ?? []
    const at = names.indexOf('Mills')
    expect(at).toBeGreaterThan(-1)
    expect(names[at - 1]).toBe('Mill Associations')

    const item = administration?.items?.find((child) => child.name === 'Mills')
    expect(item?.path).toBe('/mills')
    // Inherited from the parent menu's flag rather than declared per-item, which is what keeps the
    // route guard and the hidden menu from drifting.
    expect(isAdminOnlyPath('/mills')).toBe(true)
  })

  test('Schedule 10 sits between Schedule 9 and Schedule 11, and is not admin-only', () => {
    // AC14 of Story 11.3. The route test proves the route exists; this proves it is REACHABLE, in
    // the right place, to a non-admin — the half no test asserted when 11.3 shipped.
    const schedules = NAVIGATION_ITEMS.find((item) => item.name === 'Schedules')
    const names = schedules?.items?.map((child) => child.name) ?? []
    const at = names.indexOf('Schedule 10')
    expect(at).toBeGreaterThan(-1)
    expect(names[at - 1]).toBe('Schedule 9')
    expect(names[at + 1]).toBe('Schedule 11')

    const item = schedules?.items?.find((child) => child.name === 'Schedule 10')
    expect(item?.path).toBe('/schedule-10')
    expect(item?.adminOnly).toBeFalsy()
    expect(isAdminOnlyPath('/schedule-10')).toBe(false)
  })
})

describe('admin-only paths (route guard source)', () => {
  test('derived from the same adminOnly items the nav hides', () => {
    expect([...ADMIN_ONLY_PATHS].sort()).toEqual([
      '/code-tables',
      '/home-content',
      '/mill-associations',
      '/mill-information-report',
      '/mill-status-report',
      '/mills',
      '/open-reporting-year',
    ])
  })

  test('isAdminOnlyPath matches admin routes only', () => {
    expect(isAdminOnlyPath('/code-tables')).toBe(true)
    expect(isAdminOnlyPath('/mill-associations')).toBe(true)
    expect(isAdminOnlyPath('/open-reporting-year')).toBe(true)
    expect(isAdminOnlyPath('/home-content')).toBe(true)
    expect(isAdminOnlyPath('/mill-information-report')).toBe(true)
    expect(isAdminOnlyPath('/mill-status-report')).toBe(true)
    expect(isAdminOnlyPath('/mills')).toBe(true)
    expect(isAdminOnlyPath('/schedule-1')).toBe(false)
    // `/mills` must not swallow the caller-scoped Home mill list, which is a different concern
    // behind a different gate and is NOT admin-only.
    expect(isAdminOnlyPath('/mill-associations-public')).toBe(false)
    expect(isAdminOnlyPath('/')).toBe(false)
  })

  test('isAdminOnlyPath matches admin sub-routes and tolerates a trailing slash', () => {
    // A sub-route or trailing slash must not slip past the guard on an exact-match miss.
    expect(isAdminOnlyPath('/code-tables/')).toBe(true)
    expect(isAdminOnlyPath('/code-tables/edit')).toBe(true)
    expect(isAdminOnlyPath('/mill-associations/123')).toBe(true)
    expect(isAdminOnlyPath('/open-reporting-year/edit')).toBe(true)
    // ...but a different route that merely starts with the same prefix string is NOT admin-only.
    expect(isAdminOnlyPath('/code-tables-public')).toBe(false)
    expect(isAdminOnlyPath('/schedule-1/other-costs')).toBe(false)
  })
})
