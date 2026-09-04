import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { Button, Column, Grid, InlineNotification, Select, SelectItem } from '@carbon/react'
import { Download } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import { extractDetail } from '@/utils/error'
import { assertCompletePdf, extractBlobDetail, triggerDownload } from '@/utils/download'
import type ReportingYear from '@/interfaces/ReportingYear'
import './index.scss'

const api = () => apiService.getAxiosInstance()
const REPORT_PATH = '/v1/reports/mill-information'
const PDF_FILENAME = 'mills_print.pdf'
const YEAR_REQUIRED = 'Report Year: Value is required.'
// Distinct from YEAR_REQUIRED on purpose: when the years list loads successfully and is EMPTY, no
// reporting period has ever been opened. Telling the administrator their Report Year is required
// blames them for a choice the page never offered. Raised in review on PR #401.
const NO_OPEN_YEAR = 'No reporting period has been opened.'

/**
 * Mill Information Report (UC-MRPT-003). Administrators pick a report year and download a PDF
 * covering every mill — one section per mill — for that year.
 *
 * <p>Built on the Print Schedules page's shape (tombstone, grid, inline notifications, a single
 * action row) so the two report surfaces read as siblings. It carries the legacy screen's content —
 * the explanatory note and the "Report Year:" selector — without the legacy panel chrome.
 *
 * <p>Unlike the schedule pages this one takes NO mill/year working context: it neither reads nor
 * needs the Home selection, which is why there is no context guard here. The year list holds only
 * opened reporting periods, newest first, and the newest is pre-selected.
 */
const MillInformationReport: FC = () => {
  const [years, setYears] = useState<ReportingYear[]>([])
  const [selectedYear, setSelectedYear] = useState('')
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api()
      .get<ReportingYear[]>('/v1/reporting-years')
      .then((response) => {
        setLoadError(null)
        setYears(response.data)
        // The list arrives year-descending, so the first entry is the most recent opened period.
        setSelectedYear(response.data.length > 0 ? String(response.data[0].reportYear) : '')
      })
      .catch((cause: unknown) =>
        setLoadError(extractDetail(cause) || 'Unable to load the reporting years.'),
      )
  }, [])

  const generate = () => {
    setError(null)
    // No year to send means no reporting period has ever been opened. Reject here with the same text
    // the server would return rather than making a request that cannot succeed.
    if (selectedYear === '') {
      // Three different situations, three different answers. A failed load already has its own
      // banner, so say nothing more; an empty list means nothing was ever opened; only a genuinely
      // unselected year is the user's to fix.
      if (!loadError) {
        setError(years.length === 0 ? NO_OPEN_YEAR : YEAR_REQUIRED)
      }
      return
    }
    setBusy(true)
    api()
      .get(REPORT_PATH, { params: { year: selectedYear }, responseType: 'blob' })
      .then(async (response) => {
        // Belt and braces — see assertCompletePdf. A generation failure is now a 500
        // problem+json (the backend exports before it commits a status) and a cut transfer is a
        // Content-Length short read axios rejects, so neither reaches this .then. Throwing here
        // still routes the leftover case to the .catch below, which keeps the selected year so
        // Generate Report retries it.
        await assertCompletePdf(response.data as Blob)
        triggerDownload(response.data as Blob, PDF_FILENAME)
      })
      .catch(async (cause: unknown) => {
        // The selected year is deliberately kept: pressing Generate Report again must retry the year
        // that was held when it failed.
        setError((await extractBlobDetail(cause)) || 'Unable to generate the report.')
      })
      .finally(() => setBusy(false))
  }

  return (
    <div className="app-page">
      <ScheduleTombstone title="Mill Information Report" />
      <Grid fullWidth className="app-page__body">
        <Column sm={4} md={8} lg={16} className="mill-information-report">
          {loadError && (
            <InlineNotification
              kind="error"
              lowContrast
              title="Error"
              subtitle={loadError}
              onCloseButtonClick={() => setLoadError(null)}
            />
          )}
          {error && (
            <InlineNotification
              kind="error"
              lowContrast
              title="Report failed"
              subtitle={error}
              onCloseButtonClick={() => setError(null)}
            />
          )}

          {/*
            Legacy reads "the mill's associated with the current logged in user". Under DL-23 the
            Administrator variant is the target and the report is unscoped, so that wording would
            misdescribe what this build produces.
          */}
          <p className="mill-information-report__note">
            The Mill Information Report created will include a report on every mill.
          </p>
          {/*
            Legacy also promises "the information of the licensees and auditors currently associated
            with each mill". Those tables are descoped — the app cannot resolve a person's name from
            a stored USER_GUID — so promising them here would be a claim the PDF does not honour.
          */}
          <p className="mill-information-report__note">
            The report will list the mill&apos;s information and the status history of each mill.
          </p>

          <Select
            id="mill-information-report-year"
            className="mill-information-report__year"
            labelText="Report Year:"
            value={selectedYear}
            disabled={busy || years.length === 0}
            onChange={(event) => setSelectedYear(event.target.value)}
          >
            {years.length === 0 && <SelectItem value="" text="" />}
            {years.map((year) => (
              <SelectItem
                key={year.reportYear}
                value={String(year.reportYear)}
                text={String(year.reportYear)}
              />
            ))}
          </Select>

          <div className="mill-information-report__actions">
            <Button renderIcon={Download} onClick={generate} disabled={busy}>
              {busy ? 'Generating…' : 'Generate Report'}
            </Button>
          </div>
        </Column>
      </Grid>
    </div>
  )
}

export default MillInformationReport
