import { test, expect, type APIRequestContext } from '@playwright/test';
import { MUTABLE_DRAFT, READONLY_ANCHOR, scheduleUrl } from '../fixtures/sch1/schedule1-test-data';

/** A Schedule 1 fixed line item as it appears in the GET response (backend `Schedule1Response.LineItem`). */
type ResponseLineItem = { costItemCode: number; volume: number | null; cost: number | null };

/**
 * The GET (`Schedule1Response`) shape — the fields needed to prove the mutable target starts EMPTY.
 * NOTE: read the RESPONSE shape, not the PUT request shape. The request's top-level `otherCostsVolume`
 * / `forestMgmtAdminVolume` / `subtotalCompanyLoggingVolume` do NOT exist on the response — the shared
 * Other-Costs volume is `otherCosts.volume`, and 143/144 are rows inside `lineItems`.
 *
 * Mirror of the fields we use from frontend/src/interfaces/Schedule1Response.ts — keep in sync.
 */
type ScheduleDoc = {
  trackStatus: string;
  editable: boolean;
  comments?: string | null;
  lineItems?: ResponseLineItem[];
  // SilvicultureBlock (codes 1/2/139/140); each member is null when its detail row is absent.
  silviculture?: {
    actualSpent?: ResponseLineItem | null; // 1  — volume + cost
    accruedLessActual?: ResponseLineItem | null; // 2  — volume + cost
    lessAdmin?: ResponseLineItem | null; // 139 — volume only (cost pulled from Sch 3)
    total?: ResponseLineItem | null; // 140 — volume only (cost derived)
  };
  // OtherCostsSummary: `volume` is the shared item-19 volume; `count` is the itemized-row count.
  otherCosts?: { volume?: number | null; count?: number };
};

type TargetFindings = { destructible: string[]; advisory: string[] };

/**
 * Classify the mutable target against its pinned "pristine empty Draft" contract, split by whether S01
 * would actually damage each field — so the failure message is ACTIONABLE (SScholefield review):
 *
 *  - DESTRUCTIBLE (→ HARD FAIL): fields `emptyScheduleRequest` really blanks — `comments`, line items
 *    12–18 (volume + cost), silviculture actualSpent(1) / accruedLessActual(2) (volume + cost) — plus
 *    itemized Other-Costs rows (S01 asserts `count === 0` and never touches them, so a stale row breaks
 *    its precondition). A value here is a genuine data-loss / precondition risk.
 *  - ADVISORY (→ WARN, don't fail): the volume-only, server-null-guarded fields — line items 143/144,
 *    silviculture lessAdmin(139) / total(140), and the shared Other-Costs(19) volume. A value here means
 *    the anchor drifted from its pinned empty baseline, but the run itself is safe — flagging it as a
 *    hard "S01 will overwrite" failure would send a maintainer chasing the wrong fix (and needlessly
 *    cascade-fail the whole suite on a safe target).
 *
 *    UPDATED 2026-08-07 — this used to read "fields S01 neither writes nor restores … S01 CANNOT
 *    overwrite them". That is no longer true for 143/144/139/140: backend commit 0b58057 made those
 *    volumes user-editable, and S01 now writes and reads them back. They stay ADVISORY rather than
 *    DESTRUCTIBLE because S01 DOES clean them up. The shared Other-Costs(19) volume is the one field the
 *    original claim still holds for — S01 never writes it.
 *
 *    UPDATED 2026-08-11 — the cleanup no longer *has* to happen at the DB. It did while the backend's
 *    `!= null` guard made a blanking PUT a silent no-op on those fields (defects.md BUG-2, issue #260);
 *    that is fixed in commit `3ee9ff2`, so the restore PUT clears them through the API and the
 *    `sch1_db_restore.py blank-guarded` call is now a redundant safety net. The ADVISORY
 *    classification is unchanged either way.
 *
 * Cost on 143/144/139/140 is intentionally NOT inspected: it is pulled from Schedule 3 / derived
 * server-side, so a non-null cost is normal on an empty Draft.
 */
function classifyMutableTarget(doc: ScheduleDoc): TargetFindings {
  const items = doc.lineItems ?? [];
  const byCode = (code: number): ResponseLineItem | undefined =>
    items.find((li) => li.costItemCode === code);
  const present = (v: number | null | undefined): boolean => v !== null && v !== undefined;
  const s = doc.silviculture;

  const destructible: string[] = [];
  if (doc.comments?.trim()) destructible.push('comments');
  for (const code of [12, 13, 14, 15, 16, 17, 18]) {
    const li = byCode(code);
    if (li && (present(li.volume) || present(li.cost))) destructible.push(`lineItem ${code} (vol/cost)`);
  }
  if (present(s?.actualSpent?.volume) || present(s?.actualSpent?.cost))
    destructible.push('silviculture actualSpent(1)');
  if (present(s?.accruedLessActual?.volume) || present(s?.accruedLessActual?.cost))
    destructible.push('silviculture accruedLessActual(2)');
  if ((doc.otherCosts?.count ?? 0) !== 0)
    destructible.push(`otherCosts.count=${doc.otherCosts?.count} (itemized rows present)`);

  const advisory: string[] = [];
  for (const code of [143, 144]) {
    const li = byCode(code);
    if (li && present(li.volume)) advisory.push(`lineItem ${code} volume`);
  }
  if (present(s?.lessAdmin?.volume)) advisory.push('silviculture lessAdmin(139) volume');
  if (present(s?.total?.volume)) advisory.push('silviculture total(140) volume');
  if (present(doc.otherCosts?.volume)) advisory.push('shared Other-Costs(19) volume');

  return { destructible, advisory };
}

/**
 * Suite PREFLIGHT — runs once (as the `setup` project the `chromium` project depends on) before any
 * scenario. It asserts the pinned real-data anchors still resolve in the loaded DB, so a stale /
 * re-extracted DB (or a backend that booted before its DB) fails HERE with ONE clear message instead of
 * dozens of confusing mid-suite failures. All checks go through the app's own API — no Oracle client at
 * runtime — and are read-only.
 */

const REGROUND =
  'Re-ground: reload the real extract (README, "Seeded Oracle DB"), evict the reference-data cache or restart the ' +
  'backend, then re-verify the pinned Schedule 1 anchors in fixtures/sch1/schedule1-test-data.ts.';

async function getDraft(
  request: APIRequestContext,
  millId: number,
  year: number,
): Promise<ScheduleDoc> {
  const url = scheduleUrl(millId, year);
  const res = await request.get(url);
  expect(
    res.ok(),
    `[preflight] Schedule 1 anchor ${millId}/${year} — GET ${url} returned HTTP ${res.status()}. ${REGROUND}`,
  ).toBeTruthy();
  return res.json();
}

test('preflight: Schedule 1 read-only anchor resolves (editable Draft)', async ({ request }) => {
  const doc = await getDraft(request, READONLY_ANCHOR.millId, READONLY_ANCHOR.year);
  expect(
    doc.trackStatus,
    `[preflight] read-only anchor ${READONLY_ANCHOR.millId}/${READONLY_ANCHOR.year} is not a Draft. ${REGROUND}`,
  ).toBe('D');
});

test('preflight: Schedule 1 mutable target resolves (empty, editable Draft)', async ({ request }, testInfo) => {
  const doc = await getDraft(request, MUTABLE_DRAFT.millId, MUTABLE_DRAFT.year);
  const at = `${MUTABLE_DRAFT.millId}/${MUTABLE_DRAFT.year}`;
  expect(
    doc.editable && doc.trackStatus === 'D',
    `[preflight] mutable target ${at} must be an editable Draft ` +
      `(the S01 save test writes here and restores it). ${REGROUND}`,
  ).toBeTruthy();

  // Empty means NULL-valued, not row-less. The page renders line items 12-18 only from `lineItems`
  // entries (components/schedule1/index.tsx), so S01's first fill (#vol-12) needs the rows to EXIST.
  // Since defect #296 a detail-less target still GETs 200 as an editable Draft, so only this check
  // stands between a thin seed and S01 timing out mid-suite on an input that never renders (it did:
  // until 2026-08-27 the seed carried no detail rows, S01 failed every first attempt, and its own
  // teardown blank-PUT healed the anchor so the retry passed — a permanent fail-once).
  const missingRows = [12, 13, 14, 15, 16, 17, 18].filter(
    (code) => !(doc.lineItems ?? []).some((li) => li.costItemCode === code),
  );
  expect(
    missingRows.length === 0,
    `[preflight] mutable target ${at} has no stored detail row for line item(s) ${missingRows.join(
      ', ',
    )} — the form cannot render their inputs, so S01's first fill will time out. Seed NULL-valued ` +
      `rows for them (db-e2e/R__80_e2e_anchor_seed.sql). ${REGROUND}`,
  ).toBeTruthy();

  // Guardrail against silent seed destruction. S01 writes here and its cleanup blanks the writable
  // fields, which is lossless ONLY if the target started empty. Split the check so the message is
  // actionable: DESTRUCTIBLE fields are a real data-loss / precondition risk (hard fail); ADVISORY
  // fields are drift the backend's write-time null-guards protect from S01 (warn, let the safe run go).
  const { destructible, advisory } = classifyMutableTarget(doc);

  if (advisory.length > 0) {
    const msg =
      `[preflight] mutable target ${at} is non-pristine at: ${advisory.join(', ')}. S01 does NOT write ` +
      `these (the backend null-guards them on write), so THIS run is safe — but the anchor has drifted ` +
      `from the empty Draft it is pinned as; re-verify the pin. ${REGROUND}`;
    console.warn(msg);
    testInfo.annotations.push({ type: 'warning', description: msg });
  }

  expect(
    destructible.length === 0,
    `[preflight] mutable target ${at} is a Draft but NOT empty at: ${destructible.join(', ')}. S01's ` +
      `blank-restore would overwrite these real seeded values (or a stale itemized row breaks its ` +
      `count:0 precondition). Pick a different empty editable Draft for MUTABLE_DRAFT, or snapshot/` +
      `restore it like the delete/retry targets. ${REGROUND}`,
  ).toBeTruthy();
});
