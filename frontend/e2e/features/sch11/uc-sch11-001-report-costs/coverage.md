# Coverage — UC-SCH11-001 Report Basic Silviculture Costs (Schedule 11)

> New to these files? See [`coverage-guide.md`](../../../coverage-guide.md) at the e2e root for the column + status-flag legend.

Sources reconciled: `UC-SCH11-001-S01..S20.feature` (20 slices; `../../../../tests/UC-SCH11-001/gherkin/`)
+ `UC-SCH11-001-slices.md` (control/message/field matrix) + `UC-SCH11-001-technical.md` (message/error
catalog: ERR-001..003, ALT-001, WRN-001, STA-001, CNT-001, FLD-001..004, SUC-001..004, ASY-001), against
the app's real write path (`schedule11/api/Schedule11Api.java` GET/POST/PUT/DELETE + `check-status`,
`dto/SilvicultureLocationRequest.java` validators, `Schedule11Service.java`, and
`components/schedule11/index.tsx` + `validation.ts`).

Test data (real, discovered 2026-08-10): pinned in `fixtures/sch11/schedule11-test-data.ts` with the
finding queries in comments. Anchors were found by enumerating `GET /v1/mill-context` over every
mill × reporting year (118 rows carrying a report-status row), then classifying each with
`GET /v1/schedule11`. 89 pristine editable-Draft keys exist; every **mutating** scenario owns a distinct
one, and `preflight/sch11-anchors.setup.ts` asserts that distinctness plus every pinned anchor and BEC
option before the suite runs.

Scope: S01 add (`happy-path.feature`); S02 multiple locations (`multiple-locations.feature`); S03 inline
edit (`inline-edit.feature`); S04–S06 Check Status (`check-status.feature`); S07–S08 delete + cancel
(`delete.feature`); S09 add-is-save persistence (`persistence.feature`); S10 track independence
(`track-independence.feature`); S11–S13 + S20 guard/read-only renders (`render-states.feature`);
S14–S19 entry rejection (`validation.feature`); the correct-and-retry recovery arm
(`correction.feature`); per-row optimistic locking (`concurrency.feature`, closing GAP-3); and WCAG 2.1 AA (NFR1) across **six** structurally distinct renders.
**Five are clean** — editable-with-a-row, the open inline row editor, read-only and a guard state
(`accessibility.feature`), plus the Check-Status result (`check-status.feature`). The **sixth**, the
**validation-error** state, carries a genuine pre-existing violation and is therefore a deliberate RED
(`accessibility.feature` `@discovered-bug`) rather than a clean pass.

**Every one of the 20 slices is dispositioned `covered`.** 29 scenarios: 28 green + **1 deliberate
`@discovered-bug` RED** tracking a pre-existing, app-wide accessibility defect (defects.md
BUG-1 — Carbon's validation-error markup is never announced to assistive technology; it
affects every schedule page and is already recorded in `deferred-work.md`, which asked for exactly this
red check). A clean run is `npm run test:gate` (the script regenerates the features first and excludes
every `@discovered-*` red).

## Re-grounding headline

Schedule 11 was rebuilt, not ported, so some structural facts of the legacy `.feature` set have **no
counterpart** in the React app. Each is asserted as the app actually behaves and logged as a Divergence
rather than silently dropped:

1. **There is no page-level Save button.** Legacy `btnSaveTop` (`schedule11.xhtml:185`) / `btnSave` (`:420`)
   are gone, so every "…then click Save" tail step has no control to click — each action saves itself
   instead (DIV-1). **Add is NOT part of this** — legacy `addLocation()` itself called `save(true)`, so
   legacy was add-is-save too (corrected 2026-08-10 against the legacy source). Delete is not part of it
   either: it behaves well and consistently with the other pages.
2. **Read-only OMITS rather than disables.** Legacy disabled the six Add fields and every row control
   (20 `disableReportEdits()` bindings); the app does not render the Add panel or the Actions column at
   all when `editable` is false (DIV-2).
3. **One Check Status button, not two** (legacy had both `:191` and `:426` — DIV-3).
4. **The per-field "original value" indicators are gone.** All six legacy row fields carried an
   `OV`/`OB`/`TT` indicator triple showing the previously-saved value once the report left Draft; the new
   app has no counterpart, and the API exposes no prior value to render one from (DIV-4). Found on
   2026-08-10 by checking whether Schedule 1's DIV-5 also applies here — it does, same root cause. No ticket
   exists for it yet, so it is **BA/QA to triage** rather than something already queued. Out of reach for
   this UC's scenarios, which all write against Draft.

**Disproved and reclassified:** an earlier revision also claimed legacy recomputed the row/footer totals
live as you typed. It does not — the derived cells are `disabled="true"` inputs whose `p:ajax` can never
fire, and no row-field `p:ajax` references a total cell or footer id. Both apps refresh derived figures on
save, so this is not a divergence at all; it now sits in *Verified — not a defect* as **VER-6**. The claim
came from over-reading S03's Gherkin — now **corrected at the source** (both the `.feature` and the
slice catalog's CNT-001 row), so **SPEC-3 is CLOSED**.

**Triage state (2026-08-10, with the Schedule 11 dev).** DIV-2 closed as confirmed-intentional; DIV-1 and
DIV-3 to be double-checked by the dev with the BA; DIV-4 with the dev (needs a backend change, and Schedule 1
has the same gap); GAP-1/GAP-2 assigned to the dev for backend `@Size` tests (still open — they close when
the tests land, not when promised); GAP-7 closed as handed off. Nothing on the E2E side is outstanding.

**Legacy-parity audit (2026-08-10).** Every legacy claim in this ledger was re-verified against the legacy
source itself (`docs/nr-ilcr-2.0.4/…/schedule11.xhtml`, `Schedule11MB.java`, `messages.properties`,
`validation.properties`, `Constant.java`) rather than the derived technical sidecar. Nine claims confirmed;
**two corrected** — DIV-1 narrowed (Add is not a divergence) and the pre-save-recompute claim disproved
outright (now VER-6).
No test assertion changed as a result: the tests already asserted the behaviour both apps actually have.

It also **resolves four items the legacy sidecar could not** — see `defects.md`
VER-1–VER-4. Two of them (`S15` Enhanced, `S18` NAR range) are the exact strings
`components/schedule11/validation.ts` still labels `PROVISIONAL … confirmed in Story 25.4` — i.e. by this
work. **The comments in `validation.ts` should now be updated to drop the PROVISIONAL label**
(GAP-7, now CLOSED — the dev accepted the follow-up; the labels may still be in `validation.ts` until she lands it).

Suite state: **28 green + 1 intentional red** (the `@discovered-bug` accessibility check above). The
whole-suite clean run `npm run test:gate` is green; the two excluded reds
are this UC's accessibility bug and Schedule 1's pre-existing `clear-amounts` bug — both genuine,
pre-existing app defects with `defects.md` entries, neither masked or weakened.

**Assertion-strength hardening (2026-08-10).** Story 25.4's Review Findings (from an earlier, since-removed
attempt) list traps a 4-layer review caught the first time round. This suite was checked against every one
and five real weaknesses in the first draft of these tests were fixed rather than inherited:
- **FLD-004's double space was not actually being asserted.** Playwright's text matchers normalize
  whitespace, so `"location  : …"` would have passed with one space. Now compared against the result
  region's raw `textContent` (`the Schedule 11 check status shows verbatim …`).
- **S16 was testing the wrong condition** — it typed text matching nothing, leaving the suggestion list
  empty, which only proves an empty field is rejected. Now types a prefix that returns 20 real
  suggestions, asserts the list populated, and chooses none: the actual forced-selection path.
- **S20 could have passed vacuously** on an empty-table regression, being proven entirely by absence. Now
  makes a positive assertion on each read-only anchor's real seeded row.
- **The zero-write proofs had no settle window** — an app that rendered the error and *then* POSTed a tick
  later would have read 0 and passed. Now the tally is read after a 750 ms hold.
- **AC7's Check-Status-result and validation-error states were never swept by axe.** Both are now swept —
  and the validation-error sweep is what found BUG-1.

**Two different yardsticks are in play — read the ledger accordingly.** Almost every row below is measured
against the **legacy app**: if legacy did X and the new app does Y, that is a Divergence, and legacy is the
authority. The four `WCAG 2.1 AA` rows are the exception — they are measured against **NFR1**, an
*additive modernization requirement* (`epics.md:70`, WCAG 2.1 AA / BC Gov standard), which legacy was never
held to and demonstrably fails worse (its Enhanced dropdown had no `label` at all, so it rendered a raw
internal field id as the error text). So an accessibility finding here is **not** a regression from legacy
and must not be triaged by asking "did legacy do this?". NFR1 is also satisfied by *triage*, not only by
zero: the epic AC reads "violations are zero, **or** each remaining violation is triaged with a recorded
disposition".

Non-flaky proof: the suite stresses cleanly at `--repeat-each=3` **except** `concurrency.feature`, which is
a SINGLE-OWNER mutating scenario and must be stressed **serially** (`--repeat-each=5 --workers=1`, 33/33 on
2026-08-10). Parallel copies self-collide on the app's own duplicate-(location, biogeo) rule — a harness
artifact, not a defect. Same constraint Schedule 1 records for its destructive scenarios.

Cleanup: a Schedule 11 location is a row the scenario itself creates, so teardown **deletes** it via the
app's own `DELETE` endpoint (the preferred route — no DB fallback needed, unlike Schedule 1 whose summary
pre-exists and must be restored). The `schedule11Cleanup` registry fails loud on residue.

| Source item (slice) | Source citation | App enforcement point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| S01 Add and save a location (happy path) | S01.feature; slices.md | `Schedule11Api.addLocation` (POST `/locations`); `Schedule11.handleAdd` (index.tsx:596) | `happy-path.feature` `@S01 @p0` | covered | Legacy's trailing Save click has no analogue — DIV-1 |
| S02 Report additional locations in one session | S02.feature (Alt, AF1) | same POST; `data.totals` re-echoed per write | `multiple-locations.feature` `@S02 @p1` | covered | Both rows asserted independently (own Enhanced flag + own BEC entry) plus accumulated footer totals |
| S03 Edit an existing location inline | S03.feature (Alt, AF2) | `Schedule11Api.editLocation` (PUT `/locations/{id}`, `OnUpdate` group); `handleSaveEdit` (index.tsx:662); `EditRow` (index.tsx:337) | `inline-edit.feature` `@S03 @p1` | covered | Asserts the post-save refresh, which is what BOTH legacy and the app do (legacy's derived cells are disabled inputs; nothing recomputed on keystroke) — see defects.md VER-6 + SPEC-3 (both CLOSED). Reject arm proves zero-write with the spy |
| S04 Check Status — all requirements met | S04.feature (AF3, BR-07 pass) | `Schedule11Service.checkStatus` (`requirementsMetMessage` non-null); `handleCheckStatus` (index.tsx:732) | `check-status.feature` `@S04 @p1` | covered | Asserts SUC-004 **and** SUC-003 together |
| S05 Check Status — missing Actual Cost flagged | S05.feature (BR-07 fail) | `Schedule11Service.missingCost` (FLD-004 composition) | `check-status.feature` `@S05 @p1` | covered | Verbatim incl. the literal DOUBLE space, asserted against RAW `textContent` because Playwright's text matchers normalize whitespace — VER-3. Precondition asserts the seeded cost is really missing |
| S06 Check Status — missing Planned Cost flagged | S06.feature (BR-07 fail) | as above, `"Planned cost"` label | `check-status.feature` `@S06 @p1` | covered | as above |
| S07 Delete a location | S07.feature (Alt, AF4) | `Schedule11Api.deleteLocation` (DELETE); `handleDelete` (index.tsx:702) | `delete.feature` `@S07 @p1` | covered | Delete confirms, removes and persists in one step — consistent with the other pages. Also asserts the table returns to its empty state AND that the footer totals blank out |
| S08 Cancel delete confirmation — row unchanged | S08.feature (Alt, gap-analysis 3d) | `Modal onRequestClose` (index.tsx:1045) | `delete.feature` `@S08 @p1` | covered | Proves the negative four ways: zero mutations (spy, after a settle window), unchanged row count, `revisionCount` still 0, and the delete-success message absent |
| S09 Add persists without a separate Save click | S09.feature (Alt, gap-analysis 3c) | POST `/locations` is itself the write | `persistence.feature` `@S09 @p1` | covered | Legacy's "navigate away" strengthened to a full page reload (discards all React/router state) |
| S10 Editability independent of the Schedule 1–10 status | S10.feature (BR-02 negative side) | `Schedule11Service:131` `editable = callerMayEdit ∧ trackStatus=="D"` — the 1–10 track is never read | `track-independence.feature` `@S10 @p0` | covered | The seed holds exactly ONE such (mill, year): 23050/2016 (1–10 = "S", silv = "D"). Preflight asserts both statuses so a re-extract fails loudly instead of testing nothing |
| S11 Mill and year not selected | S11.feature (EF1); ERR-001 | `Schedule11` contextMissing branch (index.tsx:761) | `render-states.feature` `@S11 @p1` | covered | Client-side guard — fires no request, so the frontend's no-trailing-space literal renders. Asserted as a substring, true of both forms |
| S12 Mill not active for the reporting year | S12.feature (EF2); ERR-002 | GET → 409; `errorDetail` PageState (index.tsx:784) | `render-states.feature` `@S12 @p1` | covered | Verbatim from the 409 `detail`; preflight pins the 409 |
| S13 Schedule not found | S13.feature (EF3); ERR-003 | GET → 404; same PageState | `render-states.feature` `@S13 @p1` | covered | Verbatim from the 404 `detail`; preflight pins the 404 |
| S14 Location missing | S14.feature (EF4 r1); FLD-001 | `@NotBlank locationRequiredErrorMsg`; `validateLocation` (validation.ts:85) | `validation.feature` `@S14 @p1` | covered | Rejection **and** zero-write proven |
| S15 Enhanced indicator not selected | S15.feature (EF4 r2); FLD-001 `[UNKNOWN]` | `@NotNull enhancedIndicatorRequiredErrorMsg`; Dropdown with NO default so null stays expressible (index.tsx:87) | `validation.feature` `@S15 @p1` | covered | **Resolves the legacy `[UNKNOWN]`** — VER-1 |
| S16 Biogeo missing / not selected from suggestions | S16.feature (EF4 r3); BR-09 | forced selection: `onInputChange` drops the resolved option (index.tsx:224); `@NotNull biogeoRequiredErrorMsg` | `validation.feature` `@S16 @p1` | covered | Types a prefix returning 20 REAL suggestions, asserts the list populated, chooses none — the genuine forced-selection path (an empty list would prove only that a blank field is rejected). Preflight pins the prefix's properties |
| S17 NAR(ha) missing | S17.feature (EF4 r4); FLD-001 | `@NotNull netAreaRequiredErrorMsg` | `validation.feature` `@S17 @p1` | covered | Rejection + zero-write |
| S18 NAR(ha) out of range | S18.feature (EF4 r5); FLD-002 `[TODO]` | `@DecimalMin/@DecimalMax/@Digits netAreaRangeErrorMsg`; `validateLocation` decimals cap | `validation.feature` `@S18 @p1` (outline ×2) | covered | **Resolves the legacy `[TODO — capture from live app]`** — VER-2. BOTH directions covered |
| S19 Actual or Planned Cost out of range | S19.feature (EF4 r6); FLD-003 | `@DecimalMin/Max costValidatorErrorMsg` on both costs; `validateCost` (validation.ts:68) | `validation.feature` `@S19 @p1` (outline ×2) | covered | Outline crosses field × direction (Actual-above, Planned-below), not one corner |
| S20 Silviculture track no longer Draft — read-only | S20.feature (EF5); STA-001 | `editable:false` → Add panel + Actions unrendered; Check Status `disabled` (index.tsx:881) | `render-states.feature` `@S20 @p1` (outline ×2) | covered | BOTH non-Draft codes ("S" and "V") covered. Also makes a POSITIVE assertion on each anchor's real seeded row, so an empty-table regression cannot pass vacuously. Omit-vs-disable — DIV-2, **CLOSED as confirmed-intentional** (dev, 2026-08-10) |
| Correct-and-retry recovery (the second scenario of S14–S19) | S14–S19.feature recovery arms | `handleAdd` clears `addErrors` and re-runs `validateLocation` over the whole form | `correction.feature` `@S14 @S17 @p2` | covered | Covered ONCE by equivalence: one code path serves every field, and each copy would need its own write anchor. The per-field REJECTIONS (what the 25.4 AC enumerates) are each covered individually |
| SUC-001 "Data saved successfully" (add + edit) | technical.md SUC-001; `messages.properties:125` | echoed in `Schedule11Response.message` | `happy-path` `@S01`, `inline-edit` `@S03`, `multiple-locations` `@S02`, `persistence` `@S09`, `track-independence` `@S10`, `correction` | covered | API-owned, asserted verbatim |
| SUC-002 "Data deleted successfully" | technical.md SUC-002; `messages.properties:126` | DELETE response `message` | `delete.feature` `@S07` | covered | Legacy fired this BEFORE persistence; the app fires it after the real delete — DIV-1 |
| SUC-003 "All requirements … met" (conditional) | technical.md SUC-003 | `requirementsMetMessage` non-null only when met | `check-status` `@S04` (present), `@S05`/`@S06` (asserted ABSENT) | covered | Both arms of the conditional asserted — symmetry check |
| SUC-004 "Status has been checked" (unconditional) | technical.md SUC-004 | `message` on every check-status response | `check-status` `@S04`/`@S05`/`@S06` | covered | Asserted on the pass AND both fail branches, which is what "unconditional" means |
| CNT-001 footer Totals recompute | technical.md CNT-001; BR-08 | `SilvicultureTotals` server-computed; rendered index.tsx:1024-1028 | `happy-path` `@S01`, `multiple-locations` `@S02`, `inline-edit` `@S03` | covered | Asserted after add, after a second add (accumulation), and after an edit |
| Null totals render BLANK, never "0" | `SilvicultureTotals` javadoc; `mask()` (index.tsx:57) | Jackson non_null omits them; `mask` returns `''` | `persistence.feature` `@S09` | covered | S09 enters no costs, so the derived cells must be blank — the distinction is meaningful, not cosmetic |
| Delete confirmation prompt text | S07/S08.feature `[UNKNOWN]` | `CONFIRM_DELETE` + Carbon `Modal` (index.tsx:1039) | `delete.feature` `@S07`/`@S08` | covered | **Resolves the legacy `[UNKNOWN]`** (`confirmDeleteMsg` was never captured) — VER-4 |
| ALT-001 no native `alert()` | technical.md ALT-001 | Carbon `Modal`, a DOM widget | `delete.feature` `@S07`/`@S08` | covered | Confirmed: no `page.on('dialog')` handler is needed anywhere |
| WRN-001 no warning messages on this screen | technical.md WRN-001 | no warning path in `Schedule11Service` | — | not-applicable | Legacy had none either; nothing to assert |
| ASY-001 no async/batch job | technical.md ASY-001 | all four operations are synchronous HTTP | — | not-applicable | Confirmed against the current API surface |
| Original-value indicator icons (`locationOB`, `biogeoOB`, `enhancedIndicatorOB`, `netAreaOB`, `actualCostOB`, `plannedCostOB`) | technical.md control ref; UI element ref; `schedule11.xhtml` (read directly 2026-08-10) | **no new-app equivalent, end to end** — no `original`/`previous` field in `schedule11/dto/*` or `Schedule11Response.ts` | — | **divergence** | **DIV-4** — a genuine legacy capability not carried over (all SIX row fields had an `OV`/`OB`/`TT` triple, guarded by `is…OriginalVal…(isSubmit)` so they rendered only once the report left Draft). Same root cause as Schedule 1's DIV-5 and should ride that ticket. Out of reach for this UC (all scenarios write against Draft) |
| Comments field, optional, max 3500 (BR-10) | slices.md; `commentsMaxLengthErrorMsg` | `maxCount` + `enableCounter` (index.tsx:959); `@Size(max=3500)` | `happy-path` `@S01` (entry + read-back only) | covered (+ gap) | Entry and persistence are covered; the 3500 CAP is not — GAP-1 |
| Location max length 30 | `locationMaxLengthErrorMsg`; slices.md | `maxLength={30}` (index.tsx:895); `@Size(max=30)` | — | not-applicable (UI) | The Carbon `maxLength` attribute makes a 31st character untypeable, so the message is unreachable through the UI. Reachable only by calling the API directly — GAP-2 |
| Character counter ("n characters remaining") | technical.md control ref `characterCounter` | Carbon `enableCounter` renders "x/3500" instead | — | deferred | Different mechanism, presentational only, no message contract. GAP-1 covers the cap it belongs to |
| Per-row optimistic lock — stale `revisionCount` → 409 | **no legacy slice** (new-app concurrency; SPEC-1) | `Schedule11Service.editLocation` stale check; token captured at `startEdit` (index.tsx:639) | `concurrency.feature` `@p1` | **covered** | **GAP-3 CLOSED** — opens the editor, moves the token via the API, saves: 409 detail renders verbatim AND the other session's value is asserted as the survivor (so a lost-update cannot pass). Proven non-vacuous by removing the conflict step. Complements `Schedule11WriteIT.staleAndMissingRevision()`, which proves the server side but not the user-visible handling |
| Duplicate biogeo/location key → 409 | **no legacy slice** (SPEC-1) | `Schedule11Service.addLocation` duplicate check | — | not-applicable (UI) | **GAP-4 CLOSED** — unreachable through the UI (forced selection); covered by `Schedule11WriteIT.duplicateBiogeoLocation_returns409()`. That IT is skipped in CI (AR17), so verified-but-ungated |
| Unresolvable BEC id → 400 `invalidBiogeoCode` | **no legacy slice** (SPEC-1) | `Schedule11Service` biogeo resolution | — | not-applicable (UI) | **GAP-4 CLOSED** — same reason; covered by `Schedule11WriteIT.unresolvableBiogeo_returns400()`, likewise ungated in CI (AR17) |
| `revisionCount` omitted on PUT → 400 | `revisionCountRequiredErrorMsg`; `OnUpdate` group | `@NotNull(groups=OnUpdate.class)` | — | not-applicable (UI) | `handleSaveEdit` refuses to fire when `editRevision` is null (index.tsx:665), so the UI cannot produce this request |
| Column sorting (3-state, per column) | **no legacy slice** — new-app behaviour | `handleSort` / `COLUMNS` (index.tsx:524, 258) | — | **covered (unit)** | **GAP-5 CLOSED** — 7 Vitest tests in `Schedule11.test.tsx`'s `describe('Schedule 11 column sorting (legacy p:column sortBy parity)')`, incl. the every-column-except-Comments/Actions parity check. These **do** gate (CI runs `npm run test:cov`). E2E would be slower and add nothing: sorting persists nothing and fires no request. SPEC-2 (no slice) is CLOSED — sorting is unit-covered, so no requirement is owed |
| Role-dependent behaviour / a role-driven 403 | `Schedule11Api` javadoc; codebase-map role model | `SchedulePermissions.ROLE_ACTIONS` grants ADMIN and SUBMITTER the SAME two actions; every `Schedule11Controller` endpoint guards on `VIEW_SCHEDULE`/`EDIT_SCHEDULE` only | — | **deferred** | **GAP-6** — no admin-only branch and no role-driven 403 exists on this UC, so there is nothing to assert today (NOT merely something we cannot reach). `deferred` rather than `not-applicable` because there is a named trigger: the day the two `ROLE_ACTIONS` sets diverge this becomes an owed test |
| Legacy role `ILCR_LICENSEE` | every S01–S20 Background line; legacy `Constant.java:580` | renamed to the ratified `ILCR_ADMIN` + `ILCR_SUBMITTER` model (PRD DL-23) | all scenarios (run as `ILCR_SUBMITTER`) | verified — not a defect | **VER-5** (was DIV-5, reclassified 2026-08-10 to match Schedule 1): the rename is ratified, so there is nothing to adjudicate. Distinct from GAP-6, which is about there being no role-dependent *behaviour* to test |
| WCAG 2.1 AA — editable page + inline editor | NFR1; issue #170 AC | `pages/common/axe.ts` (wcag2a+2aa+21a+21aa) | `accessibility.feature` `@p1` | covered | Zero violations. Sweeps the Add panel (7 controls incl. Dropdown + ComboBox), sortable headers, row actions, Totals row, and the open inline editor's hidden-label inputs |
| WCAG 2.1 AA — read-only page | NFR1 | as above | `accessibility.feature` `@p2` | covered | Zero violations. A structurally different tree (no Add panel, no Actions column) |
| WCAG 2.1 AA — guard state | NFR1 | as above | `accessibility.feature` `@p2` | covered | Zero violations. The PageState notification that replaces the whole body |
| WCAG 2.1 AA — Check Status result | NFR1; story AC7 names this state | as above | `check-status.feature` `@S04 @p1` | covered | Zero violations. A freshly-rendered notification set no other sweep sees |
| WCAG 2.1 AA — validation-error state | NFR1; story AC7; `deferred-work.md` | Carbon `TextInput` `invalid`/`invalidText` wiring (app-wide, not sch11 code) | `accessibility.feature` `@discovered-bug @p1` | **bug** | **RED — genuine critical violation** `aria-valid-attr-value` on `#add-location`: validation errors are never announced to assistive technology. Pre-existing and app-wide (Schedules 1/2/3/4/8/11). defects.md BUG-1 |

**Status values:** `covered` · `covered (+ gap)` · `not-applicable` (legacy-only or unreachable by design) · `deferred` (see Coverage gaps) · `blocked` (env/auth can't reach the state) · `divergence` / `bug` (a genuinely-failing `@discovered-divergence` / `@discovered-bug` test — see this UC's `defects.md`).

## Symmetry checks performed

The guide's "one arm of a mirror matrix covered but not the other" smell test, applied deliberately:

- **SUC-003 conditional** — covered present (S04) *and* absent (S05, S06). ✔
- **SUC-004 unconditional** — covered on the pass branch *and* both fail branches. ✔
- **Check Status missing cost** — Actual (S05) *and* Planned (S06). ✔
- **NAR range** — above max *and* below min. ✔
- **Cost range** — Actual-above *and* Planned-below (field × direction). ✔
- **Non-Draft read-only** — Submitted ("S") *and* Verified ("V"). ✔
- **Delete** — confirmed (S07) *and* cancelled (S08). ✔
- **Add validity** — accepted (S01) *and* each rejection (S14–S19), plus the recovery arm. ✔
- **Enhanced** — Yes (S01) *and* No (S02 second row, S10). ✔
- **Costs present vs absent** — both entered (S01) *and* both omitted, proving null-renders-blank (S09). ✔
- **Track independence** — the silviculture-Draft-editable side (S10) *and* the silviculture-non-Draft-read-only side (S20). ✔
