import type { MessageInfo, Schedule10CheckStatusResponse } from '@/interfaces/Schedule10Response'
import { CHECK_STATUS_MET } from '@/interfaces/Schedule10Response'

/** The flat shape the shared banner stack renders. */
export type Schedule10CheckSummary = {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
}

/**
 * Flatten a Check Status result into the shared banner shape.
 *
 * The response nests issues under each page and road detail, but every issue's text is already
 * composed with its own page and road labels, so the lines stand alone — which is also how legacy
 * rendered them, as one flat message list. Flattening keeps that behaviour and reuses the shared
 * notification stack instead of duplicating it.
 *
 * On an issues outcome the response lists EVERY page and road detail, including those that passed,
 * so only the ones carrying issues contribute lines.
 */
export const summariseCheckStatus = (
  response: Schedule10CheckStatusResponse,
): Schedule10CheckSummary => {
  const requirementsMet = response.outcome === CHECK_STATUS_MET
  const errors = response.pages.flatMap((page) => [
    ...page.issues.map((issue) => issue.message),
    ...page.roadDetails.flatMap((detail) => detail.issues.map((issue) => issue.message)),
  ])
  return {
    requirementsMet,
    errors,
    requirementsMetMessage: requirementsMet ? (response.messages[0] ?? null) : null,
  }
}
