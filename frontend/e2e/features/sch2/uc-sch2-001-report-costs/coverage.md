# Coverage — UC-SCH2-001 Report Purchased and Private Log Costs and Sales (Schedule 2)

> New to these files? See [`coverage-guide.md`](../../../coverage-guide.md) at the e2e root for the column + status-flag legend.

Sources reconciled: `UC-SCH2-001-S01..S16.feature` (16 slices, 21 scenarios) + `UC-SCH2-001-slices.md`
(control/message/field/rule matrix
and its Gap Analysis Summary) + `UC-SCH2-001-detailed.md` + `UC-SCH2-001-technical.md` (message catalog:
SUC-001..003, FLD-001..004, ERR-001..004, STA-001), against the app's real write path
(`schedule2/api/Schedule2Api.java` GET/PUT/DELETE + `check-status`, `Schedule2Controller.java`,
`Schedule2Service.java`, `dto/Schedule2Request.java`, and `components/schedule2/index.tsx` +
`validation.ts`).

> **Where the source documents live.** They are in the **`ilcr-bmad`** planning repo, not this one, so no
> relative link from here can resolve (this suite is deliberately self-contained so it can be lifted into
> the app repo). Repo-root-relative paths in `ilcr-bmad`:
> `_bmad-output/implementation-artifacts/tests/UC-SCH2-001/gherkin/` (the 16 `.feature` slices) and
> `_bmad-output/planning-artifacts/requirements/use-cases/UC-SCH2-001/` (the detailed UC, slice catalog
> and technical sidecar).

Test data (real, discovered 2026-08-13): pinned in `fixtures/sch2/schedule2-test-data.ts` with the
finding queries in comments. Anchors were found by enumerating `GET /v1/mills` and probing
`GET /v1/schedule2` over every mill × reporting year 2010–2026 (357 probes, 111 rows carrying a
Schedules 1–10 track), then classifying by `trackStatus` / `editable` / `revisionCount`. Every
**mutating** scenario owns a distinct key, and `preflight/sch2-anchors.setup.ts` asserts that
distinctness plus every pinned anchor, the carried Schedule 1/3 figures, and both guard responses before
the suite runs.

**Cross-schedule note (why the anchor choice matters here more than elsewhere):** Schedule 2's
`totalCompanyLogging` is computed from **Schedule 1's** figures and `purchasedLogCost.volume` /
`purchasedWoodOverhead` are carried from **Schedule 3**. A `sch1` scenario writing to the same
(mill, year) would move the very numbers this suite's derived-figure assertions pin. All sch2 anchors are
therefore on four mills no other domain touches — 22050, 23051, 23052, 25053.

Scope: S01 enter+save with full derived arithmetic (`happy-path.feature`); S02 update-over-insert
(`update.feature`); S03/S04 blank fields accepted (`blank-fields.feature`); S05 delete + cancel
(`delete.feature`); S06/S09/S10/S11 render states and context guards (`render-states.feature`);
S07/S08 Check Status both sides (`check-status.feature`); S12 persistence failure (`save-error.feature`);
S13–S16 entry rejection (`validation.feature`); the save round-trip surviving a reload
(`persistence.feature`); and WCAG 2.1 AA (NFR1) across four structurally distinct renders
(`accessibility.feature`).

**Every one of the 16 slices is dispositioned `covered`.** 33 scenarios (39 tests after Scenario-Outline
expansion). **All green as of 2026-08-24:** the one deliberate `@discovered-bug` RED — Delete offered on a
schedule that has never been saved (defects.md **BUG-1**, BR-08/S06) — was fixed in nr-ilcr #292, so its tag
is removed and the scenario now runs in the gate as the regression barrier. The suite's own `deleteButton`
locator moved from the top bar to the bottom one at the same time, because #292 also restored legacy's
bottom-bar-only Delete. A clean run is `npm run test:gate` (regenerates the features first and excludes
every `@discovered-*` red — of which Schedule 2 now has none).

Priorities: **5 × p0, 17 × p1, 11 × p2.**

## Story AC traceability — bcgov/nr-ilcr#78 (Story 3.4)

The issue names the critical journeys explicitly. Each is mapped here so the AC can be checked without
reading the matrix below:

| Issue #78 requires | Scenario | Status |
|---|---|---|
| read-only view with each context guard (S09–S11 render states) | `render-states.feature` `@S09`, `@S10` ×2, `@S11` outline ×2 | `covered` |
| the carried Schedule 3 volume displaying correctly | `happy-path.feature` `@p0 @S01` (at-rest volume 10, carried, read-only) | `covered` |
| computed Net Purchased / Total Average figures displaying correctly | `happy-path.feature` `@p0 @S01` (full arithmetic, UI **and** stored) | `covered` |
| enter / save / update / **retry** (S01–S04, S12) | `happy-path`, `update`, `blank-fields`, `save-error` (both arms) | `covered` |
| out-of-range and multi-error rejection (S13–S16) | `validation.feature` (9 scenarios, 14 tests) | `covered` |
| delete (S05) **and Delete absent when unsaved (S06)** | `delete.feature` ×2; `render-states.feature` `@S06` | `covered` (S06 was the RED — BUG-1, fixed #292) |
| Check Status success / missing (S07, S08) | `check-status.feature` ×3 | `covered` |
| written after implementation per AD-10 (verification, not red phase) | 3.1–3.3 were `done` before this suite was authored | satisfied |
| axe: zero violations **or** triaged disposition (NFR1) | `accessibility.feature` — 4 clean renders; the 5th is GAP-4's recorded disposition | `covered` |
| CI: wired into the pipeline **or** documented as a manual gate | `reusable-tests.yml` runs the data-independent `@smoke` project; the data-backed suite is a documented manual gate in `e2e/README.md` | satisfied (manual-gate branch) |

> **Note on the issue's stated context (`514/2021`).** #78 describes the local setup as "context 514/2021";
> that is an example working context for bringing the stack up, not a constraint on which records the
> suite pins. Mill 514 (millId 16050) is already claimed by the sch1 and sch11 suites, and Schedule 2's
> derived figures are computed from Schedule 1/3 data for the same mill/year — so pinning anchors there
> would have made these assertions vulnerable to another domain's writes. See the cross-schedule note
> above for why sch2 owns four otherwise-untouched mills instead.

## Re-grounding headline

Schedule 2 was rebuilt on React/Carbon, not ported from JSF/PrimeFaces, so several structural facts of
the legacy `.feature` set have no literal counterpart. Each is asserted as the app actually behaves and
recorded rather than silently dropped:

1. **Every legacy locator is gone.** `schedule2.xhtml` → route `/schedule-2`; `schedule2Form:*`
   naming-container ids → stable Carbon ids (`#purchasedLogCostCost`, `#lessLogSalesVolume`,
   `#lessLogSalesCost`, `#comments`). The four `[UNKNOWN — no element id in source]` markers the Gherkin
   carried forward (the comments textarea and the three buttons) are **resolved** by the rewrite — every
   control is addressable, so no scenario needs a JSF action-binding workaround.
2. **Derived figures refresh on Save, not as you type** (DIV-1). Legacy recomputed totals on each field's
   `f:ajax event="change"`; every derived value is now computed server-side (AD-5/AD-6) and refreshed
   only by the save response. Pinned explicitly in `happy-path.feature` — the at-rest figures are
   asserted *after* entry and *before* the save — so a change in either direction is caught.
3. **Delete is rendered-but-disabled, never absent.** The legacy slice states Delete is "either absent or
   present, never disabled" (its `rendered` condition). The React page always renders it and gates via
   `disabled`. The scenarios assert the slice's *intent* (delete cannot be initiated) rather than the
   legacy mechanism — which is what exposed **BUG-1**, since the gate is currently ineffective.
4. **No `p:messages` panel.** Results render as Carbon `InlineNotification`s whose severity is carried by
   an explicit title word, never colour alone (WCAG 2.1 AA), and field errors render as inline
   `invalidText` under the offending control. The **message text is unchanged**, so every legacy contract
   string is still pinned verbatim.
5. **The delete confirmation is a Carbon `Modal`** ("Delete"/"Cancel"), not a PrimeFaces `confirmDialog`
   (`.ui-confirmdialog-yes`) and not a native browser dialog.
6. **Validation is client-advisory + server-authoritative.** Legacy rejected on the field's own AJAX
   round-trip; the rewrite validates on every keystroke and additionally *blocks* Save/Check Status with
   an advisory banner. Both the legacy FLD-* text and the new gate wording are asserted.

## Re-grounding gains (covered here, absent from the legacy slice catalogue)

| Gain | Why the legacy catalogue lacked it | Scenario |
|---|---|---|
| **ERR-004 "Schedule not found." is reachable** | The legacy UC and technical sidecar both flagged ERR-004 as an `[ASSUMPTION]`-level *unreachable* state (`Schedule2DAO.getReportSummaryID()` never returned null) and modelled no slice. In the rewrite `MillContextService.validateMillYearActive` 404s whenever the mill/year carries **no report-status row** — confirmed live: `GET /v1/schedule2?millId=13&year=2016` → HTTP 404. | `render-states.feature` `@p2 @S10` |
| **Fractional cost refused** | The catalogue deliberately excluded non-numeric entry: "no confirmed message text exists … Inventing a message text would violate the evidence constraint." The rewrite **has** one (`Entered cost is invalid.`), so the row is now evidence-backed. | `validation.feature` `@p2 @S13` |
| **Non-numeric volume refused** | Same exclusion; the rewrite has a distinct volume wording (`Entered volume entry is invalid.`). | `validation.feature` `@p2 @S14` |
| **Save round-trip survives a reload** | No legacy slice proves persistence independently of the client's own post-save repaint — after Save the page re-seeds its form from the response, which looks identical whether or not anything reached the database. | `persistence.feature` `@p0 @S01` |
| **Both action bars work** | Legacy noted Save/Check Status "appears twice, top and bottom" only as a *locator* problem. Neither bar was ever asserted to function. | `happy-path.feature` `@p2 @S01` |
| **The wider `costSize="9"` range is per-field** | The catalogue documents the two different cost ranges but never contrasts them; a value legal for item 26 and illegal for item 25 proves the range is applied per field rather than globally. | `validation.feature` `@p2 @S15` |

## Slice matrix

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Enter cost + log-sales volume/cost, save, figures recomputed | `S01` | `Schedule2Service.saveSchedule2` / `index.tsx:109` | `happy-path.feature` `@p0 @S01` | `covered` | — |
| Update previously saved values (overwrite, not insert) | `S02` | `Schedule2Repository.bumpRevision` + `upsertDetail` | `update.feature` `@p1 @S02` | `covered` | — |
| Save with Purchased/Private Log Costs cost blank | `S03` | `validation.ts:59` (blank skipped) | `blank-fields.feature` `@p1 @S03` | `covered` | — |
| Save with both (less) Log Sales fields blank | `S04` | same | `blank-fields.feature` `@p1 @S04` | `covered` | — |
| Delete a saved schedule → empty editable document | `S05` | `Schedule2Service.deleteSchedule2` / `index.tsx:145` | `delete.feature` `@p0 @S05` | `covered` | — |
| Cancelling the delete confirmation is a no-op | `S05` (AF1 dismiss) | Carbon `Modal` secondary action | `delete.feature` `@p1 @S05` | `covered` | — |
| Delete not available for an unsaved (new) schedule | `S06`, BR-08 | `utils/schedule.ts` `isScheduleSaved` → `ScheduleActions` `scheduleSaved` | `render-states.feature` `@p1 @S06` | `covered` | BUG-1 fixed #292 |
| A never-saved schedule still renders both action bars + legacy row order | `S06` (Relevant Controls) | `index.tsx` `actionBar()` (Delete on the bottom bar only, #292), row order below it | `render-states.feature` `@p1 @S06` | `covered` | — |
| Check Status — all requirements met | `S07`, BR-07 | `Schedule2Service.checkStatus` | `check-status.feature` `@p0 @S07` | `covered` | — |
| Check Status — purchased-log cost missing | `S08`, BR-07 | same | `check-status.feature` `@p0 @S08` | `covered` | — |
| A **saved** schedule with no cost still fails Check Status | `S08` (follow-on of `S03`) | same | `check-status.feature` `@p1 @S08` | `covered` | — |
| Mill and reporting year not selected → form suppressed | `S09`, ERR-001 | `index.tsx:206` `contextMissing` | `render-states.feature` `@p1 @S09` | `covered` | — |
| Mill not active for the reporting year | `S10`, ERR-002 | `MillContextService.validateMillYearActive` → 409 | `render-states.feature` `@p1 @S10` | `covered` | — |
| Mill/year with no report-status row | *(gain — legacy excluded ERR-004)* | same → 404 | `render-states.feature` `@p2 @S10` | `covered` | — |
| Schedule not editable outside Draft (Submitted **and** Verified) | `S11`, STA-001, BR-01 | `Schedule2Response.editable` (server-derived, AD-5) | `render-states.feature` `@p1 @S11` outline | `covered` | — |
| Save fails — persistence error, values kept, nothing written | `S12`, ERR-003 | `ScheduleNotSavedException` / `index.tsx:138` | `save-error.feature` `@p1 @S12` | `covered` | — |
| …and retrying the Save after the error clears succeeds (recovery arm) | `S12` recovery scenario; issue #78 "enter/save/update/**retry**" | same — the retry is not intercepted, so it reaches the real backend | `save-error.feature` `@p1 @S12` (2nd) | `covered` | — |
| Purchased/Private Log Costs cost out of range | `S13`, FLD-001, BR-05 | `validation.ts` `ITEM_25_COST` | `validation.feature` `@p1 @S13` outline | `covered` | — |
| …and an in-range value accepted afterwards (recovery arm) | `S13` recovery | same | `validation.feature` `@p2 @S13` outline (bounds) | `covered` | GAP-2 |
| (less) Log Sales volume out of range | `S14`, FLD-002, BR-05 | `validation.ts` `ITEM_26_VOLUME` | `validation.feature` `@p1 @S14` outline | `covered` | — |
| …and an in-range value accepted afterwards (recovery arm) | `S14` recovery | same | `validation.feature` `@p2 @S14` outline (bounds) | `covered` | GAP-2 |
| (less) Log Sales cost out of range (wider range) | `S15`, FLD-003, BR-05 | `validation.ts` `ITEM_26_COST` | `validation.feature` `@p1 @S15` outline | `covered` | — |
| …and an in-range value accepted afterwards (recovery arm) | `S15` recovery | same | `validation.feature` `@p2 @S15` | `covered` | GAP-2 |
| Multiple field errors reported together on one Save | `S16` | `validateSchedule2` returns a map of ALL invalid fields | `validation.feature` `@p1 @S16` | `covered` | — |
| Check Status also blocked while a field is invalid | `S16` / legacy `validateClient="true"` | `index.tsx:184` | `validation.feature` `@p2 @S16` | `covered` | — |

## Message catalog

| ID | Text | Where it now comes from | Scenario | Status |
|---|---|---|---|---|
| SUC-001 | `Data saved successfully` | API `message.text` (AD-8), key `dataSavedSuccesfullyInfoMsg` | `happy-path` / `update` / `blank-fields` / `persistence` | `covered` |
| SUC-002 | `Data deleted successfully` | API, key `dataDeletedSuccesfullyInfoMsg` | `delete.feature` `@p0 @S05` | `covered` |
| SUC-003 | `All requirements for this schedule have been met` | API, key `scheduleRequirementsMetMsg` | `check-status.feature` `@p0 @S07` | `covered` |
| FLD-001 | `Entered cost must be between -99,999,999 and 99,999,999.` | `validation.ts` (mirrors backend bundle) | `validation.feature` `@p1 @S13` | `covered` |
| FLD-002 | `Entered volume must be between 0 and 9,999,999.` | same | `validation.feature` `@p1 @S14` | `covered` |
| FLD-003 | `Entered cost must be between -999,999,999 and 999,999,999.` | same | `validation.feature` `@p1 @S15` | `covered` |
| FLD-004 | `Purchased/Private Log Costs - Cost: Value Required` | `Schedule2Controller:88-95` prefixes the label onto `missingRequiredFieldMsg` | `check-status.feature` `@p0 @S08` | `covered` |
| ERR-001 | `Please Select Mill and Reporting Year in the Home Page.` | client-side (no request), `index.tsx:36` | `render-states.feature` `@p1 @S09` | `covered` |
| ERR-002 | `This Mill is not active for the current Reporting Year. Please select another mill from the Home Page.` | API `ProblemDetail.detail` (409) | `render-states.feature` `@p1 @S10` | `covered` |
| ERR-003 | `Schedule could not be saved.` | page fallback when the API returns no detail | `save-error.feature` `@p1 @S12` | `covered` |
| ERR-004 | `Schedule not found.` | API `ProblemDetail.detail` (404) | `render-states.feature` `@p2 @S10` | `covered` *(gain)* |
| STA-001 | read-only state (controls disabled/absent) | `Schedule2Response.editable` | `render-states.feature` `@p1 @S11` | `covered` |
| *(new)* | `Please correct the highlighted fields before saving.` | client gate, `index.tsx:119` | `validation.feature` `@p1 @S13/@S14/@S15/@S16` | `covered` |
| *(new)* | `Please correct the highlighted fields before checking status.` | client gate, `index.tsx:187` | `validation.feature` `@p2 @S16` | `covered` |
| *(new)* | `Entered cost is invalid.` | `validation.ts` integer guard | `validation.feature` `@p2 @S13` | `covered` |
| *(new)* | `Entered volume entry is invalid.` | `validation.ts` NaN guard | `validation.feature` `@p2 @S14` | `covered` |
| *(new)* | `Unable to load Schedule 2.` / `Unable to delete Schedule 2.` | page fallbacks when the API returns no detail | — | `deferred` — GAP-3 (belongs in Vitest, not E2E; no unit coverage exists today) |

## Controls (8 in the legacy Field Reference)

| Control | Legacy id | New app | Scenario | Status |
|---|---|---|---|---|
| Purchased/Private Log Costs **Volume** (read-only, carried from Sch 3, BR-03) | `purchasedLogCostVol` | read-only cell | `happy-path` `@p0 @S01` — asserts the carried value **and** that the cell is not an input (`only the purchased-log cost and both log-sales fields are editable`) | `covered` |
| Purchased/Private Log Costs **Cost** (editable) | `purchasedLogCostCos` | `#purchasedLogCostCost` | `happy-path`, `validation`, `check-status` | `covered` |
| (less) Log Sales **Volume** (editable) | `lessLogSalesVol` | `#lessLogSalesVolume` | `happy-path`, `validation` | `covered` |
| (less) Log Sales **Cost** (editable, `costSize="9"`) | `lessLogSalesCos` | `#lessLogSalesCost` | `happy-path`, `validation` | `covered` |
| Additional comments | `[UNKNOWN]` | `#comments` | `happy-path`, `update`, `persistence` | `covered` |
| Save (× 2 bars) | `[UNKNOWN]` | Carbon `Button` | `happy-path` (both bars), all save scenarios | `covered` |
| Check Status (× 2 bars) | `[UNKNOWN]` | Carbon `Button` | `check-status`, `render-states` | `covered` |
| Delete | `[UNKNOWN]` | Carbon `Button` (danger--tertiary) | `delete`, `render-states` | `covered` |
| Comments 3,500-character limit | `maxlength="3500"` | Carbon `maxCount` + `enableCounter` | — | `not-applicable` — the counter caps input in the browser; there is no server rejection path or message to assert (the legacy catalogue excluded it for the same reason) |

## Business rules

| Rule | Scenario | Status |
|---|---|---|
| BR-01 editable only in Draft (+ Licensee role) | `render-states.feature` `@p1 @S11` outline | `covered` |
| BR-02 two line items persisted as cost-report detail records | `happy-path` (API read-back of both), `blank-fields` | `covered` |
| BR-03 purchased-log volume carried from Schedule 3, not entered | `happy-path` — at-rest table pins the carried 10, and the editable-surface assertion proves the cell is not enterable | `covered` |
| BR-04 item 25 records only a cost; item 26 a volume **and** a cost | `happy-path` — read-back proves item 26 stores **both**, and the editable-surface assertion proves item 25 has no volume input. The stored-side half (item 25 persists a NULL volume) is **not** E2E-observable: the API serves the *carried* Schedule 3 volume in that block, so it is covered by `Schedule2RepositoryMapperTest` / `Schedule2WriteServiceTest` instead | `covered (+ backend)` |
| BR-05 amounts within their allowed ranges | `validation.feature` (all outlines) | `covered` |
| BR-06 net/subtotal/total-average computed read-only | `happy-path` (full derived arithmetic, UI **and** stored) | `covered` |
| BR-07 Check Status requires the purchased-log cost | `check-status.feature` both sides | `covered` |
| BR-08 Delete only when saved **and** editable | `render-states` (`@p1 @S06`, in the gate), `delete`/`update`/`persistence` (available when saved, unavailable again after the delete) | `covered` — BUG-1 fixed #292 |

> **Honest note on the "Delete is available" assertions — resolved 2026-08-24.** `update.feature`,
> `delete.feature` and `persistence.feature` each assert Delete **is** available on a saved schedule. While
> BUG-1 stood those assertions were **non-discriminating**, because Delete was enabled whenever the schedule
> was editable; they were kept on the grounds that they would regain their power the moment BUG-1 was fixed.
> It is fixed (nr-ilcr #292), so they now discriminate: the gate is `editable && isScheduleSaved(doc)`, and
> `delete.feature` additionally asserts the button goes unavailable again once the record is gone.

## Deliberately excluded by the slice catalogue — re-checked against the new app

The catalogue's Gap Analysis lists 13 deliberate exclusions. Each was re-tested against the rewrite
rather than inherited:

| Excluded item | Legacy reason | Verdict against the new app |
|---|---|---|
| Non-numeric entry into a cost/volume field | no confirmed message text existed | **Now covered** — the rewrite has both messages (see gains) |
| Exact-boundary numeric values | "an Examples-table concern for the downstream Gherkin author" | **Now covered** — taken up as `Examples` rows on the bound outlines |
| Comments at/beyond 3,500 characters | client `maxlength` prevents it; no server path | `not-applicable` — unchanged in the rewrite (Carbon `maxCount`) |
| Comments left blank | optional, no distinct outcome | `not-applicable` — `blank-fields`/`check-status` exercise blank comments incidentally |
| Authorization precondition failure | no in-page behaviour documented | `blocked` — GAP-1 (single-role mock auth) |
| Schedule 3 not started for the mill/year | flagged untraced; "figures would be blank" | `covered` incidentally — the `blank-cost`/`blank-sales` anchors carry **no** Schedule 3 data, so every carried figure renders absent; asserted in `blank-fields.feature` |
| Session expiry during entry | no source evidence | `not-applicable` — no schedule-specific timeout handling in the rewrite either |
| Navigating back after partial entry | single-page form, not a wizard | `not-applicable` — unchanged |
| ERR-004 unreachable | tracing showed it could not occur | **Now covered** — reachable in the rewrite (see gains) |
| Original-value indicator icons | require a Submitted-then-revised state owned by a sibling UC | `not-applicable` — no equivalent in the rewrite |
| Clicking No/Cancel on the delete dialog | standard widget dismiss | **Now covered** — `delete.feature` `@p1 @S05` asserts it is a genuine no-op (no request sent) |
| Missing cost **and** blank log sales (combination) | collapses to S03 ∪ S04 | `not-applicable` — unchanged, the union adds no behaviour |
| Delete while Check Status would fail (combination) | collapses to S05 ∪ S08 | `not-applicable` — BR-08 does not depend on Check Status |

## Symmetry checks performed

The mirror-matrix smell test — one arm covered but not the other:

| Pair | Both arms covered? |
|---|---|
| Save succeeds / Save fails | yes — `happy-path` ∥ `save-error` |
| Check Status MET / ISSUES | yes — `check-status` `@S07` ∥ `@S08` |
| Delete confirmed / cancelled | yes — `delete.feature` both scenarios |
| Delete available (saved) / unavailable (unsaved) | yes — `update`+`persistence` ∥ `render-states` (the unavailable arm is the RED that found BUG-1) |
| Editable (Draft) / read-only (non-Draft) | yes — every editable scenario ∥ `render-states` `@S11` |
| Read-only **Submitted** / **Verified** | yes — the `@S11` outline runs both track codes |
| Save fails / retry after the error clears | yes — `save-error.feature` both scenarios |
| Value out of range / on the bound | yes — reject outline ∥ bound outline, for all three fields |
| Blank cost accepted at Save / caught by Check Status | yes — `blank-fields` `@S03` ∥ `check-status` `@p1 @S08` |
| Item 25 narrow range / item 26 wide range | yes — contrasted directly in `validation` `@p2 @S15` |
| Guard 409 (closed mill) / 404 (no report status) | yes — `render-states` `@S10` both scenarios |
| Client-rejected write / server-rejected write | yes — `validation` (spy proves 0 requests) ∥ `save-error` (500, read-back proves nothing stored) |

## Test-quality DoD (RV) — self-audit

Audited against the skill's `quality-and-coverage-gates.md` §A on 2026-08-13. **No HIGH findings.**

| Rule | Verdict |
|---|---|
| No hard waits | **pass** — zero `waitForTimeout`/`sleep` anywhere in `steps/sch2`, `pages/sch2`, the fixtures or the preflight; every wait is a web-first assertion or `expect.poll` |
| No flow conditionals | **pass with one commented exception** — `schedule2Page.rowValues()` branches on whether a cell holds an input, to read the editable and read-only renders with one step. It selects a *read strategy*, never a path through the assertion, and cannot mask a wrong render mode (that is asserted separately and explicitly). Justified inline. The only other branches are the cleanup registry's fail-loud `try/catch`, the route-spy method filter, and an unknown-block-name guard that throws — none steer a test. |
| Deterministic, unique data | **pass** — no `Math.random()`/`Date.now()`, no hardcoded dates (the UC has no date fields); every mutating scenario owns a dedicated anchor, so fixed literals cannot collide |
| Isolated & self-cleaning | **pass** — fail-loud cleanup registry restores each anchor to unsaved and re-reads to prove it; distinctness asserted by preflight |
| Explicit assertions | **pass** — every `expect()` is in a step body; page-object helpers return values, never booleans standing in for assertions |
| Prove the negative | **pass — corrected after review.** Client rejections assert **spy-count 0** *and* record-absent; the server rejection asserts the failure path *and* reads back that nothing was stored. **The spy reads were initially taken at one instant**, which a reviewer caught (CGI-BC/nr-ilcr#8): a regression that renders the inline error and *then* fires the PUT a tick later would have read 0 and passed green. Both zero-write steps now cross the shared `settleBeforeReadingSpy` barrier first, so the negative must hold over a window. |
| Poll UI-triggered read-backs | **pass** — `the stored Schedule 2 record is:` / `derived figures are:` / `schedule is stored` all use bounded `expect.poll`; never a single-shot GET after a click |
| Resilient selectors | **pass** — `getByRole` for the table, buttons, cells and notifications (`role="status"`, verified live); stable app-exposed `#id`s for the four form fields; one commented CSS scope (`.schedule-2__actions`) used only to stop the page's Delete resolving the modal's own Delete button |
| Reuse over duplication | **pass, with one logged cross-module constant.** Steps reuse the promoted `home-context` Given and the four common assertion steps; no DOM selectors leaked into steps; repeated behaviour is a `Scenario Outline` (6 of them). The zero-write settle barrier was **promoted** from `steps/sch11/` to `pages/common/settle.ts` and is now shared by both domains rather than copy-pasted — that promotion is what the review finding above prompted. Remaining: `'ilcr:mill-year-context'` is declared in `fixtures/sch1`, `fixtures/sch11` **and** `fixtures/sch2`; it was already duplicated twice before Schedule 2 existed, and promoting it edits two other UCs' files, so it belongs in the repo-wide consistency PR — logged rather than silently accepted. |
| Focused (~<300 lines/file) | **LOW deviation, logged not fixed** — `steps/sch2/schedule2.steps.ts` is 567 lines. This matches the established house shape for a domain's step file (`steps/sch1/schedule1.steps.ts` 599, `steps/sch11/schedule11.steps.ts` 744) and the file is already sectioned by concern with DOM detail extracted to the page object and data to fixtures. Splitting it purely to hit the number would diverge from the two sibling domains for no readability gain. |

## Coverage gate (TR)

- **P0: 100%** — all 5 P0 scenarios green (happy path incl. full derived arithmetic, delete, both Check
  Status arms, persistence).
- **P1: 100%** of P1 items covered — 17 scenarios, **all green** since nr-ilcr #292 closed BUG-1; the
  formerly-excluded BR-08/S06 scenario now runs inside the gate rather than counting as covered-while-red.
- **Overall: 16/16 slices covered**, plus every message-catalog row except the two `deferred` fallbacks
  (GAP-3) and the `blocked` role item (GAP-1). Both remain above the 80% bar.
- **Verdict: PASS** — no waiver needed. GAP-1 (`blocked`, single-role mock auth) and GAP-3/GAP-4
  (`deferred`, documented) are the only non-covered items and none is P0/P1-critical.

## Accessibility (NFR1 / Story 3.4 AC2)

Four structurally distinct renders are swept, all clean: editable-and-populated, read-only, the
Check-Status result notification, and a guard state. The **validation-error** state is deliberately *not*
swept here — see GAP-4; it would re-find one already-tracked, app-wide Carbon defect.
