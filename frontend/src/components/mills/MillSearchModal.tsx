import type { FC } from 'react'
import { useState } from 'react'
import {
  Button,
  Dropdown,
  InlineNotification,
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
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import {
  MILL_STATUS_OPTIONS,
  type AdminMill,
  type MillSearchResponse,
} from '@/interfaces/MillMaintenance'

const api = () => apiService.getAxiosInstance()

const SEARCH_PATH = '/v1/admin/mills'

const SEARCH_FAILED = 'The mill search could not be completed.'

/**
 * A dropdown entry: the two server codes plus the "no criterion" choice. Deliberately wider than
 * `MillStatusOption` — asserting `{ code: '' }` into that closed union would let a future
 * exhaustiveness check reason about a value the type says cannot exist.
 */
type StatusItem = { readonly code: string; readonly description: string }

/** The "no status criterion" choice. Legacy's dropdown led with a blank item (mills.xhtml:208). */
const ANY_STATUS: StatusItem = { code: '', description: 'Any' }

const STATUS_ITEMS: StatusItem[] = [ANY_STATUS, ...MILL_STATUS_OPTIONS]

const dash = (value: string | null | undefined) => (value == null || value === '' ? '—' : value)

type MillSearchModalProps = {
  readonly onSelect: (mill: AdminMill) => void
  readonly onClose: () => void
}

/**
 * The "Find and select Mill" dialog (UC-MILL-001 S11, S15). Serves both `Select Mill` and
 * `Change Mill` — legacy pointed both buttons at the same dialog, and D4 fixes the Change Mill
 * button rather than reproducing the breakage that stopped it opening.
 *
 * <p>Its criteria are its OWN state, deliberately not shared with the import dialog: legacy bound
 * both dialogs' Number/Name to the same bean fields (mills.xhtml:247, MillsMB.java:518), so one
 * dialog's criteria leaked into the other's.
 *
 * <p>Choosing a result row IS the select action — legacy selected on `rowSelect` and hid the dialog
 * in the same breath (mills.xhtml:216-217), so there is no OK button to add. The activation control
 * lives IN the Mill Number cell rather than on the row element: a bare `onClick` on a `<tr>` is not
 * keyboard-operable, and a button keeps the three legacy columns exactly as they were.
 */
const MillSearchModal: FC<MillSearchModalProps> = ({ onSelect, onClose }) => {
  const [millNumber, setMillNumber] = useState('')
  const [millName, setMillName] = useState('')
  const [status, setStatus] = useState<StatusItem>(ANY_STATUS)

  const [results, setResults] = useState<readonly AdminMill[] | null>(null)
  // The zero-match sentence (ERR-001), which arrives on a 200 beside an empty list.
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Also the concurrency guard: Search is disabled while a search is in flight, so there is never
  // a second one to sequence against — which is why this dialog needs no monotonic token, unlike
  // the type-ahead picker, where every keystroke can dispatch.
  const [searching, setSearching] = useState(false)

  const search = () => {
    setSearching(true)
    // The previous query's rows must not survive into this one's outcome: were this request to
    // fail, the error banner would otherwise stand BESIDE stale results, reading as if those rows
    // answered the current criteria (PR #459 review).
    setResults(null)
    setNotice(null)
    setError(null)
    api()
      .get<MillSearchResponse>(SEARCH_PATH, {
        // An omitted criterion is NOT a filter, so an empty field must not travel as `name=` —
        // which would filter on the empty string. axios drops undefined params.
        params: {
          millNumber: millNumber.trim() === '' ? undefined : millNumber.trim(),
          millName: millName.trim() === '' ? undefined : millName.trim(),
          status: status.code === '' ? undefined : status.code,
        },
      })
      .then((response) => {
        setResults(response.data.results)
        // A zero match is not a failure: the criteria stay on screen and the sentence rides the
        // 200 (S11, MillMaintenanceController:52-60). `message` is present only in that case.
        setNotice(response.data.message ?? null)
      })
      .catch((failure: unknown) => {
        // Every refusal here is a bare ProblemDetail — there is no messageKey on an error path, so
        // the branch is on `detail` alone. S15's converter text arrives this way as a 400.
        setError(extractDetail(failure) || SEARCH_FAILED)
      })
      .finally(() => setSearching(false))
  }

  const clear = () => {
    // Legacy's Clear reset the dialog's fields and re-rendered it; it issued no search
    // (mills.xhtml:213 → MillsMB.clear).
    setMillNumber('')
    setMillName('')
    setStatus(ANY_STATUS)
    setResults(null)
    setNotice(null)
    setError(null)
  }

  return (
    <Modal
      open
      passiveModal
      size="lg"
      modalHeading="Find and select Mill"
      aria-label="Find and select Mill"
      onRequestClose={onClose}
    >
      {/* A form, so Enter in either text criterion searches (deviation (J) — additive; legacy
          suppressed keyCode 13 app-wide, so neither dialog ever had it). */}
      <form
        className="mills__criteria"
        onSubmit={(event) => {
          event.preventDefault()
          if (!searching) search()
        }}
      >
        <TextInput
          id="mill-search-number"
          labelText="Number:"
          maxLength={10}
          value={millNumber}
          onChange={(event) => setMillNumber(event.target.value)}
        />
        <TextInput
          id="mill-search-name"
          labelText="Name:"
          maxLength={100}
          value={millName}
          onChange={(event) => setMillName(event.target.value)}
        />
        <Dropdown<StatusItem>
          id="mill-search-status"
          titleText="Status:"
          label="Any"
          items={STATUS_ITEMS}
          itemToString={(item) => item?.description ?? ''}
          // `null`, never `undefined`: an undefined selectedItem flips Carbon to uncontrolled.
          selectedItem={status}
          onChange={({ selectedItem }) => setStatus(selectedItem ?? ANY_STATUS)}
        />
        <Button type="submit" size="sm" disabled={searching}>
          Search
        </Button>
        <Button size="sm" kind="secondary" disabled={searching} onClick={clear}>
          Clear
        </Button>
      </form>

      {/* Severity is carried by BOTH the kind and an explicit title word, never colour alone, and
          InlineNotification is a live region — these appear after the fact, so without one a
          screen-reader user gets silence on exactly the outcome they asked for. The API text is
          the subtitle, rendered verbatim (AD-8); only the title is page-owned chrome. */}
      {notice && (
        <InlineNotification kind="info" lowContrast title="No matches" subtitle={notice} />
      )}
      {error && <InlineNotification kind="error" lowContrast title="Error" subtitle={error} />}

      {results !== null && results.length > 0 && (
        <TableContainer className="mills__grid">
          <Table aria-label="Mill search results">
            <TableHead>
              <TableRow>
                <TableHeader>Mill Number</TableHeader>
                <TableHeader>Mill Name</TableHeader>
                <TableHeader>Status</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {results.map((mill) => (
                <TableRow key={mill.millId}>
                  <TableCell>
                    <Button
                      kind="ghost"
                      size="sm"
                      // Repeated row controls need a disambiguating name, or every row's button
                      // reads identically to a screen-reader user.
                      aria-label={`Select mill ${dash(mill.millNumber)}`}
                      onClick={() => onSelect(mill)}
                    >
                      {dash(mill.millNumber)}
                    </Button>
                  </TableCell>
                  <TableCell>{dash(mill.millName)}</TableCell>
                  {/* The SERVER's description, which is "Active" or "Close" (deviation (G)) — never
                      a client label derived from the code. */}
                  <TableCell>{dash(mill.statusDescription)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Modal>
  )
}

export default MillSearchModal
