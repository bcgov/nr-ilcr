import type { ProblemBody } from '@/interfaces/WorkingContext'

// The RFC 7807 `detail` from an axios error's problem+json body, if any. Generic across pages —
// per-field `messages` extraction (e.g. home's extractSaveErrors) stays page-specific because each
// page owns its fallback text.
export function extractDetail(error: unknown): string | undefined {
  if (error && typeof error === 'object' && 'response' in error) {
    return (error as { response?: { data?: ProblemBody } }).response?.data?.detail
  }
  return undefined
}
