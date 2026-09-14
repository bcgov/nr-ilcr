import type { FC } from 'react'
import { useEffect, useRef, useState } from 'react'
import { Button, Column, FilterableMultiSelect, Grid, Select, SelectItem } from '@carbon/react'
import { Reset } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import NotificationColumn from '@/components/core/NotificationColumn'
import { extractDetail } from '@/utils/error'
import { extractBlobMessages, triggerDownload } from '@/utils/download'
import type MillSummary from '@/interfaces/MillSummary'
import type { MessageInfo } from '@/interfaces/Schedule1Response'
import type ReportingYear from '@/interfaces/ReportingYear'
import {
  buildExtractRequest,
  matchesPrefix,
  millNumberEcho,
  millOptionLabel,
  SCHEDULE_OPTIONS,
} from '@/components/dataExtract/validation'
import './index.scss'

const api = () => apiService.getAxiosInstance()
const MILLS_PATH = '/v1/mills'
const YEARS_PATH = '/v1/reporting-years'
const EXTRACT_PATH = '/v1/reports/data-extract'
const MESSAGES_PATH = '/v1/messages'

const MILLS_FAILED = 'Unable to load the mills.'
const YEARS_FAILED = 'Unable to load the reporting years.'
const EXTRACT_FAILED = 'Unable to generate the data extract.'

/**
 * SUC-001. Legacy queued this sentence after it had already streamed the file, so RENDER_RESPONSE
 * never ran and no user ever saw it; the rebuild shows it. Its text comes from the server bundle
 * through the allowlisted messages endpoint, never from a literal here — a page hardcoding a server
 * string is a recorded defect on this project. The misspelled key is legacy's own.
 */
const SUCCESS_KEY = 'dataExtractedSuccesfullyInfoMsg'

/**
 * The one permitted client literal, and only as the last resort: it mirrors a bundle key that DOES
 * exist, and is reached solely when the lookup itself fails. A generated file with a silent page
 * would be worse than a mirrored sentence.
 */
const SUCCESS_FALLBACK = 'Data extraction successfully.'

/**
 * `dataExtract<yyyyMMdd>.csv`, built HERE rather than parsed off the response's
 * `Content-Disposition` — the idiom both shipped download pages already follow. The backend derives
 * the same name on a clock pinned to Pacific time, so the date is taken in that zone here too: an
 * administrator in Ottawa or on a UTC laptop would otherwise save a differently dated file for
 * several hours of every day, not only across midnight. `en-CA` puts the parts in y-m-d order.
 */
const PACIFIC_DATE = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Vancouver',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const extractFilename = (now: Date): string =>
  `dataExtract${PACIFIC_DATE.format(now).replaceAll('-', '')}.csv`

const SAVE_FAILED =
  'The data extract was generated but could not be saved by this browser. Please try again.'

/**
 * The select-all row. Carbon recognises it by the presence of the `isSelectAll` key, lifts it out of
 * the sortable items, and pins it to the top of the menu — so it is an item here, not a prop.
 */
const SELECT_ALL = { isSelectAll: true, label: 'Select all' } as const

/**
 * A menu row. Mill rows carry their `millId` so a selection round-trips by IDENTITY, not by display
 * string: `MILL_NUMBER` and `MILL_NAME` are both nullable with no uniqueness constraint, so two
 * rows can share a label, and keying on the label would tick and submit both.
 */
type PickerItem = {
  readonly label: string
  readonly millId?: number
  readonly isSelectAll?: boolean
}

const itemLabel = (item: PickerItem | null) => item?.label ?? ''

/**
 * Prefix filtering over the option label, applied to the real options only — the select-all row is
 * never filtered out, or selecting all of a filtered list would become unreachable the moment the
 * query stopped matching the word "Select".
 */
const filterByPrefix = (items: PickerItem[], { inputValue }: { inputValue: string | null }) =>
  items.filter(
    (item) => item.isSelectAll === true || !inputValue || matchesPrefix(item.label, inputValue),
  )

/**
 * Identity sort: the server's order IS the order.
 *
 * Carbon sorts the menu by `localeCompare` with `numeric: true` by default, which happens to
 * reproduce both orderings this page needs — but only by accident of the labels leading with their
 * numbers. That correctness would disappear silently the day a label changed shape. Declaring the
 * identity makes the API responses authoritative: reporting years descending, mills by mill number
 * ascending, schedules 1 through 11. Legacy's `selectCheckboxMenu` never reordered either.
 */
const keepServerOrder = <T,>(items: T[]) => items

/**
 * Data Extract selection (UC-EXT-001, legacy `extractData.xhtml` / `ExtractDataMB`). An
 * administrator picks a reporting-year range, any number of mills and any of the eleven schedules,
 * and generates a CSV of what was reported.
 *
 * <p>The page takes NO Home mill/year working context — deliberately, and in its DATA path only.
 * `initMills()`/`initPeriods()` never read the session's mill or year, and the render guard that
 * would have required one is commented out in the legacy view (`:11-24`, `:26`). The tombstone above
 * is shared chrome, exactly as legacy's submenu template displayed the working context above every
 * page including this one; it says nothing about what the extract covers, and nothing here reads it.
 *
 * <p>Validation belongs to the server, all of it. The page submits whatever is on screen — including
 * nothing at all — and renders every message that comes back, together. That is the point of the
 * screen rather than an implementation shortcut: see `validation.ts`.
 */
const DataExtract: FC = () => {
  const [mills, setMills] = useState<MillSummary[]>([])
  const [years, setYears] = useState<ReportingYear[]>([])
  const [startYear, setStartYear] = useState('')
  const [endYear, setEndYear] = useState('')
  const [selectedMills, setSelectedMills] = useState<readonly MillSummary[]>([])
  const [selectedSchedules, setSelectedSchedules] = useState<readonly string[]>([])
  const [loadErrors, setLoadErrors] = useState<readonly string[]>([])
  const [messages, setMessages] = useState<readonly string[]>([])
  const [busy, setBusy] = useState(false)
  /**
   * Whether the last submit produced a file. Separate from `successText`, which is the sentence to
   * show and is fetched once on mount: the banner must appear only after a download, not because
   * the lookup resolved.
   */
  const [generated, setGenerated] = useState(false)
  const [successText, setSuccessText] = useState(SUCCESS_FALLBACK)
  /**
   * The polite live-region text for a submit outcome. A separate channel from `messages`, which
   * paints the visible banners: a freshly mounted notification is not reliably announced, so the
   * region below is mounted for the life of the page and this string feeds it.
   */
  const [status, setStatus] = useState('')
  /**
   * Whether the page is still mounted, for the submit's late callbacks. The two load effects carry
   * their own `active` flag; the submit is started from a click, not an effect, so it reads this.
   */
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  /**
   * One banner per DISTINCT load failure. Both lookups can fail with the same problem `detail` (one
   * gateway answering for both), and the banners are keyed by their text.
   */
  const addLoadError = (text: string) =>
    setLoadErrors((current) => (current.includes(text) ? current : [...current, text]))

  useEffect(() => {
    let active = true
    api()
      .get<MillSummary[]>(MILLS_PATH)
      .then((response) => {
        if (active) {
          setMills(response.data)
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          addLoadError(extractDetail(cause) || MILLS_FAILED)
        }
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true
    api()
      .get<ReportingYear[]>(YEARS_PATH)
      .then((response) => {
        if (!active) {
          return
        }
        setYears(response.data)
        // Both years default to the LATEST opened period (ExtractDataMB.java:101-104). The list is
        // served descending, so that is its first element; an empty list leaves both pickers on
        // their placeholder rather than inventing a year.
        const latest = response.data[0]
        if (latest) {
          setStartYear(String(latest.reportYear))
          setEndYear(String(latest.reportYear))
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          addLoadError(extractDetail(cause) || YEARS_FAILED)
        }
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true
    api()
      .get<MessageInfo>(MESSAGES_PATH, { params: { key: SUCCESS_KEY } })
      .then((response) => {
        if (active && response.data.text) {
          setSuccessText(response.data.text)
        }
      })
      .catch(() => {
        // Deliberately silent, and NOT a load-error banner: the page is fully usable without this
        // sentence, and the state already holds the mirrored fallback. Announcing a failed lookup
        // for a confirmation the administrator has not yet earned would be noise.
      })
    return () => {
      active = false
    }
  }, [])

  const millItems: PickerItem[] = [
    SELECT_ALL,
    ...mills.map((mill) => ({ label: millOptionLabel(mill), millId: mill.millId })),
  ]
  const scheduleItems: PickerItem[] = [
    SELECT_ALL,
    ...SCHEDULE_OPTIONS.map((name) => ({ label: name })),
  ]

  /**
   * The selections, expressed as the CHOSEN SUBSET OF THE OPTION LIST rather than as the picker's
   * own selection array.
   *
   * Carbon reports selections in the order they were clicked; legacy submitted its checkbox values
   * in component order. Deriving from the option list restores that, so the summary reads in the
   * same order as the menu the administrator was just looking at, whichever order they ticked.
   */
  const selectedMillsInOptionOrder = mills.filter((mill) =>
    selectedMills.some((chosen) => chosen.millId === mill.millId),
  )
  const selectedSchedulesInOptionOrder = SCHEDULE_OPTIONS.filter((name) =>
    selectedSchedules.includes(name),
  )

  /** Any change to any picker drops standing messages — they describe a selection that has moved. */
  const changed = () => {
    setMessages([])
    setStatus('')
    // The success banner describes a file built from a selection that has now moved, so it goes the
    // same way the refusals do. The downloaded file is untouched — only the claim on screen goes.
    setGenerated(false)
  }

  const changeStartYear = (value: string) => {
    setStartYear(value)
    changed()
  }

  const changeEndYear = (value: string) => {
    setEndYear(value)
    changed()
  }

  const changeMills = (items: readonly PickerItem[]) => {
    const ids = new Set(items.map((item) => item.millId))
    setSelectedMills(mills.filter((mill) => ids.has(mill.millId)))
    changed()
  }

  const changeSchedules = (items: readonly PickerItem[]) => {
    const labels = new Set(items.map((item) => item.label))
    setSelectedSchedules(SCHEDULE_OPTIONS.filter((name) => labels.has(name)))
    changed()
  }

  /**
   * Clear empties all four pickers — and does NOT put the years back to the latest-year default.
   * Legacy's `clear()` nulls all four fields and does not re-run `initPeriods()`
   * (`ExtractDataMB.java:241-246`). The Print Schedules page's Clear restores its defaults instead;
   * this is a different screen and must not be made to match it.
   */
  const clear = () => {
    setStartYear('')
    setEndYear('')
    setSelectedMills([])
    setSelectedSchedules([])
    changed()
  }

  const generate = () => {
    // Submitted unconditionally, empty selection included. The server owns every check and reports
    // them together; refusing here would hide the response this screen exists to show.
    //
    // While the request is in flight every control is disabled (see `busy` below) — legacy blocked
    // the whole panel with <p:blockUI trigger="generateBtn"> (extractData.xhtml:160) — so the
    // messages that come back always describe the selection still on screen.
    setBusy(true)
    setMessages([])
    setGenerated(false)
    setStatus('Generating the data extract.')
    api()
      .post(
        EXTRACT_PATH,
        buildExtractRequest({
          startYear,
          endYear,
          mills: selectedMillsInOptionOrder,
          schedules: selectedSchedulesInOptionOrder,
        }),
        // A success IS the file. An error body arrives as a Blob for the same reason, which is why
        // the catch below reads it through extractBlobMessages rather than extractMessages.
        { responseType: 'blob' },
      )
      .then((response) => {
        // A page the administrator has already left must not have a file dropped on it, the same
        // call the Print Schedules download makes when its context has moved on.
        if (!mountedRef.current) {
          return
        }
        // Saved INSIDE .then, not deferred past it: `busy` releases in .finally, so deferring the
        // download would drop the panel lock while the file was still being handed to the browser.
        // A save the browser refuses (a blocked object URL) is not a failed BUILD, so it gets its
        // own sentence rather than falling into the catch and blaming the server for a file it made.
        try {
          triggerDownload(response.data as Blob, extractFilename(new Date()))
        } catch {
          setMessages([SAVE_FAILED])
          setStatus(SAVE_FAILED)
          return
        }
        setGenerated(true)
        setStatus(`The data extract has been generated. ${successText}`)
      })
      .catch(async (cause: unknown) => {
        // Every refusal, not just the first: the 400 gate reports all of them together, and that
        // accumulation is the whole point of the screen.
        const texts = await extractBlobMessages(cause, EXTRACT_FAILED)
        if (!mountedRef.current) {
          return
        }
        setMessages(texts)
        setStatus(`The data extract could not be generated. ${texts.join(' ')}`)
      })
      .finally(() => {
        if (mountedRef.current) {
          setBusy(false)
        }
      })
  }

  const summaryRow = (label: string, value: string) => (
    <div className="data-extract__summary-row" key={label}>
      <span className="data-extract__summary-label">Selected {label}:</span>
      <span className="data-extract__summary-value" data-testid={`data-extract-summary-${label}`}>
        {value}
      </span>
    </div>
  )

  return (
    <div className="app-page">
      <ScheduleTombstone title="Data Extract" />
      <Grid fullWidth className="app-page__body">
        {loadErrors.map((message) => (
          <NotificationColumn key={message} kind="error" title="Error" subtitle={message} />
        ))}
        {generated && (
          // Severity carried by BOTH the kind and the title word, never colour alone — and the
          // title is a word, not a restatement of the sentence below it.
          <NotificationColumn
            kind="success"
            title="Generated"
            subtitle={successText}
            testId="data-extract-success"
          />
        )}
        {messages.map((message) => (
          // One banner per message, each a direct child of the page Grid because NotificationColumn
          // IS a Column. Keys are safe: extractMessages deduplicates.
          <NotificationColumn
            key={message}
            kind="error"
            title="Cannot generate"
            subtitle={message}
            testId="data-extract-message"
          />
        ))}
        <Column sm={4} md={8} lg={16} className="data-extract">
          {/*
            Mounted for the LIFE of the page, always, even while empty — that is what makes it work.
            A live region has to already be in the accessibility tree for a change inside it to be
            announced, so mounting it alongside a banner would announce nothing. Named, because
            Carbon's InlineNotification also renders role="status".
          */}
          <div
            className="cds--visually-hidden"
            role="status"
            aria-live="polite"
            aria-label="Data extract status"
          >
            {status}
          </div>

          <section className="data-extract__panel" aria-labelledby="data-extract-select-heading">
            <h2 className="data-extract__panel-heading" id="data-extract-select-heading">
              Select Report Data
            </h2>
            <div className="data-extract__fields">
              <Select
                id="data-extract-start-year"
                labelText="Start Year:"
                value={startYear}
                disabled={busy}
                onChange={(event) => changeStartYear(event.target.value)}
              >
                {/* Legacy's own no-selection item (xhtml:43). It stays selectable, which is what
                    lets Clear put the picker back to empty — and therefore what makes the
                    blank-required state reachable at all. */}
                <SelectItem value="" text="Start Year" />
                {years.map((year) => (
                  <SelectItem
                    key={year.reportYear}
                    value={String(year.reportYear)}
                    text={String(year.reportYear)}
                  />
                ))}
              </Select>

              <Select
                id="data-extract-end-year"
                labelText="End Year:"
                value={endYear}
                disabled={busy}
                onChange={(event) => changeEndYear(event.target.value)}
              >
                <SelectItem value="" text="End Year" />
                {years.map((year) => (
                  <SelectItem
                    key={year.reportYear}
                    value={String(year.reportYear)}
                    text={String(year.reportYear)}
                  />
                ))}
              </Select>

              <FilterableMultiSelect
                id="data-extract-mills"
                titleText="Mills:"
                placeholder="Select Mills"
                items={millItems}
                itemToString={itemLabel}
                selectedItems={millItems.filter(
                  (item) =>
                    item.millId !== undefined &&
                    selectedMillsInOptionOrder.some((mill) => mill.millId === item.millId),
                )}
                disabled={busy}
                // Carbon's default hoists selected options to the top of the menu on reopen, which
                // would destroy the mill-number ordering above.
                selectionFeedback="fixed"
                sortItems={keepServerOrder}
                filterItems={filterByPrefix}
                onChange={({ selectedItems }) => changeMills(selectedItems ?? [])}
              />

              <FilterableMultiSelect
                id="data-extract-schedules"
                titleText="Schedules:"
                placeholder="Select Schedules"
                items={scheduleItems}
                itemToString={itemLabel}
                selectedItems={scheduleItems.filter(
                  (item) => item.isSelectAll !== true && selectedSchedules.includes(item.label),
                )}
                disabled={busy}
                selectionFeedback="fixed"
                sortItems={keepServerOrder}
                filterItems={filterByPrefix}
                onChange={({ selectedItems }) => changeSchedules(selectedItems ?? [])}
              />
            </div>

            <div className="data-extract__actions">
              <Button kind="secondary" renderIcon={Reset} disabled={busy} onClick={clear}>
                Clear
              </Button>
            </div>
          </section>

          <section className="data-extract__panel" aria-labelledby="data-extract-summary-heading">
            <h2 className="data-extract__panel-heading" id="data-extract-summary-heading">
              Selected Report Data Summary
            </h2>
            <div className="data-extract__summary">
              {summaryRow('Start Year', startYear)}
              {summaryRow('End Year', endYear)}
              {summaryRow('Mills', millNumberEcho(selectedMillsInOptionOrder))}
              {summaryRow('Schedules', selectedSchedulesInOptionOrder.join(', '))}
            </div>

            <div className="data-extract__actions">
              <Button disabled={busy} onClick={generate}>
                Generate Report
              </Button>
            </div>
          </section>
        </Column>
      </Grid>
    </div>
  )
}

export default DataExtract
