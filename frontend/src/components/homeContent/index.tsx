import type { FC } from 'react'
import { useEffect, useState } from 'react'
import { Button, Column, Grid } from '@carbon/react'
import ScheduleTombstone from '@/components/core/ScheduleTombstone'
import NotificationColumn from '@/components/core/NotificationColumn'
import RichTextEditor from '@/components/homeContent/RichTextEditor'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import type { ProblemBody } from '@/interfaces/WorkingContext'
import type { HomeContentEntry, HomeContentSaveResponse } from '@/interfaces/HomeContent'

const api = () => apiService.getAxiosInstance()

// The three role editors, in legacy screen order. The Auditor message has no reachable audience in the
// two-group FAM model (DL-23) but is kept editable for parity — labelled so admins know.
const ROLES = [
  { role: 'LICENSEE', label: 'Licensee Welcome Message' },
  { role: 'AUDITOR', label: 'Auditor Welcome Message (no current audience)' },
  { role: 'ADMIN', label: 'Administrator Welcome Message' },
] as const

const REQUIRED = (label: string) => `${label}: Value is required.`

/** Empty once tags, &nbsp; and whitespace are stripped — mirrors the server's required-editor check. */
const isBlankHtml = (html: string) =>
  html
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim().length === 0

/** Verbatim per-field message(s) from a 400 body, else detail, else a generic fallback. */
const extractSaveErrors = (error: unknown): string[] => {
  if (error && typeof error === 'object' && 'response' in error) {
    const data = (error as { response?: { data?: ProblemBody } }).response?.data
    const texts = [
      ...new Set((data?.messages ?? []).map((m) => m.text).filter(Boolean)),
    ] as string[]
    if (texts.length > 0) return texts
    if (data?.detail) return [data.detail]
  }
  return ['Unable to save the Home content.']
}

/**
 * Content Editing (Story 24.2 / UC-CNT-001). Admin-only surface: edit the three role welcome messages
 * (rich text) and save all together. Each editor is required (FLD-001, validated client-side and
 * re-checked server-side); the API 403s a non-admin. Reachable only via the admin-gated Administration
 * menu; the API is the authorization boundary.
 */
const HomeContent: FC = () => {
  const [messages, setMessages] = useState<Record<string, string>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [loaded, setLoaded] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveMessage, setSaveMessage] = useState<string | null>(null)
  const [saveErrors, setSaveErrors] = useState<string[]>([])

  useEffect(() => {
    let active = true
    api()
      .get<HomeContentEntry[]>('/v1/home-content')
      .then((response) => {
        if (!active) return
        const next: Record<string, string> = {}
        response.data.forEach((entry) => {
          next[entry.role] = entry.messageText ?? ''
        })
        setMessages(next)
        setLoaded(true)
      })
      .catch((error: unknown) => {
        if (active) setLoadError(extractDetail(error) || 'Unable to load the Home content.')
      })
    return () => {
      active = false
    }
  }, [])

  const save = () => {
    if (saving) return
    const nextErrors: Record<string, string> = {}
    ROLES.forEach(({ role, label }) => {
      if (isBlankHtml(messages[role] ?? '')) nextErrors[role] = REQUIRED(label)
    })
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSaving(true)
    setSaveMessage(null)
    setSaveErrors([])
    api()
      .put<HomeContentSaveResponse>('/v1/home-content', {
        licensee: messages.LICENSEE,
        auditor: messages.AUDITOR,
        administrator: messages.ADMIN,
      })
      .then((response) => setSaveMessage(response.data.message))
      .catch((error: unknown) => setSaveErrors(extractSaveErrors(error)))
      .finally(() => setSaving(false))
  }

  return (
    <div className="app-page schedule-page">
      <ScheduleTombstone title="Home Content" />
      <Grid fullWidth className="app-page__body">
        {loadError && <NotificationColumn kind="error" title="Error" subtitle={loadError} />}
        {saveMessage && <NotificationColumn kind="success" title="Saved" subtitle={saveMessage} />}
        {saveErrors.map((message) => (
          <NotificationColumn key={message} kind="error" title="Cannot save" subtitle={message} />
        ))}
        <Column sm={4} md={8} lg={16}>
          {loaded &&
            ROLES.map(({ role, label }) => (
              <RichTextEditor
                key={role}
                id={`home-content-${role.toLowerCase()}`}
                label={label}
                value={messages[role] ?? ''}
                invalid={Boolean(errors[role])}
                invalidText={errors[role]}
                onChange={(html) => setMessages((prev) => ({ ...prev, [role]: html }))}
              />
            ))}
          <Button disabled={saving || !loaded} onClick={save}>
            Save
          </Button>
        </Column>
      </Grid>
    </div>
  )
}

export default HomeContent
