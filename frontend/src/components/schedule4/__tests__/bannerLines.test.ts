import { describe, expect, test } from 'vitest'
import {
  VALIDATION_MESSAGES,
  locationBannerLines,
  validateLocationForm,
  type CategoryForm,
  type LegacyPanel,
} from '@/components/schedule4/validation'

// Every label Schedule 4 can require, verbatim from the legacy XHTML (#359 group B):
// existing — schedule4ExistingLocation.xhtml; new/copy — schedule4NewLocation.xhtml (its `m<sup>3</sup>`
// markup rendered as `m³`, Iman's ruling).
const LABELS: Record<LegacyPanel, Record<number, [string, string, string]>> = {
  existing: {
    47: ['Truck Barge Ferry (Km)', 'Truck Barge Ferry Volume (m3)', 'Truck Barge Ferry (Cost $)'],
    48: ['Crew Barge Ferry (Km)', 'Crew Barge Ferry Volume (m3)', 'Crew Barge Ferry (Cost $)'],
    52: ['Rail Haul (Km)', 'Rail Haul Volume (m3)', 'Rail Haul (Cost $)'],
  },
  new: {
    47: ['Distance (Km)', 'Truck Barge Ferry (Volume m³)', 'Truck Barge Ferry (Cost $)'],
    48: ['Distance (Km)', 'Crew Barge Ferry (Volume m³)', 'Crew Barge Ferry (Cost $)'],
    52: ['Distance (Km)', 'Rail Haul (Volume m³)', 'Rail Haul (Cost $)'],
  },
}

const one = (code: number, entry: Partial<{ volume: string; cost: string; distance: string }>) =>
  ({ [code]: { volume: '', cost: '', distance: '', ...entry } }) as CategoryForm

const cases = (['existing', 'new'] as const).flatMap((panel) =>
  Object.entries(LABELS[panel]).map(([code, labels]) => ({ panel, code: Number(code), labels })),
)

describe('locationBannerLines — every legacy label, both panels', () => {
  test.each(cases)(
    '$panel panel, code $code: a Distance alone requires Volume then Cost',
    ({ panel, code, labels }) => {
      const validation = validateLocationForm('Named', one(code, { distance: '5' }))
      expect(locationBannerLines('Named', validation, panel)).toEqual([
        `${labels[1]}: Value is required.`,
        `${labels[2]}: Value is required.`,
      ])
    },
  )

  // All-or-nothing since 2026-09-29 (Iman) — a deliberate fix of legacy, which asked for the Distance
  // alone here and for the Cost only on the next attempt.
  test.each(cases)(
    '$panel panel, code $code: a Volume alone requires the Distance AND the Cost, at once',
    ({ panel, code, labels }) => {
      const validation = validateLocationForm('Named', one(code, { volume: '5' }))
      expect(locationBannerLines('Named', validation, panel)).toEqual([
        `${labels[0]}: Value is required.`,
        `${labels[2]}: Value is required.`,
      ])
    },
  )

  test.each(cases)(
    '$panel panel, code $code: a Cost alone requires the Distance AND the Volume, at once',
    ({ panel, code, labels }) => {
      const validation = validateLocationForm('Named', one(code, { cost: '5' }))
      expect(locationBannerLines('Named', validation, panel)).toEqual([
        `${labels[0]}: Value is required.`,
        `${labels[1]}: Value is required.`,
      ])
    },
  )

  test.each(['existing', 'new'] as const)(
    '%s panel: a blank name is JSF required; a whitespace name keeps the bean message',
    (panel) => {
      expect(locationBannerLines('', validateLocationForm('', {}), panel)).toEqual([
        'Location Name: Value is required.',
      ])
      expect(locationBannerLines('   ', validateLocationForm('   ', {}), panel)).toEqual([
        VALIDATION_MESSAGES.nameEmpty,
      ])
    },
  )

  test('page order: the name, then the grid in code order, Dist/Volume/Cost within a row; ranges verbatim', () => {
    const form: CategoryForm = {
      ...one(52, { distance: '5' }),
      ...one(40, { volume: '10000000' }),
    }
    expect(locationBannerLines('', validateLocationForm('', form), 'existing')).toEqual([
      'Location Name: Value is required.',
      VALIDATION_MESSAGES.volume,
      'Rail Haul Volume (m3): Value is required.',
      'Rail Haul (Cost $): Value is required.',
    ])
  })
})

describe('validateLocationForm BR-04 — all-or-nothing on the distance rows (Iman, 2026-09-29)', () => {
  const required = VALIDATION_MESSAGES.required
  test.each([
    ['volume only', { volume: '5' }, ['distance', 'cost']],
    ['cost only', { cost: '5' }, ['distance', 'volume']],
    ['distance only', { distance: '5' }, ['volume', 'cost']],
    ['distance + volume', { distance: '5', volume: '5' }, ['cost']],
    ['all three', { distance: '5', volume: '5', cost: '5' }, []],
    ['none', {}, []],
    ['typed zeros count as present', { distance: '0', volume: '0', cost: '0' }, []],
  ] as const)('%s', (_name, entry, missing) => {
    const { fieldErrors } = validateLocationForm('Named', one(48, entry))
    expect(fieldErrors).toEqual(
      Object.fromEntries(missing.map((field) => [`48-${field}`, required])),
    )
  })

  test('a fixed category never gets BR-04', () => {
    expect(validateLocationForm('Named', one(40, { volume: '5' })).fieldErrors).toEqual({})
  })
})
