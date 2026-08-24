import { type Page } from '@playwright/test';

/**
 * Shared budget for cold paints / initial navigations (e.g. auth-flow redirects and home page loading),
 * which can take longer on a heavily-loaded or cold parallel runner.
 */
export const NAVIGATION_BUDGET = 30000;

/**
 * Barrier a "no write was sent" assertion crosses before reading a route spy — deterministic, NOT a
 * fixed sleep.
 *
 * PROMOTED to common/ 2026-08-14 when Schedule 2 landed. It was written for Schedule 11 and lived as a
 * module-local function in `steps/sch11/schedule11.steps.ts`; every domain with a mutation spy needs the
 * identical barrier, so it moved here rather than being re-inlined per domain (the suite's
 * reuse-over-duplication rule). Schedule 2 shipped its zero-write assertions WITHOUT it and a reviewer
 * caught the omission — the copy-paste-vs-promote decision is exactly what that near-miss argues for.
 *
 * WHY A BARRIER IS NEEDED AT ALL: the negative has to hold over a window, not at one instant. A
 * regression that renders the inline error and THEN fires the request a tick later would read a tally
 * of 0 and pass green.
 *
 * WHY NOT `waitForTimeout`: a wall-clock constant is tuned, not derived — too short and it flakes on a
 * loaded runner, too long and every rejection scenario pays for it. This waits on events instead, so it
 * is as fast as the app is and does not depend on machine speed:
 *   1. drain the page's own deferrals — pending microtasks, then one `MessageChannel` task (the queue
 *      React's scheduler yields through), then one timer task: between them they cover every way a click
 *      handler can defer work (promise chain, React commit/effect, `setTimeout`). Deliberately NOT
 *      `requestAnimationFrame`, which browsers throttle when the page is not visible — a headed run
 *      whose window is backgrounded would hang here rather than settle;
 *   2. then complete a real round-trip through the page's network stack. The spy is a Playwright route,
 *      and routes fire at request INITIATION in FIFO order, so any mutation the rejected action had
 *      already initiated is counted before this sentinel's response lands.
 *
 * The sentinel hits the app's own origin (index.html — no API, nothing mutated) and so is not counted by
 * any API-scoped spy. Residual limit: a mutation deferred past both queues AND a network round-trip would
 * still escape the tally — the persisted-state read-backs in the same scenarios are what catch that,
 * which is why every reject arm asserts BOTH the spy count and the absent/unchanged record.
 */
export async function settleBeforeReadingSpy(page: Page): Promise<void> {
  await page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        const channel = new MessageChannel();
        channel.port1.onmessage = () => setTimeout(resolve, 0);
        channel.port2.postMessage(null);
      }),
  );
  await page.evaluate(async () => {
    await fetch(`${window.location.origin}/?e2e-no-write-barrier`, { cache: 'no-store' });
  });
}
