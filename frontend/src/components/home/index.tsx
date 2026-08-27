import type { FC } from 'react'
import { useEffect, useRef, useState } from 'react'
import { Button, Column, Dropdown, Grid, InlineNotification } from '@carbon/react'
import apiService from '@/service/api-service'
import useMillYear from '@/context/millYear/useMillYear'
import LoadingScreen from '@/components/core/LoadingScreen'
import PageTitle from '@/components/core/PageTitle'
import type MillSummary from '@/interfaces/MillSummary'
import type ReportingYear from '@/interfaces/ReportingYear'
import type WorkingContext from '@/interfaces/WorkingContext'
import type { ProblemBody } from '@/interfaces/WorkingContext'
import { extractDetail } from '@/utils/error'
import { sanitizeHtml } from '@/utils/sanitizeHtml'
import type { HomeContentEntry } from '@/interfaces/HomeContent'
import './index.scss'

// Home is the landing page (legacy home.xhtml): pick a mill + reporting year and Save to establish
// the working context every schedule page operates on. Contract-carried message text originates
// from the API (AD-8) — SUC-001 from the 200 `message.text`, required-field text from the 400
// `messages` — never hardcoded here. The only literals below are client-side chrome with no legacy
// counterpart (last-resort fallbacks for a missing problem body / list-load failure, and Carbon
// notification titles), mirroring the ratified schedule1 idiom [schedule1/index.tsx:30-35].

// The verbatim per-field message(s) from a 400 body (S08 shows both together); fall back to `detail`,
// then a last-resort generic (only if the server sent no problem body at all). Deduplicated — the
// contract does not guarantee distinct texts, repeated texts add nothing, and unique texts keep the
// notification list's React keys collision-free.
function extractSaveErrors(error: unknown): string[] {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: ProblemBody } }).response?.data
    const texts = [
      ...new Set(
        (data?.messages ?? [])
          .map((message) => message.text)
          .filter((text): text is string => Boolean(text)),
      ),
    ]
    if (texts.length > 0) {
      return texts
    }
    if (data?.detail) {
      return [data.detail]
    }
  }
  return ['Unable to save the working context.']
}

const millItemToString = (mill: MillSummary | null) =>
  mill ? `${mill.millNumber ?? ''} - ${mill.millName ?? ''}` : ''
const yearItemToString = (year: ReportingYear | null) => (year ? String(year.reportYear) : '')

const Home: FC = () => {
  const { millId, year, setContext } = useMillYear()

  // Mount-time context snapshot (AC4/S03 legacy parity): legacy home.xhtml binds the dropdowns to
  // the session context (homeMB.getSelectedMill/getSelectedYear read userSessionMB — homeMB.java:81-95),
  // so returning to Home re-renders the current selection and a year-only change works. A ref, not an
  // effect dep — the lists load once per visit; a post-Save context change must not refetch or reset
  // the form. Only the RENDERING mirrors legacy: setContext still fires exclusively on the Save 200
  // (AC2/AC5) — legacy's eager on-change session mutation is deliberately not copied.
  const initialContextRef = useRef({ millId, year })

  // Lifetime flag for handleSave (the mount effect has its own `active` guard): a late resolve 200
  // arriving after unmount must not rewrite the global context or set state on this component.
  const aliveRef = useRef(true)
  useEffect(() => {
    aliveRef.current = true
    return () => {
      aliveRef.current = false
    }
  }, [])

  const [mills, setMills] = useState<MillSummary[]>([])
  const [years, setYears] = useState<ReportingYear[]>([])
  const [selectedMill, setSelectedMill] = useState<MillSummary | null>(null)
  const [selectedYear, setSelectedYear] = useState<ReportingYear | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveErrors, setSaveErrors] = useState<string[]>([])
  // Role-specific welcome message (Story 24.2 / UC-CNT-001, FR3 tie). A failure is surfaced without
  // blocking the mill/year picker.
  const [roleMessage, setRoleMessage] = useState<string | null>(null)
  const [roleMessageError, setRoleMessageError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    apiService
      .getAxiosInstance()
      .get<HomeContentEntry>('/v1/home-content/mine')
      .then((response) => {
        if (active) {
          setRoleMessage(response.data.messageText)
          setRoleMessageError(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setRoleMessageError(extractDetail(error) || 'Unable to load the Home message.')
        }
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true
    // Both option lists load on mount, in parallel, through the apiService singleton (NFR2).
    Promise.all([
      apiService.getAxiosInstance().get<MillSummary[]>('/v1/mills'),
      apiService.getAxiosInstance().get<ReportingYear[]>('/v1/reporting-years'),
    ])
      .then(([millsResponse, yearsResponse]) => {
        if (!active) {
          return
        }
        setMills(millsResponse.data)
        setYears(yearsResponse.data)
        // Reflect the current working context in the dropdowns (AC4/S03 legacy parity — see the
        // initialContextRef note above). When the context mill isn't in the list, fall back to the
        // S02 single-mill pre-select (legacy homeMB.java:53-56); both stay changeable.
        const { millId: contextMillId, year: contextYear } = initialContextRef.current
        const contextMill = millsResponse.data.find((mill) => mill.millId === contextMillId) ?? null
        setSelectedMill(
          contextMill ?? (millsResponse.data.length === 1 ? millsResponse.data[0] : null),
        )
        setSelectedYear(yearsResponse.data.find((item) => item.reportYear === contextYear) ?? null)
        setLoadError(null)
      })
      .catch((error: unknown) => {
        if (active) {
          setLoadError(extractDetail(error) || 'Unable to load the mill and reporting-year lists.')
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false)
        }
      })
    return () => {
      active = false
    }
  }, [])

  const handleSave = () => {
    // Re-entrancy guard (mirrors schedule1): avoid concurrent resolve calls on a double-click.
    if (saving) {
      return
    }
    setSaving(true)
    setSaveMessage(null)
    setSaveErrors([])
    // Empty params are sent verbatim when a dropdown is still on its placeholder — validation is
    // backend-authoritative (S04/S05/S08); the backend returns the required-field 400.
    const millIdParam = selectedMill ? String(selectedMill.millId) : ''
    const yearParam = selectedYear ? String(selectedYear.reportYear) : ''
    apiService
      .getAxiosInstance()
      .get<WorkingContext>(`/v1/mill-context?millId=${millIdParam}&year=${yearParam}`)
      .then((response) => {
        // Stale-response guard: ignore a resolve that lands after the user left the page.
        if (!aliveRef.current) {
          return
        }
        // AR11: the working context is client-side now; a successful resolve makes this selection the
        // source of context, replacing the 514/2021 default.
        setContext(response.data.millId, response.data.reportYear)
        // SUC-001 verbatim from the API message (AD-8), never hardcoded.
        setSaveMessage(response.data.message?.text ?? null)
      })
      .catch((error: unknown) => {
        if (!aliveRef.current) {
          return
        }
        // Leave the existing MillYearContext untouched on any error — never setContext (S04/S05).
        setSaveErrors(extractSaveErrors(error))
      })
      .finally(() => {
        if (aliveRef.current) {
          setSaving(false)
        }
      })
  }

  const header = (
    <Grid fullWidth className="app-page__header">
      <PageTitle title="Mill and Reporting Year" />
    </Grid>
  )

  if (isLoading) {
    return (
      <div className="app-page">
        {header}
        <Grid fullWidth className="app-page__body">
          <Column sm={4} md={8} lg={16}>
            <LoadingScreen label="Loading mills and reporting years" />
          </Column>
        </Grid>
      </div>
    )
  }

  return (
    <div className="app-page">
      {header}
      <Grid fullWidth className="app-page__body">
        {loadError && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              title="Unable to load"
              subtitle={loadError}
            />
          </Column>
        )}

        {roleMessageError && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification
              kind="error"
              lowContrast
              title="Unable to load Home message"
              subtitle={roleMessageError}
            />
          </Column>
        )}

        {saveMessage && (
          <Column sm={4} md={8} lg={16}>
            <InlineNotification kind="success" lowContrast title="Success" subtitle={saveMessage} />
          </Column>
        )}
        {saveErrors.map((message) => (
          // Keys are safe: extractSaveErrors deduplicates, so message texts are unique.
          <Column sm={4} md={8} lg={16} key={message}>
            <InlineNotification kind="error" lowContrast title="Cannot save" subtitle={message} />
          </Column>
        ))}

        {roleMessage && sanitizeHtml(roleMessage) && (
          <Column sm={4} md={8} lg={16} className="home__message">
            {/* Admin-authored welcome message, sanitized at render (the only dangerouslySetInnerHTML
                in the app — see utils/sanitizeHtml). */}
            {/* eslint-disable-next-line @eslint-react/dom-no-dangerously-set-innerhtml -- HTML is
                DOMPurify-sanitized in sanitizeHtml(); this is the sole, deliberate rich-text sink. */}
            <div dangerouslySetInnerHTML={{ __html: sanitizeHtml(roleMessage) }} />
          </Column>
        )}

        <Column sm={4} md={8} lg={16} className="home__field">
          <Dropdown<MillSummary>
            id="home-mill"
            titleText="Mill"
            label="Select Mill"
            items={mills}
            itemToString={millItemToString}
            selectedItem={selectedMill ?? undefined}
            onChange={({ selectedItem }) => {
              setSelectedMill(selectedItem ?? null)
              // A changed selection is unsaved — stale save feedback would misstate its status.
              setSaveMessage(null)
              setSaveErrors([])
            }}
          />
        </Column>
        <Column sm={4} md={8} lg={16} className="home__field">
          <Dropdown<ReportingYear>
            id="home-year"
            titleText="Reporting Year"
            label="Select Reporting Year"
            items={years}
            itemToString={yearItemToString}
            selectedItem={selectedYear ?? undefined}
            onChange={({ selectedItem }) => {
              setSelectedYear(selectedItem ?? null)
              setSaveMessage(null)
              setSaveErrors([])
            }}
          />
        </Column>

        <Column sm={4} md={8} lg={16} className="home__actions">
          <Button kind="primary" disabled={saving} onClick={handleSave}>
            Save
          </Button>
        </Column>
      </Grid>
    </div>
  )
}

export default Home
