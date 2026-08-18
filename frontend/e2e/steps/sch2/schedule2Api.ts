import { type APIRequestContext, expect } from '@playwright/test';
import {
  type ScheduleKey,
  scheduleUrl,
} from '../../fixtures/sch2/schedule2-test-data';

/**
 * Thin API helpers for the Schedule 2 aggregate document. Used to seed a precondition (a schedule that
 * has already been saved once), to READ BACK what the UI wrote, and to restore an anchor at teardown.
 * The specs stay pure-UI: these run only inside Given preconditions, read-back Thens and cleanup,
 * always through the app's own real endpoints (never direct SQL — Schedule 2 exposes GET/PUT/DELETE,
 * so the skill's "prefer the app's own endpoint" rule applies and no DB fallback is needed).
 */

/**
 * One cost block as served by `GET /api/v1/schedule2`.
 *
 * Every member is OPTIONAL because Jackson is configured non_null: a null column is omitted from the
 * JSON entirely, so it arrives as `undefined` rather than `null`. Treat absent and null as the same
 * "no value".
 */
export interface Sch2CostBlock {
  volume?: number | null;
  cost?: number | null;
  perUnit?: number | null;
}

/** The Schedule 2 document (`Schedule2Response`). */
export interface Sch2Document {
  millId: number;
  year: number;
  trackStatus: string | null;
  editable: boolean;
  /** ABSENT (undefined) when the schedule has never been saved — the unsaved-at-rest token. */
  revisionCount?: number | null;
  comments?: string | null;
  purchasedLogCost: Sch2CostBlock;
  purchasedWoodOverhead: Sch2CostBlock;
  subtotal: Sch2CostBlock;
  lessLogSales: Sch2CostBlock;
  netPurchased: Sch2CostBlock;
  totalCompanyLogging: Sch2CostBlock;
  totalAverage: Sch2CostBlock;
  message?: { key: string; text: string } | null;
}

/** The values a seeded (already-saved) precondition carries. Each is nullable so a slice can omit one. */
export interface SeedSchedule2 {
  purchasedLogCostCost?: number | null;
  lessLogSalesVolume?: number | null;
  lessLogSalesCost?: number | null;
  comments?: string | null;
}

/**
 * NO check-status helper lives here on purpose. Check Status is asserted entirely through the UI — the
 * rendered notification — because what matters is what the reporter is shown, not what the endpoint
 * returns (the endpoint itself is already covered by `Schedule2CheckStatusIT`). An API helper here
 * would only ever have re-proved the backend.
 */

/** GET the document, failing loud on a non-200 (a drifted anchor must not look like a UI bug). */
export async function getSchedule2(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<Sch2Document> {
  const res = await request.get(scheduleUrl(millId, year));
  await expect(res, `GET schedule2 ${millId}/${year} returned HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as Sch2Document;
}

/**
 * Save the schedule through the app's own endpoint, to SEED a precondition (never to assert).
 *
 * Reads the current `revisionCount` first rather than taking a token from the caller: hard-coding one
 * would make the helper itself the thing under test, and an unsaved anchor legitimately has none (the
 * write contract sends 0 for a brand-new schedule).
 */
export async function saveSchedule2(
  request: APIRequestContext,
  key: ScheduleKey,
  seed: SeedSchedule2,
): Promise<Sch2Document> {
  const current = await getSchedule2(request, key);
  const res = await request.put(scheduleUrl(key.millId, key.year), {
    data: {
      revisionCount: current.revisionCount ?? 0,
      comments: seed.comments ?? null,
      purchasedLogCostCost: seed.purchasedLogCostCost ?? null,
      lessLogSalesVolume: seed.lessLogSalesVolume ?? null,
      lessLogSalesCost: seed.lessLogSalesCost ?? null,
    },
  });
  await expect(res, `seed PUT schedule2 ${key.millId}/${key.year} returned HTTP ${res.status()}: ${await res.text()}`).toBeOK();
  return (await res.json()) as Sch2Document;
}

/**
 * Restore an anchor to its at-rest UNSAVED state.
 *
 * Schedule 2 has no per-row sub-resource — the whole schedule is one summary plus its two detail rows —
 * so DELETE is the cleanup. It is idempotent by contract (`Schedule2Service.deleteSchedule2` returns
 * early when no summary exists), so cleaning an anchor a scenario never actually wrote to is a no-op
 * rather than an error.
 *
 * Then PROVES the anchor is genuinely back to unsaved (fail loud): residue would break the next run's
 * "empty document" assumption and, worse, make a later Check Status scenario read a value it never
 * entered.
 */
export async function restoreSchedule2(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<void> {
  const res = await request.delete(scheduleUrl(key.millId, key.year));
  await expect(res, `cleanup DELETE schedule2 ${key.millId}/${key.year} returned HTTP ${res.status()}`).toBeOK();
  const after = await getSchedule2(request, key);
  expect(
    after.revisionCount ?? null,
    `Schedule 2 anchor ${key.millId}/${key.year} still holds a saved summary after cleanup — the seeded DB is left mutated`,
  ).toBeNull();
}

/** Read a block's stored numbers as a plain triple, normalising absent → null. */
export function blockValues(block: Sch2CostBlock): [number | null, number | null, number | null] {
  return [block.volume ?? null, block.cost ?? null, block.perUnit ?? null];
}
