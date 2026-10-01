// Story 24.3 (UC-CODE-001) Table Maintenance wire types — mirror the backend codetable DTOs.

/** A selectable code table for the maintenance dropdown. */
export interface CodeTableSummary {
  key: string
  label: string
  codeMaxLength: number
  descriptionMaxLength: number
  contractual?: boolean
}

/** One row of a code table: the code, its description, and the effective/expiry window (ISO dates). */
export interface CodeTableEntry {
  code: string
  description: string
  effectiveDate: string | null
  expiryDate: string | null
}

/** The response to a save: which arm ran, the verbatim success message, and the reloaded grid. */
export interface CodeTableSaveResponse {
  outcome: 'INSERTED' | 'UPDATED'
  messageKey: string
  message: string
  entries: CodeTableEntry[]
}
