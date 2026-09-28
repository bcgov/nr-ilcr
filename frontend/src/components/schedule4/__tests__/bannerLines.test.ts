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

  test.each(cases)(
    '$panel panel, code $code: a Volume alone requires the Distance',
    ({ panel, code, labels }) => {
      const validation = validateLocationForm('Named', one(code, { volume: '5' }))
      expect(locationBannerLines('Named', validation, panel)).toEqual([
        `${labels[0]}: Value is required.`,
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
