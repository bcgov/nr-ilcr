import { type APIRequestContext, expect } from '@playwright/test';
import {
  type ScheduleKey,
  recordDeleteUrl,
  scheduleUrl,
} from '../../fixtures/sch6/schedule6-test-data';

/**
 * Schedule 6 API reads — the black-box read-back the Thens assert against, and the cleanup the
 * registry calls.
 *
 * WHY READ BACK THROUGH THE API AT ALL, when the screen already shows the record: after `Add Report`
 * the page re-seeds its row forms from the RESPONSE (index.tsx:796-799), so the row looks identical
 * whether or not anything reached the database. A UI-only assertion therefore proves rendering, not
 * persistence. The same trap sch5's S01 header calls out.
 */

/** One served road record, as the document carries it. */
export interface RoadRecord {
  recordId: number;
  areaType: string | null;
  tflNumber: string | null;
  supplyBlock: string | null;
  rmg: string | null;
  volume: number | null;
  cost: number | null;
  costPerVolume: number | null;
  comments: string | null;
  revisionCount: number;
}

/** The Schedule 6 document, narrowed to what the sch6 steps read. */
export interface Schedule6Doc {
  millId: number;
  year: number;
  trackStatus: string | null;
  editable: boolean;
  roadRecords: RoadRecord[];
  totalVolume: number | null;
  totalCost: number | null;
  totalCostPerVolume: number | null;
  generalComments?: string | null;
}

/** GET the Schedule 6 document, failing with the status when it does not resolve. */
export async function readSchedule6(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<Schedule6Doc> {
  const res = await request.get(scheduleUrl(key.millId, key.year));
  await expect(
    res,
    `GET Schedule 6 ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();
  return (await res.json()) as Schedule6Doc;
}

/**
 * Every record on a (mill, year) whose per-record comment matches exactly.
 *
 * The COMMENT is the cleanup handle because a road record has no unique natural key: the same
 * TSA/Supply Block pair may legitimately be recorded more than once, so matching on classification
 * could delete a row the scenario did not create. Each scenario therefore writes a comment it owns.
 */
export async function findRecordsByComment(
  request: APIRequestContext,
  key: ScheduleKey,
  comments: string,
): Promise<RoadRecord[]> {
  const doc = await readSchedule6(request, key);
  return doc.roadRecords.filter((r) => (r.comments ?? '') === comments);
}

/**
 * Delete one road record.
 *
 * NO `revisionCount`: this endpoint carries no optimistic-lock token, matching legacy's row Delete
 * (Schedule6MB.remove :208-218, deviation (c2)). Passing one would be inventing a contract — and
 * Schedule 5's camp DELETE *does* require it, so the difference is easy to get wrong.
 */
export async function removeRecord(
  request: APIRequestContext,
  key: ScheduleKey,
  recordId: number,
): Promise<void> {
  const res = await request.delete(recordDeleteUrl(recordId, key.millId, key.year));
  await expect(
    res,
    `DELETE Schedule 6 record ${recordId} on ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();
}

/**
 * Remove every record carrying `comments` from (mill, year), and PROVE the anchor is back at rest.
 *
 * Deleting all matches rather than the first is deliberate: a killed run can leave more than one, and a
 * cleanup that removes one of two leaves the anchor dirty while reporting success — which the next
 * run then sees as a preflight failure pointing at the wrong scenario.
 *
 * The read-back is the half that makes this a contract rather than a best effort. `removeRecord`
 * already fails on a non-OK status, but a DELETE that returns 200 while the row survives would
 * otherwise go unnoticed until the next run's preflight.
 */
export async function removeRecordsByComment(
  request: APIRequestContext,
  key: ScheduleKey,
  comments: string,
): Promise<void> {
  for (const record of await findRecordsByComment(request, key, comments)) {
    await removeRecord(request, key, record.recordId);
  }

  const left = await findRecordsByComment(request, key, comments);
  expect(
    left.map((r) => r.recordId),
    `Schedule 6 record(s) commented "${comments}" survived cleanup on ${key.millId}/${key.year}`,
  ).toEqual([]);
}
