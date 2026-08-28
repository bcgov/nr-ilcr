import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { Button, Column, Grid, Select, SelectItem } from '@carbon/react'
import { Report } from '@carbon/icons-react'
import apiService from '@/service/api-service'
import NotificationColumn from '@/components/core/NotificationColumn'
import { extractDetail } from '@/utils/error'
import { extractBlobDetail, triggerDownload } from '@/utils/download'
import type ReportingYear from '@/interfaces/ReportingYear'
import './index.scss'

const api = () => apiService.getAxiosInstance()
const REPORT_PATH = '/v1/reports/mill-information'
const PDF_FILENAME = 'mills_print.pdf'
const YEAR_REQUIRED = 'Report Year: Value is required.'

/**
 * Mill Information Report. Administrators pick a report year and download a PDF covering every mill
 * — one section per mill — for that year.
 *
 * <p>Unlike the schedule pages this one takes NO mill/year working context: it neither reads nor
 * needs the Home selection, which is why there is no context guard or tombstone here. The year list
 * holds only opened reporting periods, newest first, and the newest is pre-selected.
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
      setError(YEAR_REQUIRED)
      return
    }
    setBusy(true)
    api()
      .get(REPORT_PATH, { params: { year: selectedYear }, responseType: 'blob' })
      .then((response) => triggerDownload(response.data as Blob, PDF_FILENAME))
      .catch(async (cause: unknown) => {
        // The selected year is deliberately kept: pressing Generate Report again must retry the year
        // that was held when it failed.
        setError((await extractBlobDetail(cause)) || 'Unable to generate the report.')
      })
      .finally(() => setBusy(false))
  }

  return (
    <Grid className="mill-information-report">
      <Column sm={4} md={8} lg={16}>
        <h1 className="mill-information-report__heading">Mill Information Report</h1>
        <p className="mill-information-report__intro">
          Generates a PDF covering every mill for the selected report year, with each mill&apos;s
          information, reporting status milestones, ownership and contacts.
        </p>
      </Column>

      {loadError && <NotificationColumn kind="error" title="Error" subtitle={loadError} />}
      {error && <NotificationColumn kind="error" title="Error" subtitle={error} />}

      <Column sm={4} md={4} lg={6}>
        <Select
          id="mill-information-report-year"
          labelText="Report Year"
          value={selectedYear}
          disabled={years.length === 0}
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
      </Column>

      <Column sm={4} md={8} lg={16}>
        <Button renderIcon={Report} onClick={generate} disabled={busy}>
          {busy ? 'Generating…' : 'Generate Report'}
        </Button>
      </Column>
    </Grid>
  )
}

export default MillInformationReport
