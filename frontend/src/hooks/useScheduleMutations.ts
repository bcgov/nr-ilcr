import apiService from '@/service/api-service'
import { useScheduleBanners } from '@/hooks/useScheduleBanners'

const api = () => apiService.getAxiosInstance()

type UseScheduleMutationsOptions = {
  /** API base path, e.g. {@code '/v1/schedule1'}; mill/year are appended as query params. */
  readonly path: string
  readonly millId: number | null
  readonly year: number | null
  /** True while the render's mill/year still matches the live context — the stale-response guard. */
  readonly isCurrent: () => boolean
}

type MutationOptions<T> = {
  /** Applied only when the request resolves under the still-current context (see {@code run}). */
  /**
   * May return a promise; {@code run} then holds the in-flight lock until it settles, so a write that
   * chains a re-GET is one locked operation (defect #292, PR #351 review). See
   * {@code useScheduleBanners.RunOptions.onSuccess}.
   */
  readonly onSuccess: (data: T) => void | Promise<unknown>
  /** Shown only when the rejection carries no ProblemDetail detail of its own (AD-8); {@code null} fails silently. */
  readonly fallback: string | null
  /** Appended to the base path before the mill/year query, e.g. {@code '/records/12'} for a by-id write. */
  readonly suffix?: string
}

/**
 * The shared save / delete / check-status concern for the schedule pages. Composes
 * {@link useScheduleBanners} — so a page gets the banner + in-flight state AND the mutation helpers
 * from one hook — and routes every write through its {@code run()}, which already carries the
 * {@code isCurrent()} guard that stops a stale in-flight write from repainting a newly-switched
 * mill/year context (proven on Schedules 7A/7B/9).
 *
 * <p>Pages supply only their {@code path} (and, per call, their {@code validateScheduleN} + the
 * {@code onSuccess} that applies the echoed document). The request/error/lock scaffolding lives here
 * and in {@code run()}, not re-hand-rolled per page. Delete deliberately takes its {@code onSuccess}
 * at the call site: single-document pages whose re-GET would 404 (Schedules 1/3) reset to an empty
 * read-only shape in place, while list pages (4/5/8) re-seed from the reload — a genuine per-page
 * empty-state difference documented at the call site rather than forked into this hook.
 *
 * <p>Like {@code run()}, this hook is intentionally NOT memoized: its helpers close over the render's
 * mill/year so each dispatch carries the current {@code isCurrent}.
 */
export function useScheduleMutations<TCheckResult>({
  path,
  millId,
  year,
  isCurrent,
}: UseScheduleMutationsOptions) {
  const banners = useScheduleBanners<TCheckResult>(isCurrent)

  const query = `?millId=${String(millId)}&year=${String(year)}`
  const url = (suffix = '') => `${path}${suffix}${query}`

  /** PUT (default) or POST a body, then apply {@code onSuccess} under the guarded {@code run()}. */
  const save = <T>(
    body: unknown,
    {
      onSuccess,
      fallback,
      suffix,
      method = 'put',
    }: MutationOptions<T> & { method?: 'put' | 'post' },
  ) =>
    banners.run<T>(
      method === 'post' ? api().post<T>(url(suffix), body) : api().put<T>(url(suffix), body),
      { fallback, onSuccess },
    )

  /** DELETE, then apply the page's {@code onSuccess} (its own post-delete empty-state) under {@code run()}. */
  const remove = <T>({ onSuccess, fallback, suffix }: MutationOptions<T>) =>
    banners.run<T>(api().delete<T>(url(suffix)), { fallback, onSuccess })

  /** POST the check-status endpoint (default suffix {@code '/check-status'}). */
  const checkStatus = <T>({ onSuccess, fallback, suffix = '/check-status' }: MutationOptions<T>) =>
    banners.run<T>(api().post<T>(url(suffix)), { fallback, onSuccess })

  return { ...banners, query, url, save, remove, checkStatus }
}

export default useScheduleMutations
