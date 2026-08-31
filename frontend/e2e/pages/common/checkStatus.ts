import { type Page } from '@playwright/test';

/**
 * Bounded, BEST-EFFORT wait for a Check Status response, shared by every schedule's page object.
 *
 * WHY IT EXISTS. A bare click is safe for a positive assertion — `toBeVisible` auto-waits — but unsound
 * for a NEGATIVE one: an absence assertion made immediately after the click can pass against a DOM that
 * has not re-rendered yet, so it proves nothing. That is not hypothetical: the first version of Schedule
 * 3's DIV-6 mirror arm passed its "no longer flagged" assertion vacuously (2026-08-27). It also matters
 * when a scenario checks TWICE in a row, because the second verdict must be the one asserted rather than
 * the first still on screen.
 *
 * WHY IT IS BEST-EFFORT RATHER THAN STRICT. Pressing Check Status does not always send a request: the
 * client legitimately BLOCKS it while a field is invalid, which is the whole point of `sch2`'s S16
 * ("Check Status is also blocked while a field is invalid"). A strict wait turns that correct no-op into a
 * 15 s timeout and fails a green test — which is exactly what a strict first attempt did. So the wait is
 * bounded and its rejection is swallowed: no request is a legitimate outcome, and the caller's own
 * assertions still decide whether the scenario passes.
 *
 * The budget is deliberately short. It is not a page-load allowance — the POST is already in flight when
 * this resolves in the normal case — it is only long enough that a slow local stack does not reintroduce
 * the race, while a blocked click costs a couple of seconds rather than the full expect timeout.
 */
const CHECK_STATUS_RESPONSE_BUDGET = 5_000;

export async function clickAwaitingCheckStatus(
  page: Page,
  path: string,
  click: () => Promise<void>,
): Promise<void> {
  const answered = page
    .waitForResponse((r) => r.url().includes(path) && r.request().method() === 'POST', {
      timeout: CHECK_STATUS_RESPONSE_BUDGET,
    })
    // Swallowed on purpose — see "WHY IT IS BEST-EFFORT" above. Never widen this into a hard assertion:
    // a blocked Check Status sends nothing, and that is correct behaviour some scenarios assert.
    .catch(() => undefined);
  await click();
  await answered;
}
