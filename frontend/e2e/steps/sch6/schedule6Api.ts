import { type APIRequestContext, expect } from '@playwright/test';
import {
  type ScheduleKey,
  addRecordUrl,
  checkStatusUrl,
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
 * The fields a road record is created with.
 *
 * Everything but `areaType`, `volume` and `comments` is OPTIONAL, because the write path genuinely
 * permits the gaps — and the Check Status slices are built on exactly those gaps:
 *  - `supplyBlock` omitted: a missing Supply Block is a Check Status finding, never a save failure
 *    (only its width is enforced on write), so S11's state is storable.
 *  - `cost` omitted: likewise storable, which is S09's state.
 *  - `tflNumber`: supplied only on the TFL branch, where BR-02 clears the TSA side instead.
 * The one gap that is NOT storable is a TFL record with no TFL number — the endpoint answers 400
 * FLD-002 — which is why S10 has to blank that field on screen rather than save it.
 */
export interface NewRoadRecord {
  areaType: string;
  supplyBlock?: string;
  tflNumber?: string;
  volume: number;
  cost?: number;
  comments: string;
}

/**
 * Create a road record through the app's own POST, and return it as the document now carries it.
 *
 * This is how a scenario whose SUBJECT is editing or deleting reaches its starting state: through the
 * same endpoint a reporter would use, so the row it then works on is a genuinely app-created record
 * rather than a hand-built one whose shape might not match. Preferred over seeding the row in the SQL
 * patch, which would also have to be mirrored into the CI seed as an explicit-id
 * ROAD_MAINTENANCE_REPORT row PLUS its ILCR_COST_REPORT_DETAIL children — and
 * `ROAD_MAINTENANCE_REPORT_ID` is not yet a parent column in `preflight/ci-seed-parity.setup.ts`, so
 * those detail rows would currently be reported as parentless.
 */
export async function addRecord(
  request: APIRequestContext,
  key: ScheduleKey,
  record: NewRoadRecord,
): Promise<RoadRecord> {
  const res = await request.post(addRecordUrl(key.millId, key.year), { data: record });
  await expect(
    res,
    `POST Schedule 6 record on ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();

  const doc = (await res.json()) as Schedule6Doc;
  const created = doc.roadRecords.find((r) => (r.comments ?? '') === record.comments);
  expect(
    created,
    `the created record commented "${record.comments}" is not in the echoed document — the add did not `
      + 'store what was sent',
  ).toBeTruthy();
  return created!;
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
 * Clear the schedule-level general comment, which is how an S04-style scenario cleans up.
 *
 * NOT A RECORD DELETE, and that is the whole point. Saving a general comment on an otherwise empty
 * schedule makes the backend insert a bare BR-09 PLACEHOLDER row to carry it — there is no record to
 * hang it on (`Schedule6Service:425`). Clearing the comment when it is the only stored thing REMOVES
 * that placeholder (`Schedule6Service:428-437`, legacy `generalCommentRemovedLastRecord`), so the
 * app's own write path is what returns the anchor to genuinely empty.
 *
 * `records: []` is correct rather than lazy: the PUT requires every SERVED row, and a placeholder is
 * never served (`Schedule6Service:460, 470`).
 */
export async function clearGeneralComment(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<void> {
  const res = await request.put(scheduleUrl(key.millId, key.year), {
    data: { generalComments: null, records: [] },
  });
  await expect(
    res,
    `PUT (clear general comment) on ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();

  // Read back BOTH halves: the comment is gone AND the placeholder it lived on is gone with it. A PUT
  // that cleared the text but left a placeholder row behind would pass a comment-only check while
  // leaving the anchor non-empty, which the next run's preflight would then blame on someone else.
  const doc = await readSchedule6(request, key);
  expect(
    doc.generalComments ?? null,
    `the general comment survived cleanup on ${key.millId}/${key.year}`,
  ).toBeNull();
  expect(
    doc.roadRecords.map((r) => r.recordId),
    `road records survived general-comment cleanup on ${key.millId}/${key.year}`,
  ).toEqual([]);
}

/** One record reduced to the fields a no-write assertion compares. */
export interface RecordSnapshot {
  recordId: number;
  areaType: string | null;
  supplyBlock: string | null;
  tflNumber: string | null;
  volume: number | null;
  cost: number | null;
  comments: string | null;
}

/**
 * The served records reduced to their comparable fields, for the BR-10 "nothing was written" check.
 *
 * `revisionCount` and the derived figures are deliberately EXCLUDED: the first would make the
 * comparison fail on any unrelated concurrent save, and the second two are recomputed from the fields
 * already compared, so including them would add no discriminating power. What IS compared is every
 * field a stray write could plausibly change — the classification, both amounts and the comment —
 * field by field rather than as a count, so a changed cost on an unchanged number of rows cannot slip
 * through.
 */
export function snapshotRecords(doc: Schedule6Doc): RecordSnapshot[] {
  return doc.roadRecords.map((r) => ({
    recordId: r.recordId,
    areaType: r.areaType ?? null,
    supplyBlock: r.supplyBlock ?? null,
    tflNumber: r.tflNumber ?? null,
    volume: r.volume ?? null,
    cost: r.cost ?? null,
    comments: r.comments ?? null,
  }));
}

/** The check-status verdict, narrowed to what the BR-10 Givens assert. */
export interface CheckStatusVerdict {
  outcome: string;
  messages: { key: string; text: string }[];
  records: { rowCounter: number; met: boolean; issues: { message: { text: string } }[] }[];
}

/**
 * The Check Status verdict for a mill/year's STORED values.
 *
 * WHY IT POSTS RATHER THAN CALLING A STORED ENDPOINT: there isn't one. `POST /check-status` is the only
 * check-status route the API exposes, and by design it evaluates the payload it is given
 * (`Schedule6CheckRequest`). The service does have a `checkStatusStored`, but that is internal to the
 * report-level callers (15.0/15.1) which have no screen, and it answers a deliberately DIFFERENT
 * question — the two can legitimately disagree, which is the whole subject of S22/S23. So this helper
 * reads the document and sends the STORED values back as the payload, which asks the public endpoint
 * "what would the verdict be if the screen matched the database?".
 *
 * Used only in the BR-10 Givens, to prove each arm's PRECONDITION at the API before the browser is
 * driven: S22 needs a stored schedule that genuinely passes, S23 one whose only gap is the cost. Get
 * either wrong and the scenario's message assertion passes for the wrong reason and proves nothing
 * about unsaved edits.
 */
export async function checkStatusStored(
  request: APIRequestContext,
  key: ScheduleKey,
): Promise<CheckStatusVerdict> {
  const doc = await readSchedule6(request, key);
  const records = doc.roadRecords.map((r) => ({
    areaType: r.areaType,
    tflNumber: r.tflNumber,
    supplyBlock: r.supplyBlock,
    volume: r.volume,
    cost: r.cost,
    comments: r.comments,
  }));

  const res = await request.post(checkStatusUrl(key.millId, key.year), { data: { records } });
  await expect(
    res,
    `POST check-status (stored values) on ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();
  return (await res.json()) as CheckStatusVerdict;
}

/**
 * Store the schedule-level general comment on an otherwise EMPTY schedule, and prove the state landed.
 *
 * How S18 reaches its subject: a comment saved with no records makes the backend insert a bare BR-09
 * PLACEHOLDER row to carry it (`Schedule6Service:425`), and that placeholder is never served as a
 * record (:470). So this is the one write that produces "stored data, but no road records".
 *
 * Done through the API rather than by typing in the page, deliberately: S18's subject is what the page
 * RENDERS when it opens on that stored state, so the state must exist BEFORE the browser is driven. S04
 * already covers entering the comment through the UI, so doing it that way here would re-test S04 and
 * then assert S18 on a page that had never been reloaded.
 *
 * `records: []` is correct rather than lazy — the PUT requires every SERVED row, and a placeholder is
 * never served.
 */
export async function setGeneralComment(
  request: APIRequestContext,
  key: ScheduleKey,
  text: string,
): Promise<void> {
  const res = await request.put(scheduleUrl(key.millId, key.year), {
    data: { generalComments: text, records: [] },
  });
  await expect(
    res,
    `PUT (set general comment) on ${key.millId}/${key.year} -> HTTP ${res.status()}`,
  ).toBeOK();

  // Read back BOTH halves, because the pair IS the fixture: the comment is stored AND the placeholder
  // it lives on is still not served as a record. If the blank-classification exclusion ever broke, the
  // scenario would otherwise fail later on a phantom row with no hint that the cause was here.
  const doc = await readSchedule6(request, key);
  expect(
    doc.generalComments ?? null,
    `the general comment did not store on ${key.millId}/${key.year}`,
  ).toBe(text);
  expect(
    doc.roadRecords.map((r) => r.recordId),
    'a comment-only schedule must serve NO road records — the BR-09 placeholder is not a record',
  ).toEqual([]);
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
