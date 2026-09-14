import type { FC } from 'react'
import { useId } from 'react'
import { Button, Column } from '@carbon/react'

/** One action's state, decided by the page from role and the track's status; the bar decides nothing. */
export type TrackAction = {
  readonly enabled: boolean
  /** Why the button is greyed, when it is; rendered visually hidden for assistive tech. */
  readonly disabledReason?: string
  /** The click target. Supplied by the transition stories; absent, the button stays disabled. */
  readonly onClick?: () => void
}

type CheckStatusActionsProps = {
  /**
   * The admin reversals. Unlike Submit/Verified these are NOT always rendered: legacy `rendered=` Set to
   * Draft only for an admin while the track is Submitted, and Set to Submit only for an admin while it is
   * Verified (CheckStatusMB.java:162-192). The page passes one only in that state; absent → not rendered.
   */
  readonly setToDraft?: TrackAction
  readonly setToSubmit?: TrackAction
  readonly submit: TrackAction
  readonly verify: TrackAction
}

/**
 * The Check Status action bar — `Set to Draft` / `Set to Submit` when the page renders them, then
 * `Submit` and `Verified`, always rendered and greyed rather than hidden, exactly as legacy did (`disabled="#{!checkStatusMB.canUserSubmitReport(…)}"` /
 * `canUserVerifyReport(…)`, checkStatus.xhtml:51-62). Legacy placed it three times: above and below
 * the Schedules 1–10 accordion for that track, and inside the Schedule 11 tab for its own track. The
 * page renders one element per track, placed as legacy did, never bars with their own gates.
 *
 * A disabled Carbon button is not focusable, so greying alone tells a screen-reader user nothing —
 * less than legacy's greying told a sighted one. The hints restore that, `cds--visually-hidden` so
 * they stay out of the visual page, and `aria-describedby` is wired only while a hint is rendered so
 * no dangling id is ever announced. `useId` keeps every instance's hints distinct.
 */
const CheckStatusActions: FC<CheckStatusActionsProps> = ({
  setToDraft,
  setToSubmit,
  submit,
  verify,
}) => {
  const submitHintId = useId()
  const verifyHintId = useId()
  const submitDisabled = !submit.enabled || submit.onClick === undefined
  const verifyDisabled = !verify.enabled || verify.onClick === undefined
  const showSubmitHint = submitDisabled && submit.disabledReason !== undefined
  const showVerifyHint = verifyDisabled && verify.disabledReason !== undefined
  return (
    <Column sm={4} md={8} lg={16} className="check-status__actions">
      {setToDraft && (
        <Button
          kind="secondary"
          disabled={!setToDraft.enabled || setToDraft.onClick === undefined}
          onClick={setToDraft.onClick}
        >
          Set to Draft
        </Button>
      )}
      {setToSubmit && (
        <Button
          kind="secondary"
          disabled={!setToSubmit.enabled || setToSubmit.onClick === undefined}
          onClick={setToSubmit.onClick}
        >
          Set to Submit
        </Button>
      )}
      <Button
        kind="primary"
        disabled={submitDisabled}
        onClick={submit.onClick}
        aria-describedby={showSubmitHint ? submitHintId : undefined}
      >
        Submit
      </Button>
      {showSubmitHint && (
        <span id={submitHintId} className="cds--visually-hidden">
          {submit.disabledReason}
        </span>
      )}
      <Button
        kind="tertiary"
        disabled={verifyDisabled}
        onClick={verify.onClick}
        aria-describedby={showVerifyHint ? verifyHintId : undefined}
      >
        Verified
      </Button>
      {showVerifyHint && (
        <span id={verifyHintId} className="cds--visually-hidden">
          {verify.disabledReason}
        </span>
      )}
    </Column>
  )
}

export default CheckStatusActions
