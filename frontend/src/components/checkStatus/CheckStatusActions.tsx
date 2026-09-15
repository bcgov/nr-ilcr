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

type ActionButtonProps = {
  readonly label: string
  readonly kind: 'primary' | 'secondary' | 'tertiary'
  readonly action: TrackAction
}

/**
 * One action button with its accessibility hint. A disabled Carbon button is not focusable, so greying
 * alone tells a screen-reader user nothing — less than legacy's greying told a sighted one. The hint
 * restores that, `cds--visually-hidden` so it stays out of the visual page, and `aria-describedby` is
 * wired only while the hint is rendered so no dangling id is ever announced. `useId` keeps every
 * instance's hint distinct however many bars the page renders.
 */
const ActionButton: FC<ActionButtonProps> = ({ label, kind, action }) => {
  const hintId = useId()
  const disabled = !action.enabled || action.onClick === undefined
  const showHint = disabled && action.disabledReason !== undefined
  return (
    <>
      <Button
        kind={kind}
        disabled={disabled}
        onClick={action.onClick}
        aria-describedby={showHint ? hintId : undefined}
      >
        {label}
      </Button>
      {showHint && (
        <span id={hintId} className="cds--visually-hidden">
          {action.disabledReason}
        </span>
      )}
    </>
  )
}

/**
 * The Check Status action bar — `Set to Draft` / `Set to Submit` when the page renders them, then
 * `Submit` and `Verified`, always rendered and greyed rather than hidden, exactly as legacy did
 * (`disabled="#{!checkStatusMB.canUserSubmitReport(…)}"` / `canUserVerifyReport(…)`,
 * checkStatus.xhtml:37-62). Legacy placed it three times: above and below the Schedules 1–10
 * accordion for that track, and inside the Schedule 11 tab for its own track. The page renders one
 * element per placement, all from the same per-track gates, never bars with their own gates.
 */
const CheckStatusActions: FC<CheckStatusActionsProps> = ({
  setToDraft,
  setToSubmit,
  submit,
  verify,
}) => (
  <Column sm={4} md={8} lg={16} className="check-status__actions">
    {setToDraft && <ActionButton label="Set to Draft" kind="secondary" action={setToDraft} />}
    {setToSubmit && <ActionButton label="Set to Submit" kind="secondary" action={setToSubmit} />}
    <ActionButton label="Submit" kind="primary" action={submit} />
    <ActionButton label="Verified" kind="tertiary" action={verify} />
  </Column>
)

export default CheckStatusActions
