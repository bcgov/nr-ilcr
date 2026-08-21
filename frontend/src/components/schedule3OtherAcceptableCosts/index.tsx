import type { FC } from 'react'
import type { OtherAcceptableRow, OtherAcceptableDocument } from '@/interfaces/Schedule3OtherCosts'
import Schedule3SubPage, { type Schedule3SubPageConfig } from '@/components/schedule3SubPage'
import { deriveOtherAcceptableSubtotal } from './derived'
import { validateOtherAcceptable, DESCRIPTION_MAX_LENGTH } from './validation'

const config: Schedule3SubPageConfig<OtherAcceptableRow, OtherAcceptableDocument> = {
  base: '/v1/schedule3/other-acceptable-costs',
  title: 'Other Costs',
  subtitle: 'Grouped acceptable costs for Schedule 3.',
  tableTitle: 'Other Costs',
  addHeading: 'Add Other Cost',
  descriptionMaxLength: DESCRIPTION_MAX_LENGTH,
  loadError: 'Unable to load Other Acceptable Costs.',
  saveError: 'Other cost could not be saved.',
  deleteError: 'Unable to delete other cost.',
  fields: [
    { key: 'total', header: 'Total $', label: 'total', get: (row) => row.total },
    { key: 'pop', header: 'PO&P $', label: 'PO&P', get: (row) => row.pop },
  ],
  // Crown $ is derived live from the row's entered Total/PO&P, matching the backend rule
  // (otherAcceptableCrown): null whenever Total is blank, else Total − PO&P (PO&P treated as 0). This
  // avoids showing −PO&P for a PO&P-only row and keeps the live value consistent with post-save.
  readonlyColumns: [
    {
      key: 'crown',
      header: 'Crown $',
      derive: (v) => (v.total === null ? null : v.total - (v.pop ?? 0)),
    },
  ],
  summaryItems: [
    { key: 'harvest', label: 'Subtotal Total $', value: (doc) => doc.subtotal?.harvest },
    { key: 'pop', label: 'Subtotal PO&P $', value: (doc) => doc.subtotal?.pop },
    { key: 'crown', label: 'Subtotal Crown $', value: (doc) => doc.subtotal?.crown },
  ],
  deriveSummary: deriveOtherAcceptableSubtotal,
  rows: (doc) => doc.rows ?? [],
  validate: (description, values) => validateOtherAcceptable(description, values.total, values.pop),
}

const OtherAcceptableCostsPage: FC = () => <Schedule3SubPage config={config} />

export default OtherAcceptableCostsPage
