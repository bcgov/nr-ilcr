import type { ChangeEvent } from 'react'
import { useEffect, useRef, useState } from 'react'
import apiService from '@/service/api-service'
import { extractDetail } from '@/utils/error'

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
}

/**
 * The shared "load a schedule document on mill/year change" concern for the schedule pages
 * (Schedule 1, Schedule 2, Other Costs): owns {@code data}/{@code form}/{@code errorDetail}/
 * {@code isLoading}, resets on context change, GETs {@code path?millId&year}, seeds the form, and
 * ignores a stale response after the context changes again. Mutations (save/delete/check-status)
 * stay in the page. Extracted so each page stops re-inlining the identical fetch effect.
 */
export function useScheduleDocument<T>({
  path,
  millId,
  year,
  contextMissing,
  seedForm,
  mapLoadError,
  onReset,
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

  return { data, setData, form, setForm, setField, errorDetail, setErrorDetail, isLoading }
}
