import { Tooltip } from '@carbon/react'
import { ArrowsHorizontal } from '@carbon/icons-react'
import type { FC } from 'react'
import type { OriginalValues } from '@/interfaces/OriginalValue'
import { originalValueState } from '@/utils/originalValue'
import './index.scss'

/**
 * The original-value indicator: shown beside a value that differs from what the Licensee originally
 * submitted, with the submitted value one hover or one Tab away (Story 16.2, UC-CHK-005/UC-CHK-010
 * BR-04). It is audit evidence, not decoration — it is how a verifier tells a ministry correction
 * from what the mill actually reported.
 *
 * One component for all twelve schedules. Legacy repeated the same triple 272 times across the
 * schedule views — a dead `<p:commandButton type="button">` carrying the jQuery-UI sprite
 * `ui-icon-transferthick-e-w`, a `<p:tooltip>` bound to the submitted value, and a styleClass
 * ternary — and its canonical form is `schedule1.xhtml:106-125`.
 *
 * ACCESSIBILITY. Legacy conveyed this with an icon plus a tooltip and, as a third channel, a change
 * of input WIDTH (`main.css:84-92`, 120px -> 90px). There is no colour anywhere in the legacy
 * feature and no `title` attribute, so the icon was always the primary channel; it stays that way
 * here. The trigger is a real focusable `<button>` carrying an accessible name, so the indicator is
 * announced and its tooltip reachable by keyboard, not hover alone.
 *
 * DEVIATIONS FROM LEGACY, both recorded in the story:
 *   * D1 — the width swap is not reproduced. It exists to make room for the icon, Carbon owns field
 *     sizing, and legacy was inconsistent about it anyway: Schedule 7A got WIDER, all three
 *     Schedule 8 views and 11 of Schedule 9's 12 fields had no swap at all despite rendering the
 *     indicator, and two of the CSS classes for it are dead.
 *   * When nothing was submitted for the field, legacy still rendered its tooltip with an empty
 *     value ("Original Submission Value: "). Here the indicator carries a plain statement instead,
 *     because a tooltip that trails off after a colon reads as a defect rather than as information.
 */
export interface OriginalValueIndicatorProps {
  // The owning object's `originalValues` map, straight off the served row or document. Null at
  // Draft, which is what suppresses every indicator on the page.
  readonly originals: OriginalValues | null | undefined
  // The field's own camelCase name, as the backend keyed it.
  readonly field: string
  // The value in the field right now, as the form holds it — so the indicator tracks an unsaved
  // edit the way legacy's ajax re-render did.
  readonly current: string | number | null | undefined
  // False for text fields (comments, descriptions, codes), so they compare with `equals` rather
  // than by rounded numeric value.
  readonly numeric?: boolean
  // What the field is, for the accessible name — e.g. "Standing Tree to Loaded Truck volume".
  readonly label: string
}

/** Shown when the field never had a submitted value: it was added after the report was submitted. */
const ADDED_SINCE_SUBMISSION = 'No value was originally submitted by the Licensee'

const OriginalValueIndicator: FC<OriginalValueIndicatorProps> = ({
  originals,
  field,
  current,
  numeric = true,
  label,
}) => {
  const { changed, tooltip } = originalValueState(originals, field, current, numeric)

  if (!changed) {
    return null
  }

  // The tooltip text is the API's, verbatim (AD-8) — "Original Submission Value: 60,000".
  const text = tooltip ?? ADDED_SINCE_SUBMISSION

  // `description`, NOT `label` — the two are not interchangeable here (found in review of #452).
  // Carbon's `label` renders the tooltip through `aria-labelledby`, which REPLACES the trigger's own
  // accessible name: the button then announces only "Original Submission Value: 60,000", and a
  // screen-reader user has to infer which of a row's thirty fields it belongs to from position
  // alone — the very thing the accessible name is here to prevent. `description` renders the same
  // popover text through `aria-describedby` instead, so the field's own name is announced first and
  // the submitted value follows it. The visible tooltip is identical either way, so the API's text
  // still reaches the screen verbatim.
  return (
    <Tooltip description={text} align="top">
      <button
        type="button"
        className="original-value-indicator"
        aria-label={`${label} differs from the originally submitted value`}
        data-testid={`original-value-${field}`}
      >
        <ArrowsHorizontal />
      </button>
    </Tooltip>
  )
}

export default OriginalValueIndicator
