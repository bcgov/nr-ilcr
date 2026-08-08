# Coverage — UC-SCH1-001 Report Average Cost of Logging (Schedule 1)

> New to these files? See [`coverage-guide.md`](../../../coverage-guide.md) at the e2e root for the column + status-flag legend.

Sources reconciled: `UC-SCH1-001-S01..S24.feature` (24 slices; `../../../../tests/UC-SCH1-001/gherkin/`)
+ `UC-SCH1-001-slices.md` (control/message matrix) + `UC-SCH1-001-technical.md` (message/error catalog),
against the app's real write path (`schedule1/api/Schedule1Api.java` PUT/GET/DELETE + `dto/Schedule1Request.java`
validators + `components/schedule1/index.tsx`).

Test data (real, 2026-07-29; Check Status anchors 2026-07-30): pinned in
`fixtures/sch1/schedule1-test-data.ts` (finding queries in comments).
Scope (Story 2.8 / issue #74 — post-implementation delivery-DB verification, essentially complete):
S01 happy path (`happy-path.feature`); S03–S07 entry validation (`validation.feature`); Check Status
S14–S18 (`check-status.feature`); context-guard render states S19–S22 (`render-states.feature`); delete
S13 (`delete.feature`); Other Costs maintain S09–S12 (`other-costs.feature` — S09 add surfaced a
delivery-DB insert 500 that dev fixed during this story, now green; defects.md Bug/Regression #1);
persistence + retry S23–S24 (`persistence.feature`); and WCAG 2.1 AA accessibility on the Schedule 1 page
+ Other Costs sub-page (`accessibility.feature`, AC4/NFR1 — zero violations). Only **S02** (crown pre-fill,
needs a Schedule 3 crown volume) and **S08** (open-Other-Costs-before-save, guard unreachable in the
current backend model) remain `deferred` — see defects.md Coverage gaps.

The whole suite runs green (`npm test`); no `@discovered-*` reds remain.

Note on S13 cleanup: delete is destructive (removes the summary + all detail rows; no create-on-open),
so `delete.feature` snapshots its dedicated target to `E2E_BAK_SCH1_*` tables before the delete and
re-inserts the rows verbatim on teardown (`scripts/sch1_db_restore.py`, thin-mode oracledb — this host
has no sqlplus). Its non-flaky proof is a SERIAL repeat run, not `--repeat-each` parallel: as the sole
owner of a destructive key it cannot be run as concurrent copies of itself (that self-collides), and a
real run only ever has one S13.

| Source item (slice) | Source citation | App enforcement point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| S01 Enter and save line items (happy path) | S01.feature; slices.md | `Schedule1Api.saveSchedule1` (PUT); `Schedule1.handleSave` | `happy-path.feature` `@S01 @p0` | covered | Role re-grounded — see defects.md Divergences |
| S02 Crown Timber volume pre-filled on first entry | S02.feature (Alt) | `Schedule1Response.schedule3CrownVolume`/`warnings` (WRN-001) | — | deferred | — |
| S03 Cost amount out of range rejected | S03.feature (Exc); FLD-001 | `validation.ts` `COST` ±99,999,999 → inline `invalidText`; `handleSave` gate (Save blocked, no PUT) | `validation.feature` `@S03 @FLD-001 @p1` | covered | Entry-mechanism divergence — see defects.md |
| S04 Non-numeric cost rejected | S04.feature (Exc); FLD-004 | `validation.ts` `costInvalid` (`Number(raw)` NaN) → inline error; `handleSave` gate | `validation.feature` `@S04 @FLD-004 @p1` | covered | Entry-mechanism divergence — see defects.md |
| S05 Volume out of 7-digit range rejected | S05.feature (Exc); FLD-002 | `validation.ts` `VOLUME_7_DIGIT` ±9,999,999 (vol-12..18, sil 1/2) → inline error; `handleSave` gate | `validation.feature` `@S05 @FLD-002 @p1` | covered | Entry-mechanism divergence — see defects.md |
| S06 Volume out of 8-digit range rejected | S06.feature (Exc); FLD-003 | `validation.ts` `VOLUME_8_DIGIT` ±99,999,999 (`otherCostsVolume` only) → inline error; `handleSave` gate | `validation.feature` `@S06 @FLD-003 @p1` | covered | Re-grounded field (Forest Mgmt Admin read-only) + entry-mechanism divergence — see defects.md |
| S07 Non-numeric volume rejected | S07.feature (Exc); FLD-005 | `validation.ts` `volumeInvalid` (`Number(raw)` NaN) → inline error; `handleSave` gate | `validation.feature` `@S07 @FLD-005 @p1` | covered | Entry-mechanism divergence — see defects.md |
| S08 Open Other Costs before first save | S08.feature (Exc) | Other Costs sub-page gate (Story 2.5) | — | deferred | — |
| S09 Add line item on Other Costs sub-page | S09.feature (Alt) | `Schedule1OtherCostsApi.addOtherCost` (POST) → SUC-002; row added + count updates | `other-costs.feature` `@S09 @p1` (add target 25050/2017) | covered | Delivery-DB insert 500 found here + fixed 2026-07-30 — defects.md Bug/Regression #1 |
| S10 Other cost line without description | S10.feature (Exc) | `validateOtherCost` `descriptionRequired` → inline error; Add blocked (zero POST) | `other-costs.feature` `@S10 @p1` | covered | Re-grounded FLD-006 message to the new bundle |
| S11 Other cost line invalid cost | S11.feature (Exc) | `validateOtherCost` cost range / non-numeric → inline error; Add blocked (zero POST) | `other-costs.feature` `@S11 @p1` (Outline: out-of-range; non-numeric) | covered | — |
| S12 Remove an additional line item | S12.feature (Alt) | `Schedule1OtherCostsApi.deleteOtherCost` (DELETE) → SUC-002; row removed + totals recomputed | `other-costs.feature` `@S12 @p1` (remove target 9050/2017) | covered | Precondition row added via the API; removed through the UI |
| S13 Delete the whole Schedule 1 | S13.feature (Alt) | `Schedule1Service.deleteSchedule1` (DELETE) → SUC-002; confirm Modal → empty read-only redisplay; summary+details removed | `delete.feature` `@S13 @p1` (dedicated target 25052/2016) | covered | Destructive — snapshot/restore via `scripts/sch1_db_restore.py`; non-flaky proven SERIAL (single-owner) |
| S14 Check Status — requirements met | S14.feature (Alt); slices.md | `Schedule1Service.checkSchedule1Status` (POST /check-status) → SUC-003; `Schedule1.handleCheckStatus` success NotificationColumn | `check-status.feature` `@S14 @p0` | covered | Anchor 24050/2017 (met); asserts read-only (revisionCount stable) |
| S15 Check Status — missing required field(s) | S15.feature (Exc) | `collectRequiredFieldErrors` → `missingRequiredFieldMsg` ("Value Required") error columns | `check-status.feature` `@S15 @p1` (Outline: missing line-item volume 24051/2016; missing Other-Costs volume 13050/2016) | covered | Messages re-grounded to the new bundle (hyphen, not em-dash) — verbatim |
| S16 Check Status — other-costs volume w/o cost | S16.feature (Exc); FLD | `otherCostsError` → `sch1.subtotal.other.costs.costs.grearter.than.zero` | `check-status.feature` `@S16 @p1` (anchor 22050/2016: oc vol 10, no rows) | covered | — |
| S17 Check Status — other-costs cost w/o volume | S17.feature (Exc) | `otherCostsError` → `sch1.subtotal.other.costs.volume.grearter.than.zero` | `check-status.feature` `@S17 @p1` (17052/2017: shared vol 0 + API-added costed row) | covered | Precondition row added via the API; cleaned via DELETE |
| S18 Check Status — empty cost row (warning only) | S18.feature (Alt) | `anyOtherCostEmpty` → `warning.schedule1.checkstatus.subtotalother.costEmpty` warning column | `check-status.feature` `@S18 @p2` (25054/2016: API-added null-cost row) | covered | Precondition row added via the API (null cost); cleaned via DELETE |
| S19 Mill and reporting year not selected | S19.feature (Exc) | `Schedule1.contextMissing` → ERR-001 banner "Please Select Mill and Reporting Year in the Home Page."; no form | `render-states.feature` `@S19 @p1` | covered | Empty context driven via localStorage (the app's own persisted null context) |
| S20 Mill not active for reporting year | S20.feature (Exc) | schedule1 GET 409 (mill CLS) → error banner; no form | `render-states.feature` `@S20 @p1` (anchor 13/2017, CLS) | covered | Also covered from the Home side in `../../sec/uc-sec-001-working-context/context-drives-schedule.feature` `@S20` (UC-SEC-001) |
| S21 Schedule not found | S21.feature (Exc) | schedule1 GET 404 "Schedule not found." → mapped "No Schedule 1 exists for Mill…" banner; no form | `render-states.feature` `@S21 @p1` (anchor 16050/2016, active, no summary) | covered | Frontend re-grounds the 404 detail to a mill/year-specific message |
| S22 Schedule not editable outside Draft | S22.feature (Exc) | GET 200 editable:false (track ≠ D) → read-only render; Save/Check/Delete disabled, no inputs | `render-states.feature` `@S22 @p1` (anchor 12050/2016, Submitted) | covered | Read-only proven by absent inputs + disabled actions |
| S23 Save fails due to persistence error | S23.feature (Exc) | `handleSave` catch → error banner; entries retained; no PUT reaches the server | `persistence.feature` `@S23 @p1` (fault-injected 500) | covered | Fault via page.route; read-only anchor, no write lands |
| S24 Retry save succeeds after transient failure | S24.feature (Alt) | retry after failure → success + persisted; persistent failure keeps failing | `persistence.feature` `@S24 @p1` (×2: recover; not-transient) | covered | Retry-succeeds writes to a dedicated target; snapshot/restore exact |
| AC4/NFR1 accessibility — Schedule 1 page | issue #74 AC4; NFR1 | axe-core wcag2a/2aa/21a/21aa on the rendered page | `accessibility.feature` `@accessibility @p1` (24050/2017) | covered | Zero WCAG 2.1 AA violations |
| AC4/NFR1 accessibility — Other Costs sub-page | issue #74 AC4; NFR1 | axe-core wcag2a/2aa/21a/21aa on the rendered page | `accessibility.feature` `@accessibility @p1` (17052/2016) | covered | Zero WCAG 2.1 AA violations |

**Symmetry check:** S01 covers the entered-fields → save → read-back (write-succeeds) arm; S03–S07 now cover
the mirror validation-reject (write-blocked) arm — both a positive and a negative save path are exercised.
The reject cluster is itself symmetric: cost (S03/S04) and volume (S05/S06/S07), each with an out-of-range
and a non-numeric case; the two volume ranges (7-digit S05, 8-digit S06) are both hit. Check Status now
covers both arms of its own mirror — the success arm (S14 met) and the failure arm (S15 missing-field,
S16 other-costs volume-without-cost, S17 cost-without-volume, S18 empty-cost-row warning) — all covered.
The four context-guard render states (S19 no-context, S20 closed-mill, S21 not-found, S22 non-Draft
read-only) are all covered — the read-only/blocked arms that mirror the S01 editable-write arm. Delete
(S13) covers the destroy arm; persistence (S23 fail / S24 retry-succeeds + retry-fails) covers both arms
of the save-failure mirror; Other Costs covers add (S09), reject (S10/S11), and remove (S12). Only crown
pre-fill S02 and open-before-save S08 stay `deferred` — each a row above, authored when their
preconditions exist. No asymmetric silent omission.

**Role / permission coverage:** partial — automated as `ILCR_SUBMITTER` (the backend mock authority with
security off). Admin-only branches and the legacy `ILCR_LICENSEE` role are **blocked by mock auth** (single fixed
authority per run); the UI mock-user persona does not change the backend authority. See defects.md.

**Status values:** `covered` · `covered (+ spec-gap)` · `not-applicable` (legacy-only, by design) · `deferred` (see Coverage gaps) · `blocked` (env/auth can't reach the state) · `divergence` / `bug` (a genuinely-failing `@discovered-divergence` / `@discovered-bug` test — see this UC's `defects.md`).
