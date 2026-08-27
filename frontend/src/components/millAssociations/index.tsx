import type { FC } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
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
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import NotificationColumn from '@/components/core/NotificationColumn'
import DirectoryPicker from '@/components/millAssociations/DirectoryPicker'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import type MillSummary from '@/interfaces/MillSummary'
import {
  MSG_ALREADY_ASSIGNED,
  type AccountResponse,
  type AssignmentResponse,
  type DirectoryUser,
  type MillSubmitter,
  type SubmitterAccount,
} from '@/interfaces/MillAssociation'

const api = () => apiService.getAxiosInstance()

/** codeTables' convention: a missing value is a dash, and dates render as the ISO string served. */
const dash = (value: string | null | undefined) => (value == null || value === '' ? '—' : value)

const millLabel = (mill: MillSummary | null) =>
  mill ? `${dash(mill.millNumber)} - ${dash(mill.millName)} (${mill.millStatusCode})` : ''

const ASSIGNMENTS_FAILED = 'The mill assignments could not be loaded.'
const ASSIGN_FAILED = 'The mill could not be assigned.'
const END_FAILED = 'The mill assignment could not be ended.'
const ACCOUNT_FAILED = 'The account could not be updated.'
const MILLS_FAILED = 'The mill list could not be loaded.'

/**
 * Users administration (UC-USR-001, legacy `users.xhtml`): choose a licensee from the ministry
 * directory, then maintain their ILCR account flag and their mill assignments.
 *
 * <p>Two legacy behaviours are reproduced deliberately. There is no confirmation step anywhere on
 * this screen — legacy's Add/Activate/Deactivate all fired on click (ALT-001) — and every message
 * is page-level, because legacy had no field-level errors here (FLD-001).
 *
 * <p>Async state is handled locally rather than through `useScheduleBanners`: this screen needs a
 * warning channel the hook has no slot for (an assign that changes nothing answers 200 with a
 * warning), and a 409 on End has to re-read the list, which the hook's catch cannot reach.
 */
const MillAssociations: FC = () => {
  const [mills, setMills] = useState<MillSummary[]>([])
  const [selectedMill, setSelectedMill] = useState<MillSummary | null>(null)

  const [selectedUser, setSelectedUser] = useState<DirectoryUser | null>(null)
  // Unknown until a write answers with it: the API serves no account read (deviation (O)).
  const [account, setAccount] = useState<SubmitterAccount | null>(null)
  const [assignments, setAssignments] = useState<MillSubmitter[]>([])

  const [message, setMessage] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // The picker's own channel: it clears this on a later successful search, and that clear must not
  // be able to erase a standing write failure — ERR-002's "listed below" instruction in particular
  // has to survive the administrator typing their next search (AC5, AC6).
  const [lookupError, setLookupError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // Mirrors the selected user for the async guards: a list that resolves after the administrator has
  // moved to another user must not repaint the now-current one's assignments.
  const selectedGuidRef = useRef<string | null>(null)
  // Synchronous write lock: two clicks in one event burst both read the same render's `busy`, so
  // state alone cannot close the double-submit window (the second PATCH would carry an already-spent
  // revisionCount and answer a spurious 409).
  const busyRef = useRef(false)
  // Sequenced like the picker's searches: guid equality alone would let an older in-flight list for
  // the SAME user land after a post-write re-read and repaint pre-write rows.
  const loadSeqRef = useRef(0)
  const millsSeqRef = useRef(0)

  // Memoised with no dependencies because it genuinely has none: everything it touches is stable
  // across renders (a ref, the setters, the module-level client). That keeps its identity constant,
  // so the mount effect below can declare it honestly instead of relying on an empty list — and a
  // future edit that closes over real state breaks the dependency list rather than going stale.
  const loadMills = useCallback(() => {
    const seq = ++millsSeqRef.current
    api()
      .get<MillSummary[]>('/v1/mills')
      .then((response) => {
        if (seq === millsSeqRef.current) setMills(response.data)
      })
      .catch((failure: unknown) => {
        if (seq === millsSeqRef.current) setError(extractDetail(failure) || MILLS_FAILED)
      })
  }, [])

  // Runs once, because `loadMills` is stable; onSelectUser retries it if this one failed.
  useEffect(() => {
    loadMills()
  }, [loadMills])

  const clearNotifications = () => {
    setMessage(null)
    setWarning(null)
    setError(null)
    setLookupError(null)
  }

  /**
   * `includeEnded=true` is not optional: the default hides every ended row, and the Activation /
   * Deactivation Date columns and the per-row Activate control exist precisely for those rows.
   */
  const loadAssignments = (userGuid: string) => {
    const seq = ++loadSeqRef.current
    return api()
      .get<MillSubmitter[]>(`/v1/submitters/${userGuid}/mills`, { params: { includeEnded: true } })
      .then((response) => {
        if (selectedGuidRef.current === userGuid && seq === loadSeqRef.current) {
          setAssignments(response.data)
        }
      })
      .catch((failure: unknown) => {
        if (selectedGuidRef.current === userGuid && seq === loadSeqRef.current) {
          // A failed re-read withdraws the write's sentence too: the table still shows pre-write
          // rows, and "has been activated" standing over them would claim the opposite of what is
          // on screen.
          setMessage(null)
          setWarning(null)
          setError(extractDetail(failure) || ASSIGNMENTS_FAILED)
        }
      })
  }

  const onSelectUser = (user: DirectoryUser | null) => {
    selectedGuidRef.current = user?.userGuid ?? null
    setSelectedUser(user)
    // The account flag and the assignments both belong to the previous user; carrying either across
    // would attribute one person's state to another.
    setAccount(null)
    setAssignments([])
    setSelectedMill(null)
    clearNotifications()
    if (user) {
      loadAssignments(user.userGuid)
      // The mill list is load-bearing (Add and the Mill Status join): a mount-time failure has just
      // had its banner cleared above, so this is the retry that keeps the panel from opening dead.
      if (mills.length === 0) loadMills()
    }
  }

  /** One guarded write: lock, clear the banners, then re-read the list from the server on success. */
  const write = <T,>(
    // A factory, not a promise: an axios call built at the call site has already been DISPATCHED
    // by the time the lock could refuse it — the guard would swallow the response of a request
    // that went to the server anyway.
    request: () => Promise<{ data: T }>,
    fallback: string,
    onDone: (data: T) => void,
  ) => {
    const userGuid = selectedGuidRef.current
    // Checked and set synchronously — see busyRef. `busy` state exists only to drive `disabled`.
    if (busyRef.current || !userGuid) return
    busyRef.current = true
    setBusy(true)
    clearNotifications()
    request()
      .then((response) => {
        if (selectedGuidRef.current !== userGuid) return undefined
        onDone(response.data)
        // Re-read rather than merging the returned row: the backend pins the order (active first,
        // then most recently dated) and a client-side splice would drift out of step with it.
        return loadAssignments(userGuid)
      })
      .catch((failure: unknown) => {
        if (selectedGuidRef.current !== userGuid) return undefined
        setError(extractDetail(failure) || fallback)
        // A 409 means the row moved underneath this view, so the view is what has to change: show
        // what the server actually holds instead of leaving a stale row inviting a retry.
        if ((failure as { response?: { status?: number } }).response?.status === 409) {
          return loadAssignments(userGuid)
        }
        return undefined
      })
      .finally(() => {
        // Unconditional: a conditional reset is a stranded-lock path (the 7.3 lesson), and the
        // picker is disabled while busy, so the guid cannot have changed under a live write.
        busyRef.current = false
        setBusy(false)
      })
  }

  /** Assign, or bring an ended assignment back — the same POST serves both. */
  const assign = (millId: number) => {
    if (!selectedUser) return
    write(
      () =>
        api().post<AssignmentResponse>(`/v1/mills/${millId}/submitters`, {
          userGuid: selectedUser.userGuid,
        }),
      ASSIGN_FAILED,
      (data) => {
        // An already-active pair answers 200 having changed nothing. Only the key tells it apart
        // from a successful assignment, and it reads as the opposite of what it means.
        if (data.messageKey === MSG_ALREADY_ASSIGNED) setWarning(data.message)
        else setMessage(data.message)
      },
    )
  }

  const end = (row: MillSubmitter) => {
    write(
      () =>
        api().patch<AssignmentResponse>(`/v1/mills/${row.millId}/submitters/${row.userGuid}`, {
          revisionCount: row.revisionCount,
        }),
      END_FAILED,
      (data) => setMessage(data.message),
    )
  }

  const setAccountActive = (active: boolean) => {
    if (!selectedUser) return
    write(
      () => api().patch<AccountResponse>(`/v1/submitters/${selectedUser.userGuid}`, { active }),
      ACCOUNT_FAILED,
      (data) => {
        setAccount(data.account)
        setMessage(data.message)
      },
    )
  }

  // The assignment row carries no mill status of its own, so it is joined from the mill list the
  // picker already loads — the mill's own state is what makes a refused reactivation legible.
  const millStatusFor = (millId: number) =>
    mills.find((mill) => mill.millId === millId)?.millStatusCode

  const userLabel = selectedUser ? (selectedUser.displayName ?? selectedUser.userGuid) : ''

  return (
    <div className="app-page schedule-page">
      <ScheduleTombstone title="Users" />
      <Grid fullWidth className="app-page__body">
        {/* Page-level, and above the assignments table: ERR-002's own text ends "listed below". */}
        {message && <NotificationColumn kind="success" title="Success" subtitle={message} />}
        {warning && <NotificationColumn kind="warning" title="Warning" subtitle={warning} />}
        {error && <NotificationColumn kind="error" title="Error" subtitle={error} />}
        {lookupError && <NotificationColumn kind="error" title="Error" subtitle={lookupError} />}

        <Column sm={4} md={8} lg={16}>
          <div className="mill-associations__section">
            <h2 className="mill-associations__heading">User Details</h2>
            <DirectoryPicker
              selected={selectedUser}
              disabled={busy}
              onSelect={onSelectUser}
              onError={setLookupError}
            />

            {selectedUser && (
              <TableContainer className="mill-associations__grid">
                <Table aria-label="User details">
                  <TableHead>
                    <TableRow>
                      <TableHeader>User ID</TableHeader>
                      <TableHeader>Name</TableHeader>
                      <TableHeader>Role</TableHeader>
                      <TableHeader>Active</TableHeader>
                      <TableHeader>Actions</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    <TableRow>
                      <TableCell>{selectedUser.idpUsername}</TableCell>
                      <TableCell>{userLabel}</TableCell>
                      <TableCell>{dash(account?.roleName)}</TableCell>
                      <TableCell>{dash(account?.activeInd)}</TableCell>
                      <TableCell>
                        {/* STA-001: mutually exclusive once the flag is known. Until then neither
                            can be ruled out, because no endpoint serves the current value. */}
                        {account?.activeInd !== 'Y' && (
                          <Button
                            kind="ghost"
                            size="sm"
                            disabled={busy}
                            aria-label="Activate account"
                            onClick={() => setAccountActive(true)}
                          >
                            Activate
                          </Button>
                        )}
                        {account?.activeInd !== 'N' && (
                          <Button
                            kind="ghost"
                            size="sm"
                            disabled={busy}
                            aria-label="Deactivate account"
                            onClick={() => setAccountActive(false)}
                          >
                            Deactivate
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </div>

          {selectedUser && (
            <div className="mill-associations__section">
              <h2 className="mill-associations__heading">Associated Mills</h2>
              <div className="mill-associations__add">
                {/* Unfiltered, with the status shown: legacy's Find and Add Mill dialog searched
                    every mill and rendered a Status column (users.xhtml:150-176), leaving the
                    closed-mill judgement to the administrator. */}
                <Dropdown<MillSummary>
                  id="assign-mill"
                  titleText="Mill"
                  label="Select Mill"
                  // Not filtered down to the unassigned ones: legacy listed every mill, and
                  // re-adding an assigned one is a defined outcome — a warning that changes
                  // nothing (AC3). Hiding those mills would make that answer unreachable.
                  items={mills}
                  itemToString={millLabel}
                  // `null`, never `undefined`: an undefined selectedItem flips Carbon to
                  // uncontrolled, which keeps the PREVIOUS user's mill on screen after a switch.
                  selectedItem={selectedMill}
                  disabled={busy}
                  onChange={({ selectedItem }) => setSelectedMill(selectedItem ?? null)}
                />
                <Button
                  size="sm"
                  disabled={busy || !selectedMill}
                  onClick={() => selectedMill && assign(selectedMill.millId)}
                >
                  Add
                </Button>
              </div>

              <TableContainer className="mill-associations__grid">
                <Table aria-label="Associated mills">
                  <TableHead>
                    <TableRow>
                      <TableHeader>Mill #</TableHeader>
                      <TableHeader>Mill Name</TableHeader>
                      <TableHeader>User To Mill Status</TableHeader>
                      <TableHeader>Activation Date</TableHeader>
                      <TableHeader>Deactivation Date</TableHeader>
                      <TableHeader>Mill Status</TableHeader>
                      <TableHeader>Actions</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {assignments.map((row) => (
                      <TableRow key={row.millId}>
                        <TableCell>{dash(row.millNumber)}</TableCell>
                        <TableCell>{dash(row.millName)}</TableCell>
                        {/* users.xhtml:64-66 renders "Active"/"Inactive"; ENDED is the wire value. */}
                        <TableCell>{row.activeDate ? 'Active' : 'Inactive'}</TableCell>
                        <TableCell>{dash(row.activeDate)}</TableCell>
                        <TableCell>{dash(row.inactiveDate)}</TableCell>
                        <TableCell>{dash(millStatusFor(row.millId))}</TableCell>
                        <TableCell>
                          {/* STA-002: the control keys off the dates, which the toggle keeps
                              mutually exclusive — there is one row per pair and no history. */}
                          {row.activeDate != null && (
                            <Button
                              kind="ghost"
                              size="sm"
                              disabled={busy}
                              aria-label={`Deactivate mill ${dash(row.millNumber)}`}
                              onClick={() => end(row)}
                            >
                              Deactivate
                            </Button>
                          )}
                          {row.inactiveDate != null && (
                            <Button
                              kind="ghost"
                              size="sm"
                              disabled={busy}
                              aria-label={`Activate mill ${dash(row.millNumber)}`}
                              onClick={() => assign(row.millId)}
                            >
                              Activate
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
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

export default MillAssociations
