# UC-SCH6-001 — Report Road Management Costs (Schedule 6) — coverage

**New to these files?** `e2e/coverage-guide.md` explains the columns and the status legend;
`e2e/defects-guide.md` explains the sibling `defects.md`. Read those first — this file is a ledger, not
a narrative.

**Where the source documents live.** The Gherkin, the slice catalogue and the UC sidecars are in the
**`ilcr-bmad` planning repo**, not here, so no relative link from this file resolves:

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-SCH6-001/UC-SCH6-001-slices.md`
- Detailed UC / technical sidecar — same directory, `-detailed.md` / `-technical.md`

**STATUS 2026-09-18 — IN PROGRESS.** S01–S20 authored and green (twenty-nine scenarios; several slices
are more than one — see the count below). **20 of 23 slices covered.** Accessibility is in scope from
the start (`accessibility.feature`, `@a11y`) and is NOT yet written — carried deliberately as the
lesson from Story 28.4's GAP-5, where "all slices authored" read as complete while half of the board
item was unverified because a11y is an NFR that no slice asks for.

**Scope is 23 slices, S01–S23.** The slice catalogue said 21 in three places while the Gherkin folder
carried 23; corrected under SPEC-1 on the planning branch (`docs/story-28-5-schedule-6-e2e`) before
authoring began. The two uncounted slices were S22/S23, the Check-Status-includes-unsaved-edits pair.

---

## Suite state

Measured, never incremented — re-measure rather than editing these numbers by hand:

```
features/sch6/**/*.feature                   10 files
scenarios (bddgen, @UC-SCH6-001)             29
preflight/sch6-anchors.setup.ts              19 checks
pinned (mill, year) anchors                  16  — 13 mutating/validate-only in 2024, 1 read-only
                                                  (24051/2024, Submitted + seeded), plus 2 guards
                                                  (1/2017 closed-mill, 23050/2024 deliberately absent)
@discovered-divergence / @discovered-bug      0
```

Twenty-nine scenarios over twenty slices, because five slices need more than one:

| Slice | Scenarios | Why |
|---|---|---|
| S05 | 2 | the legacy file has two arms and they cannot share an anchor (the correction arm saves) |
| S12 | 2 | same split — the correction arm saves, so it has its own minted cell |
| S13 | 2 | reject arm + correction arm (neither saves) |
| S14 | 4 | a 3-row `Scenario Outline` (both bounds + the 3-decimal case) plus a correction arm |
| S16 | 3 | a 2-row `Scenario Outline` (both bounds) plus a correction arm |

Verification runs, 2026-09-18 (S18/S19/S20):

- full suite preflight → **199 passed** (180 before sch6 + 19), at `--workers=2`
- the three scenarios green on the first run; `--repeat-each=5 --workers=1` → **15 passed**, 5/5 stable
- whole UC at `--workers=2` → **29 passed** (S01–S17 unregressed)
- all thirteen mutating/validate-only anchors confirmed empty afterwards, and the read-only anchor
  confirmed intact

Earlier, for S17:

- full suite preflight → **196 passed** (180 before sch6 + 16), run at `--workers=2`
- `--grep @read-only --workers=1` → **1 passed** on the first run
- `--repeat-each=5 --workers=1` → **5 passed**, 5/5 stable
- whole UC at `--workers=2` → **26 passed** (so S01–S16 are unregressed by the shared changes)
- the ten mutating anchors confirmed empty afterwards, **and** the read-only anchor confirmed still
  non-Draft and still holding exactly its two seeded records — a read-only fixture has to be checked
  for SURVIVAL, not for emptiness, which is the opposite of every other anchor here

Earlier, for the S12–S16 block:

- full suite preflight → **195 passed**, at `--workers=2`
- `--grep "@required-field|@amount-validation" --workers=1` → **13 passed** on the first run
- `--repeat-each=5 --workers=1` → **65 passed**, 5/5 stable per scenario
- `--workers=2` single pass → **13 passed**; all ten anchors empty afterwards

Earlier, for S01–S11: preflight 194, `--grep @UC-SCH6-001 --workers=1` 12 passed, `--repeat-each=5`
60 passed, `--workers=2` 12 passed.

**Scope the grep by FEATURE tag, not by slice tag.** `--grep "@S12|@S13"` matches every domain's
S12/S13 as well — the slice numbers are per-UC, not global, so that selection pulled in 76 tests across
seven domains (including other domains' known `@discovered-divergence` reds, which then read as new
failures). `--grep "@required-field|@amount-validation"` selects exactly this block; `@UC-SCH6-001`
selects the whole UC.

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

---

## S12 - a road record must name an area type

Scenarios: `required-field.feature` -> `@p1 @S12` (reject arm, shared **validate-only** anchor
22050/2024) and `@p1 @S12` (correction arm, **24050/2024** - its own cell, minted for it, because it
is the only scenario in the whole S12-S16 block that SAVES).

**This slice closes SPEC-2.** The source Gherkin carries `[UNKNOWN]` for the message; the running app
declares it as `areaTypeRequired` = `"TSA or TFL: Value is required."`
(`src/components/schedule6/validation.ts:61`). Taken from the app exactly as SPEC-2 said it would have
to be. **Still needs BA/QA acceptance** - see `defects.md` SPEC-2.

| # | Source item (S12 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | Open the Add panel with the area type at its blank default | `emptyForm()`; the combo starts unset | `Then the Add panel is shown with its fields blank` / `And no area type is selected in the Add panel` | covered |
| 2 | Click "Add Report" with the dropdown blank | `handleAdd` -> `validateRoadRecord` | `When I enter valid amounts with no area type selected` / `And I submit the Add panel` | covered |
| 3 | The system displays the required-field error | `ROAD_MESSAGES.areaTypeRequired`, rendered as the combo's `invalidText` | `Then I should see the error "TSA or TFL: Value is required."` | covered (text re-grounded - SPEC-2) |
| 4 | The record is not added to the list | nothing stored | `And no road record was stored on that anchor` / `And the Schedule 6 record list shows no records` | covered |
| 5 | *(beyond the Gherkin)* the refusal costs NO request | `handleAdd` returns before the POST is built (`index.tsx:682-686`) | `And no Schedule 6 write was attempted` | covered |
| 6 | Select an area type -> "Data saved successfully" | `POST /records` | `When I select the area type and supply block` / `Then I should see the message ...` | covered |
| 7 | *(beyond the Gherkin)* the corrected record PERSISTS with its derived figures | API read-back of area type, block, RMG, volume, cost, rate | `And the corrected road record is persisted` | covered |
| 8 | *(beyond the Gherkin)* the refused attempt left nothing behind | exactly one row after the retry | same step | covered |

**Why item 5 is asserted, and why it differs from S05.** Both slices refuse an entry, but for opposite
reasons: S05's invalid TFL number can only be judged by the server (the RMG table is server-side), so
it costs exactly **one** POST. A missing area type is decided entirely on the client, so it costs
**zero**. Pinning each at its own number is what keeps the two mechanisms from being conflated - and a
regression that started posting on a failed client gate would fail here.

**Why item 4 asserts twice.** `no road record was stored` is a DB read and `the record list shows no
records` is the screen. An error banner establishes neither; the request was never sent, but only a
read proves nothing was stored, and only the list proves the reporter was not shown a phantom row.

### Anchor note

24050/2024 was minted for the correction arm (patch + CI seed, same change). The other nine scenarios
in this block never save, so they all share the validate-only cell. Mill 24050 is ACT and already
pinned by sch1/sch4/sch5 at 2016-2023, so only the YEAR is new; confirmed free beforehand with the
suite's own scanner (`preflight/anchor-keys.ts` `collectAnchorKeys`), which shows the only 2024+ keys
anywhere are sch6's.

---

## S13 / S14 / S15 / S16 - the volume and cost entries are validated

Scenarios: `amount-validation.feature`. **Every one rides the shared validate-only anchor
(22050/2024)** - nothing in the file writes, which is what lets eleven scenarios share one cell.
S14 and S16 are `Scenario Outline`s so both bounds of each two-sided rule are proved.

| # | Source item | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | A non-numeric volume -> "Entered volume entry is invalid." | `volumeInvalid`; `parseDecimalInput` returns null | `@S13` reject arm | covered |
| 2 | A volume outside 0-9,999,999 -> "Entered volume must be between 0 and 9,999,999." | `volumeRange` | `@S14` outline, rows 1-2 | covered |
| 3 | A non-numeric cost -> "Entered cost is invalid." | `costInvalid` | `@S15` reject arm | covered |
| 4 | A cost outside -99,999,999-99,999,999 -> "Entered cost must be between ..." | `costRange`, checked on the ROUNDED value | `@S16` outline, rows 1-2 | covered |
| 5 | "the value is not accepted" | nothing stored | `And no road record was stored on that anchor` (every reject arm) | covered |
| 6 | Enter a valid value -> the field accepts it | the mask re-groups it (`16,000` / `56,000`) | `Then the Add panel volume/cost field still reads ...` | covered |
| 7 | `cal` recomputes the cost-per-volume | `commitRate` advances the baseline on a valid entry | `And the Add panel shows the computed cost per volume "3.50"` | covered |
| 8 | *(beyond the Gherkin)* each refusal costs NO request | client-side gate; `handleAdd` returns early | `And no Schedule 6 write was attempted` | covered |
| 9 | *(beyond the Gherkin)* the typo STAYS on screen | `groupInput` / `groupFixedInput` return unparseable text unchanged (`utils/number.ts:59-64`) | `Then the Add panel ... still reads "abc"` | covered |
| 10 | *(beyond the Gherkin)* the `$ / m³` cell does NOT move on a refused entry | `commitRate` advances only from an entry that passes the gate (ruled 2026-08-21) | `And the Add panel shows no computed cost per volume` | covered |
| 11 | *(beyond the Gherkin)* >2 decimal places resolves to the RANGE message | VOLUME is `NUMBER(10,2)`; the backend's `@Digits` maps to the same key (`validation.ts:130-136`) | `@S14` outline, row 3 | covered |
| 12 | *(beyond the Gherkin)* the error does NOT clear when the field is corrected | `setAddField` never touches `addErrors` (`index.tsx:610-611`) | `And the error ... is still shown until the next submit` | covered - recorded as `defects.md` **VER-5** |

**All four messages match the source Gherkin byte-for-byte** - unusually, only the TIMING had to be
re-grounded, not the text. They are declared in `ROAD_MESSAGES`
(`src/components/schedule6/validation.ts:60-70`), transcribed there verbatim from the backend bundle so
an advisory message reads identically to a server rejection.

**Items 9 and 10 are the pair that makes a refusal meaningful rather than merely loud.** A field that
blanked or zeroed a bad entry would hide the reporter's own mistake while still satisfying the error
assertion; and a derived cell driven from an unpersistable value would show a rate no Save could ever
store. Item 10 is only a real discriminator because the correction arms assert the same cell reaches
`3.50` - a blank-cell assertion alone could pass against a cell that never works.

**Why both bounds (items 2 and 4).** The Gherkin states two-sided rules, and a scenario that tried only
a too-LARGE value would leave the floor unproven - the one-armed asymmetry this guide calls a smell.
Cost's floor is genuinely negative (a road cost may be negative here, unlike a volume), so
`-100,000,000` is the only way to show that bound exists at all.

### Deliberately not asserted in S13-S16

| Source item | Why not here | Where it lands |
|---|---|---|
| A BLANK volume or cost | Blank is VALID at this gate - both validators return undefined on blank (`validation.ts:121-125, 140-144`), because a missing cost is a Check Status finding (S09), not an entry error. Asserting a rejection would claim behaviour the app deliberately lacks | S09 (Check Status) |
| The per-record Comments 400-character cap | A different field and a different rule (`recordCommentsMaxLength`) | not in the S01-S23 catalogue; recorded so the omission is visible |
| A value exactly ON each bound being accepted | The boundary-accepted mirror. sch2 covers this class for its own ranges; here the correction arms prove acceptance with an in-range value, but not at the exact bound | open - a cheap future addition, no new anchor needed |
| These rules on a saved ROW (rather than the Add panel) | The row editor shares `RoadRecordFields` and the same validator, but the page-level Save is a different submit path (`handleSave`, per-row `rowErrors`) | S19 or a follow-up; recorded because the shared component makes it easy to assume it is covered |

---

## S17 - the schedule is read-only once the report leaves Draft

Scenario: `read-only.feature` -> `@p1 @S17`. Anchor **24051/2024** - the ONLY sch6 anchor that is
neither Draft nor empty, so it is deliberately **not** in `EDITABLE_DRAFT_ANCHORS` and has its own
preflight check. Track code `S` (Submitted); nothing is written, so the scenario is a pure read.

| # | Source item (S17 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | The 1-10 report is not in Draft | `editable = caller.allows(trackStatus)` (`Schedule6Service:126`) | `Given the Schedule 6 report for that mill and year is not in Draft` | covered |
| 2 | The six Add-form fields are disabled | the panel renders only while its toggle is open (`index.tsx:971`) and the toggle is disabled (`:907`) | `Then every Schedule 6 entry control is disabled` | covered (**re-grounded** - the panel is absent and unreachable; see below) |
| 3 | The same six field names, as a reporter meets them on a saved record | the row editor shares the very same `RoadRecordFields` component; `disabled={entryLocked}` (`:1020`) | `And every saved record's own fields are disabled` | covered |
| 4 | `saveButton0` **and** `saveButton1` disabled | both instances gated on `!editable \|\| saving` (`:887`) | `Then every Schedule 6 entry control is disabled` | covered (both instances, count asserted first) |
| 5 | `checkStatusButton0` **and** `checkStatusButton1` disabled | both gated the same way (`:897`) | same step | covered (both instances) |
| 6 | The Add/Close toggle is disabled | `disabled={entryLocked}` (`:907`) | same step | covered |
| 7 | `schedule6Form:comments` (general comment) disabled | `disabled={!editable \|\| saving}` (`:1067`) | same step | covered |
| 8 | Existing road records remain visible | rows render regardless of editability | `And the existing road records remain visible with their stored figures` | covered |
| 9 | Running totals remain visible | server-recomputed on the served document | `And the running totals remain visible` | covered |
| 10 | The general comment remains visible | re-seeded from the response | `And the general comment remains visible` | covered |
| 11 | *(beyond the Gherkin)* `editable` is **false**, not merely non-Draft | editability is role x status | the Given asserts both | covered |
| 12 | *(beyond the Gherkin)* each row's **Delete** is disabled | `deleteDisabled = entryLocked` (`:878`) | `And every saved record's own fields are disabled` | covered |
| 13 | *(beyond the Gherkin)* the stored figures are the **stored** ones, per row | row values + both derived cells (RMG and `$ / m³`) | `And the existing road records remain visible ...` | covered |
| 14 | *(beyond the Gherkin)* the totals are a genuine **SUM**, not one row echoed | `4.00` = 120,000 / 30,000, which is neither record's rate | `And the running totals remain visible` | covered |
| 15 | *(beyond the Gherkin)* the general comment field is **populated**, not just disabled | a disabled-but-blanked textarea would lose the reporter's text | `And the general comment remains visible` | covered |

### The one material re-grounding: the Add surface (item 2)

Legacy rendered the Add panel inline and always present, so "disabled" was the only lock available and
its Gherkin names six fields (`tsaNumberOneMenu`, `tflNumber`, `tsbNumberOneMenu`, `vol`, `cos`,
`comAdd`). The React page renders the panel **only while its toggle is open** and disables the toggle,
so on a locked schedule the panel is not in the DOM at all.

Asserting those six are "disabled" would therefore **fail** against elements that do not exist, and
asserting them merely absent would **pass vacuously** against any page - the same vacuous-pass trap
S06-S08 records from the other direction. The faithful pair is: the toggle **exists and is disabled**,
and the panel is **absent**. That is strictly stronger than six inert fields, because the entry surface
is unreachable rather than merely inactive. The six field *names* are still asserted disabled where
they genuinely exist on this page - on the rows (item 3), through the same shared component.

### Why role x status is called out (item 11)

`editable` is not a function of the status code alone: SUBMITTER edits at Draft only while ADMIN edits
at Submitted/Verified (`ScheduleEditability:63-64`; administrator is deliberately *not* a superset,
because the statuses are a hand-off chain). The suite runs as `ILCR_SUBMITTER`, matching every feature
file's "As a Licensee", which is what makes a Submitted anchor read-only. **If the suite's identity
were ever switched to ADMIN this same anchor would become editable and the slice would invert** - so
the Given and preflight both assert `editable: false` rather than trusting `trackStatus`.

### The fixture, and why it is two records

S17's "records, totals and general comment remain visible" half needs DATA, and the records **cannot be
created through the app** - every write to a non-Draft document is refused, which is the slice's own
subject. So they are seeded by `real-test-data-patches/sch6/view-mode-road-records.sql`, mirrored into
the CI seed in the same change, with `ROAD_MAINTENANCE_REPORT_ID` added to `parentsByColumn` in
`preflight/ci-seed-parity.setup.ts` (the gate's own header had named it as the next FK to expect). This
is the first `ROAD_MAINTENANCE_REPORT` content the CI seed has ever carried.

| Record | Classification | Volume / Cost | `$ / m³` | RMG | Derived from |
|---|---|---|---|---|---|
| 1 | TSA `01` / block `01B` | 10,000 / 30,000 | 3.00 | 15 | the supply block |
| 2 | TFL `48` | 20,000 / 90,000 | 4.50 | 10 | the fixed `RoadGroupLookup` table |
| **totals** | | **30,000 / 120,000** | **4.00** | | server-recomputed |

**Two records, not one, and the totals are the reason.** With a single record the totals equal that
record's own figures, so a page that echoed one row into the totals strip would pass. `4.00` is neither
record's rate, and the two rates differ from each other, so the per-row cells cannot be transposed
unnoticed either. One TSA row and one TFL row also prove the read-only render for both BR-02 branches,
which derive RMG by different routes. Both RMGs are values the suite already proves the server derives
(S01 pins `15`, S03 pins `10`), now read back on a locked page.

**Two rows is deliberately under the 5-row page size.** The totals' pagination scope is unresolved in
legacy source (`defects.md` SPEC-3), so a six-row fixture would force this slice to assert an answer to
an open question instead of testing the read-only render.

**No `recordId` is pinned anywhere.** The local patch draws ids from `ILCR_REPORT_COMMON_SEQ` while the
CI seed uses explicit `3100`/`3101`, so the ids genuinely differ between environments - and every row
locator is built from `row-<recordId>-*`. The Given resolves each record's id **and** its display
ordinal from the served document by matching the per-record **comment**, which is stable everywhere.

### Deliberately not asserted in S17

| Source item | Why not here | Where it lands |
|---|---|---|
| That a write to a non-Draft document is REFUSED at the API | S17's subject is the rendered page. The refusal is the backend gate (HTTP 409) and is a different claim from "the controls are locked" | not in the S01-S23 catalogue; recorded so the gap is visible - it is also what makes this fixture need SQL |
| The read-only render of the ADD panel's own fields | Unreachable by construction here (the toggle is disabled), so there is no state in which they exist and are disabled | n/a - see the re-grounding note above |
| Pagination controls on a locked page | Two records is under one page | SPEC-3 / the multi-record slices |
| The ADMIN-at-Submitted mirror (editable when the suite is ADMIN) | The suite has one identity and every feature file declares Licensee; asserting the ADMIN arm would need a second role fixture | not in the catalogue; the role x status rule is covered by backend tests |

---

## S18 - the schedule holds only a general comment

Scenario: `general-comment.feature` -> `@p1 @S18` (alongside S04, whose behaviour it builds on).
Anchor **25050/2024** - its own cell because it WRITES the comment; empty at rest, because clearing the
comment removes the BR-09 placeholder with it.

| # | Source item (S18 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | The only stored data is a general-comment record | the comment lives on a bare BR-09 placeholder row (`Schedule6Service:425`) | `Given only a general comment is stored for that mill and year` (via the API, then the page is opened fresh) | covered |
| 2 | The empty-records placeholder is displayed | the read side excludes rows whose classification is entirely blank (`:470`) | `Then the Schedule 6 record list shows no records` | covered |
| 3 | The comments field shows the previously saved comment | re-seeded from the response | `And the general comment field shows the stored comment` | covered |
| 4 | `totalVol` / `totalCos` / `totalCal` show zero | volume and cost are real `0`; the RATE is `null` | `And the schedule totals are at rest` | covered (**re-grounded** - two zeroes and a BLANK; `defects.md` VER-7) |
| 5 | *(beyond the Gherkin)* Check Status answers **MET**, with no phantom failing row | the placeholder is excluded from the check candidates too (`:779-784`) | `When I run Schedule 6 Check Status` / `Then I should see the message ...` | covered (recorded **deviation (d)**; VER-7) |

**Why item 4 is a re-grounding and not a weakened assertion.** Probed on this anchor: `totalVolume` 0,
`totalCost` 0, `totalCostPerVolume` **null**. Volume and cost are real zeros that must still show; the
rate is null because 0/0 is undefined, and `ratioMask(null)` renders the empty string
(`index.tsx:86-95`, whose own comment states the distinction). Asserting `"0"` on the third total would
fail; asserting it loosely would hide real behaviour. So the step asserts two zeroes and a blank.

**Why item 5 was added though the Gherkin never mentions Check Status.** `general-comment.feature`'s
header had flagged it as S18's subject, and it is the assertion that proves the placeholder exclusion
holds **end to end**. S04 proves the placeholder is not *served* as a record; this proves it is not
*evaluated* as one either. If that filter broke, the schedule would report "a phantom failing row,
since a placeholder has no area type, no supply block and no cost" - the service's own words. The MET
verdict is recorded **deviation (d)**: legacy reported ISSUES for a comment-only mill/year.

---

## S19 - reclassify an existing record from TSA to TFL

Scenario: `edit.feature` -> `@p1 @S19` (alongside S02, which deliberately leaves the classification
alone). Anchor **25052/2024**.

| # | Source item (S19 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | A TSA-type record already exists and is listed | created by the Given through the app's own POST | `Given a TSA road maintenance record commented ... already exists on that anchor` | covered |
| 2 | Expand the record's accordion tab | Carbon `AccordionItem`; required for visibility (VER-2) | `When I switch the record's area type to TFL` (expands first) | covered |
| 3 | Select "TFL" from the row's area-type dropdown | the row renders the same `CodeComboBox` as the Add panel | same step | covered |
| 4 | Enter a valid TFL number in the row's field | `#row-<recordId>-tfl-number` | same step | covered |
| 5 | Click Save -> "Data saved successfully" | page-level `PUT` | `When I save the schedule` / `Then I should see the message ...` | covered |
| 6 | The row's TFL number field is ENABLED | enabled only on the TFL branch | `Then the row's TFL number is enabled and its Supply Block is disabled` | covered |
| 7 | The row's Supply Block field is DISABLED | `disabled` when `areaType === 'TFL'` (BR-02) | same step | covered |
| 8 | The row's RMG shows the **re-derived** grouping | server-derived from the TFL code via `RoadGroupLookup` | `And the row shows its re-derived RMG` | covered |
| 9 | *(beyond the Gherkin)* BR-02 counterpart-clear on an **UPDATE** | the service clears the TSA side (`Schedule6Service:597`) | `And the reclassified record is persisted with its re-derived RMG` | covered |
| 10 | *(beyond the Gherkin)* the amounts are UNCHANGED | volume, cost and the rate survive a classification-only edit | same step | covered |
| 11 | *(beyond the Gherkin)* updated IN PLACE | exactly one row survives | same step | covered |
| 12 | *(beyond the Gherkin)* the record really STARTED on the TSA branch | the Given asserts the seeded block and its block-derived RMG | the Given | covered |

**Item 9 is why this slice exists as well as S03.** A PUT that set the TFL side while leaving the old
TSA and Supply Block populated would store a row belonging to **both** branches at once - and the
screen would look perfectly correct, because the form disables the Supply Block control regardless of
what sits behind it. S03 proves the clearing happens on an INSERT; only this slice proves it on an
UPDATE, which is a different code path (`updateRoadRecord` vs `insertRoadReport`).

**Why the amounts are held constant (item 10).** 40,000 / 10,000 = 4.00 exactly, before and after, so
the RMG moving from `15` to `10` cannot be mistaken for an amounts recalculation - and the unchanged
rate doubles as proof the PUT did not disturb what it was not asked to touch. Item 12 is the mirror
guard: a record that arrived already on the TFL branch would make every later assertion pass while
testing nothing.

---

## S20 - Check Status, mixed results across two records

Scenario: `check-status-missing.feature` -> `@p1 @S20`. Anchor **25053/2024** - **the only cell that
holds two records at once**, which is why it could not borrow any of S09/S10/S11's.

| # | Source item (S20 Gherkin) | App enforcement | Scenario step | Status |
|---|---|---|---|---|
| 1 | Two records exist: row 1 complete, row 2 with a blank cost | both created by the Given through the app's POST | `Given two road maintenance records exist on that anchor, the second missing its cost` | covered |
| 2 | "All requirements for 1 have been met." | `roadRequirementsMetMsg` with the display ordinal substituted as a **String** | `Then Check Status reports that row 1 has met its requirements` | covered |
| 3 | "Road : 2 - TSA or TFL (Cost $) : Value Required" | composed server-side, legacy mislabel included | `And Check Status reports the second row is missing its cost` | covered |
| 4 | The schedule-level met banner is NOT shown | `outcome: ISSUES` emits no schedule message | `And Check Status does not report the schedule as met` | covered |
| 5 | *(beyond the Gherkin)* the finding carries a severity WORD | "Action required" title, not colour alone | `And Check Status reports the second row ...` | covered |
| 6 | *(beyond the Gherkin)* row 2's cost is ABSENT, not `0` | the check is null-only, so `0` would PASS | the Given asserts `cost` is null | covered |
| 7 | *(beyond the Gherkin)* the served ORDER puts the complete record first | the read side sorts by `ROAD_MAINTENANCE_REPORT_ID` | the Given asserts the served comment order | covered |

**This is the only state in which the per-record "met" line appears at all** - it is emitted when the
schedule fails while some individual record passes, so it needs two records that disagree. Verified
against the running endpoint before authoring: `outcome "ISSUES"`, `messages: []`, record 1 met, record
2 failing on cost. Both literals are byte-identical to the Gherkin.

**Note the trailing period, and that it is the opposite of the schedule-level message.** The per-record
line ends in one (`messages.properties:151`) while `"All requirements for this schedule have been met"`
does not (`:191`). Both are pinned verbatim; the difference is real, not a transcription slip. The two
also cannot collide in the assertions - the per-record text does not contain the schedule-level text,
so the absence check in item 4 is not satisfied or defeated by the met line in item 2.

**Items 6 and 7 both guard against a vacuous green.** If row 2's cost were `0` rather than absent it
would PASS (null-only check, D2 precedent), the schedule would legitimately be MET, and item 4 would
then be asserting the banner's absence against a schedule that had every right to show it. Item 7
matters because items 2 and 3 assert *which* ordinal passed and which failed - the row counter is the
1-based display position, so the order is part of the claim rather than incidental.

### Recorded so nobody adds it later

| Not asserted | Why |
|---|---|
| A record missing SEVERAL values at once | S21 |
| The mixed state re-evaluated after the missing cost is filled in | S22/S23's family (Check Status over unsaved on-screen edits) |
| Pagination of the per-record lines beyond 5 rows | SPEC-3 is still open; two records keeps this slice clear of it |

---

## Remaining slices

S21-S23 not yet authored (20 of 23 covered). Accessibility sweeps not yet authored. Each will be added
here with its own item table as it lands; `defects.md` carries anything found along the way.

S21 (a record missing several values at once) needs no new anchor shape - one record with two gaps on a
cell of its own. S22/S23 are the Check-Status-includes-unsaved-edits pair, **expected green here**
(unlike Schedule 5's live divergence) because this endpoint already evaluates the on-screen payload -
see `defects.md` section 1.

S20/S21 need states no current anchor holds: the per-record "met" line is emitted only
when the SCHEDULE fails while some individual record passes, which takes two records on one anchor -
and every anchor today is asserted NOT to hold records at rest, so it will need a dedicated cell.

**Anchor budget note for whoever continues this.** Every further mutating scenario needs its OWN
`(mill, year)` — the suite runs `fullyParallel` and an add creates a real `ROAD_MAINTENANCE_REPORT`
row. There is no free cell anywhere in the extract (survey in the fixture header: 151 pinned keys, 136
Draft cells, zero usable), so each one is minted in reporting year **2024** by extending
`real-test-data-patches/sch6/draft-anchors.sql` — and mirrored into
`backend/src/test/resources/db-e2e/R__80_e2e_anchor_seed.sql` **in the same change**, column for
column. A patch that is not folded in does not exist in CI; that omission is what reddened Story 28.4.

**DONE 2026-09-18, with S17.** `ROAD_MAINTENANCE_REPORT_ID` is now registered in `parentsByColumn` in
`preflight/ci-seed-parity.setup.ts`, so seeded `ILCR_COST_REPORT_DETAIL` rows are matched to their road
report instead of being reported as parentless. S17 is the slice that needed it: it is the only one
whose data must be seeded in SQL (its page is non-Draft, so the app refuses to create the records), and
it carries the first `ROAD_MAINTENANCE_REPORT` rows the CI seed has ever held. S02 does **not** need it
— its Given creates the record through the app's own POST, which is still the preferred route for any
future slice that can use it.
