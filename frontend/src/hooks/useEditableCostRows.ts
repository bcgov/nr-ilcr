import { useEffect, useRef, useState } from 'react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import { extractDetail } from '@/utils/error'
import { toNum } from '@/utils/number'

/** One editable row held in local state. `id` is null for a row added but not yet saved. */
export interface EditRow {
  key: number
  id: number | null
  description: string
  values: Record<string, string>
}

/** Advisory validation errors keyed by `description` and each editable field key. */
export type RowValidationErrors = Record<string, string | undefined>

/** The minimum document shape the editor needs (edit flag + a mutation success message). */
export interface EditableRowsDoc {
  editable: boolean
  message?: { text: string } | null
}

interface Params<TDoc extends EditableRowsDoc> {
  /** API base path, e.g. {@code '/v1/schedule1/other-costs'}. */
  base: string
  /** The editable numeric field keys carried by every row (e.g. {@code ['total','pop']}). */
  fieldKeys: string[]
  /** Verbatim load/save error fallbacks (AD-8 messages come from the server on success). */
  loadError: string
  saveError: string
  /** Map a loaded document to the seed rows (id + description + raw string field values). */
  rowsFromDoc: (
    doc: TDoc,
  ) => Array<{ id: number; description: string; values: Record<string, string> }>
  /** Advisory row validation, mirroring the backend request DTO. */
  validate: (description: string, values: Record<string, string>) => RowValidationErrors
  /** Navigate away (the caller owns the typed route). */
  onBack: () => void
}

/** Everything {@link useEditableCostRows} exposes — the shared editing state + handlers. */
export interface EditableCostRows<TDoc extends EditableRowsDoc> {
  contextMissing: boolean
  data: TDoc | null
  isLoading: boolean
  errorDetail: string | null
  message: string | null
  actionError: string | null
  saving: boolean
  rows: EditRow[]
  rowErrors: Record<number, RowValidationErrors>
  addDescription: string
  setAddDescription: (value: string) => void
  addValues: Record<string, string>
  setAddValue: (fieldKey: string, value: string) => void
  addErrors: RowValidationErrors
  confirmBackOpen: boolean
  setConfirmBackOpen: (open: boolean) => void
  setRowDescription: (key: number, value: string) => void
  setRowValue: (key: number, fieldKey: string, value: string) => void
  handleAdd: () => void
  removeRow: (key: number) => void
  handleSave: () => void
  handleBack: () => void
  confirmBack: () => void
  onBack: () => void
}

const emptyValues = (keys: string[]): Record<string, string> =>
  Object.fromEntries(keys.map((k) => [k, '']))

const hasErrors = (errors: RowValidationErrors): boolean =>
  Object.values(errors).some((v) => v !== undefined)

/**
 * The shared editing state machine for the Schedule 1 / Schedule 3 cost sub-pages, replicating the
 * legacy edit-in-place + batch-persist model: every row is a live input held in memory, and each
 * mutation (Add, Remove, Save) persists the WHOLE set in one call (the server reconciles
 * insert/update/delete). Add/Remove persist immediately; the {@code intent} only selects the success
 * message. Callers own only their page-specific columns/markup — this owns load, edit, add, remove,
 * save, and the unsaved-changes Back guard.
 */
export function useEditableCostRows<TDoc extends EditableRowsDoc>({
  base,
  fieldKeys,
  loadError,
  saveError,
  rowsFromDoc,
  validate,
  onBack,
}: Params<TDoc>): EditableCostRows<TDoc> {
  const { millId, year } = useMillYear()
  const contextMissing = millId === null || year === null

  const [data, setData] = useState<TDoc | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [rows, setRows] = useState<EditRow[]>([])
  const [rowErrors, setRowErrors] = useState<Record<number, RowValidationErrors>>({})
  const [dirty, setDirty] = useState(false)

  const [addDescription, setAddDescription] = useState('')
  const [addValues, setAddValues] = useState<Record<string, string>>(() => emptyValues(fieldKeys))
  const [addErrors, setAddErrors] = useState<RowValidationErrors>({})

  const [confirmBackOpen, setConfirmBackOpen] = useState(false)

  // Monotonic client key for React list identity (independent of the server id, null for new rows).
  const keyCounterRef = useRef(0)

  const query = `?millId=${millId}&year=${year}`

  const seedRows = (doc: TDoc): EditRow[] =>
    rowsFromDoc(doc).map((r) => ({
      key: keyCounterRef.current++,
      id: r.id,
      description: r.description,
      values: r.values,
    }))

  useEffect(() => {
    if (contextMissing) {
      return
    }
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    setMessage(null)
    setActionError(null)
    setRows([])
    setRowErrors({})
    setDirty(false)
    setAddDescription('')
    setAddValues(emptyValues(fieldKeys))
    setAddErrors({})
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<TDoc>(`${base}${query}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setRows(seedRows(response.data))
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(extractDetail(error) || loadError)
          setData(null)
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false)
        }
      })
    return () => {
      active = false
    }
    // fieldKeys/seedRows derive from static config; keep the effect keyed on context only.
    // eslint-disable-next-line @eslint-react/exhaustive-deps
  }, [millId, year, contextMissing, base, query, loadError])

  const setRowDescription = (key: number, value: string) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, description: value } : r)))
    setDirty(true)
  }

  const setRowValue = (key: number, fieldKey: string, value: string) => {
    setRows((prev) =>
      prev.map((r) => (r.key === key ? { ...r, values: { ...r.values, [fieldKey]: value } } : r)),
    )
    setDirty(true)
  }

  const setAddValue = (fieldKey: string, value: string) =>
    setAddValues((prev) => ({ ...prev, [fieldKey]: value }))

  const applyDocument = (doc: TDoc) => {
    setData(doc)
    setRows(seedRows(doc))
    setRowErrors({})
    setMessage(doc.message?.text ?? null)
    setActionError(null)
    setDirty(false)
  }

  // Persist the WHOLE current row set in one call — the legacy update() that every mutation funnels
  // through. `intent` only selects the success message; the persistence is identical either way.
  const persist = (rowsToSave: EditRow[], intent: 'save' | 'delete') => {
    if (!data || saving) {
      return
    }
    const errs: Record<number, RowValidationErrors> = {}
    for (const row of rowsToSave) {
      const rowErr = validate(row.description, row.values)
      if (hasErrors(rowErr)) {
        errs[row.key] = rowErr
      }
    }
    if (Object.keys(errs).length > 0) {
      setRowErrors(errs)
      return
    }
    setRowErrors({})
    setMessage(null)
    setActionError(null)
    setSaving(true)
    const rowsPayload = rowsToSave.map((row) => ({
      id: row.id,
      description: row.description.trim(),
      ...Object.fromEntries(fieldKeys.map((k) => [k, toNum(row.values[k])])),
    }))
    apiService
      .getAxiosInstance()
      .put<TDoc>(`${base}${query}&intent=${intent}`, { rows: rowsPayload })
      .then((response) => {
        applyDocument(response.data)
        // Save (not delete) clears the add form — legacy clearAdd*Form() inside update(true).
        if (intent === 'save') {
          setAddDescription('')
          setAddValues(emptyValues(fieldKeys))
          setAddErrors({})
        }
      })
      .catch((error: unknown) => {
        setActionError(extractDetail(error) || saveError)
      })
      .finally(() => setSaving(false))
  }

  // "Add" appends the entered row and immediately persists the whole set (legacy addOtherCost → save).
  const handleAdd = () => {
    if (saving) {
      return
    }
    setMessage(null)
    setActionError(null)
    const errors = validate(addDescription, addValues)
    if (hasErrors(errors)) {
      setAddErrors(errors)
      return
    }
    setAddErrors({})
    const next = [
      ...rows,
      {
        key: keyCounterRef.current++,
        id: null,
        description: addDescription.trim(),
        values: { ...addValues },
      },
    ]
    setRows(next)
    setAddDescription('')
    setAddValues(emptyValues(fieldKeys))
    setDirty(true)
    persist(next, 'save')
  }

  // "Remove" drops the row and immediately persists the whole set (legacy delete → update(false)).
  const removeRow = (key: number) => {
    if (saving) {
      return
    }
    const next = rows.filter((r) => r.key !== key)
    setRows(next)
    setRowErrors((prev) => {
      const cleared = { ...prev }
      delete cleared[key]
      return cleared
    })
    setDirty(true)
    persist(next, 'delete')
  }

  // "Save" persists the whole set (legacy save() → update(true)).
  const handleSave = () => {
    if (rows.length > 0) {
      persist(rows, 'save')
    }
  }

  // Guard unsaved edits on Back (legacy confirmNavigationMsg); navigate directly when not dirty.
  const handleBack = () => {
    if (dirty) {
      setConfirmBackOpen(true)
      return
    }
    onBack()
  }

  const confirmBack = () => {
    setConfirmBackOpen(false)
    onBack()
  }

  return {
    contextMissing,
    data,
    isLoading,
    errorDetail,
    message,
    actionError,
    saving,
    rows,
    rowErrors,
    addDescription,
    setAddDescription,
    addValues,
    setAddValue,
    addErrors,
    confirmBackOpen,
    setConfirmBackOpen,
    setRowDescription,
    setRowValue,
    handleAdd,
    removeRow,
    handleSave,
    handleBack,
    confirmBack,
    onBack,
  }
}
