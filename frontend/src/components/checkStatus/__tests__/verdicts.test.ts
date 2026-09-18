import { describe, expect, test } from 'vitest'
import {
  checkStatusFieldLabel,
  checkStatusLocationName,
  labelFor,
} from '@/components/schedule4/validation'
import { flattenVerdict, SCHEDULE_TITLES } from '../verdicts'
import {
  MET_TEXT,
  SCH1_CROSS_CHECK_TEXT,
  SCH1_WARNING_TEXT,
  SCH11_CHECKED_TEXT,
  SCH11_ERROR_TEXT,
  SCH4_ALL_GOOD_MET_TEXT,
  SCH4_EMPTY_LANDING_MET_TEXT,
  SCH5_BLANK_NAME_TEXT,
  SCH5_MET_CAMP_TEXT,
  SCH6_ERROR_TEXT,
  SCH6_RECORD_MET_TEXT,
  SCH7A_BRIDGE_MET_TEXT,
  SCH8_HARVESTED_TEXT,
  SCH8_NO_SAMPLE_TEXT,
  SCH8_SKIDDING_TEXT,
  SCH10_PAGE_ISSUE_TEXT,
  SCH10_ROAD_ISSUE_TEXT,
  schedule1Fail,
  schedule1FailWithWarning,
  schedule1Met,
  schedule10Issues,
  schedule10Met,
  schedule11Fail,
  schedule11Met,
  schedule2Issues,
  schedule2Met,
  schedule4Issues,
  schedule4Met,
  schedule5Issues,
  schedule5Met,
  schedule6AllSegments,
  schedule6Issues,
  schedule6Met,
  schedule7aFail,
  schedule7aMet,
  schedule7bFail,
  schedule8Issues,
  schedule8Met,
  schedule8NoSamples,
  schedule9Fail,
} from './fixtures'

const texts = (list: readonly { text: string }[]) => list.map((m) => m.text)

describe('Check Status verdict flatteners (D5/D6/D8 composition rules)', () => {
  test('the twelve legacy tab titles, verbatim from checkStatus.xhtml', () => {
    expect(SCHEDULE_TITLES).toEqual({
      '1': 'Schedule 1 - Average Cost of Logging',
      '2': 'Schedule 2 - Log Costs & Log Sales',
      '3': 'Schedule 3 - Forest Mgmt Admin Costs',
      '4': 'Schedule 4 - Special Log Transportation Systems',
      '5': 'Schedule 5 - Camp and Access Expense',
      '6': 'Schedule 6 - Road Maintenance',
      '7A': 'Schedule 7A - Bridge Costs',
      '7B': 'Schedule 7B - Culvert Costs',
      '8': 'Schedule 8 - Tree to Truck and Log Hauling',
      '9': 'Schedule 9 - Miscellaneous/Unique Logging Costs',
      '10': 'Schedule 10 - New Road Construction',
      '11': 'Schedule 11 - Basic Silviculture – Other Silviculture',
    })
  })

  // ---- Family A --------------------------------------------------------------------------------

  test('Schedule 1 met: `message` becomes the met line; nothing else', () => {
    const flat = flattenVerdict({ schedule: '1', requirementsMet: true, verdict: schedule1Met })
    expect(flat).toEqual({
      requirementsMet: true,
      errors: [],
      warnings: [],
      requirementsMetMessage: { key: 'scheduleRequirementsMetMsg', text: MET_TEXT },
    })
  })

  test('Schedule 1 fail: every error verbatim including the cross-check line; no met line', () => {
    const flat = flattenVerdict({ schedule: '1', requirementsMet: false, verdict: schedule1Fail })
    expect(flat.requirementsMet).toBe(false)
    expect(flat.errors).toHaveLength(23)
    expect(texts(flat.errors)).toContain(SCH1_CROSS_CHECK_TEXT)
    expect(flat.requirementsMetMessage).toBeNull()
  })

  test('Schedule 1 lines carry the units the legacy Check Status tab showed; other text untouched', () => {
    const flat = flattenVerdict({ schedule: '1', requirementsMet: false, verdict: schedule1Fail })
    const lines = texts(flat.errors)
    expect(lines).toContain('Standing Tree to Loaded Truck - Volume m³: Value Required')
    expect(lines).toContain('Standing Tree to Loaded Truck - Cost $: Value Required')
    expect(lines).toContain('Actual $ Spent - Cost $: Value Required')
    expect(lines.filter((t) => / - Volume: | - Cost: /.test(t))).toEqual([])
    // The cross-check line has no ` - Cost: ` segment and is left byte-for-byte.
    expect(lines).toContain(SCH1_CROSS_CHECK_TEXT)
    expect(flat.errors[0].key).toBe('missingRequiredFieldMsg')
  })

  test('Schedule 1 warnings ride through as warnings, separate from errors', () => {
    const flat = flattenVerdict({
      schedule: '1',
      requirementsMet: false,
      verdict: schedule1FailWithWarning,
    })
    expect(texts(flat.warnings)).toEqual([SCH1_WARNING_TEXT])
    expect(texts(flat.errors)).not.toContain(SCH1_WARNING_TEXT)
  })

  test('Schedule 7A: errors only — bridgeMessages are NOT rendered (D8)', () => {
    const flat = flattenVerdict({ schedule: '7A', requirementsMet: false, verdict: schedule7aFail })
    expect(flat.errors).toHaveLength(2)
    expect(texts(flat.errors)).not.toContain(SCH7A_BRIDGE_MET_TEXT)
    expect(flat.requirementsMetMessage).toBeNull()
    const met = flattenVerdict({ schedule: '7A', requirementsMet: true, verdict: schedule7aMet })
    expect(met.requirementsMetMessage?.text).toBe(MET_TEXT)
  })

  test('Schedule 7B and 9 carry requirementsMetMessage as-is', () => {
    const fail = flattenVerdict({ schedule: '7B', requirementsMet: false, verdict: schedule7bFail })
    expect(fail.errors).toHaveLength(3)
    expect(fail.requirementsMetMessage).toBeNull()
    const nine = flattenVerdict({ schedule: '9', requirementsMet: false, verdict: schedule9Fail })
    expect(nine.errors).toHaveLength(4)
  })

  test('Schedule 11: the always-present "Status has been checked" is dropped (deviation L)', () => {
    const met = flattenVerdict({ schedule: '11', requirementsMet: true, verdict: schedule11Met })
    expect(met.requirementsMetMessage?.text).toBe(MET_TEXT)
    expect(texts(met.errors)).not.toContain(SCH11_CHECKED_TEXT)
    const fail = flattenVerdict({ schedule: '11', requirementsMet: false, verdict: schedule11Fail })
    expect(texts(fail.errors)).toEqual([
      SCH11_ERROR_TEXT,
      'location  : Missing Planned - Planned cost: Value Required',
    ])
    expect(fail.requirementsMetMessage).toBeNull()
    expect(texts(fail.errors)).not.toContain(SCH11_CHECKED_TEXT)
  })

  // ---- Family B --------------------------------------------------------------------------------

  test('Schedule 2: MET → messages[0] is the met line; ISSUES → messages are the errors', () => {
    const met = flattenVerdict({ schedule: '2', requirementsMet: true, verdict: schedule2Met })
    expect(met.requirementsMetMessage?.text).toBe(MET_TEXT)
    expect(met.errors).toEqual([])
    const issues = flattenVerdict({
      schedule: '2',
      requirementsMet: false,
      verdict: schedule2Issues,
    })
    expect(texts(issues.errors)).toEqual(['Purchased/Private Log Costs - Cost $: Value Required'])
    expect(issues.requirementsMetMessage).toBeNull()
  })

  test('Schedule 4 ISSUES: a NULL-named location is identified by id, the field from the client label table (D5); met locations render nothing (D8)', () => {
    const flat = flattenVerdict({ schedule: '4', requirementsMet: false, verdict: schedule4Issues })
    expect(texts(flat.errors)).toEqual(['Location 7001 - Description: Value Required'])
    expect(flat.errors[0].key).toBe('missingRequiredFieldMsg')
    expect(texts(flat.errors)).not.toContain(SCH4_EMPTY_LANDING_MET_TEXT)
    expect(flat.requirementsMetMessage).toBeNull()
  })

  test('Schedule 4 MET: one schedule line; the per-location met messages are ignored (D8)', () => {
    const flat = flattenVerdict({ schedule: '4', requirementsMet: true, verdict: schedule4Met })
    expect(flat.requirementsMetMessage?.text).toBe(MET_TEXT)
    expect(flat.errors).toEqual([])
    expect(JSON.stringify(flat)).not.toContain(SCH4_ALL_GOOD_MET_TEXT)
  })

  test('a cost-item code, were one ever emitted again, names its category the way the Schedule 4 page does (#326)', () => {
    const flat = flattenVerdict({
      schedule: '4',
      requirementsMet: false,
      verdict: {
        ...schedule4Issues,
        locations: [
          {
            id: 7001,
            name: 'Harbour Dump',
            met: false,
            messages: [],
            issues: [
              { code: 52, message: { key: 'missingRequiredFieldMsg', text: 'Value Required' } },
              { code: 999, message: { key: 'missingRequiredFieldMsg', text: 'Value Required' } },
            ],
          },
        ],
      },
    })
    expect(texts(flat.errors)).toEqual([
      'Harbour Dump - Rail Haul (Cost $): Value Required',
      // An unknown code names the location only — never a fabricated field.
      'Harbour Dump: Value Required',
    ])
  })

  test('checkStatusLocationName: null, blank and whitespace names fall back to the id; a null id to "Location"', () => {
    expect(checkStatusLocationName({ id: 7001, name: null })).toBe('Location 7001')
    expect(checkStatusLocationName({ id: 7001, name: '' })).toBe('Location 7001')
    expect(checkStatusLocationName({ id: 7001, name: '   ' })).toBe('Location 7001')
    expect(checkStatusLocationName({ id: null, name: null })).toBe('Location')
    expect(checkStatusLocationName({ id: 7001, name: 'Harbour Dump' })).toBe('Harbour Dump')
  })

  test('Schedule 4 ISSUES: a blank-string name flattens the same way as a null one', () => {
    const flat = flattenVerdict({
      schedule: '4',
      requirementsMet: false,
      verdict: {
        ...schedule4Issues,
        locations: [{ ...schedule4Issues.locations[0], name: '' }],
      },
    })
    expect(texts(flat.errors)).toEqual(['Location 7001 - Description: Value Required'])
  })

  test('checkStatusFieldLabel: Description for code 0, "<category> (Cost $)" for a cost-item code, undefined otherwise', () => {
    expect(checkStatusFieldLabel(0)).toBe('Description')
    expect(checkStatusFieldLabel(40)).toBe('Lakeside Dry Dump (Cost $)')
    expect(checkStatusFieldLabel(46)).toBe('Truck Rehaul-Dewater/Transfer (Cost $)')
    expect(checkStatusFieldLabel(999)).toBeUndefined()
  })

  test('labelFor covers every category and sub-page code and is undefined for an unknown code', () => {
    expect(labelFor(40)).toBe('Lakeside Dry Dump')
    expect(labelFor(52)).toBe('Rail Haul')
    expect(labelFor(43)).toBe('Towing Total')
    expect(labelFor(46)).toBe('Truck Rehaul-Dewater/Transfer')
    expect(labelFor(55)).toBe('Other Transportation')
    expect(labelFor(999)).toBeUndefined()
  })

  test('Schedule 4: an unknown code falls back to the bare text — never throws, never invents a label', () => {
    const flat = flattenVerdict({
      schedule: '4',
      requirementsMet: false,
      verdict: {
        outcome: 'ISSUES',
        messages: [],
        locations: [
          {
            id: 1,
            name: 'Odd Dump',
            met: false,
            messages: [],
            issues: [
              { code: 999, message: { key: 'missingRequiredFieldMsg', text: 'Value Required' } },
            ],
          },
        ],
      },
    })
    expect(texts(flat.errors)).toEqual(['Odd Dump: Value Required'])
  })

  test('Schedule 5: eight failing-camp lines, text already composed; the met camp renders nothing (D8)', () => {
    const flat = flattenVerdict({ schedule: '5', requirementsMet: false, verdict: schedule5Issues })
    expect(flat.errors).toHaveLength(8)
    expect(texts(flat.errors)).toContain(SCH5_BLANK_NAME_TEXT)
    expect(texts(flat.errors)).not.toContain(SCH5_MET_CAMP_TEXT)
    // Structural read: the CampCheckMessage's `field` is not rendered, key/text survive.
    expect(flat.errors[0]).toEqual({
      key: 'missingRequiredFieldMsg',
      text: 'Camp Report Name : Bare Descriptor Camp - Road Distance to Operating Area: Value Required',
    })
    // The one Schedule 5 field legacy's tab labelled with a unit; the sub-page lines already carry theirs.
    expect(texts(flat.errors)).toContain(
      'Camp Report Name : Bare Descriptor Camp - Associated Camp Volume (m³): Value Required',
    )
    expect(texts(flat.errors)).toContain(
      'Camp Report Name : Sub Page Issue Camp - Other Camp Expense List (Cost $): Value Required',
    )
    const met = flattenVerdict({ schedule: '5', requirementsMet: true, verdict: schedule5Met })
    expect(met.requirementsMetMessage?.text).toBe(MET_TEXT)
  })

  test('Schedule 6: failing records only; metMessage is ignored (D8)', () => {
    const flat = flattenVerdict({
      schedule: '6',
      requirementsMet: false,
      verdict: schedule6AllSegments,
    })
    expect(texts(flat.errors)).toEqual([
      'Road : 1 - TSA or TFL TYPE : Value Required',
      'Road : 1 - Supply Block : Value Required',
      SCH6_ERROR_TEXT,
      'Road : 2 - TFL Number : Value Required',
    ])
    expect(texts(flat.errors)).not.toContain(SCH6_RECORD_MET_TEXT)
    const one = flattenVerdict({ schedule: '6', requirementsMet: false, verdict: schedule6Issues })
    expect(texts(one.errors)).toEqual([SCH6_ERROR_TEXT])
    const met = flattenVerdict({ schedule: '6', requirementsMet: true, verdict: schedule6Met })
    expect(met.requirementsMetMessage?.text).toBe(MET_TEXT)
  })

  test('Schedule 8 ISSUES: page issues then each sample’s, attributed positionally (D6), texts verbatim', () => {
    const flat = flattenVerdict({ schedule: '8', requirementsMet: false, verdict: schedule8Issues })
    expect(texts(flat.errors)).toEqual([
      'Page # 1 - Division: Value Required',
      'Page # 1 - Contact: Value Required',
      'Page # 1 - Phone: Value Required',
      'Page # 1 - Supply Block: Value Required',
      'Page # 1 - Sample # 1 - Cut Block: Value Required',
      'Page # 1 - Sample # 1 - Slope Distance: Value Required',
      'Page # 1 - Sample # 1 - Support Number: Value Required',
      'Page # 1 - Sample # 1 - Support Avg Dist: Value Required',
      'Page # 1 - Sample # 1 - Coniferous: Value Required',
      'Page # 1 - Sample # 1 - Deciduous: Value Required',
      'Page # 1 - Sample # 1 - Original TtT Rate: Value Required',
      `Page # 1 - Sample # 1 - Skidding/Yarding: ${SCH8_SKIDDING_TEXT}`,
      `Page # 1 - Sample # 2 - Actual Harvested: ${SCH8_HARVESTED_TEXT}`,
    ])
    // The raw DB ids never appear.
    expect(JSON.stringify(flat)).not.toMatch(/897[234]/)
  })

  test('Schedule 8: the no-samples page case and MET with `pages` still populated', () => {
    const noSamples = flattenVerdict({
      schedule: '8',
      requirementsMet: false,
      verdict: schedule8NoSamples,
    })
    expect(texts(noSamples.errors)).toEqual([`Page # 1 - Sample: ${SCH8_NO_SAMPLE_TEXT}`])
    const met = flattenVerdict({ schedule: '8', requirementsMet: true, verdict: schedule8Met })
    expect(met.errors).toEqual([])
    expect(met.requirementsMetMessage?.text).toBe(MET_TEXT)
  })

  test('Schedule 10 goes through summariseCheckStatus (road issues attributed to their road)', () => {
    const flat = flattenVerdict({
      schedule: '10',
      requirementsMet: false,
      verdict: schedule10Issues,
    })
    expect(texts(flat.errors)).toEqual([
      SCH10_PAGE_ISSUE_TEXT,
      'Page 1, Period: null, TSA: 01, SB: 01A, TFL:- Period Surveyed: Value Required',
      `Road #1, Blank Material Road: ${SCH10_ROAD_ISSUE_TEXT}`,
      'Road #2, Second Road: Page 1, Period: null, TSA: 01, SB: 01A, TFL:-, Road #2, Second Road Material Type Total (%): Total value must be equal to 100.',
    ])
    const met = flattenVerdict({ schedule: '10', requirementsMet: true, verdict: schedule10Met })
    expect(met.requirementsMetMessage?.text).toBe(MET_TEXT)
  })

  test('degenerate payloads flatten to nothing — no invented text', () => {
    const familyA = flattenVerdict({
      schedule: '9',
      requirementsMet: false,
      verdict: { requirementsMet: false, errors: [], requirementsMetMessage: null },
    })
    expect(familyA).toEqual({
      requirementsMet: false,
      errors: [],
      warnings: [],
      requirementsMetMessage: null,
    })
    const familyB = flattenVerdict({
      schedule: '6',
      requirementsMet: false,
      verdict: {
        outcome: 'ISSUES',
        messages: [],
        records: [{ recordId: 1, rowCounter: 1, met: true, issues: [] }],
      },
    })
    expect(familyB.errors).toEqual([])
    expect(familyB.requirementsMetMessage).toBeNull()
  })
})
