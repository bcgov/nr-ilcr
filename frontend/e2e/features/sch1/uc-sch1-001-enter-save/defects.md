# Defects — UC-SCH1-001 Report Average Cost of Logging (Schedule 1)
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

**Bug / Regression:**

- **#1 — Adding a Subtotal Other Costs line item fails with a 500 on the delivery database.**
  - **What's wrong:** On the delivery Oracle DB, clicking **Add** on the Other Costs sub-page (or `POST /api/v1/schedule1/other-costs`) fails — the page shows "Other cost could not be saved." and no row is added. The same defect blocks any *first-time* Schedule 1 detail insert; the main Save (S01) escapes it only because it *updates* rows that already exist in the seed.
  - **Expected vs actual:** Expected the row to be added and the API to return "Data saved successfully" with the row persisted. Actual — HTTP 500 `"Schedule could not be saved."`, nothing persisted.
  - **How we caught it (verified on real data 2026-07-30):** `POST /api/v1/schedule1/other-costs?millId=25050&year=2017` and `…millId=9050&year=2017` both → HTTP 500. Reproduced at the DB: the app's `INSERT` into `THE.ILCR_COST_REPORT_DETAIL` raises `ORA-01400: cannot insert NULL into (…"REVISION_COUNT")` (then `UPDATE_USERID`, `UPDATE_TIMESTAMP`).
  - **Why (technical):** `THE.ILCR_COST_REPORT_DETAIL` has `REVISION_COUNT`, `UPDATE_USERID`, `UPDATE_TIMESTAMP` as **NOT NULL with no column default**, but `Schedule1Repository.insertOtherCost` / `insertFixedDetail` / `insertFixedDetailVolume` do **not** supply them. The app was built against a schema where these are defaulted/nullable; the delivery schema is stricter, so every insert is rejected. (Existing seed rows all carry `REVISION_COUNT = 0`.)
  - **Is it a defect?** Yes — a genuine 500 that broke Other Costs add (and any first detail insert) on the delivery DB.
  - **Fix (applied by dev during this story, verified 2026-07-30):** `Schedule1Repository.insertOtherCost` now sets `REVISION_COUNT = 0` and the `UPDATE_USERID` / `UPDATE_TIMESTAMP` audit columns in its INSERT (the app-side fix, not a schema change — the column is still `NOT NULL` with no default). The rebuilt backend was redeployed; `POST /api/v1/schedule1/other-costs` now returns 200 and persists, and the S09 round-trip is green. (The raw column-omitting INSERT still fails at the DB, confirming the fix is the added columns.)
  - **Follow-up:** confirm the sibling first-insert paths `insertFixedDetail` / `insertFixedDetailVolume` carry the same fix (same omission class) so a brand-new Schedule 1 with no seeded rows can save; out of this slice's scope to verify E2E.
  - **Priority / env:** p1 · branch `e2e-test/epic2-2.8-delivery-db-a11y` · local seeded delivery DB `THE/…@localhost:1525/DBDOCK_01`.
  - **Status:** RESOLVED — found and fixed 2026-07-30; S09 now a normal green scenario. (BA/QA to confirm the fix is intentional/persistent and whether the real delivery Oracle shared the strict schema.)
  - **Test:** `other-costs.feature` `@S09 @p1` — GREEN (was the `@discovered-bug` red that tracked this).

**Divergences:**

- **#1 — Invalid amounts are flagged inline and Save is blocked, rather than rejected at the keystroke.**
  - **What's wrong:** The legacy-derived Gherkin (S03–S07) says an out-of-range or non-numeric cost/volume is rejected *at entry* — "the invalid amount is not accepted into the field." In the new app the field **accepts** the typed value, an inline error appears immediately, and the **Save is blocked** (nothing is sent). The protection those slices exist for — invalid data can never be saved — still holds; only the moment/mechanism of the block differs.
  - **Expected vs actual:** Expected the field to refuse the value on entry (legacy FLD-001/002/003/004/005). Actual — the value stays in the field with a red inline message (e.g. "Entered cost must be between -99,999,999 and 99,999,999."), and clicking Save shows "Please correct the highlighted fields before saving." and sends nothing to the server.
  - **How we caught it (verified on real data 2026-07-29):** Re-grounding S03–S07 against 24050/2017. For each field we typed an invalid value, saw the inline error, clicked Save, and a `page.route` spy confirmed **zero** `PUT /api/v1/schedule1` calls. Validate-only, so no row was created.
  - **Why (technical):** `components/schedule1/validation.ts` computes advisory per-field errors rendered as Carbon `invalidText`; `Schedule1.handleSave` aborts the PUT when any field is invalid (backend stays authoritative per AD-8).
  - **Is it a defect?** Looks like a deliberate client design that preserves the guarantee — BA/QA to confirm the mechanism change is acceptable parity.
  - **Action:** BA/QA to confirm parity is acceptable. Kept as a **GREEN** re-grounded test asserting the preserved guarantee (inline error + proven zero-write), **not** a `@discovered-divergence` red — the guarantee holds, so there is no failing behavior to track.
  - **Priority / env:** p1 · branch `e2e-scaffold` · local seeded DB THE/…@localhost:1525/DBDOCK_01 (security off, datasource on).
  - **Status:** OPEN. Found 2026-07-29.
  - **Test:** `validation.feature` (all five scenarios, S03–S07) — GREEN.

- **#2 — Legacy 8-digit-volume fields Forest Mgmt Admin & Subtotal Company Logging are read-only here.**
  - **What's wrong:** Legacy FLD-003 (8-digit volume range, ±99,999,999) applied to three editable fields: Forest Management Admin volume, Subtotal Other Costs volume, and Subtotal Company Logging Cost volume. In the new app only **Subtotal Other Costs volume** is editable; Forest Mgmt Admin (code 143) and Subtotal Company Logging (code 144) render **read-only/derived** and cannot be typed into.
  - **Expected vs actual:** Expected three editable 8-digit volume inputs. Actual — one editable input (`#otherCostsVolume`); the other two are disabled server-owned values.
  - **How we caught it (verified on real data 2026-07-29):** Re-grounding S06. `WRITABLE_LINE_ITEM_CODES = [12..18]` excludes 143/144 (`interfaces/Schedule1Request.ts`); `components/schedule1/index.tsx` renders 143/144 as disabled `TextInput`s. S06 was moved to the only editable 8-digit volume (Subtotal Other Costs volume), which carries the identical FLD-003 message.
  - **Why (technical):** Those subtotals are pulled/derived server-side rather than user-keyed — likely an intentional simplification.
  - **Is it a defect?** Probably intentional; BA/QA to confirm against parity intent.
  - **Action:** BA/QA to confirm. S06 re-grounded (GREEN); the 8-digit range itself is covered on Subtotal Other Costs volume.
  - **Priority / env:** p1 · branch `e2e-scaffold` · local seeded DB.
  - **Status:** OPEN. Found 2026-07-29.
  - **Test:** `validation.feature` (S06, re-grounded field) — GREEN.

**Coverage gaps (not tested yet — no app problem):**

- **#1 — Role/permission branches can't be exercised under single-role mock auth (authorization).**
  - **Why not:** With security off the backend authorizes every request as one fixed authority (`ILCR_SUBMITTER`), so admin-only and permission-denied (403) branches of Schedule 1 can't be produced — the mock principal never changes per run.
  - **Future action:** revisit once FAM auth + finer roles land (the auth story); cover the authorization guards as backend tests meanwhile.
  - **Status:** OPEN.
  - **Test:** none — `blocked` in coverage.md.

- **#2 — S02 (crown pre-fill) not yet automated.**
  - **Why not:** S02 needs a mill/year whose Schedule 3 carries a Crown Timber volume while the Schedule 1 volumes are all still empty (first entry), so the WRN-001 pre-fill fires. No such anchor was discovered among the seeded editable Drafts, and manufacturing one means seeding Schedule 3 data (out of this slice's scope).
  - **Future action:** discover or seed a Schedule-3-crown / empty-Schedule-1 pair, then author S02 (assert the pre-filled volumes + the WRN-001 notice). `deferred` row in coverage.md until then.
  - **Status:** OPEN.
  - **Test:** none yet — `deferred` in coverage.md.

- **#3 — S08 (open Other Costs before first save) is unreachable in the current backend model.**
  - **Why not:** The legacy guard blocked opening Other Costs before Schedule 1 was saved. In the new app an openable schedule is always already saved (the GET 404s when no summary exists), so `Schedule1.handleOtherCosts`'s `!data` branch (the ALT-001 "save first" Modal) cannot be produced through the UI against real data — the code comment says as much ("effectively unreachable").
  - **Future action:** cover the guard as a component/unit test (mock a no-summary state) rather than E2E; keep `deferred` here.
  - **Status:** OPEN.
  - **Test:** none — `deferred` in coverage.md.
  - **Status:** OPEN (in progress).
  - **Test:** partial — see `covered` rows in coverage.md; the rest tracked as `deferred` rows.

**Spec gaps (the Gherkin is missing scenarios its own docs list):** _none._

**Verified — not a defect:**

- **Accessibility (AC4 / NFR1): zero WCAG 2.1 AA violations.** `@axe-core/playwright` (tags `wcag2a` + `wcag2aa` + `wcag21a` + `wcag21aa`) ran against the Schedule 1 page (24050/2017) and the Other Costs sub-page (17052/2016) → **zero violations** on both, so no triage/dispositions are required. (`accessibility.feature`, verified 2026-07-30.) If a future change introduces a violation, the axe helper prints each rule + node + help URL for a recorded disposition.

- **The legacy `ILCR_LICENSEE` role was re-grounded to the new two-group model.** The Gherkin authenticates as `ILCR_LICENSEE`, but the new app has no such role — the ratified model is `ILCR_ADMIN` + `ILCR_SUBMITTER` (PRD DL-23). Schedule 1 saves are authorized for `ILCR_SUBMITTER` (live: `PUT /api/v1/schedule1?millId=13050&year=2017` with security off → HTTP 200, `message.text = "Data saved successfully"`, persisted on read-back). Scenarios use the real role; deliberate rename, not a defect. (Verified 2026-07-29.)
