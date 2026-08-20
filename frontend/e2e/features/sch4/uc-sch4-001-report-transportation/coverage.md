# Coverage — UC-SCH4-001 Report Special Log Transportation Costs (Schedule 4)

> How to read this file (columns, status flags, the `not-applicable`/`blocked`/`deferred` rule):
> [coverage-guide.md](../../../coverage-guide.md). Domain terms and tag meanings: [defects-guide.md](../../../defects-guide.md).

**Source inventory** — built from the UNION of three sources, not the `.feature` files alone:

| Source | What it contributed |
|---|---|
| `UC-SCH4-001/gherkin/*.feature` (31 slices S01–S31) | the executable scenarios |
| `UC-SCH4-001-slices.md` (Relevant Controls / Messages / Fields / Business Rules per slice; Gap Analysis) | 9 business rules (BR-01…BR-09), 27 fields, the deliberate-exclusion list |
| `UC-SCH4-001-technical.md` (Confirmed Messages, Validation Rules, UI Element Reference) | the ERR/WRN/STA/CNT/FLD/SUC/EF2/NAV catalogue (25 rows) |

…then reconciled against the app's ACTUAL write path: `components/schedule4/{index,SubPage,validation,subPageDefs}.tsx|ts`,
`Schedule4Controller`/`Schedule4Service`/`Schedule4Repository`, the `Schedule4LocationRequest` /
`Schedule4SubPageRowRequest` DTO constraints, and `messages.properties` — plus a by-hand API probe of every
write path on 2026-08-17 (documented in `fixtures/sch4/schedule4-test-data.ts`).

**Test data:** every Draft scenario seeds its own state through the app's own endpoints and deletes it again;
the read-only arm needs the one seed patch (`real-test-data-patches/sch4/view-mode-amounts.sql`) because the
extract contains **no Schedule 4 amounts at all** (1316 `ILCR_COST_REPORT_DETAIL` rows, none with a cost item
40–55). That is a data gap, not an app defect.

**Role/permission coverage:** `blocked`. `editable` is derived server-side from the caller's `EDIT_SCHEDULE`
permission AND the Draft track (`Schedule4Controller` + `Schedule4Service.getSchedule4`), and the E2E
environment's mock auth stamps ONE authority per process, so a "viewer is denied" branch cannot be produced
from a browser. It is also not yet a branch that exists: `SchedulePermissions.ROLE_ACTIONS` still grants
`ILCR_ADMIN` and `ILCR_SUBMITTER` the same two actions. The **Draft** half of that gate IS covered (S18, both
non-Draft codes), and the endpoint-level `@PreAuthorize` guards are covered by the backend's own
`Schedule4WriteAuthorizationIT`. Owned by the cross-cutting deferral in `deferred-work.md` (role-gated
behaviour under single-role mock auth) — QA returns to it once the role-specific behaviours are implemented.
See GAP-1.

---

## Happy path, alternatives and the write model

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Add a location with a fixed + a distance category; success message; $/m³ recomputed | S01, BR-05, BR-06 | `index.tsx` `putLocation`→`PUT /v1/schedule4/locations`; `Schedule4Service.saveLocation`; `perUnit` in `Schedule4Service.perUnit` | happy-path `@S01 @p0` | covered | — |
| …the recomputed $/m³ shown on the panel that saved it | S01, S02 ("shows the recomputed cost-per-volume") | `handleSave` never re-seeds `panelPerUnit` | nav-and-recompute `@S01 @S02 @discovered-divergence` | divergence | DIV-4 |
| Legacy grid row order (12 categories + 3 sub-page groups interleaved by cost-item code; dead 54 absent) | slices Field Reference; `subPageDefs.ts` | `GRID_ENTRIES` sort in `index.tsx` | happy-path `@S01 @p0` | covered | — |
| Edit a saved location's amount and re-save | S02, AF1 | `putLocation` with `id`+`revisionCount` | update `@S02 @p0` | covered | — |
| Optimistic lock refreshed after a save (a 2nd save must not 409) | §Decision 3 (Story 10.2) | `handleSave` re-seeds `panelRevision` | update `@S02 @p1` | covered | — |
| Rename moves the whole family (primary + distance children) | §Decision 2; `renameFamily` | `Schedule4Service.saveLocation` | update `@S02 @p1` | covered | — |
| Clearing a distance category deletes its child report | §Decision 1 write mirror | `writeDistanceCategory` empty-branch | update `@S02 @p2` | covered | — |
| A saved location survives a full reload | implied by BR-01 storage | `useScheduleDocument` re-GET | update `@S02 @p1` | covered | — |
| Towing Total row from an UNSAVED new location (save-first) | S03, NAV-003 | `requestOpenSubPage`→`confirmNav`→`saveLocationReturningId` | subpages `@S03 @p0` | covered | — |
| Towing Total row from a SAVED location (re-fetch, edits discarded) | S04, NAV-002 | `requestOpenSubPage` `kind:'existing'` | subpages `@S04 @p0` | covered | — |
| Truck Rehaul row carries Cycle (and only that sub-page has one) | S05 | `subPageDefs.ts` `hasCycle`; `SubPage.tsx` | subpages `@S05 @p1` | covered | — |
| Other Transportation row (no Cycle field) | S06 | same, `hasCycle:false` | subpages `@S06 @p1` | covered | — |
| Copy prefills a new location, clears the name, shows WRN-001 | S07, BR-09, WRN-001 | `openCopy` + `copyWarning` | copy `@S07 @p1` | covered | — |
| A copy also carries the source comments | `copyLocation` parity | `openCopy` sets `panelComments` | copy `@S07 @p2` | covered | — |
| Saving a copy with no name is refused | slices Gap Analysis combination (1) → S13 outcome | client gate in `putLocation` | copy `@S07 @S13 @p2` | covered | — |
| Save a location with only a name (all categories optional) | S08 | `buildRequest` omits empty categories | happy-path `@S08 @p1` | covered | — |
| A 30-character name is accepted and stored untruncated | S09 | `TextInput maxLength={30}`; DB column VARCHAR2(30) | happy-path `@S09 @p2` | covered | — |
| Delete a location and its related rows (family cascade) | S10, BR-08, NAV-004 | `handleDelete`→`DELETE .../locations?id=`; `deleteFamily` | delete `@S10 @p0` | covered | — |
| Cancelling the delete confirm is a no-op | NAV-004 (dialog semantics) | Carbon Modal `onRequestClose` | delete `@S10 @p1` | covered | — |
| Deleting one location leaves the others | BR-08 scoping | `findLocationName(id, mill, year)` | delete `@S10 @p1` | covered | — |
| Delete a sub-page row; totals recompute | S11, BR-08, NAV-005 | `handleDeleteRow`→`DELETE .../rows/{id}` | subpage-rows `@S11 @p0` | covered | — |
| Deleting the last row returns the table to empty + count 0 | S11 boundary | same | subpage-rows `@S11 @p1` | covered | — |
| Edit a sub-page row IN PLACE, then Save | slices/technical Control Reference (per-row editable cells); NOT in any slice | `SubPage.tsx` `putRow`→`PUT .../rows/{id}` | subpage-rows `@S11 @p1` | covered | SPEC-1 |
| Running totals across several rows incl. Cycle | S05, legacy footer | `SubPage.tsx` totals row | subpage-rows `@S05 @p1` | covered | — |
| The group's grid row shows the rolled-up totals | CNT-001 + legacy rollup inputs | `panelSubTotals` + `renderSubPageRow` | subpages `@S03 @S05 @p0/@p1` | covered | — |
| Sub-page link labels carry the live row count | CNT-001 | `` `${def.label} (${totals.count}):` `` | update/subpages/subpage-rows `@p0…@p2` | covered | — |
| Warn before discarding unsaved input (panel Close / Edit / Add New, and each sub-page's Back) | S12, NAV-001, epics.md Story 10.5 AC | NOT IMPLEMENTED (`closePanel`, `openNew`, `openEditOrView` switch unconditionally; `SubPage.tsx` has no confirm at all) | nav-and-recompute `@S12 @discovered-divergence` ×3 | divergence | DIV-3 |
| A discarded panel edit is never written | S12 consequence | no write path on close | nav-and-recompute `@S12 @p1` | covered | — |
| Cancelling NAV-002 stays on the panel with the edit intact | NAV-002 dialog semantics | `setNavConfirm(null)` | subpages `@S04 @p2` | covered | — |
| Column sort on a sub-page (3-state) | none — app-only affordance | `SubPage.tsx` `toggleSort` | subpage-rows `@p2` | covered | — |
| Cancelling NAV-003 stays on the New panel with the typed name, nothing saved | NAV-003 dialog semantics (the mirror of the NAV-002 cancel arm) | `setNavConfirm(null)` | subpages `@S03 @p2` | covered | — |
| The BROWSER Back button returns from a sub-page to the list | the rewrite's URL-state design (`?loc=&sub=`); legacy used a JSF forward + flash flag | `openSubPage` pushes a history entry | subpages `@S04 @p0` | covered | — |
| The View panel's secondary action is "Close" (it is "Back" while editable) | legacy `closeLocationBtn1…4` / STA-001 | `readOnlyPanel ? 'Close' : 'Back'` | render-states `@S18 @p0` outline | covered | — |

## Validation, messages and business rules

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| ERR-001 blank Location Name | S13, ERR-001, BR-02 | client `validateLocationForm` gate + server `@NotBlank{locationEmptyOrNull}` | validation `@S13 @p1`; whitespace-only `@S13 @p2` | covered | — |
| ERR-002 duplicate name, case-insensitive | S14, ERR-002, BR-02 | `Schedule4Service` `nameExists` → 409 verbatim | duplicate-name `@S14 @p1` | covered | — |
| …the name field is RESET to its prior value afterwards | ERR-002 trigger note | app KEEPS the entered value (Story 10.5 AC "entered values retained") | duplicate-name `@S14 @p1` (asserts as-built) | covered (re-grounded) | DIV-5 (log-only) |
| BR-02 excludes the location's own family (no-op / case-only self-rename allowed) | BR-02, `oldName` exclusion | `saveLocation` `oldName` | duplicate-name `@S14 @S02 @p1` | covered | — |
| FLD-001 category Volume range [0, 9,999,999] | S19, FLD-001 | `validation.ts` VOLUME + `CategoryInput` `@DecimalMin/Max` | validation `@S19 @p1` outline (both ends) | covered | — |
| FLD-002 category Cost range [-99,999,999, 99,999,999] | S20, FLD-002 | same | validation `@S20 @p1` outline (both ends) | covered | — |
| FLD-003 Distance range [0, 999,999.9] | S21, FLD-003 | `validation.ts` DISTANCE | validation `@S21 @p1` outline (both ends) | covered | — |
| …those bounds are INCLUSIVE | implied by the ranges | `fieldRange.ts` `rangeError` | validation `@S19 @S20 @S21 @p2` outline | covered | — |
| FLD-005 / BR-04 Distance ⇒ Volume+Cost required | S22, BR-04, FLD-005 `[UNKNOWN]` | `validation.ts` BR-04 + `DistanceCategoryCompleteValidator` | validation `@S22 @p1` outline | covered (message re-grounded) | — |
| FLD-005 / BR-04 Volume\|Cost ⇒ Distance required | S23, BR-04 | same | validation `@S23 @p1` outline | covered (message re-grounded) | — |
| BR-04 does NOT apply to the 9 fixed categories | slices Data Field Reference | `validation.ts` `kind === 'DISTANCE'` guard | validation `@S22 @p2` | covered | — |
| A fully-empty distance category raises nothing | BR-04 boundary | `DistanceCategoryCompleteValidator` empty-branch | validation `@S22 @S23 @p2` | covered | — |
| Two invalid cells report independently | slices Gap Analysis combination (2) | per-field `fieldErrors` map | validation `@S19 @S20 @p2` | covered | — |
| Sub-page Volume range [0, 999,999] (`volSize=6`) | S24 (quoted the default text) | `subPageDefs.ts` VOLUME + `{volume6DigitValidatorErrorMsg}` | subpage-validation `@S24 @p1` outline | covered (message re-grounded) | — |
| Sub-page Cost range [-9,999,999, 9,999,999] (`costSize=7`) | S25 `[UNKNOWN]` | `subPageDefs.ts` COST + `{costSize7ValidatorErrorMsg}` | subpage-validation `@S25 @p1` outline | covered (message resolved) | — |
| Truck Rehaul Cycle range [0, 999,999] | S26, FLD-004 | `subPageDefs.ts` CYCLE | subpage-validation `@S26 @p1` outline | covered | — |
| Sub-page Description required | S27 `[UNKNOWN]` | `validateSubPageRow` + `@NotBlank{missingRequiredFieldMsg}` | subpage-validation `@S27 @p1` outline | covered (message resolved) | — |
| …the tighter sub-page band really is tighter than the grid's | S24's premise | two different validator tables | subpage-validation `@S24 @p1` | covered | — |
| …those row bounds are INCLUSIVE | implied | `rangeError` | subpage-validation `@S24 @S25 @S26 @p2` | covered | — |
| An out-of-range IN-PLACE row edit blocks the sub-page Save | S25 applied to the row cells | `handleSave` `rowInvalid` gate | subpage-rows `@S11 @S25 @p1` | covered | SPEC-1 |
| Every rejection persists NOTHING | BR-02, S13–S27 | client gate; server 400 | every validation scenario (spy = 0 + read-back) | covered | — |
| Location Name > 30 chars → server 400 | `@Size(max=30)` | unreachable from the UI (`maxLength={30}`) | happy-path `@S09 @p2` proves the input truncates | not-applicable (UI) | — |
| Comments ≤ 3500 chars | slices Field Reference; `@Size(max=3500)` | `TextArea maxCount={COMMENTS_MAX}` | — | not-applicable | slices excluded it ("no distinct behaviour at the boundary"); the counter is cosmetic |
| ERR-003 generic service error on save/delete | technical ERR-003 `[UNKNOWN]` | `extractDetail(error) \|\| 'Schedule could not be saved.'` | — | deferred | GAP-2 |
| Stale optimistic-lock token → 409 | `scheduleRevisionConflictErrorMsg` | `bumpRevision` returns 0 → `StaleRevisionException` | — | deferred | GAP-3 |
| ALT-001 browser `alert()` | technical ALT-001 | none — no `alert()` in the app | — | not-applicable | legacy had none either |
| ASY-001 async/job | technical ASY-001 | none — all synchronous | — | not-applicable | legacy had none either |

## Check Status (BR-07 / EF3)

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| A stored category with a null Cost is "Value Required" | S28, EF3, BR-07 | `Schedule4Service.checkStatus` cost-null branch | check-status `@S28 @p0` (incl. recovery) | covered | — |
| A stored Cost of ZERO counts as present | §Decision 1 (`CheckStatusUtil`) | `category.cost() == null` only | check-status `@S28 @p1` | covered | — |
| A sub-page ROW with a null Cost fails its location | BR-07 ("and per sub-page row") | `checkStatus` row loop | check-status `@S28 @S11 @p1` | covered | — |
| SUC-005 per-location "All requirements for {0} have been met." | SUC-005 | `locationRequirementsMetMsg` + name arg | check-status `@S28 @S31` | covered | — |
| SUC-006 whole-schedule banner ONLY when every location passes | SUC-006, S31 | `scheduleMet &= met` | check-status `@S31 @p1` | covered | — |
| Mixed per-location results in one response | S31 | per-location `LocationCheckResult` | check-status `@S31 @p1` | covered | — |
| A mill/year with no locations is vacuously MET | legacy AND-over-locations | `checkStatus` empty loop | check-status `@S28 @p2` | covered | — |
| Check Status mutates nothing | AD-5 | `@Transactional(readOnly)`; no state change | implicit in every check-status scenario (read-backs unchanged) | covered | — |
| …the message NAMES the field that needs a value | EF3 (`"Location : <name> - <field> (Cost $) "`), §Decision 4 (`FieldIssue.code`) | the page renders only the location name + "Value Required" | check-status `@S28 @discovered-divergence` | divergence | DIV-2 |
| A missing DISTANCE fails Check Status | S29 (premise flagged as inferred) | NOT enforced by design (§Decision 2 — legacy's check is commented out) | check-status `@S29 @p1` covers the actual behaviour | covered (re-grounded) | SPEC-3 (log-only) |
| Missing COMMENTS flagged as "Value Required" | S30 | NOT enforced by design (§Decision 3 — no bundle key exists) | check-status `@S30 @p1` covers the actual behaviour | covered (re-grounded) | SPEC-4 (log-only) |

## Guard states, read-only mode and chrome

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| EF2-001 no mill/year → panel suppressed | S15, EF2-001 | `contextMissing` branch in `index.tsx` | render-states `@S15 @p1` | covered | — |
| EF2-002 mill not active for the year (409) | S16, EF2-002 | `MillContextService.validateMillYearActive` → verbatim detail | render-states `@S16 @p1` outline | covered | — |
| EF2-003 no Schedule 4 for the mill/year (404) | S17, EF2-003 | same | render-states `@S17 @p1` outline | covered | — |
| …the recovery step ("select a valid mill and re-open") | S15/S16 recovery arms | Home page (UC-SEC-001) | — | not-applicable | belongs to UC-SEC-001, which covers it (`features/sec/…`) |
| STA-001 read-only outside Draft: row action becomes View | S18, STA-001, BR-03 | `openEditOrView(location, editable ? 'edit' : 'view')` | render-states `@S18 @p0` outline (S + V) | covered | — |
| STA-001 Add New Location disabled | S18, BR-03 | `disabled={!editable \|\| saving}` | render-states `@S18 @p0` outline | covered | — |
| STA-001 Copy/Delete disabled | S18, BR-03 | `disabled={!editable \|\| saving}` | render-states `@S18 @p0` outline | covered (mechanism re-grounded: disabled, not omitted) | — |
| STA-001 the panel renders read-only (values as text, no inputs) | S18 | `readOnlyPanel` branches | render-states `@S18 @p0` outline | covered | — |
| STA-001 the sub-page loses its add-row form and per-row Delete | S18 + Story 10.6 AC5 | `editable &&` guards in `SubPage.tsx` | render-states `@S18 @p0` outline | covered | — |
| STA-001 Check Status disabled outside Draft | S18 (explicit), technical Control Reference `schedule4.xhtml:43` | NOT disabled (`disabled={saving}` only) | render-states `@S18 @discovered-divergence` | divergence | DIV-1 |
| The empty-list state | S01 precondition | `data.locations.length === 0` branch | render-states `@S01 @p2` | covered | — |
| NAV-004 confirm text | NAV-004 (`confirmDeleteMsgPart1` + `Part2`) | `CONFIRM_DELETE` in `index.tsx` | delete `@S10 @p0` | covered (punctuation re-grounded) | DIV-6 (log-only) |
| NAV-005 row-delete confirm text | NAV-005 | `CONFIRM_DELETE_ROW` in `SubPage.tsx` | subpage-rows `@S11 @p0` | covered | — |
| Existing Locations table lists category + sub-page COUNT COLUMNS | Story 10.5 AC1 / Story 10.7 AC1 | not rendered — and legacy had only a Location Name column (`schedule4.xhtml:50-104`) | counts covered where they exist (CNT-001, sub-page links) | not-applicable | SPEC-2 |
| WCAG 2.1 AA on every Schedule 4 surface | NFR1, Story 10.7 AC2 | — | accessibility `@a11y` ×9 | covered (3 red) | DIV-7, BUG-1 |
| WCAG 2.1 AA on the VALIDATION-ERROR state | NFR1, Story 10.7 AC2 | Carbon `TextInput` `invalid` wiring (app-wide) | — | deferred | GAP-4 |
| Viewer/role-denied branch | BR-03 (actor lacks edit rights) | `permissions.hasPermission(auth,'EDIT_SCHEDULE')` — both roles currently grant it, and mock auth stamps one authority per process | — | blocked | GAP-1 |

---

## Coverage gate (§B of the quality gates)

Counted mechanically from the three tables above (88 source-item rows; a row's priority is the lowest `@pN`
its Scenario cell carries). **6 rows are `not-applicable`** and leave the denominator: the >30-char name via
the UI, the Comments maxlength, ALT-001, ASY-001, the EF2 recovery arms (owned by UC-SEC-001), and the AC1
count columns. That leaves **82 coverage-eligible rows**.

| Bar | Threshold | Result |
|---|---|---|
| **P0** | 100% | **100%** — 19 of 19 eligible P0 rows covered, 0 gaps. Every core journey (S01 create, S02 edit, S03/S04 sub-page entry, S07 copy, S10 delete, S11 row delete, S18 read-only ×2 codes, S28 Check Status) is covered by a passing or deliberately-red scenario. |
| **P1** | ≥ 90% (floor 80%) | **100%** — 37 of 37 eligible P1-tagged rows covered, 0 gaps. |
| **Overall** | ≥ 80% | **95.1%** — 78 of 82 eligible rows covered; 4 counted gaps, all named below. |

The four counted gaps (each filed in `defects.md`, none of them an app fault):

| Gap | Kind | Why it counts against coverage |
|---|---|---|
| GAP-1 | `blocked` | role-gated behaviour cannot be produced under single-role mock auth, and the two `ROLE_ACTIONS` sets do not yet diverge. Endpoint enforcement is covered by the backend's `Schedule4WriteAuthorizationIT`; owned by the cross-cutting deferral in `deferred-work.md`, so a gate should treat it as **waived**. |
| GAP-2 | `deferred` | ERR-003's generic save-failure text is `[UNKNOWN]` in the source docs; no slice exists and none was invented. |
| GAP-3 | `deferred` | the stale-save-token 409 needs two concurrent sessions; verified at the API by hand, not through the browser. |
| GAP-4 | `deferred` | the validation-error axe sweep is skipped by the project's cross-cutting convention (`deferred-work.md`, app-wide WCAG 4.1.2), so Schedule 4's error state is genuinely unswept. |

`@discovered-divergence` / `@discovered-bug` reds COUNT as covered — they map to their requirement and are
deliberately red (never forced green; the red is the signal). **Gate result: PASS**, with the four gaps named
rather than absorbed.

## Run summary (authored 2026-08-17, stress + final gate 2026-08-18; local delivery DB, app commit `9632f7f`)

| Run | Command | Result |
|---|---|---|
| Full suite | `npm test -- --grep @sch4` | **195 passed / 0 skipped / 9 deliberately-red** (8 `@discovered-divergence`, 1 `@discovered-bug`) of 204 — no unexplained red. Re-measured 2026-08-19. |
| **The gate** | `npm run test:gate -- --grep @sch4` | **exit 0, 194 passed** — the known reds excluded |
| DB left as found | anchor sweep after every run | **0 residue** across all 50 mutating anchors after every COMPLETED run, and after a run stopped gracefully (fixture teardown still runs). A run whose PROCESS is killed outright does leave residue — teardown never executes — which happened once during authoring and left 2 locations behind; `preflight/sch4-anchors.setup.ts` is the designed net for exactly that: it fails the next run naming the dirty anchors and telling you the exact `DELETE /api/v1/schedule4/locations?millId=&year=&id=` to run |
| Cleanup blind spot | the full run INCLUDES the `@discovered-*` reds | their teardown was exercised too (the residue sweep above followed that run) |
| Parallel stress ×3 | `--repeat-each=5` (twice at default workers, once at `--workers=4`) | **512 / 514 each run — 1,542 executions, 6 failures, ALL at the entry point (app-shell paint, Home's first fetch, one Chrome launch >60 s), never the same test twice, and ZERO data-contention failures.** Two genuine readiness waits were stabilised as a result (see `defects.md`); the rest is this box's dev-mode Vite server saturating under ~24 concurrent browsers for 20+ min. Reported as measured rather than retried away or timeout-inflated — see the note below. |

The eight deliberate reds, each named in `defects.md` with an `ACTION: BA/QA → Jira`:

| Red | Entry | What it tracks |
|---|---|---|
| render-states `@S18` | DIV-1 | Check Status stays enabled outside Draft |
| check-status `@S28` | DIV-2 | the Check Status issue does not name the category |
| nav-and-recompute `@S12` ×3 | DIV-3 | NAV-001 confirm is not implemented — panel Back, Add New Location, and sub-page Back |
| nav-and-recompute `@S01 @S02` | DIV-4 | the recomputed $/m³ is stale until the location is reopened |
| accessibility ×2 | DIV-7 | the editing-row highlight should not exist (legacy had none); it also fails contrast at 3.81:1, Draft and View |
| accessibility ×1 | BUG-1 | app-wide: a hovered table row fails contrast (3.79:1) |

> ⚠️ **Every mutating scenario owns its own (mill, year)** — 50 of them, listed in
> `fixtures/sch4/schedule4-test-data.ts`'s anchor table, and `preflight/sch4-anchors.setup.ts` fails the run
> if two ever collide or if one is not empty at rest. A `Scenario Outline` row is its own test, which is why
> the sub-page validation outline carries an `anchor` column instead of sharing one.
>
> ⚠️ **On the stress result — read this before treating it as a red flag.** The suite's *design* clears the
> non-flaky bar: across 1,542 parallel executions there was not one anchor collision, shared-state race or
> ordering dependency, which is what the one-mutating-anchor-per-scenario rule exists to guarantee. Every
> failure was the SPA failing to boot or Home failing its first fetch under sustained load — one of them the
> app itself rendering "Unable to load" — and no test failed twice across the three runs. Two readiness waits
> (app shell, Home's first fetch) were genuinely too tight and are now on the navigation budget; timeouts were
> deliberately NOT inflated further, because chasing a saturated dev server that way hides the signal and
> slows every rejection scenario. If you want a clean sweep on a developer box, either pass `--workers=4` or
> stress against a production build (`vite preview`) so the frontend is not compiling on demand.
>
> ⚠️ **Never run two suites against this DB at once.** Each scenario owns its anchor *within* a run; two
> concurrent runs will fight over the same anchors and each other's cleanup sweeps (learned the hard way
> while authoring — a focused run started alongside a stress run produced two bogus reds).
