# UC-SCH6-001 — Report Road Management Costs (Schedule 6) — coverage

**New to these files?** `e2e/coverage-guide.md` explains the columns and the status legend;
`e2e/defects-guide.md` explains the sibling `defects.md`. Read those first — this file is a ledger, not
a narrative.

**Where the source documents live.** The Gherkin, the slice catalogue and the UC sidecars are in the
**`ilcr-bmad` planning repo**, not here, so no relative link from this file resolves:

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-SCH6-001/UC-SCH6-001-slices.md`
- Detailed UC / technical sidecar — same directory, `-detailed.md` / `-technical.md`

**STATUS 2026-09-17 — IN PROGRESS.** S01 and S02 authored and green. 2 of 23 slices covered.
Accessibility is in scope from the start (`accessibility.feature`, `@a11y`) and is NOT yet written —
carried deliberately as the lesson from Story 28.4's GAP-5, where "all slices authored" read as
complete while half of the board item was unverified because a11y is an NFR that no slice asks for.

**Scope is 23 slices, S01–S23.** The slice catalogue said 21 in three places while the Gherkin folder
carried 23; corrected under SPEC-1 on the planning branch (`docs/story-28-5-schedule-6-e2e`) before
authoring began. The two uncounted slices were S22/S23, the Check-Status-includes-unsaved-edits pair.

---

## Suite state

Measured, never incremented — re-measure rather than editing these numbers by hand:

```
features/sch6/**/*.feature                    2 files
scenarios (bddgen, @UC-SCH6-001)              2
preflight/sch6-anchors.setup.ts               5 checks
pinned (mill, year) anchors                   2  (9050/2024, 10050/2024)
@discovered-divergence / @discovered-bug      0
```

Verification runs, 2026-09-17:

- sch6 preflight + the CI-seed parity gate → **12 passed**
- `--grep @UC-SCH6-001` → **2 passed**
- `--repeat-each=5 --workers=1` → **10 passed**, 5/5 stable
- `--repeat-each=3 --workers=2` → **6 passed** — run in PARALLEL on purpose, since the two anchors
  being distinct is what makes that safe, and a shared key is the failure sch5's S24 companion hit
- Both anchors confirmed empty afterwards, and `ROAD_MAINTENANCE_REPORT` holds **zero** 2024 rows, so
  the cleanup registry returns the seeded DB to its at-rest state

Those scenario runs used `--no-deps`, and that needs stating plainly rather than buried: a whole-suite
run was interrupted partway through, which left **sch1's** `13050/2017` anchor holding the values its
S01 scenario writes, because that scenario's blank-restore teardown never ran. `preflight/anchors.setup.ts`
then hard-fails on it, and since the `chromium` project depends on `setup`, that one dirty anchor blocks
every data-backed scenario in every domain. It is neither a sch6 problem nor an app problem.

So the sch6 preflight was run separately and in full (12 passed, above) and only the two scenarios
skipped dependencies — nothing sch6 relies on went unchecked. **Still owed:** restoring that sch1
anchor (its own `schedule1Restore` teardown does it — GET for the `revisionCount`, then PUT
`emptyScheduleRequest`) and one clean `npm run test:gate` over the whole suite.

---

## S01 — Add a Road Maintenance Record by TSA and Supply Block (happy path)

Scenario: `happy-path.feature` → `@p0 @S01`. Anchor **9050/2024** (minted; see
`real-test-data-patches/sch6/draft-anchors.sql`).

| # | Source item (S01 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | Navigate via "Schedules" menu → "Schedule 6" | side-nav group → route `/schedule-6` | `When I open Schedule 6` | covered |
| 2 | Report is in Draft and editable | `editable` served by the API (AD-9) | Given (anchor precondition, re-asserted at scenario time) | covered |
| 3 | Empty schedule shows the placeholder | `EMPTY_LIST` "No records found." (PrimeFaces default, inherited verbatim) | `Then the Schedule 6 record list shows no records` | covered |
| 4 | "Add" toggle opens the "Add Road Maintenance report" panel | `addToggle` → labelled `region` | `When I open the Add Road Maintenance report panel` | covered |
| 5 | Panel opens with blank fields | fresh `emptyForm()` | `Then the Add panel is shown with its fields blank` | covered |
| 6 | Select a Timber Supply Area (`tsaNumberOneMenu`) | `CodeComboBox`, options are code DESCRIPTIONS | `When I enter the S01 road record` | covered |
| 7 | Select a Supply Block (`tsbNumberOneMenu`) | `CodeComboBox`, list filtered to codes starting with the TSA (`supplyBlocksFor`) | same | covered |
| 8 | Enter the volume (`vol`) | `#add-volume`, re-grouped on blur (`groupInput`) | same | covered |
| 9 | Enter the cost (`cos`) | `#add-cost`, re-grouped on blur to 0 decimals (`groupFixedInput`) | same | covered |
| 10 | `cal` shows the computed cost-per-volume | Add panel mirrors `recordCostPerVolume` from the blurred inputs | `Then the Add panel shows the computed cost per volume "3.84"` | covered |
| 11 | `RMG` shows the derived Resource Management Grouping | **server-derived; the Add panel is passed `rmg=""` deliberately** (`index.tsx:401`) — asserted on the saved ROW instead | `Then the new record row shows its derived figures` | covered (re-grounded — see `defects.md` VER-1) |
| 12 | "Add Report" saves | `POST /api/v1/schedule6/records` (add-is-save) | `When I submit the Add panel` | covered |
| 13 | "Data saved successfully" | `dataSavedSuccesfullyInfoMsg`, rendered from `message.text` (AD-8) | `Then I should see the message "Data saved successfully"` | covered |
| 14 | The record is added as a row in `roadReportDataList` | row fieldset, ids `row-<recordId>-*` | `Then the new record row shows its derived figures` | covered |
| 15 | *(beyond the Gherkin)* the record is PERSISTED with its derived figures | API read-back of `areaType`/`supplyBlock`/`volume`/`cost`/`rmg`/`costPerVolume` | `Then the road record is persisted with its derived figures` | covered |
| 16 | *(beyond the Gherkin)* BR-02 clears the counterpart | a TSA record stores no `tflNumber` | same step | covered |
| 17 | `totalVol` / `totalCos` / `totalCal` recomputed | server-recomputed on the echoed document | `Then the schedule totals are recomputed from the new record` | covered |
| 18 | Check Status → "All requirements for this schedule have been met" | `POST /check-status` on the ON-SCREEN payload, `outcome: MET` | `When I run Schedule 6 Check Status` / `Then I should see the message …` | covered |

### Deliberately not asserted in S01

| Source item | Why not here | Where it lands |
|---|---|---|
| Per-record `Comments` persistence rules | Entered by S01 (it is the cleanup handle) but its own column/cap behaviour is a different subject — `ILCR_COST_REPORT_DETAIL.COMMENTS` VARCHAR2(400), distinct from the schedule-level 4000-wide `ROAD_MAINTENANCE_REPORT.COMMENTS` | S04 (general comment) and the validation slices |
| Volume as a Check Status requirement | Legacy never checks volume — commented out at `Schedule6CheckStatus:19`, ported verbatim. A passing Check Status proves the area-type / supply-block / cost trio ONLY, and asserting otherwise would claim behaviour the app deliberately does not have | n/a — recorded so nobody "fixes" it |
| The per-page `$ / m³` column for every row | S01 has one record, so the row and the total coincide | S19/S20 (multi-record states) |
| Pagination of the running totals (5 rows/page) | Unresolved in legacy source — `slices.md` excluded item 10 flags whether totals cover the page or the record set | open question, carried to the multi-record slices |

---

---

## S02 — Edit an existing road maintenance record

Scenario: `edit.feature` → `@p1 @S02`. Anchor **10050/2024** (minted; empty at rest — the Given creates
the record it then edits through `POST /records`, so nothing is seeded in SQL and the CI seed needs no
`ROAD_MAINTENANCE_REPORT` row).

| # | Source item (S02 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | A record already exists and is listed | created by the Given through the app's own POST | `Given a road maintenance record commented … already exists on that anchor` | covered |
| 2 | Expand the record's `roadAccord` tab | Carbon `AccordionItem`, title `Road Maintenance report Id: <ordinal>` | `Then the record row shows the amounts it was created with` (expands first) | covered |
| 3 | Change the row's `volume` field | `#row-<recordId>-volume`, filled and blurred | `When I change the record's volume and cost` | covered |
| 4 | Change the row's `cost` field | `#row-<recordId>-cost`, filled and blurred | same | covered |
| 5 | *(beyond the Gherkin)* the row's `$ / m³` recomputes BEFORE any save | row mirrors `recordCostPerVolume` from the blurred inputs | `Then the record row shows the recomputed cost per volume "4.50"` | covered |
| 6 | Click `saveButton0` ("Save") | page-level `PUT`, fans every served row plus the general comment | `When I save the schedule` | covered |
| 7 | "Data saved successfully" | `dataSavedSuccesfullyInfoMsg` from `message.text` | `Then I should see the message …` | covered |
| 8 | *(beyond the Gherkin)* the edit PERSISTS | API read-back of volume, cost, `costPerVolume` | `Then the edited amounts are persisted` | covered |
| 9 | *(beyond the Gherkin)* the edit UPDATES IN PLACE | exactly one row survives | same step | covered |
| 10 | *(beyond the Gherkin)* untouched fields survive | area type, supply block and the RMG derived from it are unchanged | same step | covered |
| 11 | `totalVol` / `totalCos` / `totalCal` recomputed | server-recomputed on the echoed document | `Then the schedule totals are recomputed from the edited record` | covered |

**Why items 9 and 10 are asserted though the legacy text does not ask for them.** The page-level Save
posts *every* served record in one PUT, so a bug that treated an edited row as new would leave the old
row behind **and** still display the new figures — the only visible symptom would be doubled totals,
which is easy to misread as a totals bug. Asserting one surviving row names the cause. Item 10 is the
mirror risk: a PUT that blanked the fields it was not asked to change would also pass items 1–8.

### Deliberately not asserted in S02

| Source item | Why not here | Where it lands |
|---|---|---|
| Changing an existing record's AREA TYPE | S02's subject is the amounts; the TSA→TFL switch on a saved record has its own slice, and mixing them would make a failure ambiguous | S19 |
| Optimistic locking on a stale `revisionCount` | A concurrency concern, not an edit concern — the PUT 409s on a stale token, which is its own behaviour | not in the S01–S23 catalogue; recorded here so the omission is visible |

---

## Remaining slices

S03–S23 not yet authored (21 of 23). Accessibility sweeps not yet authored. Each will be added here
with its own item table as it lands; `defects.md` carries anything found along the way.

**Anchor budget note for whoever continues this.** Every further mutating scenario needs its OWN
`(mill, year)` — the suite runs `fullyParallel` and an add creates a real `ROAD_MAINTENANCE_REPORT`
row. There is no free cell anywhere in the extract (survey in the fixture header: 151 pinned keys, 136
Draft cells, zero usable), so each one is minted in reporting year **2024** by extending
`real-test-data-patches/sch6/draft-anchors.sql` — and mirrored into
`backend/src/test/resources/db-e2e/R__80_e2e_anchor_seed.sql` **in the same change**, column for
column. A patch that is not folded in does not exist in CI; that omission is what reddened Story 28.4.

Once a slice seeds a road record with volume/cost (S02 edit, S17 read-only), its
`ILCR_COST_REPORT_DETAIL` rows are parented by `ROAD_MAINTENANCE_REPORT_ID`, which is **not yet** in
`parentsByColumn` in `preflight/ci-seed-parity.setup.ts`. Add it in that same change or those rows are
reported as parentless — the gate's own header calls this out as the obvious next FK.
