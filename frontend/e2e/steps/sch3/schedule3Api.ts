import { type APIRequestContext, expect } from '@playwright/test';
import {
  ANCHORS,
  MUTATING_ANCHOR_KEYS,
  type EnteredLine,
  type ScheduleKey,
  otherAcceptableUrl,
  schedule1Url,
  scheduleUrl,
  unacceptableUrl,
} from '../../fixtures/sch3/schedule3-test-data';
import { applySch3Patch } from './schedule3DbRestore';

/**
 * Thin API helpers for the Schedule 3 aggregate document and its two sub-resources. Used to SEED a
 * precondition, to READ BACK what the UI wrote, and to restore an anchor at teardown. The specs stay
 * pure-UI: these run only inside Given preconditions, read-back Thens and cleanup — always through the
 * app's own real endpoints.
 *
 * NO check-status helper lives here on purpose. Check Status is asserted entirely through the UI — the
 * rendered notifications — because what matters is what the reporter is shown, not what the endpoint
 * returns (the endpoint itself is already covered by the backend's own `Schedule3CheckStatusIT`). An API
 * helper here would only ever have re-proved the backend.
 */

/** One fixed line as served by `GET /api/v1/schedule3`. Jackson omits nulls → absent, not null. */
export interface Sch3CostLine {
  costItemCode: number;
  harvest?: number | null;
  pop?: number | null;
  crown?: number | null;
}

/** A timber / overhead block. `volume` is entered (except Total Overhead's); the rest is derived. */
export interface Sch3TimberBlock {
  volume?: number | null;
  cost?: number | null;
  perUnit?: number | null;
}

export interface Sch3Total {
  harvest?: number | null;
  pop?: number | null;
  crown?: number | null;
}

/** The Schedule 3 document (`Schedule3Response`). */
export interface Sch3Document {
  millId: number;
  year: number;
  trackStatus: string | null;
  editable: boolean;
  revisionCount?: number | null;
  overrideHarvestTotalPop?: string | null;
  comments?: string | null;
  lineItems: Sch3CostLine[];
  popTimber: Sch3TimberBlock;
  crownTimber: Sch3TimberBlock;
  totalOverhead: Sch3TimberBlock;
  subtotalOtherCosts: Sch3Total;
  subtotalActualCosts: Sch3Total;
  includedUnacceptableCosts: Sch3Total;
  totalCosts: Sch3Total;
  otherAcceptableCount: number;
  unacceptableCount: number;
  warnings?: { key: string; text: string }[];
  message?: { key: string; text: string } | null;
}

/** One itemized other-acceptable group (an item-124 TOT + PO&P pair). */
export interface Sch3OtherAcceptableRow {
  id: number;
  description?: string | null;
  total?: number | null;
  pop?: number | null;
  crown?: number | null;
}

export interface Sch3OtherAcceptableDocument {
  editable: boolean;
  count: number;
  subtotal?: Sch3Total;
  rows?: Sch3OtherAcceptableRow[];
  message?: { key: string; text: string } | null;
}

/** One itemized included-unacceptable row (item 38). */
export interface Sch3UnacceptableRow {
  id: number;
  description?: string | null;
  total?: number | null;
}

export interface Sch3UnacceptableDocument {
  editable: boolean;
  count: number;
  subtotalTotal?: number | null;
  annualRentsTotal?: number | null;
  rows?: Sch3UnacceptableRow[];
  message?: { key: string; text: string } | null;
}

/** The Schedule 1 fields this suite reads — only enough to prove the BR-09 crown push landed. */
export interface Sch1VolumeProbe {
  revisionCount?: number | null;
  lineItems?: { costItemCode: number; volume?: number | null; cost?: number | null }[];
  silviculture?: Record<string, { volume?: number | null } | null>;
  otherCosts?: { volume?: number | null; count?: number };
}

/** All eleven fixed-line codes, in the app's own display order. */
const ALL_LINE_CODES = [27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37];

/** GET the document, failing loud on a non-200 (a drifted anchor must not look like a UI bug). */
export async function getSchedule3(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<Sch3Document> {
  const res = await request.get(scheduleUrl(millId, year));
  await expect(res, `GET schedule3 ${millId}/${year} returned HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as Sch3Document;
}

/** The raw status of a schedule3 GET — for the guard preconditions, which EXPECT a 404 / 409. */
export async function schedule3Status(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<{ status: number; detail: string }> {
  const res = await request.get(scheduleUrl(millId, year));
  const body = await res.text();
  let detail = '';
  try {
    detail = (JSON.parse(body) as { detail?: string }).detail ?? '';
  } catch {
    detail = body;
  }
  return { status: res.status(), detail };
}

/** A line's stored values, or undefined when the line carries nothing at all. */
export function line(doc: Sch3Document, code: number): Sch3CostLine | undefined {
  return doc.lineItems.find((li) => li.costItemCode === code);
}

/** A line's stored triple, normalising absent → null so an assertion reads as one value. */
export function lineValues(
  doc: Sch3Document,
  code: number,
): [number | null, number | null, number | null] {
  const found = line(doc, code);
  return [found?.harvest ?? null, found?.pop ?? null, found?.crown ?? null];
}

/** A derived total's triple, normalising absent → null. */
export function totalValues(total: Sch3Total): [number | null, number | null, number | null] {
  return [total.harvest ?? null, total.pop ?? null, total.crown ?? null];
}

/** A timber block's triple, normalising absent → null. */
export function blockValues(
  block: Sch3TimberBlock,
): [number | null, number | null, number | null] {
  return [block.volume ?? null, block.cost ?? null, block.perUnit ?? null];
}

/** The values a seeded (already-saved) precondition carries. */
export interface SeedSchedule3 {
  lines?: readonly EnteredLine[];
  popTimberVolume?: number | null;
  crownTimberVolume?: number | null;
  overrideHarvestTotalPop?: 'N' | 'Y';
  comments?: string | null;
}

/**
 * Save the schedule through the app's own endpoint, to SEED a precondition (never to assert).
 *
 * Reads the current `revisionCount` first rather than taking a token from the caller: hard-coding one
 * would make the helper itself the thing under test, and the token legitimately advances on every write.
 */
export async function saveSchedule3(
  request: APIRequestContext,
  key: ScheduleKey,
  seed: SeedSchedule3,
): Promise<Sch3Document> {
  const current = await getSchedule3(request, key);
  const byCode = new Map((seed.lines ?? []).map((l) => [l.code, l]));
  const res = await request.put(scheduleUrl(key.millId, key.year), {
    data: {
      revisionCount: current.revisionCount ?? 0,
      comments: seed.comments ?? null,
      overrideHarvestTotalPop: seed.overrideHarvestTotalPop ?? 'N',
      lineItems: ALL_LINE_CODES.map((code) => ({
        costItemCode: code,
        harvest: byCode.get(code)?.harvest ?? null,
        pop: byCode.get(code)?.pop ?? null,
      })),
      popTimberVolume: seed.popTimberVolume ?? null,
      crownTimberVolume: seed.crownTimberVolume ?? null,
    },
  });
  await expect(
    res,
    `seed PUT schedule3 ${key.millId}/${key.year} returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeOK();
  return (await res.json()) as Sch3Document;
}

// ---- the two sub-resources ------------------------------------------------------------------------

export async function getOtherAcceptable(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<Sch3OtherAcceptableDocument> {
  const res = await request.get(otherAcceptableUrl(millId, year));
  await expect(res, `GET other-acceptable ${millId}/${year} returned HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as Sch3OtherAcceptableDocument;
}

export async function getUnacceptable(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<Sch3UnacceptableDocument> {
  const res = await request.get(unacceptableUrl(millId, year));
  await expect(res, `GET unacceptable ${millId}/${year} returned HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as Sch3UnacceptableDocument;
}

/**
 * Replace the WHOLE other-acceptable group set (the batch endpoint the sub-page's own Save uses: rows
 * with a known id are updated, rows without one inserted, and anything absent deleted). Passing `[]` is
 * therefore how cleanup removes every group.
 */
export async function putOtherAcceptable(
  request: APIRequestContext,
  key: ScheduleKey,
  rows: { id?: number | null; description: string; total: number | null; pop: number | null }[],
): Promise<Sch3OtherAcceptableDocument> {
  const res = await request.put(otherAcceptableUrl(key.millId, key.year), {
    // `{ ...r, id: … }`, not `{ id: …, ...r }` — the spread has to come FIRST or it overwrites the
    // default and the `?? null` can never fire. Worse, an explicit `id: undefined` would serialize the
    // field away entirely, and absent-vs-null may well mean insert-vs-update to this batch endpoint
    // (the #292 rule, one layer up). Dead until a caller passes `id`; corrected in review.
    data: { rows: rows.map((r) => ({ ...r, id: r.id ?? null })) },
  });
  await expect(
    res,
    `PUT other-acceptable ${key.millId}/${key.year} returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeOK();
  return (await res.json()) as Sch3OtherAcceptableDocument;
}

/** Replace the WHOLE included-unacceptable row set; `[]` removes every row. */
export async function putUnacceptable(
  request: APIRequestContext,
  key: ScheduleKey,
  rows: { id?: number | null; description: string; total: number | null }[],
): Promise<Sch3UnacceptableDocument> {
  const res = await request.put(unacceptableUrl(key.millId, key.year), {
    // `{ ...r, id: … }`, not `{ id: …, ...r }` — the spread has to come FIRST or it overwrites the
    // default and the `?? null` can never fire. Worse, an explicit `id: undefined` would serialize the
    // field away entirely, and absent-vs-null may well mean insert-vs-update to this batch endpoint
    // (the #292 rule, one layer up). Dead until a caller passes `id`; corrected in review.
    data: { rows: rows.map((r) => ({ ...r, id: r.id ?? null })) },
  });
  await expect(
    res,
    `PUT unacceptable ${key.millId}/${key.year} returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeOK();
  return (await res.json()) as Sch3UnacceptableDocument;
}

// ---- Schedule 1, for the BR-09 crown push --------------------------------------------------------

/** GET Schedule 1 — the read-back that proves the crown push landed (or the 404 that proves it can't). */
export async function getSchedule1(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<Sch1VolumeProbe> {
  const res = await request.get(schedule1Url(millId, year));
  await expect(res, `GET schedule1 ${millId}/${year} returned HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as Sch1VolumeProbe;
}

// `schedule1Status()` was DELETED here on 2026-08-31 (raised in PR #402 review): it was callerless, and
// its doc still promised that a 404 means "Schedule 1 not opened" — a proxy defect #296 inverted, which
// is the whole reason `schedule1IsSaved` below exists. Use that.

/**
 * Whether a Schedule 3 has ever been SAVED for this mill/year — the category-3 summary exists.
 *
 * Same reasoning as {@link schedule1IsSaved}: since defect #296 an unsaved (or just-deleted) Schedule 3
 * answers 200 with an empty EDITABLE document instead of 404, so the HTTP status no longer distinguishes
 * "never saved" from "saved and blank". `revisionCount` does — the server issues it only once the summary
 * row exists, and omits it otherwise.
 */
export async function schedule3IsSaved(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<boolean> {
  const res = await request.get(scheduleUrl(key.millId, key.year));
  if (res.status() !== 200) {
    return false;
  }
  const doc = (await res.json()) as { revisionCount?: number | null };
  return doc.revisionCount != null;
}

/**
 * Whether a Schedule 1 has ever been SAVED for this mill/year — i.e. whether a category-1
 * `ILCR_REPORT_SUMMARY` row exists, which is what legacy's `isScheduleOpen()` reported and what the
 * BR-09 crown push actually branches on (`Schedule1Service.applyCrownTimberVolume`).
 *
 * WHY NOT THE HTTP STATUS. Until defect #296 an unsaved Schedule 1 answered 404, so
 * `schedule1Status(...) === 404` was a faithful proxy for "never opened". Since #296 the GET serves a
 * 200 empty EDITABLE document instead, so that proxy silently inverted — it is what broke the BR-09
 * preflight the moment the fix landed. The durable signal is the one the app itself uses
 * (`utils/schedule.ts` `isScheduleSaved`): `revisionCount` is the optimistic-lock token the server
 * issues only once the summary exists, and the backend omits null fields, so an unsaved document
 * carries no token at all. Loose `!= null` deliberately, matching the app.
 */
export async function schedule1IsSaved(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<boolean> {
  const res = await request.get(schedule1Url(millId, year));
  if (res.status() !== 200) {
    return false;
  }
  const doc = (await res.json()) as { revisionCount?: number | null };
  return doc.revisionCount != null;
}

/**
 * The volume the BR-09 push wrote to each of the THIRTEEN items it is documented to cover
 * (`Schedule1Service.CROWN_PUSH_VOLUME_ITEMS`): fixed lines 12-18, Forest Mgmt Admin (143), Subtotal
 * Company Logging (144) and the four silviculture rows (1/139/2/140). Keyed, so a failure names the item
 * that missed rather than a count.
 *
 * Keyed rather than counted DELIBERATELY. The push ALSO overwrites every item-19 Other-Costs row
 * (`updateAllOtherCostVolumes`), and whether such a row exists depends on history — Schedule 1's write
 * path creates the shared row, so the total number of stored volumes was 13 on a pristine anchor and 14
 * after this suite's own cleanup had PUT through it once. A count assertion therefore passed on the first
 * run and failed on the second; the contract it was trying to state is "all thirteen carry the pushed
 * value", which this expresses directly.
 */
export function schedule1PushedVolumes(doc: Sch1VolumeProbe): Record<string, number | null> {
  const byCode = new Map((doc.lineItems ?? []).map((li) => [li.costItemCode, li.volume ?? null]));
  const silviculture = doc.silviculture ?? {};
  const entries: [string, number | null][] = [
    ...[12, 13, 14, 15, 16, 17, 18, 143, 144].map(
      (code) => [`item ${code}`, byCode.get(code) ?? null] as [string, number | null],
    ),
    ['silviculture actualSpent (1)', silviculture['actualSpent']?.volume ?? null],
    ['silviculture accruedLessActual (2)', silviculture['accruedLessActual']?.volume ?? null],
    ['silviculture lessAdmin (139)', silviculture['lessAdmin']?.volume ?? null],
    ['silviculture total (140)', silviculture['total']?.volume ?? null],
  ];
  return Object.fromEntries(entries);
}

/** Every non-null stored Schedule 1 detail volume — what the BR-09 push writes. */
export function schedule1Volumes(doc: Sch1VolumeProbe): number[] {
  const fromLines = (doc.lineItems ?? [])
    .map((li) => li.volume)
    .filter((v): v is number => v !== null && v !== undefined);
  const fromSilviculture = Object.values(doc.silviculture ?? {})
    .map((block) => block?.volume)
    .filter((v): v is number => v !== null && v !== undefined);
  const shared = doc.otherCosts?.volume;
  return [...fromLines, ...fromSilviculture, ...(shared === null || shared === undefined ? [] : [shared])];
}

// ---- cleanup --------------------------------------------------------------------------------------

const MUTATING_KEYS: readonly string[] = MUTATING_ANCHOR_KEYS;

/** Is this (mill, year) one of the anchors this suite is allowed to write to? */
export function isMutatingAnchor({ millId, year }: ScheduleKey): boolean {
  return MUTATING_KEYS.some((k) => {
    const anchor = ANCHORS[k];
    return anchor.key.millId === millId && anchor.key.year === year;
  });
}

/**
 * Restore a mutating anchor to its at-rest state: an EMPTY Schedule 3 — no line amounts, no timber
 * volumes, no comments, Override "N", and no sub-page rows.
 *
 * Done through the app's own endpoints (a blanking PUT plus an empty batch-save on each sub-resource),
 * NOT at the DB, because Schedule 3 exposes every write this suite makes. The one thing the API cannot
 * undo is a DELETE — it removes the summary and there is no create path — so when the GET comes back
 * 404 the seed patch is re-applied first (it is idempotent and re-inserts exactly the summary it
 * originally added). `deletedSchedule1` does the same for the `crown-applied` anchor's patched
 * Schedule 1.
 *
 * GUARDED: refuses to run against a (mill, year) that is not on the fixture's mutating allow-list, so a
 * future copy-paste can never point this at an anchor holding real extract data.
 *
 * Then PROVES the anchor is genuinely back to empty (fail loud): residue would break the next run's
 * "an empty schedule" precondition and, worse, make a later Check Status scenario read a value it never
 * entered.
 */
export async function restoreAnchor(
  request: APIRequestContext,
  key: ScheduleKey,
  opts: { alsoRestoreSchedule1?: boolean } = {},
): Promise<void> {
  expect(
    isMutatingAnchor(key),
    `refusing to clean up ${key.millId}/${key.year}: it is not one of the sch3 MUTATING_ANCHOR_KEYS. ` +
      'Add it to the fixture (and to preflight) before writing to it.',
  ).toBe(true);

  // A destructive scenario may have removed the summary (and, on the crown anchor, Schedule 1).
  //
  // RE-GROUNDED 2026-08-26 (defect #296). Two things changed here. First, a deleted Schedule 3 now
  // answers 200-unsaved rather than 404, so the old `status === 404` test never fired and the sub-page
  // PUTs below then failed with "Schedule not found." — the sub-pages deliberately KEPT their 404.
  // Saved-ness is the signal now. Second, the app itself can put the summary back: Save creates on
  // absent, so the SQL patch is no longer the only way home and is now needed only for the category-1
  // Schedule 1 the crown anchor depends on.
  const sch3Unsaved = !(await schedule3IsSaved(request, key));
  const schedule1Missing =
    opts.alsoRestoreSchedule1 === true && !(await schedule1IsSaved(request, key));
  if (schedule1Missing) {
    applySch3Patch();
  }
  if (sch3Unsaved) {
    // Must precede either sub-page PUT: those still require an existing summary.
    await saveSchedule3(request, key, {});
  }

  await putOtherAcceptable(request, key, []);
  await putUnacceptable(request, key, []);
  await saveSchedule3(request, key, {});

  const after = await getSchedule3(request, key);
  const residue: string[] = [];
  for (const item of after.lineItems) {
    if (item.harvest !== null && item.harvest !== undefined) {
      residue.push(`line ${item.costItemCode} harvest=${item.harvest}`);
    }
    if (item.pop !== null && item.pop !== undefined && item.costItemCode !== 33) {
      // Scaling (33) PO&P is DERIVED from the two timber volumes, so it is null once they are — but it
      // is never a stored value this cleanup could leave behind.
      residue.push(`line ${item.costItemCode} pop=${item.pop}`);
    }
  }
  if (after.popTimber.volume !== null && after.popTimber.volume !== undefined) {
    residue.push(`popTimber.volume=${after.popTimber.volume}`);
  }
  if (after.crownTimber.volume !== null && after.crownTimber.volume !== undefined) {
    residue.push(`crownTimber.volume=${after.crownTimber.volume}`);
  }
  if (after.comments) {
    residue.push('comments');
  }
  if (after.overrideHarvestTotalPop === 'Y') {
    residue.push('overrideHarvestTotalPop=Y');
  }
  if (after.otherAcceptableCount !== 0) {
    residue.push(`otherAcceptableCount=${after.otherAcceptableCount}`);
  }
  // BOTH sub-page counts, not just the other-acceptable one. Until 2026-08-31 (raised in PR #402
  // review) a leftover item-38 row survived this "fails loud" teardown and surfaced instead as a
  // whole-domain failure in `preflight/sch3-anchors.setup.ts` ("every mutating Schedule 3 anchor is
  // EMPTY at rest", which DOES check it) — one whole run away from the scenario that caused it, i.e.
  // exactly the opposite of the fail-at-the-cause property the rest of this file is built on.
  //
  // Safe to assert unconditionally even though the count is not a plain row count: `Schedule3Service`
  // adds +1 when the Annual Rents harvest is present and non-zero (`Schedule3Response`'s own javadoc
  // says so). The blanking PUT above has already nulled Annual Rents, so that term cannot fire here and
  // 0 really does mean "no item-38 rows left".
  if (after.unacceptableCount !== 0) {
    residue.push(`unacceptableCount=${after.unacceptableCount}`);
  }
  expect(
    residue,
    `Schedule 3 anchor ${key.millId}/${key.year} is not back to empty after cleanup — the seeded DB is left mutated`,
  ).toEqual([]);

  if (opts.alsoRestoreSchedule1 === true) {
    // The BR-09 push writes Schedule 1's volume rows. The patched Schedule 1 is empty at rest, so
    // clearing every stored volume restores it; done at the app's own endpoint, and proven.
    await clearSchedule1Volumes(request, key);
  }
}

/**
 * Blank every Schedule 1 volume the BR-09 push can have written, through Schedule 1's own PUT, then
 * prove none is left. Only ever called for the `crown-applied` anchor, whose Schedule 1 is itself a
 * patched empty summary (so "no stored volume" IS its at-rest state).
 *
 * THAT PRECONDITION IS NOW ASSERTED, NOT ASSUMED (2026-08-31, raised in PR #402 review). The PUT below
 * is WIDER than the push it undoes — it nulls every line volume AND cost, both silviculture blocks, the
 * shared Other-Costs volume and the comments — so on a Schedule 1 holding real reporter data this is a
 * destructive write, and the only thing standing between the two was this comment.
 * `preflight/sch3-anchors.setup.ts` now carries "the crown-applied anchor Schedule 1 is EMPTY at rest,
 * not merely saved", which fails the whole run before a browser opens if that ever stops being true.
 * Do not widen the caller to another anchor without extending that gate (or teaching this function to
 * snapshot and restore, the way `steps/sch1/schedule1DbRestore.ts` does).
 */
export async function clearSchedule1Volumes(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<void> {
  const current = await getSchedule1(request, key);
  // Mirrors `frontend/src/interfaces/Schedule1Request.ts` — entered fields only. Every volume the
  // BR-09 push can write is represented, so one blanking PUT clears them all: the seven writable fixed
  // lines, the two silviculture amounts, the four volume-only fields (143/144/139/140) and the shared
  // Other-Costs volume.
  const res = await request.put(schedule1Url(key.millId, key.year), {
    data: {
      revisionCount: current.revisionCount ?? 0,
      comments: null,
      lineItems: [12, 13, 14, 15, 16, 17, 18].map((costItemCode) => ({
        costItemCode,
        volume: null,
        cost: null,
      })),
      silviculture: {
        actualSpent: { volume: null, cost: null },
        accruedLessActual: { volume: null, cost: null },
        lessAdminVolume: null,
        totalVolume: null,
      },
      otherCostsVolume: null,
      forestMgmtAdminVolume: null,
      subtotalCompanyLoggingVolume: null,
    },
  });
  await expect(
    res,
    `cleanup PUT schedule1 ${key.millId}/${key.year} returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeOK();
  const after = await getSchedule1(request, key);
  expect(
    schedule1Volumes(after),
    `Schedule 1 on ${key.millId}/${key.year} still holds pushed volumes after cleanup`,
  ).toEqual([]);
}
