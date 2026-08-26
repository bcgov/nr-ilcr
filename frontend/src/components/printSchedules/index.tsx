import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { Button, Checkbox, Column, FormGroup, Grid, InlineNotification } from '@carbon/react'
import { Printer } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import { useScheduleContextGuard } from '@/hooks/useScheduleContextGuard'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import { extractBlobDetail, triggerDownload } from '@/utils/download'
import './index.scss'

const PRINT_PATH = '/v1/reports/print'
const PDF_FILENAME = 'schedules_print.pdf'

// The twelve schedule flags in the legacy PrintSchedulesMB order. Every flag is offered (legacy
// parity); the backend renders every schedule section except the still-deferred Mill Information
// report.
type ScheduleFlag =
  | 'schedule1'
  | 'schedule2'
  | 'schedule3'
  | 'schedule4'
  | 'schedule5'
  | 'schedule6'
  | 'schedule7a'
  | 'schedule7b'
  | 'schedule8'
  | 'schedule9'
  | 'schedule10'
  | 'schedule11'

// `renderable` = the backend produces a section today. Deferred choices are shown for legacy parity
// but disabled with a "(coming soon)" note, so a selection can't silently no-op.
const SCHEDULES: {
  readonly key: ScheduleFlag
  readonly label: string
  readonly renderable: boolean
}[] = [
  { key: 'schedule1', label: 'Schedule 1', renderable: true },
  { key: 'schedule2', label: 'Schedule 2', renderable: true },
  { key: 'schedule3', label: 'Schedule 3', renderable: true },
  { key: 'schedule4', label: 'Schedule 4', renderable: true },
  { key: 'schedule5', label: 'Schedule 5', renderable: true },
  { key: 'schedule6', label: 'Schedule 6', renderable: true },
  { key: 'schedule7a', label: 'Schedule 7A', renderable: true },
  { key: 'schedule7b', label: 'Schedule 7B', renderable: true },
  { key: 'schedule8', label: 'Schedule 8', renderable: true },
  { key: 'schedule9', label: 'Schedule 9', renderable: true },
  { key: 'schedule10', label: 'Schedule 10', renderable: true },
  { key: 'schedule11', label: 'Schedule 11', renderable: true },
]

type OptionFlag = 'printScheduleInformation' | 'printComments' | 'printMillInformationReport'

const OPTIONS: { readonly key: OptionFlag; readonly label: string; readonly available: boolean }[] =
  [
    { key: 'printScheduleInformation', label: 'Schedule information', available: true },
    { key: 'printComments', label: 'Comments', available: true },
    // Deferred to Epic 19 (no Mill Information backend yet).
    { key: 'printMillInformationReport', label: 'Mill information report', available: false },
  ]

const RENDERABLE_SCHEDULES = SCHEDULES.filter((s) => s.renderable)
const AVAILABLE_OPTIONS = OPTIONS.filter((o) => o.available)
const comingSoon = (label: string) => `${label} (coming soon)`

const noneSelected = <T extends string>(keys: readonly { key: T }[]): Record<T, boolean> =>
  Object.fromEntries(keys.map((k) => [k.key, false])) as Record<T, boolean>

// The S06 default selection: Schedule Information pre-checked, everything else cleared. Used on first
// load and by the Clear button.
const defaultOptions = (): Record<OptionFlag, boolean> => ({
  printScheduleInformation: true,
  printComments: false,
  printMillInformationReport: false,
})

/**
 * Print Schedules selection page (Epic 20.3). Mirrors the legacy PrintSchedulesMB screen: pick any of
 * the twelve schedules (+ "select all") and the print options, then download the combined bookmarked
 * PDF the backend assembles at {@code POST /api/v1/reports/print} for the working mill/year. Printing
 * is read-only for every role (BR-01); the server is authoritative for selection validation.
 */
const PrintSchedules: FC = () => {
  const { millId, year, contextMissing, isCurrent } = useScheduleContextGuard()

  const [schedules, setSchedules] = useState<Record<ScheduleFlag, boolean>>(() =>
    noneSelected(SCHEDULES),
  )
  const [options, setOptions] = useState<Record<OptionFlag, boolean>>(defaultOptions)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // On a mill/year change, drop the in-flight lock + banners from the previous context, so a context
    // switch can't leave a stale "Done"/error banner or a stuck Generate button; a late response is
    // separately ignored via isCurrent() (in handleGenerate). The selection itself is intentionally kept.
    // Deliberate reset-on-context-change — the synchronous setState here is the point.
    /* eslint-disable @eslint-react/set-state-in-effect */
    setBusy(false)
    setMessage(null)
    setError(null)
    /* eslint-enable @eslint-react/set-state-in-effect */
  }, [millId, year])

  // "All"/enable guards consider only the renderable schedules and available options — the disabled
  // ones can never be selected, so they must not gate (or be swept into) a generate.
  const allSelected = RENDERABLE_SCHEDULES.every((s) => schedules[s.key])
  const anyScheduleSelected = RENDERABLE_SCHEDULES.some((s) => schedules[s.key])
  const anyOptionSelected = AVAILABLE_OPTIONS.some((o) => options[o.key])
  const canGenerate = !busy && !contextMissing && anyScheduleSelected && anyOptionSelected

  function toggleAll(checked: boolean) {
    setSchedules((prev) => {
      const next = { ...prev }
      for (const s of RENDERABLE_SCHEDULES) {
        next[s.key] = checked
      }
      return next
    })
  }

  async function handleGenerate() {
    // Capture the dispatch-time context guard: if the user switches mill/year while Jasper renders the
    // sections, the late response must NOT download or repaint under the new context (the same
    // stale-response guard the schedule pages apply to their writes).
    const dispatchedCurrent = isCurrent
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      // Send only the individual flags — NOT the legacy allSchedules "all" shortcut: it would expand
      // server-side to all twelve (BR-07), re-including the deferred schedules the UI deliberately
      // disables. The renderable flags already carry the full selectable set.
      const body = { ...schedules, allSchedules: false, ...options }
      const response = await apiService
        .getAxiosInstance()
        .post(`${PRINT_PATH}?millId=${String(millId)}&year=${String(year)}`, body, {
          responseType: 'blob',
        })
      if (!dispatchedCurrent()) {
        return
      }
      triggerDownload(response.data as Blob, PDF_FILENAME)
      setMessage('Your Print Schedules PDF has been generated and downloaded.')
    } catch (err: unknown) {
      if (!dispatchedCurrent()) {
        return
      }
      // With selection validation client-gated, the real-world failure is a valid mill/year that simply
      // has no rows in the ticked schedules → 404 ERR-005. Verbatim "Schedule not found." reads wrong for
      // a print, so special-case it; keep the verbatim problem+json detail for 400/409 (ERR-002/003/004),
      // where the legacy-verbatim-text rule actually applies. (Blob error body → extractBlobDetail.)
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 404) {
        setError('No data to print for the selected schedules.')
      } else {
        setError((await extractBlobDetail(err)) ?? 'Unable to generate the PDF. Please try again.')
      }
    } finally {
      if (dispatchedCurrent()) {
        setBusy(false)
      }
    }
  }

  function handleClear() {
    // Reset to the S06 default: Schedule Information re-checked, all schedules and other options cleared.
    setSchedules(noneSelected(SCHEDULES))
    setOptions(defaultOptions())
    setMessage(null)
    setError(null)
  }

  return (
    <div className="app-page">
      <ScheduleTombstone title="Print Schedules" />
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16} className="print-schedules">
          {contextMissing ? (
            <InlineNotification
              kind="info"
              lowContrast
              hideCloseButton
              title="Select a mill and reporting year"
              subtitle="Choose a mill and reporting year on the Home page to print schedules."
            />
          ) : (
            <>
              {message && (
                <InlineNotification
                  kind="success"
                  lowContrast
                  title="Done"
                  subtitle={message}
                  onCloseButtonClick={() => setMessage(null)}
                />
              )}
              {error && (
                <InlineNotification
                  kind="error"
                  lowContrast
                  title="Print failed"
                  subtitle={error}
                  onCloseButtonClick={() => setError(null)}
                />
              )}

              <FormGroup legendText="Schedules" className="print-schedules__group">
                <Checkbox
                  id="print-select-all"
                  labelText="Select all schedules"
                  checked={allSelected}
                  onChange={(_event, { checked }) => toggleAll(checked)}
                />
                {SCHEDULES.map((s) => (
                  <Checkbox
                    key={s.key}
                    id={`print-${s.key}`}
                    labelText={s.renderable ? s.label : comingSoon(s.label)}
                    disabled={!s.renderable}
                    checked={schedules[s.key]}
                    onChange={(_event, { checked }) =>
                      setSchedules((prev) => ({ ...prev, [s.key]: checked }))
                    }
                  />
                ))}
              </FormGroup>

              <FormGroup legendText="Print options" className="print-schedules__group">
                {OPTIONS.map((o) => (
                  <Checkbox
                    key={o.key}
                    id={`print-${o.key}`}
                    labelText={o.available ? o.label : comingSoon(o.label)}
                    disabled={!o.available}
                    checked={options[o.key]}
                    onChange={(_event, { checked }) =>
                      setOptions((prev) => ({ ...prev, [o.key]: checked }))
                    }
                  />
                ))}
              </FormGroup>

              <div className="print-schedules__actions">
                <Button
                  renderIcon={Printer}
                  disabled={!canGenerate}
                  onClick={() => void handleGenerate()}
                >
                  {busy ? 'Generating…' : 'Generate PDF'}
                </Button>
                <Button kind="secondary" disabled={busy} onClick={handleClear}>
                  Clear
                </Button>
              </div>
            </>
          )}
        </Column>
      </Grid>
    </div>
  )
}

export default PrintSchedules
