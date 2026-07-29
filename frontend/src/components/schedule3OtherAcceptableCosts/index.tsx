import type { FC } from 'react'
import type { OtherAcceptableRow, OtherAcceptableDocument } from '@/interfaces/Schedule3OtherCosts'
import Schedule3SubPage, { type Schedule3SubPageConfig } from '@/components/schedule3SubPage'
import { validateOtherAcceptable, DESCRIPTION_MAX_LENGTH } from './validation'

const config: Schedule3SubPageConfig<OtherAcceptableRow, OtherAcceptableDocument> = {
  base: '/v1/schedule3/other-acceptable-costs',
  title: 'Other Acceptable Costs',
  subtitle: 'Grouped acceptable costs for Schedule 3.',
  tableTitle: 'Other Acceptable Costs',
  addHeading: 'Add Other Acceptable Cost',
  deleteHeading: 'Delete other cost',
  descriptionMaxLength: DESCRIPTION_MAX_LENGTH,
  loadError: 'Unable to load Other Acceptable Costs.',
  saveError: 'Other cost could not be saved.',
  deleteError: 'Unable to delete other cost.',
  fields: [
    { key: 'total', header: 'Total $', label: 'total', get: (row) => row.total },
    { key: 'pop', header: 'PO&P $', label: 'PO&P', get: (row) => row.pop },
  ],
  readonlyColumns: [{ header: 'Crown $', value: (row) => row.crown }],
  summaryItems: [
    { label: 'Subtotal Total $', value: (doc) => doc.subtotal?.harvest },
    { label: 'Subtotal PO&P $', value: (doc) => doc.subtotal?.pop },
    { label: 'Subtotal Crown $', value: (doc) => doc.subtotal?.crown },
  ],
  rows: (doc) => doc.rows ?? [],
  validate: (description, values) => validateOtherAcceptable(description, values.total, values.pop),
}

const OtherAcceptableCostsPage: FC = () => <Schedule3SubPage config={config} />

export default OtherAcceptableCostsPage
