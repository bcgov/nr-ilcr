import type { FC } from 'react'
import { useRef, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import {
  Button,
  Column,
  Dropdown,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react'
import { Add, CheckmarkOutline, Edit, Misuse, View } from '@carbon/icons-react'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import NotificationColumn from '@/components/core/NotificationColumn'
import MillSearchModal from '@/components/mills/MillSearchModal'
import ImportMillModal from '@/components/mills/ImportMillModal'
import AddUserModal from '@/components/mills/AddUserModal'
import { canSaveContacts, toSaveRequest, type ContactForm } from '@/components/mills/validation'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import {
  MSG_ALREADY_ASSIGNED,
  type AssignmentResponse,
  type MillSubmitter,
} from '@/interfaces/MillAssociation'
import {
  MILL_ACTIVE,
  type AddMillUserRequest,
  type AdminMill,
  type AdminMillResponse,
  type ChangeMillStatusRequest,
  type ChangeMillUserRequest,
  type ContactOption,
  type ImportableMill,
} from '@/interfaces/MillMaintenance'

const api = () => apiService.getAxiosInstance()

const ADMIN_MILLS = '/v1/admin/mills'

/** codeTables' convention: a missing value is a dash, never a blank cell. */
const dash = (value: string | null | undefined) => (value == null || value === '' ? '—' : value)

/**
 * Legacy rendered every date on this screen through `f:convertDateTime pattern="dd/MM/yyyy"` — six
 * sites, none with a time, all in the server timezone (WEB-INF/web.xml:92-93). The wire carries an
 * ISO `LocalDate`, so this is a re-spelling of the parts and NOT a `Date` parse: constructing a
 * Date from "2026-03-04" reads it as UTC midnight and can render the previous day west of it.
 *
 * <p>Note the shipped users screen renders the same values as the raw ISO string. That divergence
 * is the users screen's, not this one's — legacy fidelity is the tie-breaker here, and converging
 * the two is a follow-up rather than a change to a shipped surface.
 */
const legacyDate = (iso: string | null | undefined): string | null => {
  if (iso == null || iso === '') return null
  const [year, month, day] = iso.split('-')
  return year && month && day ? `${day}/${month}/${year}` : iso
}

/**
 * A row this surface's own Add created reports `ENDED` carrying an `inactiveDate` that is really
 * its CREATION date — it was never active (22.2 deviation (E),
 * MillAssociationService.java:223-231). The mandated derivation is the only thing that separates
 * it from a genuinely deactivated row, since both serialize the same status.
 *
 * <p>Nulls are ABSENT from the JSON rather than null, so this tests `== null` deliberately.
 */
const isNeverActivated = (row: MillSubmitter) => row.activeDate == null && row.revisionCount === 0

/** The explicit "no contact" choice. A blank selection is a DELETE, so it must read as a choice. */
const NO_CONTACT: ContactOption = { clientContactId: -1, contactName: '(None)' }

const HEAD_OFFICE_ITEMS = [
  { code: 'Y', label: 'Yes' },
  { code: 'N', label: 'No' },
] as const

type HeadOfficeItem = (typeof HEAD_OFFICE_ITEMS)[number]

const MILL_FAILED = 'The mill could not be re-read.'
const CONTACTS_FAILED = 'The mill contacts could not be loaded.'
const USERS_FAILED = 'The associated users could not be loaded.'
const SAVE_FAILED = 'The mill could not be saved.'
const STATUS_FAILED = 'The mill status could not be changed.'
const IMPORT_FAILED = 'The mill could not be imported.'
const ADD_FAILED = 'The user could not be added to the mill.'
const TOGGLE_FAILED = 'The association could not be changed.'

const formFor = (mill: AdminMill): ContactForm => ({
  // ABSENT means never set, and D5 refuses to guess: legacy defaulted the control to "No" and the
  // first Save silently persisted N over a stored NULL, changing that mill's Mill Information
  // output. Null here renders as no selection and gates Save instead (deviation (E)).
  headOfficeContactInd: mill.headOfficeContactInd ?? null,
  headOfficeContactId: mill.headOfficeContactId ?? null,
  divisionContactId: mill.divisionContactId ?? null,
})

/**
 * Mill administration (UC-MILL-001, legacy `mills.xhtml`): select or import a mill, then work that
 * one mill — its details, its status, and its associated licensee users.
 *
 * <p>Three legacy behaviours on this screen are reproduced deliberately and three are deliberately
 * NOT. Kept: no confirmation on any status or per-row action (CNF-001 on import is the only
 * confirm on the whole screen), every message page-level because legacy had no field-level errors
 * here (FLD-001), and a blank contact selection persisting as a cleared column. Fixed, each a
 * ratified decision rather than a judgement call: `Change Mill` opens its dialog (D4 — legacy's
 * called a pre-4.0 widget namespace that no longer existed), the head-office indicator requires an
 * explicit choice when unset (D5), and the search's `Status` list is client-held because no
 * endpoint serves it (D3).
 *
 * <p>Async state is handled locally rather than through `useScheduleBanners`, for the same two
 * reasons the users screen documents: this screen needs a warning channel the hook has no slot for
 * (a duplicate add answers 200 with a warning), and a 409 has to re-read, which the hook's catch
 * cannot reach. Its `run()` also takes a pre-built promise, which has already been dispatched by
 * the time the lock could refuse it.
 */
const Mills: FC = () => {
  const navigate = useNavigate()

  const [mill, setMill] = useState<AdminMill | null>(null)
  const [contacts, setContacts] = useState<readonly ContactOption[]>([])
  const [users, setUsers] = useState<readonly MillSubmitter[]>([])
  const [form, setForm] = useState<ContactForm | null>(null)

  const [message, setMessage] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // The picker's own channel: it clears this on a later successful search, and that clear must not
  // be able to erase a standing write failure.
  const [lookupError, setLookupError] = useState<string | null>(null)
  // The import dialog's own channel, so a refused import leaves the dialog and its results
  // standing rather than closing over the administrator's search.
  const [importError, setImportError] = useState<string | null>(null)
  // The add dialog's own channel, for the same reason: a refused add leaves the dialog open, and a
  // page-level banner would sit unreadable behind the Carbon overlay.
  const [addError, setAddError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const [selectOpen, setSelectOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [addOpen, setAddOpen] = useState(false)

  // Mirrors the selected mill for the async guards: a list that resolves after the administrator
  // has moved to another mill must not repaint the now-current one's panel.
  const millIdRef = useRef<number | null>(null)
  // Synchronous write lock: two clicks in one event burst both read the same render's `busy`, so
  // state alone cannot close the double-submit window — and the second request would carry an
  // already-spent revisionCount and answer a spurious 409.
  const busyRef = useRef(false)
  // Sequenced as well as keyed: mill equality alone would let an older in-flight list for the SAME
  // mill land after a post-write re-read and repaint pre-write rows.
  const usersSeqRef = useRef(0)
  const contactsSeqRef = useRef(0)

  const clearNotifications = () => {
    setMessage(null)
    setWarning(null)
    setError(null)
    setLookupError(null)
    setImportError(null)
    setAddError(null)
  }

  /** `includeEnded` is OMITTED on purpose: it defaults TRUE here (MillAssociationApi.java:57). */
  const loadUsers = (millId: number) => {
    const seq = ++usersSeqRef.current
    return api()
      .get<MillSubmitter[]>(`${ADMIN_MILLS}/${millId}/users`)
      .then((response) => {
        if (millIdRef.current === millId && seq === usersSeqRef.current) setUsers(response.data)
      })
      .catch((failure: unknown) => {
        if (millIdRef.current !== millId || seq !== usersSeqRef.current) return
        // A failed re-read withdraws the write's sentence too: the table still shows pre-write
        // rows, and "has been deactivated" standing over them would claim the opposite of what is
        // on screen.
        setMessage(null)
        setWarning(null)
        setError(extractDetail(failure) || USERS_FAILED)
      })
  }

  const loadContacts = (millId: number) => {
    const seq = ++contactsSeqRef.current
    return api()
      .get<ContactOption[]>(`${ADMIN_MILLS}/${millId}/contact-options`)
      .then((response) => {
        if (millIdRef.current === millId && seq === contactsSeqRef.current) {
          setContacts(response.data)
        }
      })
      .catch((failure: unknown) => {
        if (millIdRef.current === millId && seq === contactsSeqRef.current) {
          setContacts([])
          setError(extractDetail(failure) || CONTACTS_FAILED)
        }
      })
  }

  /**
   * Adopt a mill as the selection. The search result already IS a full `AdminMill`, so nothing is
   * re-read here — legacy's `onRowSelect` likewise only went on to hydrate the contact list
   * (MillsMB.java:370-374).
   */
  const adopt = (next: AdminMill, keepMessages = false) => {
    // Set SYNCHRONOUSLY, before any request: the guards above compare against this, and a ref set
    // after an await would let the outgoing mill's responses through.
    millIdRef.current = next.millId
    setMill(next)
    setForm(formFor(next))
    // Both belong to the previous mill; carrying either would attribute one mill's state to
    // another. Legacy reset them on import too (MillsMB.java:409-412).
    setContacts([])
    setUsers([])
    if (!keepMessages) clearNotifications()
    loadContacts(next.millId)
    loadUsers(next.millId)
  }

  /**
   * Re-read one mill after a conflict, so the next attempt carries a live revision. The staged
   * contact FORM deliberately survives: legacy's view-scoped bean kept its values across a failed
   * save and a status action (only row-select and import reset it), and wiping it on a BR-09
   * refusal would erase the very selection the administrator was just told to correct. Only `mill`
   * is refreshed — the revision token is read off `mill`, never off the form.
   */
  const reread = (millId: number) => {
    api()
      .get<AdminMill>(`${ADMIN_MILLS}/${millId}`)
      .then((response) => {
        if (millIdRef.current !== millId) return
        setMill(response.data)
      })
      .catch((failure: unknown) => {
        if (millIdRef.current === millId) setError(extractDetail(failure) || MILL_FAILED)
      })
    loadUsers(millId)
  }

  /** One guarded write: lock, clear the banners, then re-read from the server on success. */
  const write = <T,>(
    // A factory, not a promise: an axios call built at the call site has already been DISPATCHED
    // by the time the lock could refuse it — the guard would swallow the response of a request
    // that went to the server anyway.
    request: () => Promise<{ data: T }>,
    fallback: string,
    onDone: (data: T) => void,
    onFailure?: (detail: string) => void,
  ) => {
    const millId = millIdRef.current
    // Checked and set synchronously — see busyRef. `busy` state exists only to drive `disabled`.
    if (busyRef.current) return
    busyRef.current = true
    setBusy(true)
    clearNotifications()
    request()
      .then((response) => {
        if (millIdRef.current !== millId) return
        onDone(response.data)
      })
      .catch((failure: unknown) => {
        if (millIdRef.current !== millId) return
        const detail = extractDetail(failure) || fallback
        if (onFailure) onFailure(detail)
        else setError(detail)
        // A 409 means the row moved underneath this view, so the view is what has to change: show
        // what the server actually holds instead of leaving a stale revision inviting a retry.
        if ((failure as { response?: { status?: number } }).response?.status === 409 && millId) {
          reread(millId)
        }
      })
      .finally(() => {
        // Unconditional: a conditional reset is a stranded-lock path (the 7.3 lesson).
        busyRef.current = false
        setBusy(false)
      })
  }

  /**
   * Apply a mill write's outcome. `messageKey` exists ONLY on a 200 — every refusal is a bare
   * ProblemDetail with no key (GlobalExceptionHandler.java:80-83) — and on import BOTH message
   * fields are absent by design, so an absent message is the import's success shape rather than a
   * missing one (deviation (I)).
   */
  const applyMillWrite = (data: AdminMillResponse) => {
    millIdRef.current = data.mill.millId
    setMill(data.mill)
    if (data.message) setMessage(data.message)
  }

  const save = () => {
    if (!mill || !form) return
    // The revision last READ, taken off the mill record at click time rather than held in state or
    // incremented locally.
    const body = toSaveRequest(form, mill.revisionCount)
    // Advisory (AD-6): the button is already disabled, and the server re-validates regardless.
    if (!body) return
    write(
      () => api().put<AdminMillResponse>(`${ADMIN_MILLS}/${mill.millId}/contacts`, body),
      SAVE_FAILED,
      (data) => {
        applyMillWrite(data)
        // Only a SUCCESSFUL save resets the staged form — to exactly what was saved. A status
        // action or a 409 re-read leaves it alone, as legacy's view-scoped bean did.
        setForm(formFor(data.mill))
      },
    )
  }

  const changeStatus = (action: 'activate' | 'deactivate') => {
    if (!mill) return
    const body: ChangeMillStatusRequest = { revisionCount: mill.revisionCount }
    write(
      () => api().post<AdminMillResponse>(`${ADMIN_MILLS}/${mill.millId}/${action}`, body),
      STATUS_FAILED,
      applyMillWrite,
    )
  }

  const importMill = (target: ImportableMill) => {
    // The import's guard is the lock alone: there is no selected mill yet, so `write`'s mill key is
    // whatever was selected before (normally nothing) and the response is adopted as the selection.
    write(
      () => api().post<AdminMillResponse>(`${ADMIN_MILLS}/${target.millId}/import`, undefined),
      IMPORT_FAILED,
      (data) => {
        setImportOpen(false)
        // BR-03: the row lands Closed with the head-office indicator set and empty panels, so
        // Activate is the action offered. Its own detail panel appearing IS the confirmation —
        // AdminMillResponse omits both message fields here and none is to be invented.
        adopt(data.mill)
      },
      // Kept in the dialog, so a refusal leaves the search results the administrator is working
      // through standing rather than closing over them.
      setImportError,
    )
  }

  const addUser = (userGuid: string) => {
    if (!mill) return
    const millId = mill.millId
    const body: AddMillUserRequest = { userGuid }
    write(
      // The SHIPPED envelope, not an inline shape: rows 10-12 of the contract serve
      // AssignmentResponse, and an ad-hoc type claiming `message` is always present would let the
      // compiler certify a contract the non_null doctrine does not make.
      () => api().post<AssignmentResponse>(`${ADMIN_MILLS}/${millId}/users`, body),
      ADD_FAILED,
      (data) => {
        setAddOpen(false)
        // A duplicate — ANY existing pair, active or ended — answers 200 having written NOTHING
        // (BR-08, MillAssociationService.java:97-101). Only the key tells it apart from a
        // successful add, and it reads as the semantic opposite of what it means.
        if (data.messageKey === MSG_ALREADY_ASSIGNED) setWarning(data.message)
        else setMessage(data.message)
        // Legacy reloaded the panel in BOTH branches (MillsMB.java:503-504). On the duplicate
        // branch that refetch is observably a no-op, which is what keeps the panel unchanged.
        loadUsers(millId)
      },
      // A refusal renders INSIDE the still-open dialog: the page banner sits behind the Carbon
      // overlay, so routing it there would show the administrator nothing at all.
      setAddError,
    )
  }

  const toggleUser = (row: MillSubmitter, action: 'activate' | 'deactivate') => {
    if (!mill) return
    const millId = mill.millId
    // POST, not PATCH: the users screen's endpoints are a different surface with different add
    // semantics. And the revision comes off the ROW, at click time.
    const body: ChangeMillUserRequest = { revisionCount: row.revisionCount }
    write(
      () =>
        api().post<AssignmentResponse>(
          `${ADMIN_MILLS}/${millId}/users/${row.userGuid}/${action}`,
          body,
        ),
      TOGGLE_FAILED,
      (data) => {
        setMessage(data.message)
        // Legacy's row flipped only because the DAO mutated the very object the table held
        // (MillsMB.java:448-461); a DTO-based rebuild must refetch or nothing changes on screen.
        loadUsers(millId)
      },
    )
  }

  const headOfficeItem =
    form?.headOfficeContactInd == null
      ? null
      : (HEAD_OFFICE_ITEMS.find((item) => item.code === form.headOfficeContactInd) ?? null)

  const contactItems: ContactOption[] = [NO_CONTACT, ...contacts]

  /**
   * The option the stored id resolves to. When the id is absent from the served list — the options
   * fetch failed, or the contact left the client location — a synthesized placeholder keeps the
   * display telling the truth about what Save will send: rendering "Select" over a retained id let
   * the payload and the screen diverge. Legacy's selectOneMenu refused that save outright; here the
   * server's BR-09 check still guards the genuinely invalid case.
   */
  const contactItem = (id: number | null): ContactOption =>
    id == null
      ? NO_CONTACT
      : (contacts.find((option) => option.clientContactId === id) ?? {
          clientContactId: id,
          contactName: `Contact ${id} (not in list)`,
        })

  const isActive = mill?.millStatusCode === MILL_ACTIVE

  return (
    <div className="app-page schedule-page">
      <ScheduleTombstone title="Mills" />
      <Grid fullWidth className="app-page__body">
        {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
        {warning && <NotificationColumn kind="warning" title="Warning" subtitle={warning} />}
        {error && <NotificationColumn kind="error" title="Error" subtitle={error} />}
        {/* While the add dialog is open this channel renders INSIDE it — a page banner would sit
            behind the Carbon overlay. It shows here only once the dialog has closed over it. */}
        {lookupError && !addOpen && (
          <NotificationColumn kind="error" title="Error" subtitle={lookupError} />
        )}

        <Column sm={4} md={8} lg={16}>
          {/* STA-001 state 1. The two entry controls render only while nothing is selected, exactly
              as legacy gated them on `showSelectMillButton` (mills.xhtml:24, :26). */}
          {!mill && (
            <div className="mills__entry">
              <Button renderIcon={Edit} onClick={() => setSelectOpen(true)}>
                Select Mill
              </Button>
              <Button kind="secondary" renderIcon={Add} onClick={() => setImportOpen(true)}>
                Import Mill
              </Button>
            </div>
          )}

          {mill && form && (
            // ABSENT rather than disabled while nothing is selected: legacy gated the whole panel
            // on `rendered="#{millsMB.millSelected}"` (mills.xhtml:28).
            <section className="mills__section" aria-labelledby="mills-detail-heading">
              <h2 className="mills__heading" id="mills-detail-heading">
                Mill Details
              </h2>

              {/* A PLAIN panel, not a table: legacy's `selectedMillList` is a one-row,
                  one-unheaded-column p:dataTable used purely as an EL scope trick
                  (mills.xhtml:28-30). Reproducing it as a table would invent a data grid.
                  Nothing here is editable but the three controls — legacy writes nothing on
                  THE.MILL, so there is no rename and no create-mill anywhere. */}
              <p className="mills__identity">
                <span className="mills__label">Mill #:</span>
                {/* One node, because the mill number and name read as one identity. The number
                    carries NO thousands separator: legacy fed a BigDecimal into MessageFormat and
                    printed "1,234", the wire carries a String (22.1 deviation (E)/(F)). */}
                <span>{`${dash(mill.millNumber)} - ${dash(mill.millName)}`}</span>
                <span className="mills__label">Status: </span>
                {/* The SERVER's description — "Active" or "Close", the code table's own text
                    (deviation (G)). Deriving it from millStatusCode would re-spell the data. */}
                <span>{dash(mill.statusDescription)}</span>
              </p>

              <div className="mills__editable">
                <Dropdown<HeadOfficeItem>
                  id="mill-head-office"
                  titleText="Head Office :"
                  label="Select"
                  items={[...HEAD_OFFICE_ITEMS]}
                  itemToString={(item) => item?.label ?? ''}
                  // `null`, never `undefined`: an undefined selectedItem flips Carbon to
                  // uncontrolled and keeps the PREVIOUS mill's value painted after a switch.
                  selectedItem={headOfficeItem}
                  disabled={busy}
                  onChange={({ selectedItem }) =>
                    setForm((prev) =>
                      prev ? { ...prev, headOfficeContactInd: selectedItem?.code ?? null } : prev,
                    )
                  }
                />
                {/* Both selectors are populated from the SAME list and have no cross-exclusion —
                    the same contact may hold both slots, as in legacy. The server orders by
                    CONTACT_NAME (22.1 D4a), so the order is taken as given. The list is
                    deliberately UNFILTERED, so BR-09 is the server's call and is never
                    pre-validated here. */}
                <Dropdown<ContactOption>
                  id="mill-head-office-contact"
                  titleText="Head Office Contact :"
                  label="Select"
                  items={contactItems}
                  itemToString={(item) => item?.contactName ?? ''}
                  selectedItem={contactItem(form.headOfficeContactId)}
                  disabled={busy}
                  onChange={({ selectedItem }) =>
                    setForm((prev) =>
                      prev
                        ? {
                            ...prev,
                            // The explicit "(None)" choice is a DELETE, not a no-op: legacy's
                            // blank selection stored null (MillDAO.java:226-234).
                            headOfficeContactId:
                              !selectedItem || selectedItem.clientContactId < 0
                                ? null
                                : selectedItem.clientContactId,
                          }
                        : prev,
                    )
                  }
                />
                <Dropdown<ContactOption>
                  id="mill-division-contact"
                  titleText="Division Contact :"
                  label="Select"
                  items={contactItems}
                  itemToString={(item) => item?.contactName ?? ''}
                  selectedItem={contactItem(form.divisionContactId)}
                  disabled={busy}
                  onChange={({ selectedItem }) =>
                    setForm((prev) =>
                      prev
                        ? {
                            ...prev,
                            divisionContactId:
                              !selectedItem || selectedItem.clientContactId < 0
                                ? null
                                : selectedItem.clientContactId,
                          }
                        : prev,
                    )
                  }
                />
              </div>

              <div className="mills__actions">
                {/* STA-001: exactly one of these, chosen by the status CODE — presence/absence,
                    never enable/disable (MillsMB.java:359-364). Neither is confirmed; legacy has
                    no p:confirm on either (mills.xhtml:100-101). */}
                {isActive ? (
                  <Button
                    kind="danger--tertiary"
                    disabled={busy}
                    renderIcon={Misuse}
                    onClick={() => changeStatus('deactivate')}
                  >
                    Deactivate
                  </Button>
                ) : (
                  <Button
                    kind="tertiary"
                    disabled={busy}
                    renderIcon={CheckmarkOutline}
                    onClick={() => changeStatus('activate')}
                  >
                    Activate
                  </Button>
                )}
                {/* D4: implemented WORKING. Legacy's button called `searchSelectMill.show()` on the
                    pre-4.0 global widget namespace with no shim and updated the dialog containers
                    rather than the inner form (mills.xhtml:102), so it almost certainly never
                    opened. The current mill stays selected until a new row is chosen — legacy's
                    `clear(false)` does not clear the selection (MillsMB.java:513-519) — and no
                    message is emitted, the artefacts being silent on one. */}
                <Button
                  kind="ghost"
                  renderIcon={Edit}
                  // Disabled while a write is in flight, like every other write-adjacent control:
                  // adopting another mill mid-write makes millIdRef drop the response, so a
                  // completed write's outcome would vanish without a message.
                  disabled={busy}
                  onClick={() => setSelectOpen(true)}
                >
                  Change Mill
                </Button>
                <Button
                  // Advisory Save-gating (D5, AD-6): disabled only while the head-office indicator
                  // has never been chosen, so no value is written that nobody picked.
                  disabled={busy || !canSaveContacts(form)}
                  onClick={save}
                >
                  Save
                </Button>
              </div>
            </section>
          )}

          {mill && (
            <section className="mills__section" aria-labelledby="mills-users-heading">
              {/* ONE panel, singular, verbatim (mills.xhtml:110). The `Associated Auditors` panel
                  is RETIRED, not deferred (DL-23; 22.2 deviation (A)) — and with it the
                  IDIR/GOVERNMENT half of the legacy user search. */}
              <h2 className="mills__heading" id="mills-users-heading">
                Associated Licensee User
              </h2>
              <div className="mills__add">
                <Button size="sm" disabled={busy} renderIcon={Add} onClick={() => setAddOpen(true)}>
                  Add
                </Button>
              </div>

              <TableContainer className="mills__grid">
                <Table aria-label="Associated Licensee User">
                  <TableHead>
                    <TableRow>
                      {/* One `User` column carrying the GUID (D2, deviation (B)): legacy's First
                          Name / last Name / BCeID have no wire source — displayName is
                          unconditionally null on this surface and there is no BCeID field — until
                          the directory join ships. */}
                      <TableHeader>User</TableHeader>
                      <TableHeader>User To Mill Status</TableHeader>
                      <TableHeader>Activation Date</TableHeader>
                      <TableHeader>Deactivation Date</TableHeader>
                      <TableHeader>Actions</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {users.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={5}>No associated users.</TableCell>
                      </TableRow>
                    ) : (
                      users.map((row) => {
                        // The WIRE's two-state vocabulary, never a date heuristic (AC6). The server
                        // derives status from INACTIVE_DATE alone (MillUserXrefEntity.java:49-51),
                        // and grandfathered legacy rows can hold BOTH dates — deviation (K)'s own
                        // premise — so `activeDate != null` would render such a row Active while
                        // the wire says ENDED.
                        const active = row.status === 'ACTIVE'
                        return (
                          <TableRow key={row.userGuid}>
                            <TableCell>{row.userGuid}</TableCell>
                            {/* mills.xhtml:123-124 renders literally "Active"/"Inactive"; the wire
                                value stays ENDED. Legacy's two independent `rendered` tests let a
                                both-dates row print "InactiveActive" and offer BOTH actions — the
                                wire's status is single-valued, so that defect is unreachable
                                (deviation (K)). */}
                            <TableCell>{active ? 'Active' : 'Inactive'}</TableCell>
                            <TableCell>{dash(legacyDate(row.activeDate))}</TableCell>
                            <TableCell>
                              {/* A never-activated row's INACTIVE_DATE is really its creation date,
                                  so printing it under this header would state that a mill was
                                  deactivated on a day it never was (deviation (L)). */}
                              {isNeverActivated(row) ? '—' : dash(legacyDate(row.inactiveDate))}
                            </TableCell>
                            <TableCell>
                              {/* The SINGLE applicable action (mills.xhtml:136-143), fired
                                  immediately — legacy confirmed neither. Both a never-activated
                                  and a deactivated row offer Activate. */}
                              {active ? (
                                <Button
                                  kind="ghost"
                                  size="sm"
                                  disabled={busy}
                                  aria-label={`Deactivate user ${row.userGuid}`}
                                  renderIcon={Misuse}
                                  onClick={() => toggleUser(row, 'deactivate')}
                                >
                                  Deactivate
                                </Button>
                              ) : (
                                <Button
                                  kind="ghost"
                                  size="sm"
                                  disabled={busy}
                                  // Live and unqualified even on a Closed mill. BR-02 is ONE
                                  // screen's guard, not a system-wide invariant: the shipped users
                                  // surface can still create an active association on a closed
                                  // mill, and that is ratified parity. A disabled control or a
                                  // "cannot" tooltip here would state a rule ILCR does not hold.
                                  aria-label={`Activate user ${row.userGuid}`}
                                  renderIcon={CheckmarkOutline}
                                  onClick={() => toggleUser(row, 'activate')}
                                >
                                  Activate
                                </Button>
                              )}
                              {/* S10, on EVERY row — legacy has no `rendered` guard on View
                                  (mills.xhtml:144-151). Navigating away loses unsaved contact edits
                                  silently, exactly as legacy's hard redirect did; there is no
                                  unsaved-changes prompt on this screen and none is to be added. */}
                              <Button
                                kind="ghost"
                                size="sm"
                                aria-label={`View user ${row.userGuid}`}
                                renderIcon={View}
                                onClick={() =>
                                  navigate({
                                    to: '/mill-associations',
                                    search: { userGuid: row.userGuid },
                                  })
                                }
                              >
                                View
                              </Button>
                            </TableCell>
                          </TableRow>
                        )
                      })
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </section>
          )}
        </Column>
      </Grid>

      {selectOpen && (
        <MillSearchModal
          onSelect={(next) => {
            setSelectOpen(false)
            adopt(next)
          }}
          onClose={() => setSelectOpen(false)}
        />
      )}
      {importOpen && (
        <ImportMillModal
          busy={busy}
          failure={importError}
          onConfirm={importMill}
          onClose={() => {
            setImportOpen(false)
            setImportError(null)
          }}
        />
      )}
      {addOpen && (
        <AddUserModal
          busy={busy}
          // Both channels render inside the dialog while it is open: the write refusal and the
          // picker outage alike would otherwise sit page-level behind the overlay.
          failure={addError ?? lookupError}
          onAdd={(user) => addUser(user.userGuid)}
          onClose={() => {
            setAddOpen(false)
            setAddError(null)
          }}
          onError={setLookupError}
        />
      )}
    </div>
  )
}

export default Mills
