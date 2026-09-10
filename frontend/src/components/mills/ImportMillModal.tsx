import type { FC } from 'react'
import { useEffect, useState } from 'react'
import {
  Button,
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
import { CheckmarkOutline } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import type { ImportableMill, MessageText } from '@/interfaces/MillMaintenance'

const api = () => apiService.getAxiosInstance()

const IMPORTABLE_PATH = '/v1/admin/mills/importable'
const MESSAGES_PATH = '/v1/messages'
const CONFIRM_IMPORT_KEY = 'confirmImportMill'

const SEARCH_FAILED = 'The importable mill search could not be completed.'
/**
 * Page-owned, and deliberately NOT a stand-in for CNF-001. The confirmation sentence has a real
 * source and is fetched from it; if that fetch fails this says so rather than inventing a business
 * sentence the bundle would have to be trusted for (AD-8). The gate FAILS CLOSED while it stands:
 * Yes is disabled until the real CNF-001 text is on screen (review ruling D-R1) — legacy's bundle
 * was local, so its confirm never posed anything but the sentence.
 */
const CONFIRM_TEXT_FAILED = 'The confirmation message could not be loaded.'
/** Page-owned chrome for a zero-match importable search — the bare array carries no ERR-001. */
const NO_IMPORTABLE_MATCHES = 'No importable mills matched the search.'

const dash = (value: string | null | undefined) => (value == null || value === '' ? '—' : value)

type ImportMillModalProps = {
  /** Runs only after the administrator has confirmed; the parent owns the write and its lock. */
  readonly onConfirm: (mill: ImportableMill) => void
  readonly onClose: () => void
  /** The parent's write lock, so a second row cannot be imported while the first is in flight. */
  readonly busy: boolean
  /** The parent's import failure, rendered here so the dialog and its results stay put. */
  readonly failure: string | null
}

/**
 * The "Find and select Mill to Import" dialog (UC-MILL-001 S02, S14, BR-03, BR-04).
 *
 * <p>No Status criterion, because an unimported mill has no ILCR status (BR-04,
 * mills.xhtml:239-243) — and its own Number/Name state, not the select dialog's (see
 * MillSearchModal for the legacy defect that shared state caused).
 *
 * <p>The per-row control is confirmed before any request (CNF-001). The confirmation text is
 * FETCHED from `GET /v1/messages?key=confirmImportMill` — allowlisted for exactly this purpose
 * (MessageController:43-48) — because the confirm is rendered before the import endpoint is called,
 * so no response exists to carry it.
 */
const ImportMillModal: FC<ImportMillModalProps> = ({ onConfirm, onClose, busy, failure }) => {
  const [millNumber, setMillNumber] = useState('')
  const [millName, setMillName] = useState('')

  const [results, setResults] = useState<readonly ImportableMill[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)

  /** The row awaiting confirmation. Null means no confirm dialog is mounted at all. */
  const [pending, setPending] = useState<ImportableMill | null>(null)
  /** The verbatim CNF-001 sentence — the ONLY value that arms Yes. */
  const [confirmText, setConfirmText] = useState<string | null>(null)
  /** The fetch failure, rendered in the confirm body while Yes stays disabled (fail closed). */
  const [confirmError, setConfirmError] = useState<string | null>(null)

  // Resolved once, when the dialog opens, rather than per row: the sentence names no mill, so a
  // fetch per click would be the same request repeated.
  useEffect(() => {
    let live = true
    api()
      .get<MessageText>(MESSAGES_PATH, { params: { key: CONFIRM_IMPORT_KEY } })
      .then((response) => {
        if (live) setConfirmText(response.data.text)
      })
      .catch(() => {
        if (live) setConfirmError(CONFIRM_TEXT_FAILED)
      })
    return () => {
      live = false
    }
  }, [])

  const search = () => {
    setSearching(true)
    setError(null)
    api()
      .get<ImportableMill[]>(IMPORTABLE_PATH, {
        params: {
          millNumber: millNumber.trim() === '' ? undefined : millNumber.trim(),
          millName: millName.trim() === '' ? undefined : millName.trim(),
        },
      })
      .then((response) => setResults(response.data))
      .catch((failed: unknown) => setError(extractDetail(failed) || SEARCH_FAILED))
      .finally(() => setSearching(false))
  }

  const clear = () => {
    setMillNumber('')
    setMillName('')
    setResults(null)
    setError(null)
  }

  const notice = failure ?? error

  return (
    <>
      <Modal
        open
        passiveModal
        size="lg"
        modalHeading="Find and select Mill to Import"
        aria-label="Find and select Mill to Import"
        // Ignored while an import is in flight: closing would unmount the failure's only renderer
        // (`failure` renders here, by design), so a late refusal would land nowhere — and resurface
        // stale over the next, unrelated session of this dialog.
        onRequestClose={() => {
          if (!busy) onClose()
        }}
      >
        {/* A form, so Enter in either criterion searches (deviation (J) — additive; legacy
            suppressed keyCode 13 app-wide). */}
        <form
          className="mills__criteria"
          onSubmit={(event) => {
            event.preventDefault()
            if (!searching && !busy) search()
          }}
        >
          <TextInput
            id="mill-import-number"
            labelText="Number:"
            maxLength={10}
            value={millNumber}
            onChange={(event) => setMillNumber(event.target.value)}
          />
          <TextInput
            id="mill-import-name"
            labelText="Name:"
            maxLength={100}
            value={millName}
            onChange={(event) => setMillName(event.target.value)}
          />
          <Button type="submit" size="sm" disabled={searching || busy}>
            Search
          </Button>
          <Button size="sm" kind="secondary" disabled={searching || busy} onClick={clear}>
            Clear
          </Button>
        </form>

        {/* Severity carried by the kind AND an explicit title word; the API text is the subtitle,
            verbatim (AD-8). A refused import renders HERE rather than page-level, so the search
            results the administrator is working through stay on screen. */}
        {notice && <InlineNotification kind="error" lowContrast title="Error" subtitle={notice} />}

        {/* A completed search that matched nothing must be distinguishable from one that never
            ran: this endpoint answers a bare array with no ERR-001 envelope, so the sentence is
            page-owned chrome. */}
        {results !== null && results.length === 0 && (
          <InlineNotification
            kind="info"
            lowContrast
            title="No matches"
            subtitle={NO_IMPORTABLE_MATCHES}
          />
        )}

        {results !== null && results.length > 0 && (
          <TableContainer className="mills__grid">
            <Table aria-label="Importable mill search results">
              <TableHead>
                <TableRow>
                  <TableHeader>Mill Number</TableHeader>
                  <TableHeader>Mill Name</TableHeader>
                  <TableHeader>Import</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {results.map((mill) => (
                  <TableRow key={mill.millId}>
                    <TableCell>{dash(mill.millNumber)}</TableCell>
                    <TableCell>{dash(mill.millName)}</TableCell>
                    <TableCell>
                      <Button
                        kind="ghost"
                        size="sm"
                        disabled={busy}
                        renderIcon={CheckmarkOutline}
                        // mills.xhtml:258 rendered `value=""` with no title or alt, so this was an
                        // unnamed icon repeated once per row. Named here — twice over, because
                        // aria-label names the BUTTON and iconDescription titles the SVG, and
                        // Carbon warns about an icon-only button that has only the former.
                        aria-label={`Import mill ${dash(mill.millNumber)}`}
                        iconDescription={`Import mill ${dash(mill.millNumber)}`}
                        onClick={() => setPending(mill)}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Modal>

      {/* Mounted only while a confirm is pending, so its Yes/No do not sit in the accessibility
          tree competing with the row controls. Cancelling issues NO request and leaves this dialog
          and its results standing (resources/js/primefaces-fix-4.0.js:38-43). */}
      {pending && (
        <Modal
          open
          modalHeading="Confirmation"
          aria-label="Confirmation"
          primaryButtonText="Yes"
          secondaryButtonText="No"
          // FAIL CLOSED (D-R1): an import is never confirmed against a blank body (fetch still in
          // flight after a fast row click) or against the fetch-failure sentence — AC3 requires the
          // confirm to pose the verbatim CNF-001 text, so anything else leaves Yes dark.
          primaryButtonDisabled={confirmText == null}
          onRequestClose={() => setPending(null)}
          onRequestSubmit={() => {
            if (confirmText == null) return
            const mill = pending
            setPending(null)
            onConfirm(mill)
          }}
        >
          <p>{confirmText ?? confirmError}</p>
        </Modal>
      )}
    </>
  )
}

export default ImportMillModal
