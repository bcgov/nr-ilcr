import type { FC } from 'react'
import { useState } from 'react'
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
// parity); the backend renders the in-scope sections (5/6/7A/7B/9/11) and accepts the rest for
// forward-compatibility (no section rendered yet — see PrintRequest).
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

const SCHEDULES: { readonly key: ScheduleFlag; readonly label: string }[] = [
  { key: 'schedule1', label: 'Schedule 1' },
  { key: 'schedule2', label: 'Schedule 2' },
  { key: 'schedule3', label: 'Schedule 3' },
  { key: 'schedule4', label: 'Schedule 4' },
  { key: 'schedule5', label: 'Schedule 5' },
  { key: 'schedule6', label: 'Schedule 6' },
  { key: 'schedule7a', label: 'Schedule 7A' },
  { key: 'schedule7b', label: 'Schedule 7B' },
  { key: 'schedule8', label: 'Schedule 8' },
  { key: 'schedule9', label: 'Schedule 9' },
  { key: 'schedule10', label: 'Schedule 10' },
  { key: 'schedule11', label: 'Schedule 11' },
]

type OptionFlag = 'printScheduleInformation' | 'printComments' | 'printMillInformationReport'

const OPTIONS: { readonly key: OptionFlag; readonly label: string }[] = [
  { key: 'printScheduleInformation', label: 'Schedule information' },
  { key: 'printComments', label: 'Comments' },
  { key: 'printMillInformationReport', label: 'Mill information report' },
]

const noneSelected = <T extends string>(keys: readonly { key: T }[]): Record<T, boolean> =>
  Object.fromEntries(keys.map((k) => [k.key, false])) as Record<T, boolean>

/**
 * Print Schedules selection page (Epic 20.3). Mirrors the legacy PrintSchedulesMB screen: pick any of
 * the twelve schedules (+ "select all") and the print options, then download the combined bookmarked
 * PDF the backend assembles at {@code POST /api/v1/reports/print} for the working mill/year. Printing
 * is read-only for every role (BR-01); the server is authoritative for selection validation.
 */
const PrintSchedules: FC = () => {
  const { millId, year, contextMissing } = useScheduleContextGuard()

  const [schedules, setSchedules] = useState<Record<ScheduleFlag, boolean>>(() =>
    noneSelected(SCHEDULES),
  )
  const [options, setOptions] = useState<Record<OptionFlag, boolean>>(() => noneSelected(OPTIONS))
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const allSelected = SCHEDULES.every((s) => schedules[s.key])
  const anyScheduleSelected = SCHEDULES.some((s) => schedules[s.key])
  const anyOptionSelected = OPTIONS.some((o) => options[o.key])
  const canGenerate = !busy && !contextMissing && anyScheduleSelected && anyOptionSelected

  function toggleAll(checked: boolean) {
    setSchedules(
      Object.fromEntries(SCHEDULES.map((s) => [s.key, checked])) as Record<ScheduleFlag, boolean>,
    )
  }

  async function handleGenerate() {
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      // allSchedules is the legacy "all" shortcut (BR-07); the individual flags carry the selection.
      const body = { ...schedules, allSchedules: allSelected, ...options }
      const response = await apiService
        .getAxiosInstance()
        .post(`${PRINT_PATH}?millId=${String(millId)}&year=${String(year)}`, body, {
          responseType: 'blob',
        })
      triggerDownload(response.data as Blob, PDF_FILENAME)
      setMessage('Your Print Schedules PDF has been generated and downloaded.')
    } catch (err: unknown) {
      // A 400/404/409 problem+json arrives as a Blob under responseType:'blob' — parse it for `detail`.
      setError((await extractBlobDetail(err)) ?? 'Unable to generate the PDF. Please try again.')
    } finally {
      setBusy(false)
    }
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
                    labelText={s.label}
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
                    labelText={o.label}
                    checked={options[o.key]}
                    onChange={(_event, { checked }) =>
                      setOptions((prev) => ({ ...prev, [o.key]: checked }))
                    }
                  />
                ))}
              </FormGroup>

              <Button
                renderIcon={Printer}
                disabled={!canGenerate}
                onClick={() => void handleGenerate()}
              >
                {busy ? 'Generating…' : 'Generate PDF'}
              </Button>
            </>
          )}
        </Column>
      </Grid>
    </div>
  )
}

export default PrintSchedules
