import { type APIRequestContext, expect } from '@playwright/test';

import {
  campDeleteUrl,
  scheduleUrl,
  type ScheduleKey,
} from '../../fixtures/sch5/schedule5-test-data';

/**
 * Schedule 5 API helpers — black-box read-back and cleanup, through the app's own endpoints.
 *
 * WHY THE APP'S OWN DELETE RATHER THAN SQL: the camp family spans CAMP_REPORT plus its keyed
 * ILCR_COST_REPORT_DETAIL rows, and delivery carries the composite FK CMP_RPT_ILCR_RCAT_FK. The
 * endpoint unwinds all of that in the right order inside one transaction; a hand-rolled DELETE would
 * have to reproduce it and would drift the first time the write path changes.
 */

export interface CampAmount {
  volume: number | null;
  cost: number | null;
  costPerVolume: number | null;
}

export interface Camp {
  campId: number;
  campName: string;
  revisionCount: number;
  associatedCampVolume: number | null;
  campSubTotal: CampAmount;
  campTotal: CampAmount;
  accessExpenseTotal: CampAmount;
  campAndAccessTotal: CampAmount;
  /**
   * The twelve stored categories, each a served `CategoryAmount` carrying its own server-derived
   * `costPerVolume`. Only the ones a spec actually asserts are typed; the rest ride the index
   * signature rather than being enumerated twice (the authoritative list is GRID_ROWS in
   * `components/schedule5/validation.ts`).
   */
  cateringAndFood?: CampAmount;
  [category: string]: unknown;
}

export interface Schedule5Doc {
  millId: number;
  year: number;
  trackStatus: string | null;
  editable: boolean;
  camps: Camp[];
}

/**
 * Create a camp through the app's own POST — for a scenario whose PRECONDITION is an existing camp.
 *
 * Seeded through the API rather than SQL on purpose: the row family spans CAMP_REPORT plus twelve
 * keyed ILCR_COST_REPORT_DETAIL rows, and the server derives every total. Building that by hand would
 * both duplicate the write path and risk seeding a shape the app never produces — so the precondition
 * is created exactly the way a user would create it.
 */
export async function createCamp(
  request: APIRequestContext,
  key: ScheduleKey,
  body: Record<string, unknown>,
): Promise<Camp> {
  const res = await request.post(`/api/v1/schedule5/camps?millId=${key.millId}&year=${key.year}`, {
    data: body,
  });
  await expect(
    res,
    `POST camp "${String(body.campName)}" on ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();

  const doc = (await res.json()) as Schedule5Doc;
  const created = doc.camps.find((c) => c.campName === body.campName);
  expect(created, `camp "${String(body.campName)}" missing from the 200 response`).toBeDefined();
  return created!;
}

/** GET the served Schedule 5 document for a (mill, year). Fails loud on a non-2xx. */
export async function getSchedule5(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<Schedule5Doc> {
  const res = await request.get(scheduleUrl(key.millId, key.year));
  await expect(
    res,
    `GET Schedule 5 ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();
  return (await res.json()) as Schedule5Doc;
}

/** The camp with this name on the anchor, or undefined. Case-insensitive, matching BR-02's own rule. */
export async function findCampByName(
  request: APIRequestContext,
  key: ScheduleKey,
  campName: string,
): Promise<Camp | undefined> {
  const doc = await getSchedule5(request, key);
  return doc.camps.find((c) => c.campName?.toUpperCase() === campName.toUpperCase());
}

/**
 * Remove a camp by name if it exists, and PROVE it is gone.
 *
 * Idempotent on purpose: cleanup runs whether or not the scenario got as far as saving, so "already
 * absent" is success. The read-back afterwards is what makes this a guarantee rather than a hope — a
 * 200 from DELETE with the row still present would otherwise leave the anchor dirty for the next run,
 * and preflight would blame the following scenario.
 */
export async function removeCampByName(
  request: APIRequestContext,
  key: ScheduleKey,
  campName: string,
): Promise<void> {
  const camp = await findCampByName(request, key, campName);
  if (camp === undefined) {
    return;
  }

  const res = await request.delete(
    campDeleteUrl(camp.campId, key.millId, key.year, camp.revisionCount),
  );
  await expect(
    res,
    `DELETE camp ${camp.campId} ("${campName}") on ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();

  const still = await findCampByName(request, key, campName);
  expect(
    still,
    `camp "${campName}" still present on ${key.millId}/${key.year} after a 200 DELETE`,
  ).toBeUndefined();
}
