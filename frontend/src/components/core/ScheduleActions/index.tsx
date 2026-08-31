import type { FC } from 'react'
import { Button, Column } from '@carbon/react'
import { CheckmarkOutline, Save, TrashCan } from '@carbon/icons-react'

// One id is safe: at most one bar renders the hint (only the Delete-bearing instance can, and a page
// renders that once). Stable so `aria-describedby` can point at it.
const HINT_ID = 'schedule-actions-delete-hint'

type ScheduleActionsProps = {
  /** Modifier class for the actions Column, e.g. {@code 'schedule-3__actions'}. */
  className: string
  editable: boolean
  saving: boolean
  onSave: () => void
  onCheckStatus: () => void
  onDelete: () => void
  /**
   * Whether to render Delete. Legacy put Delete on the BOTTOM bar only — the top bar carried Save and
   * Check Status alone (schedule1.xhtml:35-38 vs :796-803; schedule3.xhtml:37-38 vs :420-426). A page
   * rendering this bar twice therefore passes `false` for its top instance. Defaults to true so the
   * bottom instance needs no prop.
   */
  showDelete?: boolean
  /**
   * Whether the schedule has a persisted record to delete — legacy `isScheduleOpen()`, i.e. a summary
   * exists (Schedule2MB.java:152-158, and the identical gate on Schedules 1 and 3). Delete is
   * DISABLED without one; legacy did not render the button at all, and disabling instead is the
   * recorded deviation from `rendered="#{... and isScheduleOpen()}"` (defect #292 decision 1).
   *
   * REQUIRED, deliberately — it was optional-defaulting-true for one commit, and the code review
   * called that fail-open: the only thing a default can do here is hand a future page pre-fix
   * behaviour (an enabled Delete on an unsaved document) with no type error and no test failure. Pass
   * `isScheduleSaved(doc)` from `@/utils/schedule`; that helper carries the absent-vs-null rule that
   * made #292 possible.
   */
  scheduleSaved: boolean
}

/**
 * The Save / Check Status / Delete action bar shared by the top-level schedule pages. The disabled
 * logic is identical across schedules (writes gated on `editable` and in-flight requests, Delete
 * additionally on a persisted record); each page differs by its section modifier class and by whether
 * this instance is the Delete-bearing one.
 */
const ScheduleActions: FC<ScheduleActionsProps> = ({
  className,
  editable,
  saving,
  onSave,
  onCheckStatus,
  onDelete,
  showDelete = true,
  scheduleSaved,
}) => (
  <Column sm={4} md={8} lg={16} className={className}>
    <Button
      kind="primary"
      size="md"
      renderIcon={Save}
      disabled={!editable || saving}
      onClick={onSave}
    >
      Save
    </Button>
    <Button
      kind="tertiary"
      size="md"
      renderIcon={CheckmarkOutline}
      disabled={!editable || saving}
      onClick={onCheckStatus}
    >
      Check Status
    </Button>
    {showDelete && (
      <>
        <Button
          kind="danger--tertiary"
          size="md"
          renderIcon={TrashCan}
          disabled={!editable || !scheduleSaved || saving}
          onClick={onDelete}
          /* Described only when the hint below is actually rendered, so assistive tech never
             resolves a dangling id. */
          aria-describedby={editable && !scheduleSaved ? HINT_ID : undefined}
        >
          Delete
        </Button>
        {/* Decision 1 of defect #292 kept legacy's behaviour (no delete without a persisted record)
            but changed its mechanism from "not rendered" to "disabled". A disabled Carbon button is
            not focusable, so on its own that is WORSE than legacy for a screen-reader user: legacy's
            absence at least matched what assistive tech reported, whereas a silent dead button
            reports nothing at all. This reason pays that cost.

            VISUALLY HIDDEN, deliberately (product call 2026-08-24, after seeing it on screen): it
            shipped as visible text beside the button for one commit and read as clutter next to a
            control that is already self-evidently greyed. `cds--visually-hidden` keeps it in the
            accessibility tree — where the disabled button gives a screen-reader user nothing at all —
            while removing it from the page. Do not delete it to "clean up" the DOM: without it,
            greying the button is strictly less accessible than legacy's omission was.

            Rendered only in the state where the question arises (editable but nothing saved yet).
            Read-only schedules say nothing: there the whole bar is disabled and the tombstone already
            shows the non-Draft status. */}
        {editable && !scheduleSaved && (
          <span id={HINT_ID} className="cds--visually-hidden">
            Available once the schedule is saved
          </span>
        )}
      </>
    )}
  </Column>
)

export default ScheduleActions
