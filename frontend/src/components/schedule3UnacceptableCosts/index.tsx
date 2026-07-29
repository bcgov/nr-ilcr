import type { FC } from 'react'
import type { UnacceptableRow, UnacceptableDocument } from '@/interfaces/Schedule3Unacceptable'
import Schedule3SubPage, { type Schedule3SubPageConfig } from '@/components/schedule3SubPage'
import { validateUnacceptable, DESCRIPTION_MAX_LENGTH } from './validation'

// Verbatim legacy intro (schedule3IncludedUnacceptableCosts.xhtml).
const INTRO =
  'Unacceptable costs include income and logging taxes, interest & penalties, annual rents ' +
  'discretionary costs (S111), stumpage & royalty, donations, residue & waste penalty billings ' +
  'and other.'

const config: Schedule3SubPageConfig<UnacceptableRow, UnacceptableDocument> = {
  base: '/v1/schedule3/included-unacceptable-costs',
  title: 'Included Unacceptable Costs',
  subtitle: 'Costs excluded from acceptable admin costs for Schedule 3.',
  tableTitle: 'Included Unacceptable Costs',
  addHeading: 'Add Included Unacceptable Cost',
  deleteHeading: 'Delete unacceptable cost',
  descriptionMaxLength: DESCRIPTION_MAX_LENGTH,
  loadError: 'Unable to load Included Unacceptable Costs.',
  saveError: 'Unacceptable cost could not be saved.',
  deleteError: 'Unable to delete unacceptable cost.',
  intro: INTRO,
  metaField: {
    id: 'annualRentsS111',
    label: 'Annual Rents (Forest Act, S111)',
    value: (doc) => doc.annualRentsTotal,
  },
  fields: [{ key: 'total', header: 'Total $', label: 'total', get: (row) => row.total }],
  summaryItems: [{ label: 'Subtotal Total $', value: (doc) => doc.subtotalTotal }],
  rows: (doc) => doc.rows ?? [],
  validate: (description, values) => validateUnacceptable(description, values.total),
}

const UnacceptableCostsPage: FC = () => <Schedule3SubPage config={config} />

export default UnacceptableCostsPage
