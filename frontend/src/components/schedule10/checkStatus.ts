import type {
  MessageInfo,
  RoadDetailCheckResult,
  Schedule10CheckStatusResponse,
} from '@/interfaces/Schedule10Response'
import { CHECK_STATUS_MET } from '@/interfaces/Schedule10Response'

/** The flat shape the shared banner stack renders. */
export type Schedule10CheckSummary = {
  readonly requirementsMet: boolean
  readonly errors: readonly MessageInfo[]
  readonly requirementsMetMessage: MessageInfo | null
}

/**
 * Prefix a road-detail issue with the road it belongs to. The message text stays verbatim after the
 * prefix — the label is added, never edited — and a road carrying no label falls through unchanged
 * rather than rendering a bare separator.
 */
const attributeToRoad = (detail: RoadDetailCheckResult, message: MessageInfo): MessageInfo => {
  const label = detail.roadDetailLabel.trim()
  return label === '' ? message : { ...message, text: `${label}: ${message.text}` }
}

/**
 * Flatten a Check Status result into the shared banner shape.
 *
 * The response nests issues under each page and road detail, and legacy rendered them as one flat
 * message list, so flattening reuses the shared notification stack instead of duplicating it.
 *
 * A PAGE issue's text is already composed with its own page label and stands alone. A ROAD-DETAIL
 * issue's is NOT: the backend prefixes `roadName` and `subzone` with the page label only
 * (`Schedule10CheckStatus.java:184-188`), so on a multi-road page the text cannot say which road is
 * at fault. The enclosing `roadDetailLabel` is prefixed here to restore that attribution — it is the
 * only place the road identity is still in hand once the tree is flattened.
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
    ...page.roadDetails.flatMap((detail) =>
      detail.issues.map((issue) => attributeToRoad(detail, issue.message)),
    ),
  ])
  return {
    requirementsMet,
    errors,
    requirementsMetMessage: requirementsMet ? (response.messages[0] ?? null) : null,
  }
}
