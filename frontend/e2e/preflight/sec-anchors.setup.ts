import { test, expect, type APIRequestContext } from '@playwright/test';
import {
  OPEN_WITH_STATUS,
  CLOSED_MILL,
  NO_STATUS,
  DEFAULT_CONTEXT,
  type MillYearFixture,
} from '../fixtures/sec/working-context-test-data';

/**
 * Suite PREFLIGHT for UC-SEC-001 anchors — part of the `setup` project the `chromium` project depends
 * on. Confirms the pinned Home working-context anchors still resolve through the app's own
 * GET /api/v1/mill-context, so a stale / re-extracted DB fails HERE with ONE clear message rather than
 * mid-suite. Read-only; no Oracle client at runtime.
 */

const REGROUND =
  'Re-ground: reload the real extract, evict the reference-data cache or restart the backend, then ' +
  're-verify the pinned anchors in fixtures/sec/working-context-test-data.ts.';

async function getContext(
  request: APIRequestContext,
  m: MillYearFixture,
): Promise<{ millViewable: boolean; schedules1To10Status: unknown; schedule11Status: unknown }> {
  const url = `/api/v1/mill-context?millId=${m.millId}&year=${m.year}`;
  const res = await request.get(url);
  expect(
    res.ok(),
    `[preflight] SEC anchor ${m.millId}/${m.year} — GET ${url} returned HTTP ${res.status()}. ${REGROUND}`,
  ).toBeTruthy();
  return res.json();
}

test('preflight: SEC default context resolves (mount pre-select)', async ({ request }) => {
  await getContext(request, DEFAULT_CONTEXT);
});

test('preflight: SEC open-with-status anchor resolves and has both track statuses', async ({
  request,
}) => {
  const doc = await getContext(request, OPEN_WITH_STATUS);
  expect(
    doc.schedules1To10Status && doc.schedule11Status,
    `[preflight] ${OPEN_WITH_STATUS.millId}/${OPEN_WITH_STATUS.year} must carry both track statuses ` +
      `for the S01 banner assertion. ${REGROUND}`,
  ).toBeTruthy();
});

test('preflight: SEC closed-mill anchor resolves and is closed', async ({ request }) => {
  const doc = await getContext(request, CLOSED_MILL);
  expect(
    doc.millViewable,
    `[preflight] ${CLOSED_MILL.millId}/${CLOSED_MILL.year} must be CLOSED (millViewable:false) for S06. ${REGROUND}`,
  ).toBe(false);
});

test('preflight: SEC no-status anchor resolves with no report-status rows', async ({ request }) => {
  const doc = await getContext(request, NO_STATUS);
  expect(
    !doc.schedules1To10Status && !doc.schedule11Status,
    `[preflight] ${NO_STATUS.millId}/${NO_STATUS.year} must have NO report-status rows (both null) for S07. ${REGROUND}`,
  ).toBeTruthy();
});
