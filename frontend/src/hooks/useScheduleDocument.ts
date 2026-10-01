import type { ChangeEvent, ReactElement, ReactNode } from 'react'
import { useEffect, useRef, useState } from 'react'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'
import { renderScheduleLoadState } from '@/components/core/ScheduleLoadState'

export type FieldValues = Record<string, string>

type UseScheduleDocumentOptions<T> = {
  /** API path, e.g. {@code '/v1/schedule1'}; mill/year are appended as query params. */
  path: string
  millId: number | null
  year: number | null
  contextMissing: boolean
  /** Seed the editable form state from the loaded document (page-specific writable fields). */
  seedForm: (doc: T) => FieldValues
  /** Map an axios error's problem+json detail to the page's load-error text. */
  mapLoadError: (detail: string | undefined, millId: number | null, year: number | null) => string
  /** Clear page-specific transient state (save/action notifications) at the start of each load. */
  onReset?: () => void
  /** The schedule's display name, e.g. {@code 'Schedule 7B'} — names the states in {@code loadState}. */
  scheduleName: string
  /** The page header band, rendered above every state in {@code loadState}. */
  header: ReactNode
}

type UseScheduleDocumentResult<T> = {
  data: T | null
  setData: React.Dispatch<React.SetStateAction<T | null>>
  form: FieldValues
  setForm: React.Dispatch<React.SetStateAction<FieldValues>>
  setField: (key: string) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => void
  errorDetail: string | null
  setErrorDetail: React.Dispatch<React.SetStateAction<string | null>>
  isLoading: boolean
  /**
   * The guard element this document's own state implies — context missing, loading, mill closed for
   * the reporting year, load failed — or null when the page has a document to render. The page's
   * whole obligation is {@code if (loadState) return loadState}: the states belong to the load this
   * hook owns, so assembling the same five arguments in twelve pages only invited them to drift.
   */
  loadState: ReactElement | null
}

/**
 * The shared "load a schedule document on mill/year change" concern for the schedule pages
 * (Schedule 1, Schedule 2, Other Costs): owns {@code data}/{@code form}/{@code errorDetail}/
 * {@code isLoading}, resets on context change, GETs {@code path?millId&year}, seeds the form, and
 * ignores a stale response after the context changes again. Mutations (save/delete/check-status)
 * stay in the page. Extracted so each page stops re-inlining the identical fetch effect.
 *
 * <p>It also RENDERS what those states mean, as {@code loadState} — see
 * {@link renderScheduleLoadState}. The page supplies its name and header band and returns the
 * element; the guard is then one line per page instead of the same five-argument call twelve times.
 */
export function useScheduleDocument<T>({
  path,
  millId,
  year,
  contextMissing,
  seedForm,
  mapLoadError,
  onReset,
  scheduleName,
  header,
}: UseScheduleDocumentOptions<T>): UseScheduleDocumentResult<T> {
  const [data, setData] = useState<T | null>(null)
  const [form, setForm] = useState<FieldValues>({})
  const [errorDetail, setErrorDetail] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(!contextMissing)

  // Latest-callback refs so the load effect depends only on the mill/year context, never on the
  // identity of the page-supplied callbacks.
  const seedFormRef = useRef(seedForm)
  seedFormRef.current = seedForm
  const mapLoadErrorRef = useRef(mapLoadError)
  mapLoadErrorRef.current = mapLoadError
  const onResetRef = useRef(onReset)
  onResetRef.current = onReset

  useEffect(() => {
    if (contextMissing) {
      return
    }
    /* eslint-disable @eslint-react/set-state-in-effect -- intentional reset on mill/year change */
    setIsLoading(true)
    setData(null)
    setErrorDetail(null)
    onResetRef.current?.()
    /* eslint-enable @eslint-react/set-state-in-effect */
    let active = true
    apiService
      .getAxiosInstance()
      .get<T>(`${path}?millId=${millId}&year=${year}`)
      .then((response) => {
        if (active) {
          setData(response.data)
          setForm(seedFormRef.current(response.data))
          setErrorDetail(null)
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setErrorDetail(mapLoadErrorRef.current(extractDetail(error), millId, year))
          setData(null)
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
  }, [path, millId, year, contextMissing])

  const setField =
    (key: string) => (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const { value } = event.target
      setForm((prev) => ({ ...prev, [key]: value }))
    }

  // Rendered on every pass, including the one that returns null — the branches are four cheap
  // comparisons, and computing it here is what lets a page carry the guard as a single line.
  const loadState = renderScheduleLoadState({
    header,
    scheduleName,
    contextMissing,
    isLoading,
    errorDetail,
  })

  return {
    data,
    setData,
    form,
    setForm,
    setField,
    errorDetail,
    setErrorDetail,
    isLoading,
    loadState,
  }
}
