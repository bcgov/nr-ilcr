// MSW fixtures for the Check Status page. Every verdict below is a golden body from
// backend/src/test/resources/checkstatus-golden/ (the real bytes each `verdict` slot carries on the
// wire), copied verbatim — except the five marked HAND-COMPOSED, for which no golden exists and the
// body is written from the DTO records instead.

import type CheckStatusSweepResponse from '@/interfaces/CheckStatusSweep'
import type { ScheduleCheckResult, ScheduleCode } from '@/interfaces/CheckStatusSweep'
import type CheckStatusResponse from '@/interfaces/CheckStatusResponse'
import type { CheckStatusResponse as Schedule2CheckStatusResponse } from '@/interfaces/Schedule2Response'
import type { Schedule4CheckStatusResponse } from '@/interfaces/Schedule4Response'
import type { Schedule5CheckStatusResponse } from '@/interfaces/Schedule5Response'
import type { Schedule6CheckStatusResponse } from '@/interfaces/Schedule6Response'
import type { Schedule7aCheckStatusResponse } from '@/interfaces/Schedule7aResponse'
import type { Schedule7bCheckStatusResponse } from '@/interfaces/Schedule7bResponse'
import type { Schedule8CheckStatusResponse } from '@/interfaces/Schedule8Response'
import type { Schedule9CheckStatusResponse } from '@/interfaces/Schedule9Response'
import type { Schedule10CheckStatusResponse } from '@/interfaces/Schedule10Response'
import type { Schedule11CheckStatusResponse } from '@/interfaces/Schedule11Response'

export const MET_TEXT = 'All requirements for this schedule have been met'
const MET = { key: 'scheduleRequirementsMetMsg', text: MET_TEXT }
const REQUIRED = { key: 'missingRequiredFieldMsg', text: 'Value Required' }

// ---- Family A ---------------------------------------------------------------------------------

/** schedule1-528-2021.json */
export const schedule1Met: CheckStatusResponse = {
  requirementsMet: true,
  errors: [],
  warnings: [],
  message: MET,
}

export const SCH1_CROSS_CHECK_TEXT =
  'Subtotal Other Costs (0): Cost: must be greater than 0 when Volume is greater than 0'

/** schedule1-530-2021.json — `message` omitted on fail. */
export const schedule1Fail: CheckStatusResponse = {
  requirementsMet: false,
  errors: [
    {
      key: 'missingRequiredFieldMsg',
      text: 'Standing Tree to Loaded Truck - Volume: Value Required',
    },
    {
      key: 'missingRequiredFieldMsg',
      text: 'Standing Tree to Loaded Truck - Cost: Value Required',
    },
    { key: 'missingRequiredFieldMsg', text: 'Log Transportation - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Log Transportation - Cost: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Road Management - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Road Management - Cost: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Road Construction Costs - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Road Construction Costs - Cost: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Post Logging Treatment - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Post Logging Treatment - Cost: Value Required' },
    {
      key: 'missingRequiredFieldMsg',
      text: 'Forest Management Administration - Volume: Value Required',
    },
    { key: 'missingRequiredFieldMsg', text: 'Stumpage and Royalty - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Stumpage and Royalty - Cost: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Depletion and Amortization - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Depletion and Amortization - Cost: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Subtotal Company Logging - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Actual $ Spent - Volume: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Actual $ Spent - Cost: Value Required' },
    {
      key: 'missingRequiredFieldMsg',
      text: 'Less Silviculture Admin Costs - Volume: Value Required',
    },
    {
      key: 'missingRequiredFieldMsg',
      text: 'Accrued less Actual $ Spent - Volume: Value Required',
    },
    { key: 'missingRequiredFieldMsg', text: 'Accrued less Actual $ Spent - Cost: Value Required' },
    { key: 'missingRequiredFieldMsg', text: 'Total Silviculture - Volume: Value Required' },
    { key: 'sch1.subtotal.other.costs.costs.grearter.than.zero', text: SCH1_CROSS_CHECK_TEXT },
  ],
  warnings: [],
  message: null,
}

export const SCH1_WARNING_TEXT =
  'Subtotal Other Costs (2) - Cost: One or more entries contain an empty Cost value. Please verify there are no Other Costs to be entered.'

/** HAND-COMPOSED — no golden carries the Schedule 1 WRN-002 Check-Status branch. */
export const schedule1FailWithWarning: CheckStatusResponse = {
  ...schedule1Fail,
  warnings: [
    { key: 'warning.schedule1.checkstatus.subtotalother.costEmpty', text: SCH1_WARNING_TEXT },
  ],
}

/** schedule3-572-2021.json */
export const schedule3Met: CheckStatusResponse = {
  requirementsMet: true,
  errors: [],
  warnings: [],
  message: MET,
}

export const SCH7A_BRIDGE_MET_TEXT = 'All requirements for 1 have been met.'

/** schedule7a-514-2021.json */
export const schedule7aFail: Schedule7aCheckStatusResponse = {
  requirementsMet: false,
  errors: [
    {
      key: 'missingRequiredFieldMsg',
      text: 'Bridge Report Id : 3 - Certification After install Cost : Value Required',
    },
    { key: 'missingRequiredFieldMsg', text: 'Bridge Report Id : 3 - Other Costs : Value Required' },
  ],
  bridgeMessages: [
    { key: 'bridgeRequirementsMetMsg', text: SCH7A_BRIDGE_MET_TEXT },
    { key: 'bridgeRequirementsMetMsg', text: 'All requirements for 2 have been met.' },
  ],
  requirementsMetMessage: null,
}

/** HAND-COMPOSED — no 7A MET golden; shape from Schedule7aCheckStatusResponse.java. */
export const schedule7aMet: Schedule7aCheckStatusResponse = {
  requirementsMet: true,
  errors: [],
  bridgeMessages: [],
  requirementsMetMessage: MET,
}

export const SCH7B_ERROR_TEXT =
  'Culvert Report Id : 3 - Culvert Type Round - Span size: Value Required'

/** schedule7b-514-2021.json */
export const schedule7bFail: Schedule7bCheckStatusResponse = {
  requirementsMet: false,
  errors: [
    { key: 'missingRequiredFieldMsg', text: SCH7B_ERROR_TEXT },
    { key: 'missingRequiredFieldMsg', text: 'Culvert Report Id: 3 - Length : Value Required' },
    {
      key: 'missingRequiredFieldMsg',
      text: 'Culvert Report Id: 3 - Install Cost : Value Required',
    },
  ],
  requirementsMetMessage: null,
}

/** HAND-COMPOSED — no 7B MET golden; shape from Schedule7bCheckStatusResponse.java. */
export const schedule7bMet: Schedule7bCheckStatusResponse = {
  requirementsMet: true,
  errors: [],
  requirementsMetMessage: MET,
}

/** schedule9-704-2021.json */
export const schedule9Met: Schedule9CheckStatusResponse = {
  requirementsMet: true,
  errors: [],
  requirementsMetMessage: MET,
}

/** schedule9-703-2021.json */
export const schedule9Fail: Schedule9CheckStatusResponse = {
  requirementsMet: false,
  errors: [
    {
      key: 'missingRequiredFieldMsg',
      text: 'Contractual Work Report Id : 1 Number of Units: Value Required',
    },
    {
      key: 'missingRequiredFieldMsg',
      text: 'Contractual Work Report Id : 1 Cost$: Value Required',
    },
    {
      key: 'invalidRangeErrorMsg',
      text: 'Contractual Work Report Id : 2 Side Slope %: Entered value must be between 0 and 99.',
    },
    {
      key: 'missingRequiredFieldMsg',
      text: 'Contractual Work Report Id : 3 Company ID: Value Required',
    },
  ],
  requirementsMetMessage: null,
}

export const SCH11_CHECKED_TEXT = 'Status has been checked'

/** schedule11-613-2021.json — `message` is always present and is NOT rendered on this page. */
export const schedule11Met: Schedule11CheckStatusResponse = {
  requirementsMet: true,
  errors: [],
  requirementsMetMessage: MET,
  message: { key: 'checkStatusMessage', text: SCH11_CHECKED_TEXT },
}

export const SCH11_ERROR_TEXT = 'location  : Missing Actual - Actual cost: Value Required'

/** schedule11-617-2021.json */
export const schedule11Fail: Schedule11CheckStatusResponse = {
  requirementsMet: false,
  errors: [
    { key: 'missingRequiredFieldMsg', text: SCH11_ERROR_TEXT },
    {
      key: 'missingRequiredFieldMsg',
      text: 'location  : Missing Planned - Planned cost: Value Required',
    },
  ],
  requirementsMetMessage: null,
  message: { key: 'checkStatusMessage', text: SCH11_CHECKED_TEXT },
}

// ---- Family B ---------------------------------------------------------------------------------

/** schedule2-621-2021.json */
export const schedule2Met: Schedule2CheckStatusResponse = { outcome: 'MET', messages: [MET] }

export const SCH2_ERROR_TEXT = 'Purchased/Private Log Costs - Cost: Value Required'

/**
 * HAND-COMPOSED — no Schedule 2 ISSUES golden; Schedule2CheckStatusResponse.java:4-15 (no entity list),
 * text as Schedule2Service composes it (LABEL_PURCHASED_LOG_COST + ": " + missingRequiredFieldMsg).
 */
export const schedule2Issues: Schedule2CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [{ key: 'missingRequiredFieldMsg', text: SCH2_ERROR_TEXT }],
}

export const SCH4_EMPTY_LANDING_MET_TEXT = 'All requirements for Empty Landing have been met.'

/** schedule4-514-2021.json — one failing location (code 52 = Rail Haul), one met. */
export const schedule4Issues: Schedule4CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [],
  locations: [
    {
      id: 7001,
      name: 'Harbour Dump',
      met: false,
      messages: [],
      issues: [{ code: 52, message: REQUIRED }],
    },
    {
      id: 7002,
      name: 'Empty Landing',
      met: true,
      messages: [{ key: 'locationRequirementsMetMsg', text: SCH4_EMPTY_LANDING_MET_TEXT }],
      issues: [],
    },
  ],
}

export const SCH4_ALL_GOOD_MET_TEXT = 'All requirements for All Good Dump have been met.'

/** schedule4-560-2021.json — MET still ships every location, each with its own met message. */
export const schedule4Met: Schedule4CheckStatusResponse = {
  outcome: 'MET',
  messages: [MET],
  locations: [
    {
      id: 8080,
      name: 'All Good Dump',
      met: true,
      messages: [{ key: 'locationRequirementsMetMsg', text: SCH4_ALL_GOOD_MET_TEXT }],
      issues: [],
    },
  ],
}

export const SCH5_MET_CAMP_TEXT = 'All requirements for Zero Descriptor Camp have been met.'
export const SCH5_BLANK_NAME_TEXT = 'Camp Report Name :     - Camp name: Value Required'

/** schedule5-673-2021.json — four camps: one met, three failing with 3 + 1 + 4 messages. */
export const schedule5Issues: Schedule5CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [],
  camps: [
    {
      campId: 8211,
      campName: 'Zero Descriptor Camp',
      requirementsMet: true,
      messages: [{ key: 'campRequirementsMetMsg', text: SCH5_MET_CAMP_TEXT }],
    },
    {
      campId: 8212,
      campName: 'Bare Descriptor Camp',
      requirementsMet: false,
      messages: [
        {
          key: 'missingRequiredFieldMsg',
          field: 'roadDistanceToOperatingArea',
          text: 'Camp Report Name : Bare Descriptor Camp - Road Distance to Operating Area: Value Required',
        },
        {
          key: 'missingRequiredFieldMsg',
          field: 'sizeOfCamp',
          text: 'Camp Report Name : Bare Descriptor Camp - Size of Camp: Value Required',
        },
        {
          key: 'missingRequiredFieldMsg',
          field: 'associatedCampVolume',
          text: 'Camp Report Name : Bare Descriptor Camp - Associated Camp Volume: Value Required',
        },
      ],
    },
    {
      campId: 8213,
      campName: '   ',
      requirementsMet: false,
      messages: [{ key: 'missingRequiredFieldMsg', field: 'campName', text: SCH5_BLANK_NAME_TEXT }],
    },
    {
      campId: 8214,
      campName: 'Sub Page Issue Camp',
      requirementsMet: false,
      messages: [
        {
          key: 'missingRequiredFieldMsg',
          field: 'otherCampExpenseDescription',
          text: 'Camp Report Name : Sub Page Issue Camp - Other Camp Expense List (Description): Value Required',
        },
        {
          key: 'missingRequiredFieldMsg',
          field: 'otherCampExpenseCost',
          text: 'Camp Report Name : Sub Page Issue Camp - Other Camp Expense List (Cost $): Value Required',
        },
        {
          key: 'missingRequiredFieldMsg',
          field: 'otherAccessExpenseDescription',
          text: 'Camp Report Name : Sub Page Issue Camp - Other Access Expense List (Description): Value Required',
        },
        {
          key: 'missingRequiredFieldMsg',
          field: 'otherAccessExpenseCost',
          text: 'Camp Report Name : Sub Page Issue Camp - Other Access Expense List (Cost $): Value Required',
        },
      ],
    },
  ],
}

/** HAND-COMPOSED — no Schedule 5 MET golden; on MET `camps` is empty (Schedule5CheckStatusResponse.java). */
export const schedule5Met: Schedule5CheckStatusResponse = {
  outcome: 'MET',
  messages: [MET],
  camps: [],
}

/** schedule6-662-2021-met.json */
export const schedule6Met: Schedule6CheckStatusResponse = {
  outcome: 'MET',
  messages: [MET],
  records: [],
}

export const SCH6_ERROR_TEXT = 'Road : 1 - TSA or TFL (Cost $) : Value Required'

/** schedule6-726-2020-issues.json */
export const schedule6Issues: Schedule6CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [],
  records: [
    {
      recordId: 1,
      rowCounter: 1,
      met: false,
      issues: [
        { field: 'cost', message: { key: 'missingRequiredFieldMsg', text: SCH6_ERROR_TEXT } },
      ],
    },
  ],
}

export const SCH6_RECORD_MET_TEXT = 'All requirements for 3 have been met.'

/** schedule6-726-2020-all-segments.json — two failing records and one met (with `metMessage`). */
export const schedule6AllSegments: Schedule6CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [],
  records: [
    {
      recordId: 1,
      rowCounter: 1,
      met: false,
      issues: [
        {
          field: 'areaType',
          message: {
            key: 'missingRequiredFieldMsg',
            text: 'Road : 1 - TSA or TFL TYPE : Value Required',
          },
        },
        {
          field: 'supplyBlock',
          message: {
            key: 'missingRequiredFieldMsg',
            text: 'Road : 1 - Supply Block : Value Required',
          },
        },
        { field: 'cost', message: { key: 'missingRequiredFieldMsg', text: SCH6_ERROR_TEXT } },
      ],
    },
    {
      recordId: 2,
      rowCounter: 2,
      met: false,
      issues: [
        {
          field: 'tflNumber',
          message: {
            key: 'missingRequiredFieldMsg',
            text: 'Road : 2 - TFL Number : Value Required',
          },
        },
      ],
    },
    {
      recordId: 3,
      rowCounter: 3,
      met: true,
      metMessage: { key: 'roadRequirementsMetMsg', text: SCH6_RECORD_MET_TEXT },
      issues: [],
    },
  ],
}

/** schedule8-600-2021.json — MET, and `pages` is still populated. */
export const schedule8Met: Schedule8CheckStatusResponse = {
  outcome: 'MET',
  messages: [MET],
  pages: [{ id: 8970, met: true, issues: [], samples: [{ id: 8971, met: true, issues: [] }] }],
}

export const SCH8_SKIDDING_TEXT =
  'The total percent value for skidding/yarding must be equal to 100%'
export const SCH8_HARVESTED_TEXT = 'Total value must be greater than 0.'

/** schedule8-601-2021.json — one page with four issues and two samples (eight issues, then one). */
export const schedule8Issues: Schedule8CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [],
  pages: [
    {
      id: 8972,
      met: false,
      issues: [
        { field: 'Division', message: REQUIRED },
        { field: 'Contact', message: REQUIRED },
        { field: 'Phone', message: REQUIRED },
        { field: 'Supply Block', message: REQUIRED },
      ],
      samples: [
        {
          id: 8973,
          met: false,
          issues: [
            { field: 'Cut Block', message: REQUIRED },
            { field: 'Slope Distance', message: REQUIRED },
            { field: 'Support Number', message: REQUIRED },
            { field: 'Support Avg Dist', message: REQUIRED },
            { field: 'Coniferous', message: REQUIRED },
            { field: 'Deciduous', message: REQUIRED },
            { field: 'Original TtT Rate', message: REQUIRED },
            {
              field: 'Skidding/Yarding',
              message: { key: 'skiddingYardingEqualsCentPercent', text: SCH8_SKIDDING_TEXT },
            },
          ],
        },
        {
          id: 8974,
          met: false,
          issues: [
            {
              field: 'Actual Harvested',
              message: { key: 'invalidLowerRangeZeroErrorMsg', text: SCH8_HARVESTED_TEXT },
            },
          ],
        },
      ],
    },
  ],
}

export const SCH8_NO_SAMPLE_TEXT = 'Please create a TtT sample data record for this page'

/** schedule8-602-2021.json — a page-level issue and no samples. */
export const schedule8NoSamples: Schedule8CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [],
  pages: [
    {
      id: 8975,
      met: false,
      issues: [
        {
          field: 'Sample',
          message: { key: 'treeToTruckReportAtleastOneSample', text: SCH8_NO_SAMPLE_TEXT },
        },
      ],
      samples: [],
    },
  ],
}

/** schedule10-715-2021.json */
export const schedule10Met: Schedule10CheckStatusResponse = {
  outcome: 'MET',
  messages: [MET],
  pages: [],
}

export const SCH10_PAGE_ISSUE_TEXT =
  'Page 1, Period: null, TSA: 01, SB: 01A, TFL:- Division: Value Required'
export const SCH10_ROAD_ISSUE_TEXT =
  'Page 1, Period: null, TSA: 01, SB: 01A, TFL:-, Road #1, Blank Material Road Material Type Total (%): Total value must be equal to 100.'

/** schedule10-720-2021.json (page 1 only — page 2's nine lines add nothing the assertions need). */
export const schedule10Issues: Schedule10CheckStatusResponse = {
  outcome: 'ISSUES',
  messages: [],
  pages: [
    {
      pageId: 8951,
      pageNumber: 1,
      pageLabel: 'Page 1, Period: null, TSA: 01, SB: 01A, TFL:-',
      met: false,
      issues: [
        {
          field: 'divisionName',
          message: { key: 'missingRequiredFieldMsg', text: SCH10_PAGE_ISSUE_TEXT },
        },
        {
          field: 'constructionPeriod',
          message: {
            key: 'missingRequiredFieldMsg',
            text: 'Page 1, Period: null, TSA: 01, SB: 01A, TFL:- Period Surveyed: Value Required',
          },
        },
      ],
      roadDetails: [
        {
          roadDetailId: 8961,
          rowNumber: 1,
          roadDetailLabel: 'Road #1, Blank Material Road',
          met: false,
          issues: [
            {
              field: 'materialTypeTotal',
              message: { key: 'invalidTotalErrorMsg', text: SCH10_ROAD_ISSUE_TEXT },
            },
          ],
        },
        {
          roadDetailId: 8962,
          rowNumber: 2,
          roadDetailLabel: 'Road #2, Second Road',
          met: false,
          issues: [
            {
              field: 'materialTypeTotal',
              message: {
                key: 'invalidTotalErrorMsg',
                text: 'Page 1, Period: null, TSA: 01, SB: 01A, TFL:-, Road #2, Second Road Material Type Total (%): Total value must be equal to 100.',
              },
            },
          ],
        },
      ],
    },
  ],
}

// ---- Sweep builders ---------------------------------------------------------------------------

/** Every schedule met, in legacy tab order: the S01 body. */
export const ALL_MET: readonly ScheduleCheckResult[] = [
  { schedule: '1', requirementsMet: true, verdict: schedule1Met },
  { schedule: '2', requirementsMet: true, verdict: schedule2Met },
  { schedule: '3', requirementsMet: true, verdict: schedule3Met },
  { schedule: '4', requirementsMet: true, verdict: schedule4Met },
  { schedule: '5', requirementsMet: true, verdict: schedule5Met },
  { schedule: '6', requirementsMet: true, verdict: schedule6Met },
  { schedule: '7A', requirementsMet: true, verdict: schedule7aMet },
  { schedule: '7B', requirementsMet: true, verdict: schedule7bMet },
  { schedule: '8', requirementsMet: true, verdict: schedule8Met },
  { schedule: '9', requirementsMet: true, verdict: schedule9Met },
  { schedule: '10', requirementsMet: true, verdict: schedule10Met },
  { schedule: '11', requirementsMet: true, verdict: schedule11Met },
]

type SweepOptions = {
  readonly millId?: number
  readonly year?: number
  /** The 1–10 track's status code; `null` OMITS the property, as the wire does for a null column. */
  readonly statusCode1To10?: string | null
  readonly statusCode11?: string | null
  /** Replace individual verdicts; anything not named stays met. */
  readonly overrides?: readonly ScheduleCheckResult[]
}

/**
 * A full sweep body: all twelve met unless overridden, partitioned 11 + 1 in legacy order, with the
 * track roll-ups derived from the verdicts exactly as TrackCheckResult.of does.
 */
export const sweep = ({
  millId = 13050,
  year = 2017,
  statusCode1To10 = 'D',
  statusCode11 = null,
  overrides = [],
}: SweepOptions = {}): CheckStatusSweepResponse => {
  const byCode = new Map<ScheduleCode, ScheduleCheckResult>(
    ALL_MET.map((entry) => [entry.schedule, entry]),
  )
  for (const override of overrides) {
    byCode.set(override.schedule, override)
  }
  const ordered = ALL_MET.map((entry) => byCode.get(entry.schedule) as ScheduleCheckResult)
  const first = ordered.filter((entry) => entry.schedule !== '11')
  const eleven = ordered.filter((entry) => entry.schedule === '11')
  return {
    millId,
    year,
    schedules1To10: {
      ...(statusCode1To10 === null ? {} : { statusCode: statusCode1To10 }),
      requirementsMet: first.every((entry) => entry.requirementsMet),
      schedules: first,
    },
    schedule11: {
      ...(statusCode11 === null ? {} : { statusCode: statusCode11 }),
      requirementsMet: eleven.every((entry) => entry.requirementsMet),
      schedules: eleven,
    },
  }
}
