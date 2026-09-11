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

**STATUS: S01–S23 AUTHORED AND GREEN; ONLY S24–S25 REMAIN.** This file is deliberately published at
23/25 rather than held back, so the ledger reflects reality rather than an intention. Both remaining
slices have a dedicated, verified anchor reserved and named for them, and preflight proves all 24
resolve on every run — so the two `deferred` rows below are waiting on authoring effort, not on a
blocker (defects.md GAP-1 resolved, GAP-2 tracks the remainder).

**S20–S23 added 2026-09-10.** S20 as `check-status-missing.feature`, and S21/S22/S23 together as
`sub-page-validation.feature` — the three belong in one file because they are the same asymmetry seen
three ways, and reading any one of them alone invites the conclusion that the other page behaves the
same. It does not: the cost BAND differs per page, the required-check TIMING differs per page, and
the add-form behaves identically on both despite the source Gherkin saying otherwise (SPEC-4).

**S16–S19 added 2026-09-10** as one `render-states.feature`, following the per-domain convention
(sch1/sch2/sch3/sch4/sch11 all group their guard and read-only slices in a file of that name). Three
are guards that need no capacity; S19 needed something the anchor did not have, and that is recorded
as GAP-3 in defects.md rather than quietly fixed: the read-only anchor held **no camps**, so
"the row-action column shows a single View button" was unassertable. A camp cannot be created there
through the app — every write to a non-Draft document is refused with HTTP 409, which is the very
condition S19 proves — so it is seeded by `real-test-data-patches/sch5/view-mode-camp.sql` and folded
into the CI seed in the same change. Its amounts are S01's, so S19 reads back off the READ path the
same arithmetic S01 proves on the WRITE path.

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
claims 22 cells in them — purely additive, no existing row modified, and safe for the year dropdown
(nothing asserts its contents, and the app has no default working context to shift). Because every key
any other fixture pins is ≤ 2021, **"year ≥ 2022 belongs to sch5" is a structural invariant** rather
than a convention: a cross-domain collision is not expressible in the new range. 16050/2022 is left
empty ON PURPOSE as S18's 404 fixture and is registered in `DELIBERATELY_ABSENT`.

**Anchor inventory (24 pinned, all verified through the API):** 23 empty editable Drafts (22 mutating + 1 validate-only), 1 Submitted document for S19's read-only render, and 2 guards that need no capacity — 25051/2017 (closed mill → 409) and 16050/2022 (absent → 404). Two were minted after the original fan-out, each for the same structural reason — a scenario that writes cannot share a key under `fullyParallel`: **17052/2023** on 2026-09-09 for S12, whose second arm corrects the blank field and SAVES; and **22050/2023** on 2026-09-10 for S23's ACCESS half, because visiting both sub-pages of one camp in a single scenario runs into the dirty-panel confirm (see the note below).

**The read-only anchor is the one exception to "empty at rest", and preflight states it as such.** Every other anchor must hold no camps; 16050/2023 must hold exactly one, the seeded `E2E View Camp`. Both are asserted, with messages that distinguish the two ways it can go wrong — zero camps means the patch was never applied, two or more means S19's "a single View button" is no longer unambiguous.

**Cross-schedule note:** Schedule 5 is one of the domains that reads Schedule 3 (`Schedule3Service`'s
consumer list is schedule1, schedule2, schedule5, reporting), so a sch3 scenario on a shared
(mill, year) could move figures under a Schedule 5 assertion. 9050/2016 is pinned by no other fixture.

Scope authored: S01 add-a-camp with descriptors, BR-03 volume propagation across all eleven
volume-bearing categories, the nine fixed-category costs, and the four server-derived totals read back
through the API (`happy-path.feature`); S02 reopen-and-edit with a `revisionCount` read-back
(`edit.feature`); S03 copy-and-rename, which resolves WRN-001's `[UNKNOWN]` and surfaced SPEC-2
(`copy.feature`); S04/S05 the Other Camp and Other Access expense sub-pages, the second through the
CFM-004 save-first confirm from an unsaved camp (`sub-page.feature`); S06 Check Status on a passing
schedule, asserting the per-camp line is ABSENT (`check-status.feature`, SPEC-3); S07 delete behind the
CFM-001 confirm with an API read-back proving the row really went (`delete.feature`); S08 BR-02's per-mill-year scoping across two anchors, re-reading the first to prove the camp was ADDED and not moved (`same-name.feature`); S09 BR-04's subtracting category against the client-side mirror, never saving (`recoveries.feature`); S10/S11 the two discard confirms, each proving the discard never reached the database (`discard-confirm.feature`); S12/S13/S14 the three camp-name rules, resolving FLD-001's `[UNKNOWN]` and re-grounding S14 per SPEC-2 (`name-validation.feature`); S15 five numeric validators as one outline (`numeric-validation.feature`); S16/S17/S18 the three EF2 guards and S19 the read-only render, all in `render-states.feature`; S20 Check Status naming a missing field and falling silent once it is supplied (`check-status-missing.feature`); S21/S22/S23 the two sub-pages' required-timing and cost-band asymmetries (`sub-page-validation.feature`).

## Slice ledger

| Slice | Name | Type | Status | Test / reason |
|---|---|---|---|---|
| S01 | Add a New Camp With Descriptors and Fixed-Category Expenses | Happy Path | **covered** | `happy-path.feature` `@p0 @S01` — GREEN |
| S02 | Edit an Existing Camp | Alternative | **covered** | `edit.feature` `@p1 @S02` — GREEN |
| S03 | Copy an Existing Camp and Save With a New Name | Alternative | **covered** | `copy.feature` `@p1 @S03 @WRN-001` — GREEN. Resolves WRN-001's `[UNKNOWN]`; found SPEC-2 |
| S04 | Enter Other Camp/Access Expenses on Sub-Page (existing camp) | Alternative | **covered** | `sub-page.feature` `@p1 @S04` — GREEN |
| S05 | Enter Other Camp/Access Expenses on Sub-Page (new, unsaved camp) | Alternative | **covered** | `sub-page.feature` `@p1 @S05 @CFM-004` — GREEN |
| S06 | Check Status — All Requirements Met | Alternative | **covered** | `check-status.feature` `@p1 @S06` — GREEN. Asserts the per-camp line is ABSENT; found SPEC-3 |
| S07 | Delete an Existing Camp | Alternative | **covered** | `delete.feature` `@p1 @S07 @CFM-001` — GREEN |
| S08 | Same Camp Name Allowed in a Different Mill/Year | Alternative | **covered** | `same-name.feature` `@p1 @S08 @BR-02` — GREEN. The one slice needing two anchors |
| S09 | Recoveries Amount Reduces the Camp Total | Alternative | **covered** | `recoveries.feature` `@p1 @S09 @BR-04` — GREEN. Client-side mirror, never saves. Does NOT cover per-category `$/m³` — see note |
| S10 | Close/Navigate Away With Unsaved Changes Prompts a Discard Confirm | Alternative | **covered** | `discard-confirm.feature` `@p1 @S10 @CFM-002` — GREEN |
| S11 | Switch to a Different Camp While Editing Prompts a Discard Confirm | Alternative | **covered** | `discard-confirm.feature` `@p1 @S11 @CFM-003` — GREEN |
| S12 | Required Descriptive Field Left Blank (Camp Name or Isolated Camp) | Exception | **covered** | `name-validation.feature` `@p1 @S12 @FLD-001` — GREEN. RESOLVES FLD-001's `[UNKNOWN]` |
| S13 | Duplicate Camp Name on Save (Case-Insensitive) | Exception | **covered** | `name-validation.feature` `@p1 @S13 @ERR-001 @BR-02` — GREEN |
| S14 | Save a Copied Camp Without Renaming It (Duplicate Name Error) | Exception | **covered** | `name-validation.feature` `@p1 @S14 @FLD-001` — GREEN, re-grounded per SPEC-2: rejected as REQUIRED, not duplicate. Prediction CONFIRMED |
| S15 | Numeric Field Fails Range/Format Validation | Exception | **covered** | `numeric-validation.feature` `@p1 @S15 @FLD-002` — GREEN, 5 outline rows. WIDE band (Wages) not exercised — see note |
| S16 | No Mill/Year Selected in Session | Exception | **covered** | `render-states.feature` `@p1 @S16 @ERR-003` — GREEN. Client-side guard; no anchor and no request |
| S17 | Selected Mill Not Active for the Reporting Year | Exception | **covered** | `render-states.feature` `@p1 @S17` — GREEN. 409 detail asserted verbatim |
| S18 | No Schedule 5 Record Found for Mill/Year | Exception | **covered** | `render-states.feature` `@p1 @S18` — GREEN. 404; absence IS the fixture |
| S19 | Schedule Not Editable — Report Not in Draft (Read-Only View) | Exception | **covered** | `render-states.feature` `@p1 @S19 @STA-001 @BR-06` — GREEN. Needed a SEEDED camp — see GAP-3 |
| S20 | Check Status Finds Missing Required Values | Exception | **covered** | `check-status-missing.feature` `@p1 @S20 @FLD-003` — GREEN. Third confirmation of SPEC-3 |
| S21 | Other Access Expense Description Left Blank | Exception | **covered** | `sub-page-validation.feature` `@p1 @S21 @FLD-001` — GREEN. RESOLVES the sub-page `[UNKNOWN]` |
| S22 | Other Camp Expense Added With Blank Description, Blocked at Sub-Page Save | Exception | **covered** | `sub-page-validation.feature` `@p1 @S22 @FLD-001` — GREEN, re-grounded per SPEC-4 |
| S23 | Invalid Cost Entered on Other Camp/Access Expense Sub-Page | Exception | **covered** | `sub-page-validation.feature` `@p1 @S23 @FLD-002` — GREEN, TWO scenarios (one per page/band) |
| S24 | Check Status includes unsaved edits — a violation entered but not saved is reported | Alternative | deferred | BR-12 family; sch1/sch2/sch4/sch11 all carry a `@discovered-divergence` here — expect the same |
| S25 | Check Status includes unsaved edits — a correction made but not saved clears the error | Alternative | deferred | as S24 |

**Coverage: 23 / 25 slices (92%). P0: 1 / 1 authored.**

> **Every `deferred` row above is reserved, not blocked.** Each has a dedicated anchor exported from
> `fixtures/sch5/schedule5-test-data.ts` with a JSDoc line naming its slice — `EDIT_ANCHOR` (S02),
> `COPY_ANCHOR` (S03), `SUBPAGE_EXISTING_ANCHOR` (S04), `SUBPAGE_NEW_ANCHOR` (S05), `CHECK_MET_ANCHOR`
> (S06), `DELETE_ANCHOR` (S07), `SAME_NAME_A/B_ANCHOR` (S08, two by construction), `RECOVERIES_ANCHOR`
> (S09), `DISCARD_CLOSE_ANCHOR` (S10), `CAMP_SWITCH_ANCHOR` (S11), `VALIDATION_ANCHOR` (S12 + S15,
> validate-only so shared), `DUPLICATE_NAME_ANCHOR` (S13), `COPY_DUPLICATE_ANCHOR` (S14),
> `CLOSED_MILL_ANCHOR` (S17, 409), `NO_SCHEDULE_ANCHOR` (S18, 404), `READ_ONLY_ANCHOR` (S19, Submitted),
> `CHECK_MISSING_ANCHOR` (S20), `ACCESS_DESC_BLANK_ANCHOR` (S21), `CAMP_DESC_BLANK_ANCHOR` (S22),
> `SUBPAGE_COST_ANCHOR` + `SUBPAGE_COST_ACCESS_ANCHOR` (S23, two by construction),
> `CHECK_UNSAVED_VIOLATION_ANCHOR` (S24), `CHECK_UNSAVED_FIX_ANCHOR` (S25).
> S16 needs none — it is the no-context guard. `preflight/sch5-anchors.setup.ts` verifies all 23 plus
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
| Per-category `$/m³` recomputes for EACH category | S01 | **deferred** | S01 asserts the two TOTAL rates. NOT picked up by S09 after all: S09 enters no volume (Recoveries is the volume-less category), so every `$/m³` stays blank there. Rides S15, which enters volumes |
| Panel redisplays with recalculated values after save | S01 | **deferred** | the API read-back proves persistence, which is the stronger claim; re-render fidelity rides S02's reopen |

## Notes

- **S23 needed a second anchor, and the reason is worth knowing before authoring S24/S25.**
  Returning from an expense sub-page leaves the camp panel OPEN and DIRTY — the sub-page write moves
  the camp's Other-expense figures underneath a panel still holding the snapshot it was seeded with —
  so the next navigation raises a discard confirm ("Switch camp report" on Edit, "Leave camp report"
  on a sub-page link). A sub-page Save does **not** clear it. S04 does not hit this because it never
  visits a second sub-page. Rather than answer confirms inside a slice about cost bands, S23 is two
  single-page scenarios and `22050/2023` was minted for the second — the same move S12 needed.

- **S20's second arm is the third confirmation of SPEC-3, and it changes what the slice can assert.**
  The source scripts "All requirements for North Camp have been met." after the missing value is
  supplied. Once the only camp passes, the SCHEDULE passes, and the pass branch returns the schedule
  banner with `camps: []` — the per-camp loop is in the `else` branch. So the per-camp "met" line is
  reachable ONLY when the schedule fails and some individual camp passes, and **no slice in the
  catalogue puts the system in that state.** `campMet` is therefore still an un-exercised message;
  that is a genuine residual gap, recorded here rather than papered over by bending S20.

- **S19's two read-only mechanisms are different, and both are asserted.** It would be easy to write
  one "the panel is read-only" step and think the slice covered. Schedule 5 does it two ways at once:
  the four text descriptors stay Carbon `TextInput`s and take the `readonly` ATTRIBUTE (index.tsx:438-478),
  `Isolated Camp` is a `Select` and is DISABLED instead (:479-483), and only the category grid drops
  its inputs entirely (`AmountCell`, :202-203). So the grid check ("zero inputs") and the descriptor
  check ("readonly attribute") are genuinely independent, and the grid one is what makes the amount
  assertions meaningful — they read rendered text rather than the contents of boxes. Worth knowing
  because **Schedule 4 renders view mode as text throughout**, so the obvious cross-domain copy of its
  step would have silently asserted nothing here.

- **S19 asserts the ABSENCE of Edit/Delete/Copy, not a disabled state — deviation (B).** The source
  Gherkin expects them rendered-but-disabled beside a `View`; the rewrite drops them from the DOM and
  leaves View alone (index.tsx:1193-1208), citing the epics AC and Schedule 6's precedent. Legacy
  rendered a permanently-disabled Delete. Net user-reachable behaviour is identical — no write action
  either way — so this is a mechanism difference, not a divergence, and it is not logged as one. Same
  call sch4 made on its own read-only arm.

- **S19 also pins that Check Status is disabled outside Draft, and that is deliberately defensive.**
  Schedule 5 includes the `!editable` term on both `Add New Camp` (index.tsx:1404) and `Check Status`
  (:1419). Schedule 4 and Schedule 8 each SHIPPED a defect by omitting exactly that term (#293 fixed,
  #322 still open), so this is the one place in the codebase where the same one-line mistake has
  already been made twice. Asserting it here costs one line and stops Schedule 5 becoming the third.

- **S15 does not exercise the WIDE cost band, and that is a real gap rather than an oversight.**
  `validation.ts` declares four bands: STANDARD (±9,999,999, eight categories), **WIDE**
  (±99,999,999, `wagesAndBenefits` ALONE), NON_NEGATIVE (0–9,999,999, `recoveries`) and NONE (the two
  Other … rows, whose cost is the sub-page sum and is read-only). S15's source scenarios name only
  Catering and Food and Recoveries, so the outline covers STANDARD and NON_NEGATIVE. WIDE is the odd
  one out precisely because it is easy to lose: Wages and Benefits is the only category whose legacy
  input omits `costSize="7"`, and a blanket ±9,999,999 rule would make any stored camp above 9,999,999
  **un-re-saveable**. Worth an added Examples row; not added here because it is outside what the slice
  catalog scripts, and silently widening a slice hides the decision.

- **DEVIATION (K) is not yet covered, and deliberately so.** Legacy attached its panel CLOSE confirm
  **unconditionally** — there is no dirty check anywhere in `schedule5.xhtml` (:169, :192, :221, :244) —
  whereas the rewrite prompts only when the panel is genuinely dirty (`panelDirty`, index.tsx:669-670).
  S10 supplies a real unsaved change, so both systems prompt and the deviation never surfaces. A
  "close a CLEAN panel" scenario would land straight on it and is **not** in the 25-slice catalog. It
  belongs in its own slice with its own adjudication rather than being smuggled into S10, where a
  reader would not expect to find it. Raised here so it is not lost.

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
