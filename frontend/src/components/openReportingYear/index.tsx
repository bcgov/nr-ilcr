import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { Button, Column, Grid, Modal, Select, SelectItem } from '@carbon/react'
import { ArrowRight } from '@carbon/icons-react'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import NotificationColumn from '@/components/core/NotificationColumn'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import type {
  OpenReportingYearResponse,
  ReportingYearAdminView,
} from '@/interfaces/ReportingYearAdmin'
import './index.scss'

const api = () => apiService.getAxiosInstance()
const CONFIRM_PROMPT = 'Please confirm you would like to create a new reporting year?'
const SELECT_REQUIRED = 'Please select the reporting year to setup ILCR.'

/**
 * Open Reporting Year (Story 24.1 / UC-RY-001). Admin-only surface: on the recurring path it opens the
 * next year (max + 1); on first-time setup the administrator picks a starting year from the bounded
 * dropdown (BR-07). The action is confirmed (CFM-001) before any server call, so declining or
 * cancelling makes no request (S05/S06). Reachable only via the admin-gated Administration menu; the
 * API independently enforces the ADMIN-only OPEN_REPORTING_YEAR action (403), which is the boundary.
 */
const OpenReportingYear: FC = () => {
  const [view, setView] = useState<ReportingYearAdminView | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedYear, setSelectedYear] = useState('')
  const [selectError, setSelectError] = useState<string | null>(null)

  const [confirming, setConfirming] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  const load = () => {
    api()
      .get<ReportingYearAdminView>('/v1/admin/reporting-years')
      .then((response) => setView(response.data))
      .catch((error: unknown) =>
        setLoadError(extractDetail(error) || 'Unable to load the reporting years.'),
      )
  }

  useEffect(load, [])

  // Validate the first-time selection (FLD-001) BEFORE opening the confirmation — a blank selection
  // never reaches the server.
  const requestOpen = () => {
    setSaveMessage(null)
    setSaveError(null)
    setSelectError(null)
    if (view?.firstTime && selectedYear === '') {
      setSelectError(SELECT_REQUIRED)
      return
    }
    setConfirming(true)
  }

  // Decline (S05) / cancel first-time (S06): close the prompt, make no server call.
  const cancel = () => setConfirming(false)

  const confirmOpen = () => {
    setConfirming(false)
    setSaving(true)
    const body = view?.firstTime ? { year: Number(selectedYear) } : {}
    api()
      .post<OpenReportingYearResponse>('/v1/admin/reporting-years', body)
      .then((response) => {
        setSaveMessage(response.data.message)
        setSelectedYear('')
        load()
      })
      .catch((error: unknown) =>
        setSaveError(extractDetail(error) || 'The reporting year could not be opened.'),
      )
      .finally(() => setSaving(false))
  }

  return (
    <div className="app-page schedule-page">
      <ScheduleTombstone title="Open Reporting Year" />
      <Grid fullWidth className="app-page__body">
        {loadError && <NotificationColumn kind="error" title="Error" subtitle={loadError} />}
        {saveMessage && <NotificationColumn kind="success" title="Saved" subtitle={saveMessage} />}
        {saveError && <NotificationColumn kind="error" title="Error" subtitle={saveError} />}
        <Column sm={4} md={8} lg={16}>
          {view && view.firstTime && (
            <Select
              id="reporting-year-start"
              labelText="Reporting Year"
              value={selectedYear}
              invalid={Boolean(selectError)}
              invalidText={selectError ?? undefined}
              onChange={(event) => setSelectedYear(event.target.value)}
            >
              <SelectItem value="" text="Select a year" />
              {view.selectableStartYears.map((year) => (
                <SelectItem key={year} value={String(year)} text={String(year)} />
              ))}
            </Select>
          )}

          {view && !view.firstTime && view.nextYear !== null && (
            <p>
              The next reporting year to open is <strong>{view.nextYear}</strong>.
            </p>
          )}

          <Button
            className="open-reporting-year__action"
            disabled={saving || view === null}
            renderIcon={ArrowRight}
            onClick={requestOpen}
          >
            Open Reporting Year
          </Button>
        </Column>
      </Grid>

      <Modal
        open={confirming}
        modalHeading="Open Reporting Year"
        primaryButtonText="Yes"
        secondaryButtonText="No"
        onRequestClose={cancel}
        onSecondarySubmit={cancel}
        onRequestSubmit={confirmOpen}
      >
        <p>{CONFIRM_PROMPT}</p>
      </Modal>
    </div>
  )
}

export default OpenReportingYear
