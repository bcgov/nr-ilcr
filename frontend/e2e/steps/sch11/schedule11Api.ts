import { type APIRequestContext, expect } from '@playwright/test';
import {
  type ScheduleKey,
  locationsUrl,
  scheduleUrl,
} from '../../fixtures/sch11/schedule11-test-data';

/**
 * Thin API helpers for the Schedule 11 locations sub-resource (Schedule11Api). Used to seed a
 * precondition row, to read back writes the UI made, and to clean up rows a scenario created. The
 * specs stay pure-UI: these run only inside Given preconditions and teardown, always through the app's
 * own real endpoints (never direct SQL — Schedule 11 exposes a full CRUD surface, so the skill's
 * "prefer the app's DELETE endpoint" rule applies and no DB fallback is needed).
 */

/**
 * One location row as served by `GET /api/v1/schedule11` (`SilvicultureLocation`).
 *
 * Every nullable member is declared OPTIONAL because Jackson is configured non_null: a null column is
 * omitted from the JSON entirely, so it arrives as `undefined` rather than `null`. Treat absent and null
 * as the same "no value" (see `asText` in schedule11.steps.ts).
 */
export interface Sch11Location {
  locationId: number;
  location: string;
  enhancedIndicator: boolean;
  biogeoclimaticCatalogueId: number;
  becLabel?: string | null;
  netArea?: number | null;
  actualCost?: number | null;
  plannedCost?: number | null;
  totalCost?: number | null;
  costPerNetArea?: number | null;
  comments?: string | null;
  revisionCount: number;
}

/** Footer totals (`SilvicultureTotals`) — any field with no contributors is ABSENT, never zero. */
export interface Sch11Totals {
  netArea?: number | null;
  actualCost?: number | null;
  plannedCost?: number | null;
  totalCost?: number | null;
  costPerNetArea?: number | null;
}

/** The Schedule 11 document (`Schedule11Response`). */
export interface Sch11Document {
  millId: number;
  year: number;
  trackStatus: string | null;
  editable: boolean;
  locations: Sch11Location[];
  totals: Sch11Totals;
  message?: { key: string; text: string } | null;
}

/**
 * NO check-status helper lives here on purpose. Check Status is asserted entirely through the UI — the
 * rendered result region, including `checkResultRawText()` for FLD-004's verbatim double space — because
 * what matters is what the user is shown, not what the endpoint returns (the endpoint is already covered
 * by `Schedule11CheckStatusIT`). An API helper here would only ever have re-proved the backend.
 */

/** The fields a seeded precondition row carries. Costs are nullable so S05/S06 can omit one. */
export interface SeedLocation {
  location: string;
  enhancedIndicator: boolean;
  biogeoclimaticCatalogueId: number;
  netArea: number;
  actualCost?: number | null;
  plannedCost?: number | null;
  comments?: string | null;
}

/** GET the Schedule 11 document, failing loud on a non-200 (a drifted anchor must not look like a UI bug). */
export async function getSchedule11(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
): Promise<Sch11Document> {
  const res = await request.get(scheduleUrl(millId, year));
  expect(res.ok(), `GET schedule11 ${millId}/${year} returned HTTP ${res.status()}`).toBeTruthy();
  return (await res.json()) as Sch11Document;
}

/** POST one location and return its assigned id. Used only to SEED a precondition, never to assert. */
export async function addLocation(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
  seed: SeedLocation,
): Promise<number> {
  const res = await request.post(locationsUrl(millId, year), {
    data: {
      actualCost: null,
      plannedCost: null,
      comments: null,
      ...seed,
    },
  });
  expect(
    res.ok(),
    `seed POST location "${seed.location}" on ${millId}/${year} returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeTruthy();
  const doc = (await res.json()) as Sch11Document;
  const added = doc.locations.find((l) => l.location === seed.location);
  expect(added, `seeded location "${seed.location}" not present in the POST response`).toBeTruthy();
  return added!.locationId;
}

/**
 * The page-level save (`PUT /locations`, `LocationSaveAllRequest`): the rows to update and the ids to
 * delete, in one transaction. The only write the page makes besides Add (Story 26.2).
 */
async function saveAll(
  request: APIRequestContext,
  { millId, year }: ScheduleKey,
  locations: { basicSilvicultureReportId: number; location: Record<string, unknown> }[],
  deletedIds: number[],
) {
  return request.put(locationsUrl(millId, year), { data: { locations, deletedIds } });
}

/**
 * Save one location through the app's own page-level save, using whatever `revisionCount` the row
 * currently carries. Used to simulate **another session** changing a row while the browser holds an open editor
 * (GAP-3): this write succeeds and bumps the token, so the browser's pending save is then stale.
 *
 * Deliberately re-reads the row first rather than taking a token from the caller — hard-coding one would
 * make the helper itself the thing under test.
 */
export async function editLocationAsAnotherSession(
  request: APIRequestContext,
  key: ScheduleKey,
  marker: string,
  changes: Partial<SeedLocation>,
): Promise<Sch11Location> {
  const row = await locationByMarker(request, key, marker);
  const res = await saveAll(
    request,
    key,
    [
      {
        basicSilvicultureReportId: row.locationId,
        location: {
          location: row.location,
          enhancedIndicator: row.enhancedIndicator,
          biogeoclimaticCatalogueId: row.biogeoclimaticCatalogueId,
          netArea: row.netArea,
          actualCost: row.actualCost ?? null,
          plannedCost: row.plannedCost ?? null,
          comments: row.comments ?? null,
          revisionCount: row.revisionCount,
          ...changes,
        },
      },
    ],
    [],
  );
  expect(
    res.ok(),
    `concurrent save on "${marker}" (${key.millId}/${key.year}) returned HTTP ${res.status()}: ${await res.text()}`,
  ).toBeTruthy();
  const after = await locationByMarker(request, key, marker);
  // The whole point is that the token MOVED — if it didn't, the browser's save would not be stale and the
  // scenario would prove nothing.
  expect(
    after.revisionCount,
    `concurrent edit must bump revisionCount (was ${row.revisionCount})`,
  ).not.toBe(row.revisionCount);
  return after;
}

/** Every location currently on the schedule whose `location` text equals `marker`. */
export async function locationsByMarker(
  request: APIRequestContext,
  key: ScheduleKey,
  marker: string,
): Promise<Sch11Location[]> {
  const doc = await getSchedule11(request, key);
  return doc.locations.filter((l) => l.location === marker);
}

/**
 * The single location carrying `marker`, asserting exactly one exists. This is the read-back used to
 * PROVE a UI write persisted — the skill's "verify by API read-back" rule: assert the full stored
 * record, not merely that a toast appeared.
 */
export async function locationByMarker(
  request: APIRequestContext,
  key: ScheduleKey,
  marker: string,
): Promise<Sch11Location> {
  const matches = await locationsByMarker(request, key, marker);
  expect(
    matches.length,
    `expected exactly one Schedule 11 location "${marker}" on ${key.millId}/${key.year}, found ${matches.length}`,
  ).toBe(1);
  return matches[0];
}

/**
 * Delete every location carrying `marker` in one page-level save, then PROVE none remain (fail loud).
 * Only rows still served are sent, so a row the UI already deleted — the S07 happy path — is simply not
 * in the list. The save is ATOMIC: a 404 means NOTHING was deleted (one id vanished between the read and
 * the save and the whole request rolled back), so it is a failure here, not "already gone".
 */
export async function deleteLocationsByMarker(
  request: APIRequestContext,
  key: ScheduleKey,
  marker: string,
): Promise<void> {
  const ids = (await locationsByMarker(request, key, marker)).map((row) => row.locationId);
  if (ids.length > 0) {
    const res = await saveAll(request, key, [], ids);
    expect(
      res.status(),
      `delete-save of locations ${ids.join(', ')} ("${marker}") returned HTTP ${res.status()}: ${await res.text()}`,
    ).toBe(200);
  }
  const remaining = await locationsByMarker(request, key, marker);
  expect(
    remaining.length,
    `Schedule 11 locations "${marker}" left behind on ${key.millId}/${key.year} after cleanup`,
  ).toBe(0);
}
