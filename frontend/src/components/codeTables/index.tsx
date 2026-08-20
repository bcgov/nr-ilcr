import type { FC } from 'react'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Button,
  Column,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import CodeComboBox from '@/components/core/CodeComboBox'
import NotificationColumn from '@/components/core/NotificationColumn'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import { blankToNull } from '@/utils/forms'
import type {
  CodeTableEntry,
  CodeTableSaveResponse,
  CodeTableSummary,
} from '@/interfaces/CodeTable'
import { validateCodeEntry, type CodeEntryErrors, type CodeEntryForm } from './validation'

const EMPTY_FORM: CodeEntryForm = { code: '', description: '', effectiveDate: '', expiryDate: '' }
const api = () => apiService.getAxiosInstance()
const dash = (value: string | null) => (value === null || value === '' ? '—' : value)

/**
 * Table Maintenance (Story 24.3 / UC-CODE-001). Admin-only surface: pick one of the lookup code
 * tables, then add a new entry or edit an existing one (upsert). Every field is validated (FLD-001..
 * 005) before the PUT; the server re-validates and 403s a non-admin. Reachable only via the
 * admin-gated Administration menu; the route itself is otherwise unguarded (the API is the boundary).
 */
const CodeTables: FC = () => {
  const [tables, setTables] = useState<CodeTableSummary[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedKey, setSelectedKey] = useState('')
  const [entries, setEntries] = useState<CodeTableEntry[]>([])

  const [addForm, setAddForm] = useState<CodeEntryForm>(EMPTY_FORM)
  const [addErrors, setAddErrors] = useState<CodeEntryErrors>({})

  const [editingCode, setEditingCode] = useState<string | null>(null)
  const [editForm, setEditForm] = useState<CodeEntryForm>(EMPTY_FORM)
  const [editErrors, setEditErrors] = useState<CodeEntryErrors>({})

  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  // Mirrors selectedKey for the async guards below: a GET/PUT that resolves after the user has
  // switched tables must not write the previous table's rows into the now-current grid.
  const selectedKeyRef = useRef('')

  // The 18 selectable tables load once on mount.
  useEffect(() => {
    let active = true
    api()
      .get<CodeTableSummary[]>('/v1/code-tables')
      .then((response) => {
        if (active) setTables(response.data)
      })
      .catch((error: unknown) => {
        if (active) setLoadError(extractDetail(error) || 'Unable to load the code tables.')
      })
    return () => {
      active = false
    }
  }, [])

  const options = useMemo(
    () => tables.map((table) => ({ code: table.key, description: table.label })),
    [tables],
  )

  // The selected table's per-field length caps (BR-06), used to bound the code / description inputs.
  const selectedTable = useMemo(
    () => tables.find((table) => table.key === selectedKey) ?? null,
    [tables, selectedKey],
  )

  const clearNotifications = () => {
    setSaveMessage(null)
    setSaveError(null)
  }

  const loadEntries = (key: string) => {
    api()
      .get<CodeTableEntry[]>(`/v1/code-tables/${key}/entries`)
      .then((response) => {
        if (selectedKeyRef.current === key) setEntries(response.data)
      })
      .catch((error: unknown) => {
        if (selectedKeyRef.current === key) {
          setSaveError(extractDetail(error) || 'Unable to load entries.')
        }
      })
  }

  const onSelectTable = (key: string) => {
    selectedKeyRef.current = key
    setSelectedKey(key)
    setEntries([])
    setAddForm(EMPTY_FORM)
    setAddErrors({})
    setEditingCode(null)
    clearNotifications()
    if (key !== '') loadEntries(key)
  }

  // Persist one entry (add or edit). On success the grid reloads from the response (single round-trip).
  const save = (form: CodeEntryForm, requireCode: boolean, onDone: () => void) => {
    const errors = validateCodeEntry(form, requireCode)
    if (requireCode) setAddErrors(errors)
    else setEditErrors(errors)
    if (Object.keys(errors).length > 0 || saving) return

    const key = selectedKey
    setSaving(true)
    clearNotifications()
    api()
      .put<CodeTableSaveResponse>(`/v1/code-tables/${key}/entries`, {
        code: form.code.trim(),
        description: form.description.trim(),
        // Blank → null so an omitted expiry serializes as a JSON null ("never expires"), not "" which
        // the server can't parse to a date. Effective is required, so it is always present.
        effectiveDate: blankToNull(form.effectiveDate),
        expiryDate: blankToNull(form.expiryDate),
      })
      .then((response) => {
        // Ignore the result if the user switched tables while the save was in flight.
        if (selectedKeyRef.current !== key) return
        setEntries(response.data.entries)
        setSaveMessage(response.data.message)
        onDone()
      })
      .catch((error: unknown) => {
        if (selectedKeyRef.current === key) {
          setSaveError(extractDetail(error) || 'The entry could not be saved.')
        }
      })
      .finally(() => setSaving(false))
  }

  const startEdit = (row: CodeTableEntry) => {
    clearNotifications()
    setEditingCode(row.code)
    setEditErrors({})
    setEditForm({
      code: row.code,
      description: row.description,
      effectiveDate: row.effectiveDate ?? '',
      expiryDate: row.expiryDate ?? '',
    })
  }

  const dateInput = (
    id: string,
    label: string,
    value: string,
    onChange: (next: string) => void,
    invalidText?: string,
  ) => (
    <TextInput
      id={id}
      labelText={label}
      hideLabel
      size="sm"
      type="date"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      invalid={Boolean(invalidText)}
      invalidText={invalidText}
    />
  )

  return (
    <div className="app-page schedule-page">
      {/* Shared schedule tombstone header: page identity left, the mill/status working context
          right-aligned — same header Schedules 2/4/8 use. */}
      <ScheduleTombstone title="Table Maintenance" />
      <Grid fullWidth className="app-page__body">
        {loadError && <NotificationColumn kind="error" title="Error" subtitle={loadError} />}
        {saveMessage && <NotificationColumn kind="success" title="Saved" subtitle={saveMessage} />}
        {saveError && <NotificationColumn kind="error" title="Error" subtitle={saveError} />}
        <Column sm={4} md={8} lg={16}>
          {/* Both section headers use the schedule pages' 1.25rem heading (e.g. Schedule 4's
              "Existing Locations" / "New Location"). The ComboBox's own label is visually hidden
              (kept for screen readers, see .code-tables__selector) so "Code List" is named once by
              the heading. */}
          <div className="code-tables__section">
            <h3 className="code-tables__heading">Code List</h3>
            <CodeComboBox
              id="code-table-selector"
              titleText="Code List"
              className="code-tables__selector"
              items={options}
              selectedCode={selectedKey}
              onSelect={onSelectTable}
            />
          </div>

          {selectedKey !== '' && (
            <div className="code-tables__section">
              <h3 className="code-tables__heading">Entries</h3>
              <TableContainer className="code-tables__grid">
                <Table aria-label="Code table entries">
                  <TableHead>
                    <TableRow>
                      <TableHeader>Code</TableHeader>
                      <TableHeader>Description</TableHeader>
                      <TableHeader>Effective Date</TableHeader>
                      <TableHeader>Expiry Date</TableHeader>
                      <TableHeader>Actions</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {/* New-entry row at the TOP of the table (BR-03 add; add-of-existing silently
                        updates server-side). */}
                    <TableRow>
                      <TableCell>
                        <TextInput
                          id="add-code"
                          labelText="Code"
                          hideLabel
                          size="sm"
                          maxLength={selectedTable?.codeMaxLength}
                          value={addForm.code}
                          invalid={Boolean(addErrors.code)}
                          invalidText={addErrors.code}
                          onChange={(event) =>
                            setAddForm((prev) => ({ ...prev, code: event.target.value }))
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <TextInput
                          id="add-desc"
                          labelText="Description"
                          hideLabel
                          size="sm"
                          maxLength={selectedTable?.descriptionMaxLength}
                          value={addForm.description}
                          invalid={Boolean(addErrors.description)}
                          invalidText={addErrors.description}
                          onChange={(event) =>
                            setAddForm((prev) => ({ ...prev, description: event.target.value }))
                          }
                        />
                      </TableCell>
                      <TableCell>
                        {dateInput(
                          'add-eff',
                          'Effective Date',
                          addForm.effectiveDate,
                          (next) => setAddForm((prev) => ({ ...prev, effectiveDate: next })),
                          addErrors.effectiveDate,
                        )}
                      </TableCell>
                      <TableCell>
                        {dateInput(
                          'add-exp',
                          'Expiry Date',
                          addForm.expiryDate,
                          (next) => setAddForm((prev) => ({ ...prev, expiryDate: next })),
                          addErrors.expiryDate,
                        )}
                      </TableCell>
                      <TableCell>
                        <Button
                          size="sm"
                          disabled={saving}
                          onClick={() =>
                            save(addForm, true, () => {
                              setAddForm(EMPTY_FORM)
                              setAddErrors({})
                            })
                          }
                        >
                          Add
                        </Button>
                      </TableCell>
                    </TableRow>
                    {entries.map((row) =>
                      editingCode === row.code ? (
                        <TableRow key={row.code}>
                          <TableCell>{row.code}</TableCell>
                          <TableCell>
                            <TextInput
                              id={`edit-desc-${row.code}`}
                              labelText={`Description (${row.code})`}
                              hideLabel
                              size="sm"
                              maxLength={selectedTable?.descriptionMaxLength}
                              value={editForm.description}
                              invalid={Boolean(editErrors.description)}
                              invalidText={editErrors.description}
                              onChange={(event) =>
                                setEditForm((prev) => ({
                                  ...prev,
                                  description: event.target.value,
                                }))
                              }
                            />
                          </TableCell>
                          <TableCell>
                            {dateInput(
                              `edit-eff-${row.code}`,
                              'Effective Date',
                              editForm.effectiveDate,
                              (next) => setEditForm((prev) => ({ ...prev, effectiveDate: next })),
                              editErrors.effectiveDate,
                            )}
                          </TableCell>
                          <TableCell>
                            {dateInput(
                              `edit-exp-${row.code}`,
                              'Expiry Date',
                              editForm.expiryDate,
                              (next) => setEditForm((prev) => ({ ...prev, expiryDate: next })),
                              editErrors.expiryDate,
                            )}
                          </TableCell>
                          <TableCell>
                            <Button
                              size="sm"
                              disabled={saving}
                              onClick={() => save(editForm, false, () => setEditingCode(null))}
                            >
                              Save
                            </Button>
                            <Button
                              kind="ghost"
                              size="sm"
                              disabled={saving}
                              onClick={() => setEditingCode(null)}
                            >
                              Cancel
                            </Button>
                          </TableCell>
                        </TableRow>
                      ) : (
                        <TableRow key={row.code}>
                          <TableCell>{row.code}</TableCell>
                          <TableCell>{row.description}</TableCell>
                          <TableCell>{dash(row.effectiveDate)}</TableCell>
                          <TableCell>{dash(row.expiryDate)}</TableCell>
                          <TableCell>
                            <Button
                              kind="ghost"
                              size="sm"
                              disabled={editingCode !== null || saving}
                              onClick={() => startEdit(row)}
                            >
                              Edit
                            </Button>
                          </TableCell>
                        </TableRow>
                      ),
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </div>
          )}
        </Column>
      </Grid>
    </div>
  )
}

export default CodeTables
