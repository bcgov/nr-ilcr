import { type APIRequestContext, expect } from '@playwright/test';
import { OTHER_COSTS_API } from '../../fixtures/sch1/schedule1-test-data';

/**
 * Thin API helpers for the Subtotal Other Costs sub-resource (Schedule1OtherCostsApi). Used to seed a
 * precondition row (S12), to prove/read back writes, and to clean up rows a scenario added. The specs
 * stay pure-UI; these run only in Given preconditions and teardown, through the app's real endpoints.
 */

export interface OtherCostRow {
  id: number;
  description: string;
  cost: number | null;
}

const query = (millId: number, year: number): string => `?millId=${millId}&year=${year}`;

export async function listOtherCosts(
  request: APIRequestContext,
  millId: number,
  year: number,
): Promise<OtherCostRow[]> {
  const res = await request.get(`${OTHER_COSTS_API}${query(millId, year)}`);
  expect(res.ok(), `GET other-costs ${millId}/${year} returned HTTP ${res.status()}`).toBeTruthy();
  const doc = (await res.json()) as { rows?: OtherCostRow[] };
  return doc.rows ?? [];
}

export async function addOtherCost(
  request: APIRequestContext,
  millId: number,
  year: number,
  description: string,
  cost: number | null,
): Promise<number> {
  const res = await request.post(`${OTHER_COSTS_API}${query(millId, year)}`, {
    data: { description, cost },
  });
  expect(res.ok(), `POST other-costs ${millId}/${year} returned HTTP ${res.status()}`).toBeTruthy();
  const doc = (await res.json()) as { rows?: OtherCostRow[] };
  const added = (doc.rows ?? []).find((r) => r.description === description);
  expect(added, `added Other Cost row "${description}" not present in the response`).toBeTruthy();
  return added!.id;
}

/** Delete every itemized row whose description equals `marker`, then prove none remain (fail loud). */
export async function deleteOtherCostsByMarker(
  request: APIRequestContext,
  millId: number,
  year: number,
  marker: string,
): Promise<void> {
  for (const row of (await listOtherCosts(request, millId, year)).filter(
    (r) => r.description === marker,
  )) {
    const res = await request.delete(`${OTHER_COSTS_API}/${row.id}${query(millId, year)}`);
    // 404 = the UI already deleted it (S12 happy path) — treat as already-gone.
    expect(
      [200, 404].includes(res.status()),
      `DELETE other-cost ${row.id} returned HTTP ${res.status()}`,
    ).toBeTruthy();
  }
  const remaining = (await listOtherCosts(request, millId, year)).filter(
    (r) => r.description === marker,
  );
  expect(remaining.length, `Other Cost rows "${marker}" left behind after cleanup`).toBe(0);
}
