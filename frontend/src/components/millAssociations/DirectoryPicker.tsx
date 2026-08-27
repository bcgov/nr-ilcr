import type { FC } from 'react'
import { useEffect, useRef, useState } from 'react'
import { ComboBox, Dropdown } from '@carbon/react'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import { IDP_BCEID_BUSINESS, IDP_IDIR, type DirectoryUser } from '@/interfaces/MillAssociation'

const LOOKUP_PATH = '/v1/users/lookup'
const DEBOUNCE_MS = 250

/**
 * Mirrors the server's floor for IDIR criteria (UserLookupController:41 — every IDIR criterion,
 * not only the user ID, is floored at two characters). Enforced here only to stop the picker
 * spending a 400 per keystroke; the server stays the authority.
 */
const MIN_IDIR_CRITERION = 2

/**
 * A no-match is a 200 with an empty list, so no ProblemDetail can carry this text and the shipped
 * bundle has no key for it — the one recorded exception to AD-8 (AC8). Defined once, here, rather
 * than inline at the render site. The legacy wording (legacy messages.properties:186) named ADAM,
 * AUDITOR and LICENSEE, all three retired, so it is recast while keeping the check-their-access
 * intent.
 */
export const NO_DIRECTORY_MATCH =
  'No user matching this criteria has been found. Please ensure the user you are looking for has been granted the ILCR Submitter role.'

/**
 * `ilcr.user-lookup.enabled` is false in every environment today, and a disabled controller leaves
 * no route at all — so the endpoint answers 404, indistinguishable from a broken path. Reported as
 * a plain not-yet-available state rather than an error, because for now it is the expected one.
 */
export const DIRECTORY_DISABLED =
  'Directory search is not enabled in this environment yet, so users cannot be looked up here.'

const LOOKUP_FAILED = 'The user directory could not be searched.'

const IDP_ITEMS = [
  { id: IDP_IDIR, label: 'IDIR' },
  { id: IDP_BCEID_BUSINESS, label: 'BCeID Business' },
] as const

type IdpItem = (typeof IDP_ITEMS)[number]

/**
 * The IDIR directory answers a contains-search over any one of these; BCeID accepts only the user
 * ID, so the selector renders for IDIR alone. Restores the legacy dialog's name criteria
 * (users.xhtml:110-147) that the first cut had narrowed to userId-only.
 */
const CRITERION_ITEMS = [
  { id: 'userId', label: 'User ID', hint: 'user ID' },
  { id: 'lastName', label: 'Last name', hint: 'last name' },
  { id: 'firstName', label: 'First name', hint: 'first name' },
] as const

type CriterionItem = (typeof CRITERION_ITEMS)[number]

/** A candidate reads as the person first, then the username the administrator recognises. */
const candidateLabel = (user: DirectoryUser | null) =>
  user ? (user.displayName ? `${user.displayName} (${user.idpUsername})` : user.idpUsername) : ''

type DirectoryPickerProps = {
  readonly selected: DirectoryUser | null
  readonly disabled?: boolean
  readonly onSelect: (user: DirectoryUser | null) => void
  /**
   * Directory failures surface page-level, where every other message on this screen lives
   * (FLD-001) — but on a channel of their own: the picker clears it on a later successful search,
   * and that clear must never be able to erase an unrelated write failure (AC6, and ERR-002's
   * standing instruction in particular).
   */
  readonly onError: (message: string | null) => void
}

/**
 * Type-ahead over the ministry directory (UC-USR-001 S01). The two providers answer different
 * shapes of question — IDIR a contains-search over user ID or name, BCeID business an exact lookup
 * — so the request branches on the selected provider and criterion rather than sending every
 * criterion and letting the server reject it (400 `error.user.lookup.parameter`).
 *
 * <p>Replaces the legacy multi-field search dialog (users.xhtml:110-147). Its blank "list everyone"
 * search has no equivalent: the directory requires a criterion, which is recorded as deviation (B)
 * against Story 2.3 and tracked outside this screen.
 */
const DirectoryPicker: FC<DirectoryPickerProps> = ({ selected, disabled, onSelect, onError }) => {
  const [idp, setIdp] = useState<string>(IDP_IDIR)
  const [criterion, setCriterion] = useState<CriterionItem>(CRITERION_ITEMS[0])
  const [items, setItems] = useState<DirectoryUser[]>([])
  const [noMatch, setNoMatch] = useState(false)
  // Latched: once the route has answered 404 the feature is off for the whole session, so further
  // keystrokes must not keep firing searches that can only 404 again.
  const [directoryOff, setDirectoryOff] = useState(false)

  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  // Monotonic search token: only the newest dispatched search may populate the list, so a slower
  // earlier response cannot overwrite fresher suggestions.
  const searchSeqRef = useRef(0)
  // Latest chosen label, set synchronously on selection: Carbon follows a selection with an
  // onInputChange carrying that label (the schedule11 idiom), and searching for it is a guaranteed
  // miss that would render the no-match note under a successful pick.
  const selectedLabelRef = useRef<string>(candidateLabel(selected))

  /**
   * Nothing scheduled or in flight may land after this. Bumping the token alone is not enough: a
   * pending debounce timer claims its token at FIRE time, so it would make itself the newest
   * search and dispatch criteria captured from a stale render (the old provider, in particular).
   */
  const cancelPending = () => {
    searchSeqRef.current += 1
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }
  }

  useEffect(() => cancelPending, [])

  // Below this the server would answer 400 rather than search, so the request is not worth making.
  const isSearchable = (term: string) =>
    idp === IDP_IDIR ? term.length >= MIN_IDIR_CRITERION : term.length > 0

  const runSearch = (query: string) => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }
    const term = query.trim()
    if (directoryOff || !isSearchable(term)) {
      // Invalidate any in-flight search too: clearing the field must not leave older suggestions
      // arriving behind it.
      searchSeqRef.current += 1
      setItems([])
      setNoMatch(false)
      return
    }
    // The note reflects the last SETTLED search: fresh typing withdraws it immediately, so a miss
    // on a partial term (every intermediate keystroke of an exact-match BCeID ID is one) cannot
    // stand as an instruction while the administrator is still typing.
    setNoMatch(false)
    // `userId` is a criterion both directories accept; the name criteria are IDIR-only, and the
    // selector that chooses them renders only there. Sending firstName/lastName under BCeID, or
    // userGuid under IDIR, is a 400 by design, so neither is ever sent.
    const param = idp === IDP_IDIR ? criterion.id : 'userId'
    timerRef.current = setTimeout(() => {
      const seq = ++searchSeqRef.current
      apiService
        .getAxiosInstance()
        .get<DirectoryUser[]>(LOOKUP_PATH, { params: { idp, [param]: term } })
        .then((response) => {
          if (seq !== searchSeqRef.current) return
          setItems(response.data)
          setNoMatch(response.data.length === 0)
          onError(null)
        })
        .catch((error: unknown) => {
          if (seq !== searchSeqRef.current) return
          setItems([])
          setNoMatch(false)
          if ((error as { response?: { status?: number } }).response?.status === 404) {
            // A timer scheduled while this request was in flight must not fire one more doomed
            // search after the latch closes.
            cancelPending()
            setDirectoryOff(true)
            return
          }
          onError(extractDetail(error) || LOOKUP_FAILED)
        })
    }, DEBOUNCE_MS)
  }

  const onIdpChange = (next: string) => {
    // The criteria differ per provider, so results carried across would answer the previous
    // question — and so would a pending or in-flight search, hence the cancel.
    cancelPending()
    setIdp(next)
    setCriterion(CRITERION_ITEMS[0])
    setItems([])
    setNoMatch(false)
    selectedLabelRef.current = ''
    onSelect(null)
  }

  const onCriterionChange = (next: CriterionItem) => {
    // A new criterion is a new question; the chosen user is kept — changing how to search must not
    // discard the assignments already loaded (AC6).
    cancelPending()
    setCriterion(next)
    setItems([])
    setNoMatch(false)
  }

  return (
    <div className="mill-associations__picker">
      <Dropdown<IdpItem>
        id="directory-idp"
        titleText="Identity provider"
        label="Select"
        items={IDP_ITEMS as unknown as IdpItem[]}
        itemToString={(item) => item?.label ?? ''}
        selectedItem={IDP_ITEMS.find((item) => item.id === idp) ?? null}
        disabled={disabled || directoryOff}
        onChange={({ selectedItem }) => onIdpChange(selectedItem?.id ?? IDP_IDIR)}
      />
      {idp === IDP_IDIR && (
        <Dropdown<CriterionItem>
          id="directory-criterion"
          titleText="Search by"
          label="Search by"
          items={CRITERION_ITEMS as unknown as CriterionItem[]}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={criterion}
          disabled={disabled || directoryOff}
          onChange={({ selectedItem }) => onCriterionChange(selectedItem ?? CRITERION_ITEMS[0])}
        />
      )}
      <ComboBox<DirectoryUser>
        // Remounting clears the typed text whenever the question changes: stale text over an empty
        // result list would otherwise read as "searched, nothing found" under the new provider.
        key={`${idp}:${criterion.id}`}
        id="directory-user"
        titleText={idp === IDP_IDIR ? criterion.label : 'User ID'}
        helperText={
          idp === IDP_IDIR
            ? `Type at least ${MIN_IDIR_CRITERION} characters of the IDIR ${criterion.hint}.`
            : 'Type the full BCeID Business user ID.'
        }
        placeholder="Type to search"
        disabled={disabled || directoryOff}
        items={items}
        selectedItem={selected}
        itemToString={candidateLabel}
        // Server-side filtered: every fetched candidate is shown, with no second client-side filter.
        shouldFilterItem={() => true}
        onChange={({ selectedItem }) => {
          // A selection settles the search: anything pending or in flight would only repaint
          // suggestions — or a spurious no-match — under the user just chosen.
          cancelPending()
          selectedLabelRef.current = candidateLabel(selectedItem ?? null)
          setNoMatch(false)
          onSelect(selectedItem ?? null)
        }}
        onInputChange={(text) => {
          // Carbon echoes the chosen label into onInputChange right after a selection; searching
          // for that label is a guaranteed miss. Typing deliberately does NOT drop the chosen user,
          // unlike the schedule type-aheads: there the input IS the value, so unmatched text has to
          // clear it; here the value is the candidate record, which only a selection can set —
          // free text can never leak a GUID. Clearing on keystroke would instead discard the
          // loaded assignments the moment the administrator started looking someone else up,
          // which is what AC6 forbids.
          if (text && text === selectedLabelRef.current) return
          runSearch(text)
        }}
      />
      {/* role="status": these appear after the fact, so without a live region a screen-reader user
          gets silence on exactly the states the notes exist to explain (NFR1). */}
      {directoryOff && (
        <p className="mill-associations__picker-note" role="status">
          {DIRECTORY_DISABLED}
        </p>
      )}
      {noMatch && !directoryOff && (
        <p className="mill-associations__picker-note" role="status">
          {NO_DIRECTORY_MATCH}
        </p>
      )}
    </div>
  )
}

export default DirectoryPicker
