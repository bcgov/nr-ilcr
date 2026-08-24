import { type APIRequestContext, expect } from '@playwright/test';
import {
  type ScheduleKey,
  type SubPageType,
  isMutatingAnchor,
  locationDeleteUrl,
  locationsUrl,
  rowsUrl,
  scheduleUrl,
} from '../../fixtures/sch4/schedule4-test-data';

/**
 * Thin API helpers for the Schedule 4 document and its sub-resources. Used to SEED a precondition (a
 * location that already exists, a sub-page row already on file), to READ BACK what the UI wrote, and to
 * restore an anchor at teardown. The specs stay pure-UI: these run only inside Given preconditions,
 * read-back Thens and cleanup — always through the app's own real endpoints, never direct SQL (Schedule
 * 4 exposes GET/PUT/POST/DELETE for everything this suite creates, so the skill's "prefer the app's own
 * endpoint" rule applies and no DB fallback is needed).
 *
 * NO check-status helper lives here on purpose. Check Status is asserted entirely through the UI — the
 * rendered notifications — because what matters is what the reporter is shown, not what the endpoint
 * returns (the endpoint itself is already covered by the backend's own `Schedule4CheckStatusIT`). An
 * API helper here would only ever have re-proved the backend.
 */

/** One transportation-category amount as served by `GET /api/v1/schedule4`. */
export interface Sch4Category {
  code: number;
  kind: 'FIXED' | 'DISTANCE';
  /** Jackson is configured non_null, so an absent member arrives as `undefined`, never `null`. */
  volume?: number | null;
  cost?: number | null;
  distance?: number | null;
  /** Server-derived $/m³ (cost ÷ volume) — never sent by the client. */
  perUnit?: number | null;
}

/** One sub-page list row (Towing 43 / Truck Rehaul 46 [cycle] / Other 55). */
export interface Sch4Row {
  id: number;
  code: number;
  description?: string | null;
  distance?: number | null;
  volume?: number | null;
  cost?: number | null;
  cycle?: number | null;
  perUnit?: number | null;
}

/** One dump location = a family of TRANSPORTATION_REPORT rows sharing a LOCATION_DESCRIPTION. */
export interface Sch4Location {
  /** The primary report id — the rename-safe write/delete handle. */
  id: number | null;
  /** The primary report's optimistic-lock token, echoed for edits. */
  revisionCount: number | null;
  name: string;
  comments?: string | null;
  categories: Sch4Category[];
  subPageRows: Sch4Row[];
}

/** The Schedule 4 document (`Schedule4Response`). */
export interface Sch4Document {
  millId: number;
  year: number;
  trackStatus: string | null;
  editable: boolean;
  locations: Sch4Location[];
  message?: { key: string; text: string } | null;
}

/** One category amount to seed. `distance` is ignored server-side for the 9 fixed codes. */
export interface SeedCategory {
  code: number;
  volume?: number | null;
  cost?: number | null;
  distance?: number | null;
}

/** A location to seed (create when `id` is absent, edit when present). */
export interface SeedLocation {
  name: string;
  comments?: string | null;
  categories?: SeedCategory[];
}

/** A sub-page row to seed. `cycle` is written for TRUCK_REHAUL only (the server ignores it otherwise). */
export interface SeedRow {
  type: SubPageType;
  description: string;
  distance?: number | null;
  volume?: number | null;
  cost?: number | null;
  cycle?: number | null;
}

/** GET the document, failing loud on a non-200 (a drifted anchor must not look like a UI bug). */
export async function getSchedule4(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<Sch4Document> {
  const res = await request.get(scheduleUrl(millId, year));
  await expect(res, `GET schedule4 ${millId}/${year} returned HTTP ${res.status()}`).toBeOK();
  return (await res.json()) as Sch4Document;
}

/** The location with `name` on this anchor, or undefined. Names are unique per (mill, year) — BR-02. */
export function findLocation(doc: Sch4Document, name: string): Sch4Location | undefined {
  return doc.locations.find((location) => location.name === name);
}

/** The location with `name`, failing loud when it is absent (a read-back that must find it). */
export function requireLocation(doc: Sch4Document, name: string): Sch4Location {
  const found = findLocation(doc, name);
  expect(
    found,
    `location "${name}" is not in the Schedule 4 document for ${doc.millId}/${doc.year} (have: ${doc.locations
      .map((l) => l.name)
      .join(', ')})`,
  ).toBeTruthy();
  return found!;
}

/** A location's stored amount for one category code, or undefined when the category was never entered. */
export function category(location: Sch4Location, code: number): Sch4Category | undefined {
  return location.categories.find((c) => c.code === code);
}

/** A location's stored sub-page rows for one sub-page code (43 / 46 / 55). */
export function rowsOfType(location: Sch4Location, code: number): Sch4Row[] {
  return location.subPageRows.filter((row) => row.code === code);
}

/**
 * Save a location through the app's own endpoint, to SEED a precondition (never to assert).
 *
 * Reads the current document first rather than taking an id/token from the caller: hard-coding one
 * would make the helper itself the thing under test, and an edit legitimately needs the CURRENT
 * `revisionCount` (a stale token is a 409 by design).
 */
export async function saveLocation(
  request: APIRequestContext,
  key: ScheduleKey,
  seed: SeedLocation,
): Promise<Sch4Document> {
  const current = await getSchedule4(request, key);
  const existing = findLocation(current, seed.name);
  const res = await request.put(locationsUrl(key.millId, key.year), {
    data: {
      id: existing?.id ?? null,
      revisionCount: existing?.revisionCount ?? null,
      name: seed.name,
      comments: seed.comments ?? null,
      categories: (seed.categories ?? []).map((c) => ({
        code: c.code,
        volume: c.volume ?? null,
        cost: c.cost ?? null,
        distance: c.distance ?? null,
      })),
    },
  });
  await expect(
    res,
    `seed PUT schedule4 location "${seed.name}" on ${key.millId}/${key.year} returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeOK();
  return (await res.json()) as Sch4Document;
}

/** Add one sub-page row to a seeded location (precondition only). */
export async function addRow(
  request: APIRequestContext,
  key: ScheduleKey,
  locationId: number,
  seed: SeedRow,
): Promise<Sch4Document> {
  const res = await request.post(rowsUrl(key.millId, key.year, locationId), {
    data: {
      type: seed.type,
      description: seed.description,
      distance: seed.distance ?? null,
      volume: seed.volume ?? null,
      cost: seed.cost ?? null,
      cycle: seed.cycle ?? null,
    },
  });
  await expect(
    res,
    `seed POST schedule4 ${seed.type} row on location ${locationId} (${key.millId}/${key.year}) returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeOK();
  return (await res.json()) as Sch4Document;
}

/** Delete one location family (primary + distance children + sub-page rows + cascaded details). */
export async function deleteLocation(
  request: APIRequestContext,
  key: ScheduleKey,
  id: number,
): Promise<void> {
  const res = await request.delete(locationDeleteUrl(key.millId, key.year, id));
  await expect(
    res,
    `DELETE schedule4 location ${id} on ${key.millId}/${key.year} returned HTTP ${res.status()}`,
  ).toBeOK();
}

/**
 * Restore an anchor to its at-rest state: NO locations.
 *
 * Every mutating anchor is pinned as empty at rest and is owned by exactly one scenario (see the
 * fixture's PARALLEL SAFETY note), so "delete every location here" is both correct and total — it
 * cleans up a location the UI created under a name this suite never had to predict, which a
 * name-keyed teardown could not.
 *
 * GUARDED: refuses to run against a (mill, year) that is not on the fixture's mutating allow-list, so
 * a future copy-paste can never point this at an anchor holding real extract data.
 *
 * Then PROVES the anchor is genuinely back to empty (fail loud): residue would break the next run's
 * "the list shows only what I created" assumption and, worse, make a later Check Status scenario read a
 * value it never entered.
 */
export async function restoreAnchor(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<string[]> {
  expect(
    isMutatingAnchor(key),
    `refusing to clean up ${key.millId}/${key.year}: it is not one of the sch4 MUTATING_ANCHORS. ` +
      'Add it to the fixture (and to preflight) before writing to it.',
  ).toBe(true);

  const before = await getSchedule4(request, key);
  const removed: string[] = [];
  for (const location of before.locations) {
    if (location.id === null) continue; // defensive: a nameless family has no delete handle
    await deleteLocation(request, key, location.id);
    removed.push(location.name);
  }
  const after = await getSchedule4(request, key);
  expect(
    after.locations.map((l) => l.name),
    `Schedule 4 anchor ${key.millId}/${key.year} still holds locations after cleanup — the seeded DB is left mutated`,
  ).toEqual([]);
  return removed;
}
