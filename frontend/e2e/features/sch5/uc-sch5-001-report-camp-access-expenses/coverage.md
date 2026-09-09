# Coverage — UC-SCH5-001 Report Camp and Access Expenses (Schedule 5)

> New to these files? See [`coverage-guide.md`](../../../coverage-guide.md) at the e2e root for the column + status-flag legend.

Sources reconciled: `UC-SCH5-001-S01..S25.feature` (25 slices) + `UC-SCH5-001-slices.md`
(control/message/field/rule matrix) + `UC-SCH5-001-detailed.md` + `UC-SCH5-001-technical.md`, against
the app's real write path (`schedule5/api/Schedule5Api.java` GET/POST/PUT/DELETE + `check-status`,
`Schedule5Controller.java`, `Schedule5Service.java`, `Schedule5Repository.java`, and
`components/schedule5/index.tsx` + `validation.ts` + `derived.ts`).

> **Where the source documents live.** They are in the **`ilcr-bmad`** planning repo, not this one, so no
> relative link from here can resolve (this suite is deliberately self-contained so it can be lifted into
> the app repo). Repo-root-relative paths in `ilcr-bmad`:
> `_bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/` (the 25 `.feature` slices) and
> `_bmad-output/planning-artifacts/requirements/use-cases/UC-SCH5-001/` (the detailed UC, slice catalog
> and technical sidecar).

**STATUS: S01–S05 AUTHORED AND GREEN; S06–S25 ANCHORED BUT NOT YET AUTHORED.** This file is
deliberately published at 5/25 rather than held back, so the ledger reflects reality rather than an
intention. Every remaining slice has a dedicated, verified anchor reserved and named for it, and
preflight proves all 22 resolve on every run — so the `deferred` rows below are waiting on authoring
effort, not on a blocker (defects.md GAP-1 resolved, GAP-2 tracks the remainder).

Test data (real, discovered 2026-09-08): pinned in `fixtures/sch5/schedule5-test-data.ts` with the
finding queries in comments. `preflight/sch5-anchors.setup.ts` asserts the anchor resolves as an
editable Draft, holds no camps at rest, and that S01's camp name is free, before the suite runs.

**ANCHOR SCARCITY, AND HOW IT WAS SOLVED.** Schedule 5 needs no summary row of its own (a valid ACTIVE
mill-year with no camps is the legitimate empty state, `Schedule5Service.java:46-47`), so the problem
was exclusivity, not missing data. Surveyed 2026-09-08: 123 report-status rows, opened years 2015–2021
only, 17 ACT listable mills — and `preflight/anchor-keys.ts` already counted **119** (mill, year) keys
pinned by the other six domains. Exactly three Draft mill-years were unpinned (1/2017, 14050/2018,
25051/2017) and **all three are CLS mills answering HTTP 409**. The 17×7 grid had three empty cells;
two were already claimed. `9050/2016` was the last, and S01 uses it.

`real-test-data-patches/sch5/draft-anchors.sql` therefore **opens reporting years 2022 and 2023** and
claims 21 cells in them — purely additive, no existing row modified, and safe for the year dropdown
(nothing asserts its contents, and the app has no default working context to shift). Because every key
any other fixture pins is ≤ 2021, **"year ≥ 2022 belongs to sch5" is a structural invariant** rather
than a convention: a cross-domain collision is not expressible in the new range. 16050/2022 is left
empty ON PURPOSE as S18's 404 fixture and is registered in `DELIBERATELY_ABSENT`.

**Anchor inventory (22 pinned, all verified through the API 2026-09-09):** 21 empty editable Drafts
(20 mutating + 1 validate-only), 1 Submitted document for S19's read-only render, and 2 guards that
need no capacity — 25051/2017 (closed mill → 409) and 16050/2022 (absent → 404).

**Cross-schedule note:** Schedule 5 is one of the domains that reads Schedule 3 (`Schedule3Service`'s
consumer list is schedule1, schedule2, schedule5, reporting), so a sch3 scenario on a shared
(mill, year) could move figures under a Schedule 5 assertion. 9050/2016 is pinned by no other fixture.

Scope authored: S01 add-a-camp with descriptors, BR-03 volume propagation across all eleven
volume-bearing categories, the nine fixed-category costs, and the four server-derived totals read back
through the API (`happy-path.feature`); S02 reopen-and-edit with a `revisionCount` read-back
(`edit.feature`); S03 copy-and-rename, which resolves WRN-001's `[UNKNOWN]` and surfaced SPEC-2
(`copy.feature`); S04/S05 the Other Camp and Other Access expense sub-pages, the second through the
CFM-004 save-first confirm from an unsaved camp (`sub-page.feature`).

## Slice ledger

| Slice | Name | Type | Status | Test / reason |
|---|---|---|---|---|
| S01 | Add a New Camp With Descriptors and Fixed-Category Expenses | Happy Path | **covered** | `happy-path.feature` `@p0 @S01` — GREEN |
| S02 | Edit an Existing Camp | Alternative | **covered** | `edit.feature` `@p1 @S02` — GREEN |
| S03 | Copy an Existing Camp and Save With a New Name | Alternative | **covered** | `copy.feature` `@p1 @S03 @WRN-001` — GREEN. Resolves WRN-001's `[UNKNOWN]`; found SPEC-2 |
| S04 | Enter Other Camp/Access Expenses on Sub-Page (existing camp) | Alternative | **covered** | `sub-page.feature` `@p1 @S04` — GREEN |
| S05 | Enter Other Camp/Access Expenses on Sub-Page (new, unsaved camp) | Alternative | **covered** | `sub-page.feature` `@p1 @S05 @CFM-004` — GREEN |
| S06 | Check Status — All Requirements Met | Alternative | deferred | as S02 |
| S07 | Delete an Existing Camp | Alternative | deferred | as S02 |
| S08 | Same Camp Name Allowed in a Different Mill/Year | Alternative | deferred | needs **two** dedicated anchors by construction |
| S09 | Recoveries Amount Reduces the Camp Total | Alternative | deferred | as S02. Also carries the per-category `$/m³` assertions S01 does not |
| S10 | Close/Navigate Away With Unsaved Changes Prompts a Discard Confirm | Alternative | deferred | validate-only; can share a non-mutating anchor when authored |
| S11 | Switch to a Different Camp While Editing Prompts a Discard Confirm | Alternative | deferred | as S10 |
| S12 | Required Descriptive Field Left Blank (Camp Name or Isolated Camp) | Exception | deferred | validate-only. FLD-001's exact text is `[UNKNOWN]` in source — re-ground against the app |
| S13 | Duplicate Camp Name on Save (Case-Insensitive) | Exception | deferred | BR-02. Its message is already OBSERVED — see note below |
| S14 | Save a Copied Camp Without Renaming It (Duplicate Name Error) | Exception | deferred | **re-ground before authoring** — SPEC-2: a copy opens with a BLANK name, so an untouched save raises the REQUIRED-name error (FLD-001), not the duplicate-name error this slice predicts |
| S15 | Numeric Field Fails Range/Format Validation | Exception | deferred | validate-only; `validation.ts` declares four cost bands to cover |
| S16 | No Mill/Year Selected in Session | Exception | deferred | context guard; no anchor needed |
| S17 | Selected Mill Not Active for the Reporting Year | Exception | deferred | guard anchor (409) — a CLS mill, no capacity needed |
| S18 | No Schedule 5 Record Found for Mill/Year | Exception | deferred | guard anchor (404) — absence IS the fixture |
| S19 | Schedule Not Editable — Report Not in Draft (Read-Only View) | Exception | deferred | read-only anchor (S/V) — no exclusivity needed |
| S20 | Check Status Finds Missing Required Values | Exception | deferred | as S02 |
| S21 | Other Access Expense Description Left Blank | Exception | deferred | validate-only; FLD `[UNKNOWN]` as S12 |
| S22 | Other Camp Expense Added With Blank Description, Blocked at Sub-Page Save | Exception | deferred | as S21 |
| S23 | Invalid Cost Entered on Other Camp/Access Expense Sub-Page | Exception | deferred | validate-only |
| S24 | Check Status includes unsaved edits — a violation entered but not saved is reported | Alternative | deferred | BR-12 family; sch1/sch2/sch4/sch11 all carry a `@discovered-divergence` here — expect the same |
| S25 | Check Status includes unsaved edits — a correction made but not saved clears the error | Alternative | deferred | as S24 |

**Coverage: 5 / 25 slices (20%). P0: 1 / 1 authored.**

> **Every `deferred` row above is reserved, not blocked.** Each has a dedicated anchor exported from
> `fixtures/sch5/schedule5-test-data.ts` with a JSDoc line naming its slice — `EDIT_ANCHOR` (S02),
> `COPY_ANCHOR` (S03), `SUBPAGE_EXISTING_ANCHOR` (S04), `SUBPAGE_NEW_ANCHOR` (S05), `CHECK_MET_ANCHOR`
> (S06), `DELETE_ANCHOR` (S07), `SAME_NAME_A/B_ANCHOR` (S08, two by construction), `RECOVERIES_ANCHOR`
> (S09), `DISCARD_CLOSE_ANCHOR` (S10), `CAMP_SWITCH_ANCHOR` (S11), `VALIDATION_ANCHOR` (S12 + S15,
> validate-only so shared), `DUPLICATE_NAME_ANCHOR` (S13), `COPY_DUPLICATE_ANCHOR` (S14),
> `CLOSED_MILL_ANCHOR` (S17, 409), `NO_SCHEDULE_ANCHOR` (S18, 404), `READ_ONLY_ANCHOR` (S19, Submitted),
> `CHECK_MISSING_ANCHOR` (S20), `ACCESS_DESC_BLANK_ANCHOR` (S21), `CAMP_DESC_BLANK_ANCHOR` (S22),
> `SUBPAGE_COST_ANCHOR` (S23), `CHECK_UNSAVED_VIOLATION_ANCHOR` (S24), `CHECK_UNSAVED_FIX_ANCHOR` (S25).
> S16 needs none — it is the no-context guard. `preflight/sch5-anchors.setup.ts` verifies all 22 plus
> both guard responses on every run, so a drifted anchor fails fast with one message instead of
> surfacing as a confusing red inside a scenario.

## Source items covered by S01

| Item | Source | Status | Where asserted |
|---|---|---|---|
| Route to the Schedule 5 screen | S01 | covered | `openViaNav()` — `/schedule-5` via Home + side-nav |
| `Add New Camp` opens the New Camp Details panel | S01 / CTL | covered | "the New Camp Details panel is shown with its descriptor fields blank" |
| All five descriptors start blank | S01 | covered | same step — asserts each of the five, not a sample |
| Camp Name entry | S01 / FLD | covered | "I enter the camp descriptors" |
| Road Distance / Size of Camp / Associated Camp Volume entry | S01 / FLD | covered | same |
| Isolated Camp selection ("Yes") | S01 / FLD | covered | same — a Carbon `Select` of ``/`No`/`Yes` |
| **BR-03** camp volume propagates to all 11 category volumes | S01 / BR-03 | covered | "the camp volume "5000" is propagated into all 11 category volume fields" — asserts ALL eleven and pins the count |
| Nine fixed-category costs entered | S01 | covered | "I enter the fixed-category costs" |
| **BR-04 / CNT-001** Camp Sub-Total, Camp Total, Access Expense Total, Total Expense recompute | S01 | covered | "the saved camp carries the expected derived totals" — server-derived, polled API read-back |
| The two totals' `$/m³` | S01 | covered | same step (0.76 and 0.98 against the 5000 camp volume) |
| **SUC** `Data saved successfully` | S01 / messages.properties:168 | covered | reuses the common "I should see the message" step |
| Camp appears in the Existing Camps table | S01 | covered | ""Cedar Creek Camp" is listed in the Existing Camps table" |
| Per-category `$/m³` recomputes for EACH category | S01 | **deferred** | S01 asserts the two TOTAL rates; the twelve per-category rates ride S09/S15 where they are the point rather than a side effect |
| Panel redisplays with recalculated values after save | S01 | **deferred** | the API read-back proves persistence, which is the stronger claim; re-render fidelity rides S02's reopen |

## Notes

- **`recoveries` is the twelfth category and has no volume cell** (`GRID_ROWS hasVolume: false`), which
  is exactly why the source Gherkin says "11 expense-category Volume fields" against twelve categories.
  The fixture asserts the count is 11 so a future grid change cannot silently weaken the BR-03 check.
- **BR-02's message is already observed, ahead of S13.** The `--repeat-each` stress run produced
  `Action failed Camp name already exists.` verbatim from the app. Recorded here so S13 is re-grounded
  against an observed string rather than the source Gherkin's guess.
- **Deterministic, unique data**: pass — no `Math.random()`/`Date.now()`, no hardcoded dates (this UC
  has no date fields); every mutating scenario owns a dedicated anchor, so fixed literals cannot collide.
- **A typed panel and a REOPENED panel render numbers differently, and S02 pins it.** A freshly entered
  panel holds the raw strings the user typed (S01 asserts `5000` on a BR-03-propagated volume). A camp
  reopened for edit is seeded from the served document through `components/schedule5/masks.ts`, whose
  `fmtVolume` / `fmtCost` are `toLocaleString('en-CA')` — so the same 1000 comes back as `1,000`. That
  grouping is transcribed from the legacy JSF converters (`ILCRVolumeConverter #,###,###`,
  `ILCRCostConverter ##,###,###`), so it is parity, not a rewrite artefact. Found by S02 failing with
  `Expected: "1000" / Received: "1,000"` on its first run; pinned in `EDIT_CAMP_DISPLAY`.
