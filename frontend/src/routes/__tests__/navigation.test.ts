import { NAVIGATION_ITEMS, visibleNavigationItems } from '@/routes/-navigation'

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
