import { describe as group, expect, it } from 'vitest'
import { describe, supplyBlocksFor } from '@/utils/codes'

const blocks = [
  { code: '01A', description: 'Block One A' },
  { code: '01B', description: 'Block One B' },
  { code: '02A', description: 'Block Two A' },
]

group('describe', () => {
  it('returns the description for a known code', () => {
    expect(describe(blocks, '01B')).toBe('Block One B')
  })

  // A code the served list never carried must still show SOMETHING: a stored value that predates
  // the code table would otherwise render blank over data that is really there.
  it('falls back to the bare code when the list does not carry it', () => {
    expect(describe(blocks, '16Z')).toBe('16Z')
  })
})

group('supplyBlocksFor', () => {
  it('narrows the list to blocks whose code starts with the chosen TSA', () => {
    expect(supplyBlocksFor(blocks, '01').map((b) => b.code)).toEqual(['01A', '01B'])
  })

  it('offers nothing but the stored value when the area type is TFL', () => {
    expect(supplyBlocksFor(blocks, 'TFL', '')).toEqual([])
  })
})
