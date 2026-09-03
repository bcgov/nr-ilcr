import type { FC } from 'react'
import type {
  SubPageDocument,
  SubPageKind,
  SubPageRow,
  SubPageRowForm,
} from '@/interfaces/Schedule5SubPage'
import type { SubPageErrors } from './validation'
import { useCallback, useEffect, useState } from 'react'
import { deriveSubPageTotals, rowCostPerVolume } from './derived'
import { isUnusableStrictEntry } from '@/utils/derivedMath'
import {
  Button,
  Column,
  Grid,
  Modal,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react'
import { Add, ArrowLeft, Save, TrashCan } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import { extractDetail } from '@/utils/error'
import LoadingScreen from '@/components/core/LoadingScreen'
import NotificationColumn from '@/components/core/NotificationColumn'
import PageState from '@/components/core/PageState'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import { fmtCost, fmtCostPerVolume, fmtVolume } from '@/components/schedule5/masks'
import {
  DESCRIPTION_MAX_LENGTH,
  VALIDATES_ROW_ON_CHANGE,
  isSubPageValid,
  rowFieldKey,
  toRowRequest,
  validateAddForm,
  validateDescriptionOnChange,
  validateRows,
} from './validation'
import './index.scss'

// Client-only chrome. Every success/error string comes from the API and renders verbatim (AD-8);
// these are confirm-dialog text and empty-state text, rendered when NO request is issued.
//
// CFM-001/CFM-002 are hardcoded alongside the camp page's three confirms rather than resolved from
// the bundle — Open question 2, settled by Scho on 2026-08-12: they are confirm chrome fired before
// any request, which § Chrome literals treats as client-owned, and consistency within the feature
// beat consistency with the messages endpoint.
const CONFIRM_DELETE = 'This will delete the current record. Do you want to continue?'
const CONFIRM_NAVIGATION = 'Any unsaved data will be lost. Are you sure you would like to continue?'
const EMPTY_LIST = 'No records found.'

/**
 * Everything that differs between the two sub-pages, in one table.
 *
 * The differences are NOT cosmetic and are not symmetric: the cost band, the required-check timing
 * and the footer arithmetic each differ, and each is a separately verified legacy fact. Parameterising
 * one component the way `schedule4/subPageDefs.ts` does keeps them side by side instead of scattered
 * through branches.
 */
interface SubPageDef {
  readonly kind: SubPageKind
  /** The legacy page name (`<ui:define name="pageName">`, `:9` on both views). */
  readonly pageName: string
  readonly addHeader: string
  readonly listHeader: string
  readonly path: string
}

const SUB_PAGE_DEFS: Record<SubPageKind, SubPageDef> = {
  CAMP: {
    kind: 'CAMP',
    pageName: 'Camp Expenses',
    addHeader: 'Add Other Camp Expense',
    listHeader: 'Other Camp Expenses',
    path: 'other-camp-expenses',
  },
  ACCESS: {
    kind: 'ACCESS',
    pageName: 'Access Expenses',
    addHeader: 'Add Other Access Expense',
    listHeader: 'Other Access Expenses',
    path: 'other-access-expenses',
  },
}

const emptyAddForm = (): SubPageRowForm => ({ rowId: null, description: '', cost: '' })

/** A served row seeded into an editable grid row. */
const seedRow = (row: SubPageRow): SubPageRowForm => ({
  rowId: row.rowId,
  description: row.description ?? '',
  // Grouped, so the editable cell reads the same as the read-only cells beside it.
  cost: row.cost === null || row.cost === undefined ? '' : fmtCost(row.cost),
})

export interface Schedule5SubPageProps {
  readonly campId: number
  readonly kind: SubPageKind
  /** Return to the camp list. The confirm ladder is this component's, not the caller's. */
  readonly onBack: () => void
}

/**
 * One Schedule 5 expense sub-page — the itemized Other Camp (item 62) or Other Access (item 68)
 * rows for a single camp (S04, S07, S10, S21, S22, S23).
 *
 * <p>The footer and the row rates are mirrored here while editable (#291); everything else is served. The footer totals, every $/m³ and every row volume arrive derived
 * from the server (AD-5); this file contains no `reduce` over costs and no division.
 */
const Schedule5SubPage: FC<Schedule5SubPageProps> = ({ campId, kind, onBack }) => {
  const def = SUB_PAGE_DEFS[kind]
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  const [doc, setDoc] = useState<SubPageDocument | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [saving, setSaving] = useState(false)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [addForm, setAddForm] = useState<SubPageRowForm>(emptyAddForm)
  const [addErrors, setAddErrors] = useState<SubPageErrors>({})
  const [rows, setRows] = useState<readonly SubPageRowForm[]>([])
  // The blur-committed copy the derived mirror reads. Legacy refreshed these figures on a row cost's
  // own `change` handler, so they settle when focus leaves rather than per keystroke (defect #291).
  const [committedRows, setCommittedRows] = useState<readonly SubPageRowForm[]>([])
  const [rowErrors, setRowErrors] = useState<SubPageErrors>({})

  const [confirmDeleteRow, setConfirmDeleteRow] = useState<SubPageRowForm | null>(null)
  const [confirmBack, setConfirmBack] = useState(false)

  const query = `millId=${String(millId)}&year=${String(year)}`
  const basePath = `/v1/schedule5/camps/${String(campId)}/${def.path}`

  const clearBanners = () => {
    setActionMessage(null)
    setActionError(null)
  }

  /** Seat a served document: rows, banners and every transient error follow the server's answer. */
  const applyDocument = useCallback((payload: SubPageDocument) => {
    setDoc(payload)
    setRows(payload.rows.map(seedRow))
    setCommittedRows(payload.rows.map(seedRow))
    setRowErrors({})
    setAddForm(emptyAddForm())
    setAddErrors({})
    setActionMessage(payload.message?.text ?? null)
    setActionError(null)
  }, [])

  /**
   * The load. This effect dispatches only — it calls no setter synchronously, so a render never
   * schedules another render.
   *
   * <strong>There is deliberately no transient-reset here.</strong> The parent mounts this component
   * under a key derived from camp, page, mill and year, so any of those changing REMOUNTS it and
   * every piece of state above — including the `saving` lock — starts fresh. That makes the Story
   * 7.3 HIGH (a lock stranded engaged by a mid-flight mill/year change, permanently disabling the
   * page) structurally unreachable here rather than merely handled: there is no surviving lock to
   * strand.
   *
   * <strong>Staleness is a local `cancelled` flag, NOT the shared `isCurrent()` guard.</strong>
   * `useScheduleContextGuard` returns a fresh `isCurrent` closure on every render, so listing it as
   * a dependency re-runs this effect on EVERY render — which re-fetched the document and re-seated
   * the form on every keystroke, silently discarding entered values and any validation error. The
   * cancel flag needs no dependency, and it also handles unmount, which `isCurrent()` does not. The
   * mutation handlers below still use `isCurrent()`: they are called from events rather than an
   * effect, so its identity is irrelevant there.
   */
  useEffect(() => {
    if (contextMissing) {
      return undefined
    }
    let cancelled = false
    apiService
      .getAxiosInstance()
      .get<SubPageDocument>(`${basePath}?${query}`)
      .then((response) => {
        if (cancelled) {
          return
        }
        applyDocument(response.data)
        setLoadError(null)
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return
        }
        setLoadError(extractDetail(error) || 'Unable to load the expense list.')
      })
    return () => {
      cancelled = true
    }
    // basePath/query already encode campId, kind, millId and year; applyDocument is stable.
  }, [basePath, query, contextMissing, applyDocument])

  // Derived rather than stored, so the effect never sets it synchronously.
  const isLoading = !contextMissing && doc === null && loadError === null

  /**
   * The single mutation tail. The context guard runs on `then`, `catch` AND `finally`: an unguarded
   * `finally` would release the `saving` lock belonging to a request dispatched under the NEW
   * context, letting two writes overlap.
   */
  const runMutation = (
    request: Promise<{ data: SubPageDocument }>,
    fallbackError: string,
    onSuccess: (payload: SubPageDocument) => void = applyDocument,
  ) => {
    setSaving(true)
    request
      .then((response) => {
        if (!isCurrent()) {
          return
        }
        onSuccess(response.data)
      })
      .catch((error: unknown) => {
        if (!isCurrent()) {
          return
        }
        // Keep every entered value in place so a corrected save can retry.
        setActionError(extractDetail(error) || fallbackError)
      })
      .finally(() => {
        if (isCurrent()) {
          setSaving(false)
        }
      })
  }

  const editable = doc?.editable === true

  /** PUT the whole list — the sole writer of this item id. */
  const save = (payload: readonly SubPageRowForm[], fallbackError: string) => {
    runMutation(
      apiService.getAxiosInstance().put<SubPageDocument>(`${basePath}?${query}`, {
        rows: payload.map(toRowRequest),
      }),
      fallbackError,
    )
  }

  /**
   * Add appends and commits the WHOLE list, including any in-grid edits (AC11).
   *
   * Legacy's Add appends then calls `save()` (`Schedule5CampExpensesMB.java:147-156`), so an add IS
   * a full-list commit — but its button submits only the add form (`schedule5CampExpenses.xhtml:51`),
   * so legacy silently discarded un-submitted grid edits and then overwrote them from the reload.
   * That discard is a defect, not a contract (deviation (E)), and is not ported.
   */
  const handleAdd = () => {
    if (saving || !editable) {
      return
    }
    clearBanners()
    const errors = validateAddForm(addForm, kind)
    setAddErrors(errors)
    // The grid goes on the wire too, so it must be valid — with required enforced, because this is
    // a commit.
    const gridErrors = validateRows(rows, kind, true)
    setRowErrors(gridErrors)
    if (!isSubPageValid(errors) || !isSubPageValid(gridErrors)) {
      return
    }
    save([...rows, { ...addForm, rowId: null }], 'Unable to add the expense.')
  }

  const handleSave = () => {
    if (saving || !editable) {
      return
    }
    clearBanners()
    // Required IS enforced at Save on both pages — that is the whole of S22 for the Camp page,
    // whose grid defers the check to here.
    const errors = validateRows(rows, kind, true)
    setRowErrors(errors)
    if (!isSubPageValid(errors)) {
      return
    }
    save(rows, 'Unable to save the expense list.')
  }

  /**
   * Row delete persists immediately, exactly as legacy's Delete button does (`:158-167`).
   *
   * Every grid row is server-seeded (Add commits immediately and re-seats from the echo), so
   * `target.rowId` is always a real stored id here — there is no unsaved-row case to drop locally.
   *
   * The echo reseeds the grid, which would silently discard edits typed into OTHER rows — the same
   * discard deviation (E) refuses to port on Add. So the surviving rows' in-flight values are
   * re-applied on top of the reseed, keyed by rowId; a row the server no longer serves drops its
   * draft with it.
   */
  const handleDeleteRow = () => {
    const target = confirmDeleteRow
    setConfirmDeleteRow(null)
    if (saving || !editable || target === null) {
      return
    }
    clearBanners()
    const drafts = new Map(
      rows
        .filter((row) => row.rowId !== null && row.rowId !== target.rowId)
        .map((row) => [row.rowId, row] as const),
    )
    runMutation(
      apiService
        .getAxiosInstance()
        .delete<SubPageDocument>(`${basePath}/${String(target.rowId)}?${query}`),
      'Unable to delete the expense.',
      (payload) => {
        applyDocument(payload)
        // Merge the surviving drafts into BOTH lists. Applying them to `rows` alone left the edited
        // cost in the input while the footer and every rate reverted to served values, until the next
        // blur (code review 2026-08-21, proven by probe).
        const merged = payload.rows.map(seedRow).map((seeded) => {
          const draft = drafts.get(seeded.rowId)
          return draft ? { ...seeded, description: draft.description, cost: draft.cost } : seeded
        })
        setRows(merged)
        setCommittedRows(merged)
      },
    )
  }

  const updateRow = (index: number, field: 'description' | 'cost', value: string) => {
    const next = rows.map((row, i) => (i === index ? { ...row, [field]: value } : row))
    setRows(next)
    // The S21/S22 timing, scoped per INPUT the way legacy's f:ajax is. Only an ACCESS description
    // validates on change (`<f:ajax event="change">`, schedule5AccessExpenses.xhtml:63); the Camp
    // grid carries no f:ajax at all (:64-67) and NEITHER page's cost does, so everything else —
    // including both cost bands — surfaces at Add/Save. Validating the whole grid here would flag
    // untouched rows, including a legally-stored blank description (deviation (F)) the licensee
    // never edited. The changed field's own stale error clears so a corrected value stops shouting.
    setRowErrors((current) => {
      const key = rowFieldKey(index, field)
      const updated: Record<string, string> = { ...current }
      delete updated[key]
      if (field === 'description' && VALIDATES_ROW_ON_CHANGE[kind]) {
        const error = validateDescriptionOnChange(next[index])
        if (error) {
          updated[key] = error
        }
      }
      return updated
    })
  }

  const requestBack = () => {
    // Legacy renders a BARE Back in the read-only branch — no confirm at all
    // (schedule5CampExpenses.xhtml:134, schedule5AccessExpenses.xhtml:130).
    if (!editable) {
      onBack()
      return
    }
    setConfirmBack(true)
  }

  // Legacy names these two screens distinctly from the list panel below them:
  // "ILCR -> Schedule 5: Camp Expenses" / ": Access Expenses" (schedule5CampExpenses.xhtml:9,
  // schedule5AccessExpenses.xhtml:9). Reusing the LIST header here instead would also make the same
  // words appear twice on one page.
  const header = <ScheduleTombstone title="Schedule 5" subtitle={def.pageName} />

  if (contextMissing) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: 'Mill and Reporting Year required',
          subtitle: 'Please Select Mill and Reporting Year in the Home Page.',
        }}
      />
    )
  }
  if (isLoading) {
    return (
      <PageState header={header}>
        <Column sm={4} md={8} lg={16}>
          <LoadingScreen label={`Loading ${def.listHeader}`} />
        </Column>
      </PageState>
    )
  }
  if (loadError !== null || doc === null) {
    return (
      <PageState
        header={header}
        notification={{
          kind: 'error',
          title: `Unable to load ${def.listHeader}`,
          subtitle: loadError ?? 'Unable to load the expense list.',
        }}
      />
    )
  }

  const addPanel = (
    <div className="schedule-5-sub-page__panel">
      <h3 className="schedule-5-sub-page__panel-heading">{def.addHeader}</h3>
      <div className="schedule-5-sub-page__add-fields">
        <TextInput
          id="sub-page-add-description"
          className="schedule-5-sub-page__add-field schedule-5-sub-page__add-field--wide"
          labelText="Description: "
          maxLength={DESCRIPTION_MAX_LENGTH}
          value={addForm.description}
          disabled={!editable || saving}
          invalid={addErrors.description !== undefined}
          invalidText={addErrors.description}
          onChange={(event) => {
            setAddForm((current) => ({ ...current, description: event.target.value }))
          }}
        />
        {/* Legacy renders this input `disabled="true"` UNCONDITIONALLY on both pages (:41 / :34) —
            it is never editable, not even for an editable schedule. It shows the camp-level
            item-141/142 volume that every row's volume is stamped from. */}
        {/* `disabled` alone, NOT disabled+readOnly: Carbon drops the disabled attribute when both
            are set, which would leave the control merely read-only. Legacy's attribute is
            `disabled="true"`. */}
        <TextInput
          id="sub-page-add-volume"
          className="schedule-5-sub-page__add-field"
          labelText="Volume: "
          value={fmtVolume(doc.associatedCampVolume)}
          disabled
        />
        <TextInput
          id="sub-page-add-cost"
          className="schedule-5-sub-page__add-field"
          labelText="Cost $: "
          value={addForm.cost}
          disabled={!editable || saving}
          invalid={addErrors.cost !== undefined}
          invalidText={addErrors.cost}
          onChange={(event) => {
            setAddForm((current) => ({ ...current, cost: event.target.value }))
          }}
        />
        {/* Last IN the field row rather than a block beneath it, matching the sibling Add panels
            (`.schedule-3-sub__actions`, Schedule 11): the button is the end of the line the fields
            make, so it reads as their action instead of a separate step (#411, Scho's call). */}
        <div className="schedule-5-sub-page__add-actions">
          <Button
            kind="primary"
            disabled={!editable || saving}
            renderIcon={Add}
            onClick={handleAdd}
          >
            Add
          </Button>
        </div>
      </div>
    </div>
  )

  // The footer triple: mirrored from the committed row costs while editable, the served figures
  // otherwise (#291 AC7). The page-specific arithmetic lives in `deriveSubPageTotals`.
  // ONE binding for the stamped volume. Three spellings of it were in this table — the volume cell,
  // the row rate and the footer each resolved it differently — where the service derives every one of
  // them from a single `stampedVolume` (code review 2026-08-21).
  const stampedVolume = doc.associatedCampVolume ?? null

  /**
   * The committed snapshot for one row, matched by `rowId` rather than by array index (code review
   * 2026-08-21). Index pairing against an `rowId`-keyed list silently mispairs the moment the two
   * arrays differ in length or order, and the old `?? row` fallback substituted the LIVE row, which
   * reintroduced per-keystroke churn for exactly that row.
   */
  const committedRowFor = (row: SubPageRowForm): SubPageRowForm =>
    committedRows.find((candidate) => candidate.rowId === row.rowId) ?? { ...row, cost: '' }

  /** Advance the baseline only from entries the Save could carry (ruled 2026-08-21). */
  const commitRows = () => {
    if (rows.some((row) => isUnusableStrictEntry(row.cost))) {
      return
    }
    setCommittedRows(rows)
  }

  const footer = editable
    ? deriveSubPageTotals(def.kind, committedRows, stampedVolume)
    : {
        volume: doc.totals?.volume ?? null,
        cost: doc.totals?.cost ?? null,
        costPerVolume: doc.totals?.costPerVolume ?? null,
      }

  const listTable = (
    <TableContainer title={def.listHeader}>
      <Table aria-label={def.listHeader}>
        <TableHead>
          <TableRow>
            <TableHeader>Description</TableHeader>
            <TableHeader>
              Volume m<sup>3</sup>
            </TableHeader>
            <TableHeader>Cost $</TableHeader>
            <TableHeader>
              $/m<sup>3</sup>
            </TableHeader>
            <TableHeader>Action</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.length === 0 && (
            <TableRow>
              <TableCell colSpan={5}>{EMPTY_LIST}</TableCell>
            </TableRow>
          )}
          {rows.map((row, index) => {
            const served = doc.rows[index]
            const descriptionError = rowErrors[rowFieldKey(index, 'description')]
            const costError = rowErrors[rowFieldKey(index, 'cost')]
            return (
              <TableRow key={row.rowId ?? `new-${String(index)}`}>
                <TableCell>
                  <TextInput
                    id={`sub-page-row-description-${String(index)}`}
                    labelText="Description"
                    hideLabel
                    maxLength={DESCRIPTION_MAX_LENGTH}
                    value={row.description}
                    disabled={!editable || saving}
                    invalid={descriptionError !== undefined}
                    invalidText={descriptionError}
                    onChange={(event) => {
                      updateRow(index, 'description', event.target.value)
                    }}
                  />
                </TableCell>
                {/* Volume and $/m³ are read-only on both pages (:72/:85 and :69/:81). The volume is
                    the stamped camp-level amount, identical on every row. */}
                <TableCell className="schedule-5-sub-page__num">
                  {fmtVolume(served?.volume ?? doc.associatedCampVolume)}
                </TableCell>
                <TableCell>
                  <TextInput
                    id={`sub-page-row-cost-${String(index)}`}
                    labelText="Cost $"
                    hideLabel
                    value={row.cost}
                    disabled={!editable || saving}
                    invalid={costError !== undefined}
                    invalidText={costError}
                    onChange={(event) => {
                      updateRow(index, 'cost', event.target.value)
                    }}
                    onBlur={() => {
                      commitRows()
                    }}
                  />
                </TableCell>
                <TableCell className="schedule-5-sub-page__num">
                  {fmtCostPerVolume(
                    editable
                      ? rowCostPerVolume(committedRowFor(row), stampedVolume)
                      : served?.costPerVolume,
                  )}
                </TableCell>
                <TableCell>
                  <Button
                    kind="ghost"
                    size="sm"
                    disabled={!editable || saving}
                    renderIcon={TrashCan}
                    onClick={() => {
                      setConfirmDeleteRow(row)
                    }}
                  >
                    Delete
                  </Button>
                </TableCell>
              </TableRow>
            )
          })}
          {/* The `Totals:` footer (:94 / :90). Mirrored from the committed row costs while editable
              (defect #291) and rendered from the document otherwise. The two pages do NOT agree on
              the volume: CAMP sums the row volumes, ACCESS reports the single camp volume
              (deviation (C)) — `deriveSubPageTotals` keeps that difference, which is real, while
              filling both derived cells on both pages, which legacy did inconsistently. */}
          <TableRow className="schedule-5-sub-page__totals">
            <TableCell>Totals:</TableCell>
            <TableCell className="schedule-5-sub-page__num">{fmtVolume(footer.volume)}</TableCell>
            <TableCell className="schedule-5-sub-page__num">{fmtCost(footer.cost)}</TableCell>
            <TableCell className="schedule-5-sub-page__num">
              {fmtCostPerVolume(footer.costPerVolume)}
            </TableCell>
            <TableCell />
          </TableRow>
        </TableBody>
      </Table>
    </TableContainer>
  )

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {actionMessage && (
          <NotificationColumn kind="success" title="Success" subtitle={actionMessage} />
        )}
        {actionError && (
          <NotificationColumn kind="error" title="Action failed" subtitle={actionError} />
        )}

        <Column sm={4} md={8} lg={16} className="schedule-5-sub-page__section">
          {addPanel}
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-5-sub-page__section">
          {listTable}
        </Column>

        <Column sm={4} md={8} lg={16} className="schedule-5-sub-page__actions">
          {/* Legacy's read-only branch renders a SECOND, permanently disabled Save (:131-133 /
              :127-129). That dead duplicate is dropped (deviation (K), the 7.3 (L) precedent); the
              single Save is rendered disabled instead, so the control a licensee expects is still
              there and visibly unavailable. */}
          <Button
            kind="primary"
            disabled={!editable || saving}
            renderIcon={Save}
            onClick={handleSave}
          >
            Save
          </Button>
          <Button kind="secondary" disabled={saving} renderIcon={ArrowLeft} onClick={requestBack}>
            Back
          </Button>
        </Column>
      </Grid>

      <Modal
        open={confirmDeleteRow !== null}
        danger
        modalHeading="Delete expense"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={() => {
          setConfirmDeleteRow(null)
        }}
        onRequestSubmit={handleDeleteRow}
      >
        {/* Rendered only while open: Carbon keeps a closed modal's children mounted, so leaving the
            text in the DOM would make "is the confirm showing?" unanswerable by any query — and the
            read-only Back case turns on exactly that question. */}
        {confirmDeleteRow !== null && <p>{CONFIRM_DELETE}</p>}
      </Modal>

      <Modal
        open={confirmBack}
        modalHeading="Leave expense list"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={() => {
          setConfirmBack(false)
        }}
        onRequestSubmit={() => {
          setConfirmBack(false)
          onBack()
        }}
      >
        {confirmBack && <p>{CONFIRM_NAVIGATION}</p>}
      </Modal>
    </div>
  )
}

export default Schedule5SubPage
