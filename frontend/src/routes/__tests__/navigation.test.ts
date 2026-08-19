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

  test('Administration (admin-only) is hidden from non-admins; everything else stays', () => {
    expect(names(false)).not.toContain('Administration')
    // The rest of the IA is unchanged for a submitter.
    expect(names(false)).toEqual([
      'Home',
      'Schedules',
      'Check Status',
      'Generate Reports',
      'Print Schedules',
      'Submissions',
    ])
  })

  test('exactly one item is admin-gated, and it is Administration', () => {
    const gated = NAVIGATION_ITEMS.filter((item) => item.adminOnly)
    expect(gated.map((item) => item.name)).toEqual(['Administration'])
  })
})

describe('admin-only paths (route guard source)', () => {
  test('derived from the same adminOnly items the nav hides', () => {
    expect([...ADMIN_ONLY_PATHS].sort()).toEqual([
      '/code-tables',
      '/mill-associations',
      '/open-reporting-year',
    ])
  })

  test('isAdminOnlyPath matches admin routes only', () => {
    expect(isAdminOnlyPath('/code-tables')).toBe(true)
    expect(isAdminOnlyPath('/mill-associations')).toBe(true)
    expect(isAdminOnlyPath('/open-reporting-year')).toBe(true)
    expect(isAdminOnlyPath('/schedule-1')).toBe(false)
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
