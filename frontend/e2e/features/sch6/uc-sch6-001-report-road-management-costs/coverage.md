# UC-SCH6-001 — Report Road Management Costs (Schedule 6) — coverage

**New to these files?** `e2e/coverage-guide.md` explains the columns and the status legend;
`e2e/defects-guide.md` explains the sibling `defects.md`. Read those first — this file is a ledger, not
a narrative.

**Where the source documents live.** The Gherkin, the slice catalogue and the UC sidecars are in the
**`ilcr-bmad` planning repo**, not here, so no relative link from this file resolves:

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-SCH6-001/UC-SCH6-001-slices.md`
- Detailed UC / technical sidecar — same directory, `-detailed.md` / `-technical.md`

**STATUS 2026-09-18 — IN PROGRESS.** S01–S11 authored and green (twelve scenarios; S05 is two).
11 of 23 slices covered. Accessibility is in scope from the start (`accessibility.feature`, `@a11y`)
and is NOT yet written — carried deliberately as the lesson from Story 28.4's GAP-5, where "all slices
authored" read as complete while half of the board item was unverified because a11y is an NFR that no
slice asks for.

**Scope is 23 slices, S01–S23.** The slice catalogue said 21 in three places while the Gherkin folder
carried 23; corrected under SPEC-1 on the planning branch (`docs/story-28-5-schedule-6-e2e`) before
authoring began. The two uncounted slices were S22/S23, the Check-Status-includes-unsaved-edits pair.

---

## Suite state

Measured, never incremented — re-measure rather than editing these numbers by hand:

```
features/sch6/**/*.feature                    7 files
scenarios (bddgen, @UC-SCH6-001)             12
preflight/sch6-anchors.setup.ts              14 checks
pinned (mill, year) anchors                  11  — 9 mutating/validate-only in 2024, plus 2 guards
                                                  (1/2017 closed-mill, 23050/2024 deliberately absent)
@discovered-divergence / @discovered-bug      0
```

Twelve scenarios over eleven slices: S05 is two (a reject arm and a correction arm) because the legacy
file has two and they cannot share an anchor — see its feature header.

Verification runs, 2026-09-18:

- full suite preflight → **194 passed** (180 before sch6 + 14), run at `--workers=2`
- `--grep @UC-SCH6-001 --workers=1` → **12 passed**
- `--repeat-each=5 --workers=1` → **60 passed**, 5/5 stable per scenario
- `--workers=2` single pass → **12 passed**
- All NINE mutating anchors confirmed empty afterwards — no stranded rows and no stranded comment

Preflight itself must also be run at `--workers=2` on this box. At the default (6) two unrelated sch4
anchor checks failed and then passed in isolation — the same overload described below, not drift.

### How to stress these, and two traps that cost real time

**Run `--repeat-each` SERIALLY (`--workers=1`).** `--repeat-each` with several workers runs duplicate
copies of the SAME scenario at once, and a mutating scenario's anchor is dedicated per SCENARIO, not
per EXECUTION — so two copies of S02 both create a record on `10050/2024` and each then fails the
other's "exactly one row" assertion. That is the stress method colliding with itself, not a suite
defect. For parallel stress, run the DISTINCT scenarios concurrently in a single pass, which is also
what the real suite does.

**This box cannot sustain 6 workers, and Playwright's default here IS 6** (12 CPUs → `cpus/2`). A
default-worker sch6 run failed most of its scenarios on browser/navigation timeouts and left records
stranded on three anchors; the next run then failed its PRECONDITIONS, which reads as a suite bug
until you notice the anchors are dirty. sch5 recorded the same limit ("this box cannot sustain 6
workers"). Use `--workers=2` locally for a parallel check. CI pins `workers: 1`, so CI is unaffected.

That second trap is also the anchor precondition earning its keep: each scenario RE-ASSERTS that its
anchor is empty at scenario time, not just in preflight, so residue is named at the cause instead of
surfacing four steps later as a confusing UI assertion.

**A third trap, worth recording because the recovery is not obvious.** Interrupting a whole-suite run
leaves whichever mutating scenario was mid-flight un-torn-down, and a single dirty anchor in ANY domain
hard-fails `setup` — which the `chromium` project depends on, so it blocks every data-backed scenario in
every domain. It happened here to sch1's `13050/2017`. The fix is NOT a manual DB edit: re-running that
domain's own scenario (`--grep @S01 --no-deps`) exercises its real teardown and restores the anchor,
because fixtures tear down even when the scenario fails. Verified — preflight returned to green with no
hand-written SQL.

Scenario runs in this domain use `--no-deps` so a sibling domain's residue cannot block them; the sch6
preflight is always run separately and in full, so nothing sch6 depends on goes unchecked.

**Still owed:** one clean `npm run test:gate` over the whole suite at `--workers=2`.

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

## S03 — record a TFL instead of a TSA

Scenario: `tfl.feature` → `@p1 @S03`. Anchor **12050/2024**.

| # | Source item (S03 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | Select "TFL" from the area-type dropdown | synthetic sentinel prepended by `areaTypeOptions`; option text is the literal "TFL" | `When I select the TFL area type` | covered |
| 2 | The TFL number field is ENABLED | `#add-tfl-number`, enabled only on the TFL branch | `Then the TFL number field is enabled and Supply Block is disabled` | covered |
| 3 | The Supply Block field is DISABLED | `#add-supply-block`, disabled when `areaType === 'TFL'` (BR-02) | same step | covered |
| 4 | Enter a valid TFL number | validity = "resolves to an RMG" in `RoadGroupLookup`; `48` → RMG `10` | `When I enter the S03 TFL road record` | covered |
| 5 | Enter volume and cost | as S01, blurred so the rate commits | same | covered |
| 6 | "Add Report" → "Data saved successfully" | `POST /records` | `When I submit the Add panel` / `Then I should see the message …` | covered |
| 7 | `RMG` shows the derived grouping | server-derived from the TFL code, not a supply block | `Then the TFL road record is persisted with its derived RMG` | covered (re-grounded; VER-1) |
| 8 | *(beyond the Gherkin)* BR-02 counterpart-clear | a TFL record stores no TSA/Supply Block | same step | covered |

**Why item 8 is asserted.** A form that merely *disabled* the Supply Block control while still posting
a stale value would satisfy items 1–7 exactly. The counterpart-clear happens server-side
(`Schedule6Service:597`), so only a read-back can show it held.

---

## S04 — enter or update the schedule general comment

Scenario: `general-comment.feature` → `@p1 @S04`. Anchor **13050/2024**.

| # | Source item (S04 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | Enter text in the General Comments field | `#general-comments`, a 3500-capped TextArea over a 4000-wide column | `When I enter the schedule general comment` | covered |
| 2 | Click Save | page-level `PUT`, comment and records together | `When I save the schedule` | covered |
| 3 | "Data saved successfully" | `dataSavedSuccesfullyInfoMsg` | `Then I should see the message …` | covered |
| 4 | The field RETAINS the entered comment | re-seeded from the response | `Then the general comment field retains the text` | covered |
| 5 | *(beyond the Gherkin)* the comment PERSISTS | API read-back of `generalComments` | `Then the general comment is persisted without adding a road record` | covered |
| 6 | *(beyond the Gherkin)* the BR-09 placeholder is not served as a record | read side excludes rows whose classification is entirely blank | same step | covered |
| 7 | *(beyond the Gherkin)* totals stay at zero | a phantom row would drag them off zero | same step | covered |

**Why items 6 and 7 exist.** Saving a comment on an empty schedule makes the backend insert a bare
BR-09 **placeholder** row to carry it — there is no record to hang it on. If the read side's
blank-classification exclusion ever broke, the screen would grow a phantom row with no area type, no
supply block and no cost, and Check Status would report it as a *failing* record. S18 builds directly
on this behaviour. It is also why this slice's cleanup is a comment-clearing PUT rather than a record
DELETE: clearing the comment is what removes the placeholder.

---

## S05 — an invalid TFL number is rejected

Two scenarios: `tfl-validation.feature` → `@p1 @S05` (reject arm, anchor **22050/2024**, the shared
validate-only cell) and `@p1 @S05` (correction arm, anchor **17052/2024**, its own because it writes).

| # | Source item (S05 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | Enter an out-of-range TFL number | 2-char code passes the client gate; the server decides | `When I enter an out-of-range TFL number with valid amounts` | covered |
| 2 | The error "Entered TFL number is not valid for Interior Regions." | `tflNumberInvalidErrorMsg`, byte-identical client and server | `Then I should see the error …` | covered (timing re-grounded — see `defects.md` VER-3) |
| 3 | "the value is not accepted" | nothing stored | `Then no road record was stored on that anchor` | covered |
| 4 | *(beyond the Gherkin)* the rejection is a SERVER round-trip, once | one mutating request, counted by a `page.route` spy | `Then the rejection came from the server, on exactly one attempt` | covered |
| 5 | Correct the number → the value is accepted | the corrected code resolves | `When I correct the TFL number` | covered |
| 6 | `RMG` shows the derived grouping after correction | `48` → `10` | `Then the corrected TFL record is persisted` | covered (re-grounded; VER-1) |
| 7 | *(beyond the Gherkin)* the rejected attempt left nothing behind | exactly one row after the successful retry | same step | covered |

**Item 3 is the load-bearing assertion, and it is a DB read.** An error banner does not establish that
nothing was written — the request *was* sent. Item 4 says how the rejection happened rather than
whether it held, and it expects **one** request rather than zero: the client cannot pre-empt this
rejection because only the server knows the RMG table. That assertion was written as zero first and
failed; the app was right.

### Deliberately not asserted in S05

| Source item | Why not here | Where it lands |
|---|---|---|
| A BLANK TFL number on the TFL branch | Caught client-side by the same verbatim message; it is a required-field case, not an out-of-range one | S12 |
| Volume / cost format and range rejections | Different fields, same validate-only anchor | S13–S16 |

---

## S06 / S07 / S08 — the three context guards

Scenarios: `render-states.feature` → `@p1 @S06 @ERR-001`, `@p1 @S07 @ERR-002`, `@p1 @S08 @ERR-003`.
None writes anything. S06 needs no anchor; S07 = **1/2017** (closed mill, reused from the extract);
S08 = **23050/2024** (a deliberate absence).

| # | Source item | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | No mill/year in session → the select-a-context error | client-only banner; the request is never issued | `When I open Schedule 6 with no working context` / `Then the Schedule 6 mill and reporting year guard is shown` | covered (message re-grounded — see `defects.md` VER-4) |
| 2 | Its notification carries a severity WORD, not colour alone | `Mill and Reporting Year required` title | same step | covered |
| 3 | Mill not active for the year → ERR-002, verbatim | API 409 detail, echoed unchanged (AD-8) | `Then the Schedule 6 page shows the closed-mill guard` | covered |
| 4 | *(beyond the Gherkin)* ERR-002 is framed as a CONTEXT problem, not a load failure | its own title; the generic one is absent | same step | covered |
| 5 | No Schedule 6 record → ERR-003, verbatim | API 404 detail | `Then the Schedule 6 page is blocked as a load failure` | covered |
| 6 | *(beyond the Gherkin)* ERR-003 IS framed as a load failure | the generic title is present | same step | covered |
| 7 | The road-maintenance data-entry forms are not displayed (all three) | the page returns its load state instead of the body | `Then the Schedule 6 data-entry surface is suppressed` | covered |

**Why items 4 and 6 exist.** S07 and S08 read almost identically — both suppress the page and show a
message — and the detail text alone cannot tell the two framings apart. So each scenario asserts the
*other's* title: a closed mill is a context the reporter fixes on Home and must **not** say "Unable to
load Schedule 6", while a missing record genuinely is a load failure and must. Without this pair the
two guards could be transposed and both would still pass.

**Why item 7 asserts absence rather than a disabled state.** A guarded page returns its load state
instead of the body (`index.tsx:840`), so the Add toggle, Add Report, the totals region and the general
comment are not in the page at all. Asserting "disabled" would pass vacuously against an element that
does not exist.

### Anchor notes for these two guards

Both are read-only, so neither needed a mutating cell — but both needed care:

- **1/2017 is reused from the extract, not minted.** It is one of only two Draft cells the whole suite
  left unpinned, and what makes it useless as a mutating anchor is exactly what makes it right here:
  mill 1 is CLS, so the GET answers 409. Minting a closed cell in 2024 was **rejected** — flipping any
  mill's `ILCR_MILL_STATUS_XREF` to manufacture a guard would silently redden the closed-mill guards
  sch2/sch3/sch4/sch5 already pin. Its 2017 report-status row was added to the CI seed, because a row
  must EXIST for the year or `MillContextService` answers 404 first and the 409 is never reached.
- **23050/2024's fixture is its absence**, carved inside sch6's own minted year. Registered in
  `DELIBERATELY_ABSENT` in `preflight/ci-seed-parity.setup.ts`, whose reverse check fails if anyone
  ever seeds it. The mill is seeded and holds 2017–2023 rows, so the mill resolves and only the YEAR is
  missing — which is what makes this a 404 rather than an unknown-mill failure.

Both are proved at the API in preflight *and* again in each scenario's Given, status and detail. A
guard's fixture is a failure mode, and failure modes rot quietly: if mill 1 were ever reopened the GET
would start answering 200 and the scenario would fail on a missing banner, which reads as a UI defect
rather than as drifted data.

---

## S09 / S10 / S11 - Check Status names the missing value

Scenarios: `check-status-missing.feature` -> `@p1 @S09`, `@p1 @S10`, `@p1 @S11`. Anchors **22051/2024**,
**23051/2024**, **23052/2024** - one each, since all three save a record to reach their state.

| # | Source item | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | A record with no cost is flagged | `evaluateRecord` cost check is null-only | `Then Check Status reports "Road : 1 - TSA or TFL (Cost $) : Value Required"` | covered |
| 2 | A TFL record with no TFL number is flagged | the TFL branch requires a number | `Then Check Status reports "Road : 1 - TFL Number : Value Required"` | covered |
| 3 | A TSA record with no Supply Block is flagged | the non-TFL branch requires a block | `Then Check Status reports "Road : 1 - Supply Block : Value Required"` | covered |
| 4 | The met banner is NOT shown in any of the three | `outcome: ISSUES` emits no schedule banner | `And Check Status does not report the schedule as met` | covered |
| 5 | *(beyond the Gherkin)* the finding carries a severity WORD | "Action required" title, not colour alone | `Then Check Status reports ...` | covered |
| 6 | *(beyond the Gherkin)* S09 record has NO cost, not a cost of 0 | the check is null-only, so 0 would PASS | the Given asserts `cost` is null | covered |

**The lines are asserted byte-for-byte, mislabel included.** `"TSA or TFL (Cost $)"` on the missing-COST
line is a labelling quirk in the legacy source (`Schedule6MB.checkStatus :172`), listed among
`Schedule6Service` pinned quirks and flagged by the source Gherkin as "reproduced exactly as found".
The text IS the requirement - if it is ever corrected, this assertion should fail loudly rather than
tolerate both spellings.

**Why S10 is built differently from the other two, and what it proves.** Probed against the running
app: a record with no cost stores fine (200), a TSA record with no Supply Block stores fine (200), but
a TFL record with **no TFL number is refused** (400 FLD-002). So S09 and S11 seed their state through
the app's own POST, while S10 saves a *valid* TFL record and then blanks the field **on screen without
saving**. Check Status still reports it, because the endpoint evaluates the on-screen payload.

That is the slice which demonstrates the capability in practice. The backend notes this branch was
"ported verbatim though it is unreachable from persisted rows (legacy view-state-only)" - with the
payload-based endpoint it is reachable from the screen again, which is what the legacy `ajax="false"`
postback did.

**Item 4 is only sound because the click waits.** `runCheckStatus` goes through the shared awaiting
helper; an absence asserted straight after a bare click can pass against a DOM that has not
re-rendered - the vacuous-pass class the sch3 DIV-6 mirror arm shipped with.

### Recorded so nobody adds it later

| Not asserted | Why |
|---|---|
| Volume as a Check Status requirement | Legacy never checks it - commented out at `Schedule6CheckStatus:19`, ported verbatim. Asserting it would claim behaviour the app deliberately lacks |
| A cost of `0` as a finding | The check is null-only (D2 precedent), so `0` is MET. S09 uses an absent cost precisely to avoid asserting the wrong thing |
| A record missing several values at once | S21 |
| A mix of passing and failing records (the only way the per-record "met" line appears) | S20 |

---

## Remaining slices

S12-S23 not yet authored (12 of 23). Accessibility sweeps not yet authored. Each will be added here
with its own item table as it lands; `defects.md` carries anything found along the way.

Next are the field validations (S12-S16), which all ride the shared **validate-only** anchor
(22050/2024) because none of them saves - except any correction arm, which needs its own cell, as
sch5 S12 found. S12 also carries `SPEC-2`: its required-field message is `[UNKNOWN]` in the source, so
the text must be taken from the running app and confirmed by BA/QA.

After those, S20/S21 need states no current anchor holds: the per-record "met" line is emitted only
when the SCHEDULE fails while some individual record passes, which takes two records on one anchor -
and every anchor today is asserted NOT to hold records at rest, so it will need a dedicated cell.

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
