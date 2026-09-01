# Coverage — UC-SCH1-001 Report Average Cost of Logging (Schedule 1)

> New to these files? See [`coverage-guide.md`](../../../coverage-guide.md) at the e2e root for the column + status-flag legend.

Sources reconciled: `UC-SCH1-001-S01..S28.feature` (28 slices; `../../../../tests/UC-SCH1-001/gherkin/`).
S25/S26 were derived 2026-08-07 (the per-row inline edit, a spec gap this suite found); **S27/S28 arrived
upstream 2026-08-27 with ilcr-bmad PR #92** — the two Check-Status-on-unsaved-edits arms, **covered the same
day** by two deliberate `@discovered-divergence` reds tracking
[#359](https://github.com/bcgov/nr-ilcr/issues/359) (defects.md **DIV-6**, a pointer; the analysis for that
app-wide divergence lives once, in `sch3/defects.md` DIV-6). Note the catalogue also carries a
COARSER business-value numbering (`S1`/`S2`/`S3`) in the high-level UC; those are not these
+ `UC-SCH1-001-slices.md` (control/message matrix) + `UC-SCH1-001-technical.md` (message/error catalog),
against the app's real write path (`schedule1/api/Schedule1Api.java` PUT/GET/DELETE + `dto/Schedule1Request.java`
validators + `components/schedule1/index.tsx`).

Test data (real, 2026-07-29; Check Status anchors 2026-07-30; crown / clear-amounts / inline-edit anchors
2026-08-07): pinned in `fixtures/sch1/schedule1-test-data.ts` (finding queries in comments).

Scope: S01 happy path (`happy-path.feature`); **S02 crown pre-fill (`crown-prefill.feature`)**; S03–S07
entry validation (`validation.feature`); Check Status S14–S18 (`check-status.feature`); context-guard
render states S19–S22 (`render-states.feature`); delete S13 (`delete.feature`); Other Costs maintain
S09–S12 (`other-costs.feature`); **Other Costs inline edit (`other-costs-inline-edit.feature`, slices
S25/S26)**; **clearing a saved amount (`clear-amounts.feature`)**; persistence + retry S23–S24
(`persistence.feature`); and WCAG 2.1 AA accessibility on the Schedule 1 page + Other Costs sub-page
(`accessibility.feature`, AC4/NFR1 — zero violations); and **S08 the save-first gate on Other Costs**
(`save-first-gate.feature`).

**Disposition: all 28 slices `covered`.** The last two to land were **S27/S28**, the
Check-Status-on-unsaved-edits arms, covered 2026-08-27 by two deliberate reds against
[#359](https://github.com/bcgov/nr-ilcr/issues/359).
**S08 was the last of the original 24 to land:** `deferred` until 2026-08-07, then `not-applicable (E2E)` as
unreachable dead code, and **covered from 2026-08-27** once defect #296 gave the branch a real trigger. See
defects.md GAP-3 (closed).

**2026-08-07 re-review.** The app moved underneath this matrix and it was re-reconciled end to end
against the current write path, the slice matrix, and the message catalog. What changed:
- Backend commit `0b58057` "restore legacy parity for derived costs" made **four volume-only fields
  user-editable again** (143, 144, 139, 140) and added them to the PUT contract. They had **no coverage
  at all** — the write path had grown and the matrix had not followed. S01 now writes and reads all four
  back; `@S05`/`@S06` now cover every editable 7-digit and 8-digit volume, matching the legacy inventory
  in `UC-SCH1-001-slices.md` (11 seven-digit, 3 eight-digit). DIV-2 was retired as obsolete.
- **S02 became automatable** once Schedule 3 shipped with crown data (28 of 30 Schedule-1/Schedule-3
  pairs carry an item-119 volume), closing a `deferred` row whose stated reason was no longer true.
- Reconciling the `.feature` set against the slice matrix surfaced a **spec gap** (per-row inline
  edit, documented in the sidecars but never projected into a `.feature`). Now covered here AND closed at
  the source: derived upstream as **UC-SCH1-001-S25** (valid edit) and **S26** (the rejects), split to
  match the catalog's own S09-Alternative / S10-S11-Exception shape after review.
- Two new bugs found: **BUG-2** (five volume fields cannot be cleared — a genuine
  `@discovered-bug` RED) and **BUG-3** (a latent NPE 500, deliberately not automated).

**2026-08-11 — BUG-2 and BUG-3 fixed; the suite is fully green.** Backend commit `3ee9ff2` fixed both
(issues [#260](https://github.com/bcgov/nr-ilcr/issues/260) / [#261](https://github.com/bcgov/nr-ilcr/issues/261)),
verified against the running app at the API, the DB column, and through the browser. Two consequences for
this matrix:
- The `@discovered-bug` RED **flipped GREEN and the tag was removed** — the scenario is now an ordinary
  regression guard, renamed to state the correct behaviour rather than the defect.
- **BUG-3 stopped being unautomatable.** Its state needed direct DB manipulation only *because* BUG-2
  blocked the clear; with BUG-2 fixed, an ordinary user action reaches it, so its row moves from
  `not-applicable (E2E)` to `covered` — guarded by a reopen at the end of the clear-amounts scenario.

> ### Suite state — the ONE place this is recorded
> **30 scenarios / 42 tests after Scenario-Outline expansion: 39 green + 3 deliberate
> `@discovered-divergence` REDs** — S12 / DIV-3 (row delete confirm, #362) and **S27/S28 / DIV-6** (Check
> Status on unsaved edits, #359). Measured from the generated specs and a full run on **2026-08-27**.
>
> Counts used to be restated three times in this section, from three different runs and on two different
> denominators (57 was this UC alone; 163 and 164 included the 126-check preflight, which has itself grown).
> A reader could not tell which was current. Re-measure with
> `npx playwright test --list --project=chromium` and edit **this block only**. No whole-suite total is
> written down anywhere by design — the e2e [`README.md`](../../../README.md) gives the command.
>
> One scenario in `sec` also carries `@UC-SCH1-001` (`context-drives-schedule.feature:27`, the closed-mill
> consequence = S20), so a tag-based grep reports 40 for sch1's folder and 41 across the tag. Both are
> right; the 40 above is this folder.

A clean run needs `npm run test:gate`, which excludes every `@discovered-*` red.

**What moved the numbers, in order (history — the state above is current):**
- **2026-08-11** — BUG-2/BUG-3 fixed, the last red retired, suite fully green.
- **2026-08-26 — S12 became a tracked red again, and not because of a new defect but a corrected test.**
  The per-row delete confirmation legacy required is missing (defects.md **DIV-3**, ticket
  [#362](https://github.com/bcgov/nr-ilcr/issues/362), one ticket with Schedule 3's DIV-5 since both sit on
  the shared `useEditableCostRows`). S12 had been *re-grounded* onto the app's no-confirm behaviour on
  2026-08-07 and passed for three weeks; the repo owner ruled that the wrong call — re-grounding onto a
  suspected defect makes the suite ratify it — so the scenario now asserts the legacy guarantee and fails
  until the prompt is restored.
- **2026-08-26 (later the same day) — defect #296 merged, and S13 needed re-grounding.** The fix removes
  the 404 for an unsaved or just-deleted Schedule 1 and stops rendering it read-only, so `delete.feature`'s
  post-delete assertions were stale and briefly failed on the merge commit. S13 now asserts a blank
  EDITABLE form with Delete withdrawn — which is what legacy did (delete, re-read, stay on the page;
  editability gated on the track status, Delete on summary existence) — and "no longer exists" means
  UNSAVED rather than 404. See defects.md, *Verified — not a defect*.
- **2026-08-27 — S08 gained its first test ever** (`save-first-gate.feature`), +1 scenario. The same #296
  fix that broke S13's proxy turned S08's dead `!data` branch into a live saved-ness gate, so the legacy
  save-first message became reachable and testable. That closed defects.md **GAP-3**, which had recorded
  it as permanently unreachable dead code.

Note on snapshot/restore cleanup: four scenarios now leave changes the app's own blank-fields PUT cannot
undo, so each snapshots its dedicated target to the `E2E_BAK_SCH1_*` tables and re-inserts the rows
verbatim on teardown (`scripts/sch1_db_restore.py`, thin-mode oracledb — this host has no sqlplus), via
the shared `schedule1DeleteRestore` registry:
- **S13 delete** — removes the summary + every detail row; a save can re-create a summary since
  defect #296, but never the seeded rows verbatim (PKs, audit columns), so the restore stays.
- **S24 retry-save** — the successful retry persists.
- **S02 crown pre-fill** — its precondition nulls volumes and drops item-19 rows at the DB.
- **clear-amounts** — snapshot/restore is retained for its two targets. Since the BUG-2 fix a blanking
  PUT *would* now clear the five volume-only fields, so the verbatim re-insert is no longer strictly
  required here — but it is exact and cheap, and it restores the schedule's original values rather than
  merely blanking them, which the other snapshot users need anyway. Left as-is deliberately.

Every one of these is a SINGLE-OWNER key: their non-flaky proof is **`--repeat-each=N --workers=1`**, NOT
`--repeat-each` in parallel, because concurrent copies of one destructive scenario self-collide on the one
snapshot row (observed 2026-08-07 when two clear-amounts scenarios briefly shared a key — value bleed
plus "no backup … snapshot was never taken" restore failures). A real run only ever has one of each.

> ⚠️ **The parallel-repeat failure mode CORRUPTS the pinned baseline — it does not just go red.**
> Learned the hard way 2026-08-11: `--repeat-each=5` on `@clear-amounts` without `--workers=1` failed 6
> of 16 (optimistic-lock 409s, so no success message), and worse, it silently drifted the 25052/2015
> anchor. `snapshot` overwrites any existing backup for that schedule, so a later repeat snapshotted a
> state an earlier repeat had **already cleared** — and the final restore then wrote those NULLs back as
> if they were the baseline. The four volume-only fields (139/140/143/144) were left NULL where the seed
> had 936564; repaired by hand from the pre-run read, and the same run serialized then passed 16/16.
> If you must stress these scenarios, pass `--workers=1`, and check the anchor afterwards.
>
> Note this hazard got *sharper* with the BUG-2 fix, not milder: a blanking PUT can now clear those four
> fields, so a mis-restore actually persists. Before the fix the same mistake was partly masked.

| Source item (slice) | Source citation | App enforcement point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| S01 Enter and save line items (happy path) | S01.feature; slices.md | `Schedule1Api.saveSchedule1` (PUT); `Schedule1.handleSave` | `happy-path.feature` `@S01 @p0` | covered | Now writes + reads back the FULL request incl. the four restored volume-only fields (143/144/139/140). Role re-grounded — see defects.md |
| S02 Crown Timber volume pre-filled on first entry | S02.feature (Alt); BR-03 | `Schedule1Service.getSchedule1` prefill (`allVolumesEmpty` + `sch3.crownTimberVolume`) → 13 pre-filled volumes + WRN-001 `crownVolumeSetForSchedule1` | `crown-prefill.feature` `@S02 @WRN-001 @p1` (target 22051/2017) | covered | Was `deferred` — closed 2026-08-07. Precondition built by snapshot → `first-entry` (null volumes + drop item-19 rows) → exact restore. Asserts served-not-stored via a DB volume count |
| Writable volume-only fields 143/144/139/140 (restored parity) | slices.md control matrix (11×7-digit, 3×8-digit); commit `0b58057` | `Schedule1Request.forestMgmtAdminVolume` / `subtotalCompanyLoggingVolume` / `silviculture.lessAdminVolume` / `totalVolume`; `numberCell(…, writable)` | `happy-path.feature` `@S01`; `validation.feature` `@S05`/`@S06` | covered | Added 2026-08-07 — these became editable and had zero coverage |
| Clearing a saved amount (blank → persisted empty) | Derived from the S01 write path (no legacy slice) | `Schedule1Service.writeWritableDetails` / `writeSilviculture` null handling | `clear-amounts.feature` `@p1` (line-item arm) + `@p1` (all five volume-only fields) | covered | Was `covered (+ bug)` with an intentional `@discovered-bug` RED for defects.md BUG-2. **Fixed 2026-08-11** (commit `3ee9ff2`, issue #260): the five scalars are written unconditionally, so a null clears. Both arms GREEN; the ex-RED is now the regression guard |
| S03 Cost amount out of range rejected | S03.feature (Exc); FLD-001 | `validation.ts` `COST` ±99,999,999 → inline `invalidText`; `handleSave` gate (Save blocked, no PUT) | `validation.feature` `@S03 @FLD-001 @p1` | covered | Entry-mechanism divergence — see defects.md |
| S04 Non-numeric cost rejected | S04.feature (Exc); FLD-004 | `validation.ts` `costInvalid` (`Number(raw)` NaN) → inline error; `handleSave` gate | `validation.feature` `@S04 @FLD-004 @p1` | covered | Entry-mechanism divergence — see defects.md |
| S05 Volume out of 7-digit range rejected | S05.feature (Exc); FLD-002 | `validation.ts` `VOLUME_7_DIGIT` ±9,999,999 (vol-12..18, sil 1/2/139/140) → inline error; `handleSave` gate | `validation.feature` `@S05 @FLD-002 @p1` (Outline ×4: line item, silv 1, silv 139, silv 140) | covered | Widened 2026-08-07 to the restored 139/140 volumes. Entry-mechanism divergence — see defects.md |
| S06 Volume out of 8-digit range rejected | S06.feature (Exc); FLD-003 | `validation.ts` `VOLUME_8_DIGIT` ±99,999,999 (`otherCostsVolume`, `vol-143`, `vol-144`) → inline error; `handleSave` gate | `validation.feature` `@S06 @FLD-003 @p1` (Outline ×3 — all three editable 8-digit volumes) | covered | Widened 2026-08-07: legacy's three editable 8-digit fields are editable again (DIV-2 retired), so all three are now exercised rather than one |
| S07 Non-numeric volume rejected | S07.feature (Exc); FLD-005 | `validation.ts` `volumeInvalid` (`Number(raw)` NaN) → inline error; `handleSave` gate | `validation.feature` `@S07 @FLD-005 @p1` | covered | Entry-mechanism divergence — see defects.md |
| S08 Open Other Costs before first save | S08.feature (Exc) | `Schedule1.handleOtherCosts` gated on `!data \|\| !isScheduleSaved(data)` (`index.tsx:288`) → "Save required" Modal (`:767`) carrying legacy `schedule1.xhtml:497` verbatim | `save-first-gate.feature` `@S08 @p1` (anchor 16050/2016, never saved) | covered | **Was `not-applicable (E2E; unreachable by construction)` until 2026-08-27.** The branch really was dead — gated on `!data`, below a `return null` on `!data` — until defect #296 made an unsaved schedule render a form and Rylan re-gated it on saved-ness, citing this slice (`index.tsx:280-287`). Read-only scenario: the click is refused, so nothing is written. Closed defects.md GAP-3 |
| S09 Add line item on Other Costs sub-page | S09.feature (Alt) | `Schedule1OtherCostsApi.saveOtherCosts` (whole-set PUT, `intent=save`) → SUC-002; row added + count updates | `other-costs.feature` `@S09 @p1` (add target 25050/2017) | covered | Add now persists the whole set (2026-08 EditableSubPage rewrite); original delivery-DB insert 500 — defects.md BUG-1 (historical) |
| S10 Other cost line without description | S10.feature (Exc) | `validateOtherCost` `descriptionRequired` → inline error; Add blocked (no mutating PUT) | `other-costs.feature` `@S10 @p1` | covered | Re-grounded FLD-006 message to the new bundle |
| S11 Other cost line invalid cost | S11.feature (Exc) | `validateOtherCost` cost range / non-numeric → inline error; Add blocked (no mutating PUT) | `other-costs.feature` `@S11 @p1` (Outline: out-of-range; non-numeric) | covered | — |
| S12 Remove an additional line item | S12.feature (Alt) | `Schedule1OtherCostsApi.saveOtherCosts` (whole-set PUT, `intent=delete`) → SUC-002; icon-only "Remove" deletes immediately (no confirm modal) + persists the set | `other-costs.feature` `@S12 @p1 @discovered-divergence` (remove target 9050/2017) | divergence | **RED on purpose since 2026-08-26** — defects.md **DIV-3**, ticket [#362](https://github.com/bcgov/nr-ilcr/issues/362). Precondition row added via the API; the scenario now asserts the legacy `confirmDeleteMsg` prompt and that the row survives until it is answered. It had been re-grounded onto the app's no-confirm behaviour and passing since 2026-08-07 — the wrong call (a suite must not ratify a divergence), corrected once the legacy source confirmed the prompt (`schedule1OtherCosts.xhtml:94-96`). Same shared hook as Schedule 3's DIV-5; one fix turns both green |
| S25 Per-row inline edit of an existing Other Cost — valid edit + BR-06 shared volume | **UC-SCH1-001-S25.feature** (derived 2026-08-07; split out of S09) | `useEditableCostRows.setRowDescription`/`setRowValue` + `handleSave` → whole-set `PUT …?intent=save` | `other-costs-inline-edit.feature` `@S25 @p1` (target 12050/2017) | covered | Covers both halves of the edit (cost and description) plus BR-06: the row exposes only description + cost as editable, and the volume cell shows the shared value |
| S26 Per-row inline edit rejected — blank description / invalid cost | **UC-SCH1-001-S26.feature** (derived 2026-08-07; split out of S10/S11) | `useEditableCostRows.persist` validates every row and returns before sending; `OtherCostSaveRequest` re-validates server-side | `other-costs-inline-edit.feature` `@S26 @FLD-006 @p1` + Outline `@S26 @FLD-001`/`@FLD-004` (validate anchor 17052/2016) | covered | Mirrors S10/S11's shape (description = scenario, cost = Outline). Validate-only: each proves a zero-write with `otherCostsSpy`, so the shared anchor is never modified |
| Shared Other-Costs row with a null volume (GET robustness) | Found while building the S02 precondition; legacy rendered this state fine and flagged it at Check Status (FLD-010) | `Schedule1Service.toOtherCosts` — row selected before mapping to its nullable volume | `clear-amounts.feature` `@p1` (the closing reopen of the five-field scenario) | covered | Was `not-applicable (E2E)` while BUG-2 made the state unreachable. **Fixed 2026-08-11** in the same commit as BUG-2, as this entry required (issue #261). Fixing BUG-2 made the state reachable by ordinary user action, so it is now guarded E2E: clear the shared volume, reopen, and the page must render instead of 500 |
| S13 Delete the whole Schedule 1 | S13.feature (Alt) | `Schedule1Service.deleteSchedule1` (DELETE) → SUC-002; confirm Modal → in-place reset to the blank EDITABLE form (defect #296 — the same state S21 opens on), Delete gate closed; summary+details removed | `delete.feature` `@S13 @p1` (dedicated target 25052/2016) | covered | Destructive — snapshot/restore via `scripts/sch1_db_restore.py`; non-flaky proven SERIAL (single-owner). Deletion proven at the API as the 200 empty document (null revisionCount, no rows) — the 404 proof died with #296 |
| S14 Check Status — requirements met | S14.feature (Alt); slices.md | `Schedule1Service.checkSchedule1Status` (POST /check-status) → SUC-003; `Schedule1.handleCheckStatus` success NotificationColumn | `check-status.feature` `@S14 @p0` | covered | Anchor 24050/2017 (met); asserts read-only (revisionCount stable) |
| S15 Check Status — missing required field(s) | S15.feature (Exc) | `collectRequiredFieldErrors` → `missingRequiredFieldMsg` ("Value Required") error columns | `check-status.feature` `@S15 @p1` (Outline: missing line-item volume 24051/2016; missing Other-Costs volume 13050/2016) | covered | Messages re-grounded to the new bundle (hyphen, not em-dash) — verbatim |
| S16 Check Status — other-costs volume w/o cost | S16.feature (Exc); FLD | `otherCostsError` → `sch1.subtotal.other.costs.costs.grearter.than.zero` | `check-status.feature` `@S16 @p1` (anchor 22050/2016: oc vol 10, no rows) | covered | — |
| S17 Check Status — other-costs cost w/o volume | S17.feature (Exc) | `otherCostsError` → `sch1.subtotal.other.costs.volume.grearter.than.zero` | `check-status.feature` `@S17 @p1` (17052/2017: shared vol 0 + API-added costed row) | covered | Precondition row added via the API; cleaned via DELETE |
| S18 Check Status — empty cost row (warning only) | S18.feature (Alt) | `anyOtherCostEmpty` → `warning.schedule1.checkstatus.subtotalother.costEmpty` warning column | `check-status.feature` `@S18 @p2` (25054/2016: API-added null-cost row) | covered | Precondition row added via the API (null cost); cleaned via DELETE |
| S19 Mill and reporting year not selected | S19.feature (Exc) | `Schedule1.contextMissing` → ERR-001 banner "Please Select Mill and Reporting Year in the Home Page."; no form | `render-states.feature` `@S19 @p1` | covered | Empty context driven via localStorage (the app's own persisted null context) |
| S20 Mill not active for reporting year | S20.feature (Exc) | schedule1 GET 409 (mill CLS) → error banner; no form | `render-states.feature` `@S20 @p1` (anchor 13/2017, CLS) | covered | Also covered from the Home side in `../../sec/uc-sec-001-working-context/context-drives-schedule.feature` `@S20` (UC-SEC-001) |
| S21 Schedule not started yet | S21.feature (Exc) | schedule1 GET 200 with an EMPTY editable document; blank form, Delete not offered, summary created by the first save | `render-states.feature` `@S21 @p1` (anchor 16050/2016, active, no summary) | covered | **Inverted by defect #296.** Was: GET 404 → a client-composed "No Schedule 1 exists for Mill 16050…" banner and no form, which left no route by which a Schedule 1 could ever be created. Legacy rendered the blank form; Schedule 2 always did |
| S22 Schedule not editable outside Draft | S22.feature (Exc) | GET 200 editable:false (track ≠ D) → read-only render; Save/Check/Delete disabled, no inputs | `render-states.feature` `@S22 @p1` (anchor 12050/2016, Submitted) | covered | Read-only proven by absent inputs + disabled actions |
| S23 Save fails due to persistence error | S23.feature (Exc) | `handleSave` catch → error banner; entries retained; no PUT reaches the server | `persistence.feature` `@S23 @p1` (fault-injected 500) | covered | Fault via page.route; read-only anchor, no write lands |
| S24 Retry save succeeds after transient failure | S24.feature (Alt) | retry after failure → success + persisted; persistent failure keeps failing | `persistence.feature` `@S24 @p1` (×2: recover; not-transient) | covered | Retry-succeeds writes to a dedicated target; snapshot/restore exact |
| AC4/NFR1 accessibility — Schedule 1 page | issue #74 AC4; NFR1 | axe-core wcag2a/2aa/21a/21aa on the rendered page | `accessibility.feature` `@a11y @p1` (24050/2017) | covered | Zero WCAG 2.1 AA violations |
| AC4/NFR1 accessibility — Other Costs sub-page | issue #74 AC4; NFR1 | axe-core wcag2a/2aa/21a/21aa on the rendered page | `accessibility.feature` `@a11y @p1` (17052/2016) | covered | Zero WCAG 2.1 AA violations |

**Symmetry check (re-run 2026-08-07).** S01 covers the entered-fields → save → read-back (write-succeeds)
arm; S03–S07 cover the mirror validation-reject (write-blocked) arm. The reject cluster is itself
symmetric: cost (S03/S04) and volume (S05/S06/S07), each with an out-of-range and a non-numeric case, and
**both volume ranges are now hit across every editable field**, not just one exemplar each — the earlier
one-field-per-range shape was the asymmetry that hid the restored 143/144/139/140 fields. Check Status
covers both arms of its own mirror — success (S14 met) and failure (S15 missing-field, S16 volume-without-
cost, S17 cost-without-volume, S18 empty-cost-row warning). The four context-guard render states (S19
no-context, S20 closed-mill, S21 not-started-yet, S22 non-Draft read-only) mirror the S01 editable-write arm.
Delete (S13) covers the destroy arm; persistence (S23 fail / S24 retry-succeeds + retry-fails) covers both
arms of the save-failure mirror.

Three mirrors were **completed** by this re-review, each of which had one arm silently missing:
- **Enter ↔ clear.** S01 proved a value can be written; nothing proved it can be un-written.
  `clear-amounts.feature` adds that arm — and its broken half was BUG-2, found precisely because the
  mirror was completed. Both halves are GREEN since the 2026-08-11 fix.
- **Add ↔ edit.** Other Costs covered add (S09), reject-on-add (S10/S11) and remove (S12), but not
  editing a row already in the list, although the sidecars describe it. `other-costs-inline-edit.feature`
  adds it (now slices S25/S26) with both its own arms: a valid edit persists, an invalid one is refused and
  changes nothing.
- **Pre-fill served ↔ stored.** S02 asserts the pre-filled values render *and* that the database is
  untouched — the second arm is what makes WRN-001's "please check and save" meaningful.

One row is `not-applicable (E2E)` with its reason recorded rather than left open: S08 (unreachable by
construction — defects.md GAP-3). The `toOtherCosts` NPE row was the second such row until 2026-08-11 —
it is now `covered`, because fixing BUG-2 made that state reachable by ordinary user action (BUG-3).
Nothing is `deferred` any more. No asymmetric silent omission.

**Role / permission coverage:** complete for what exists. `SchedulePermissions` grants `ILCR_ADMIN` and
`ILCR_SUBMITTER` the identical action set (`VIEW_SCHEDULE` + `EDIT_SCHEDULE`), so Schedule 1 has **no
role-dependent behaviour and no role-driven 403** to assert. Scenarios run as `ILCR_SUBMITTER` (the
backend's fixed mock authority); the header's mock-user selector is frontend-display only and does not
change it. Revisit when FAM lands and the two action sets diverge — defects.md GAP-1.

**Status values:** `covered` · `covered (+ spec-gap)` · `not-applicable` (legacy-only, by design) · `deferred` (see Coverage gaps) · `blocked` (env/auth can't reach the state) · `divergence` / `bug` (a genuinely-failing `@discovered-divergence` / `@discovered-bug` test — see this UC's `defects.md`).
