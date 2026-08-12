# Coverage — UC-SEC-001 Establish Working Context (Home)

> New to these files? See [`coverage-guide.md`](../../../coverage-guide.md) at the e2e root for the column + status-flag legend.

Sources reconciled: `UC-SEC-001-S01..S08.feature` (8 slices; `../../../../tests/UC-SEC-001/gherkin/`)
+ `UC-SEC-001-slices.md` + `UC-SEC-001-technical.md` (control/message/field catalog), against the app's
real read path (`components/home/index.tsx` + `Layout/ContextBanner.tsx` + `GET /api/v1/mill-context`).
Also mapped to **story HOME-1.5** (`1-5-...-delivery-database.md`) — see the AC-compliance table below.

Test data (real, 2026-07-30): pinned in `fixtures/sec/working-context-test-data.ts` (finding queries in
comments). Home "Save" is a read/resolve (`GET /v1/mill-context`) that writes nothing → no teardown;
every scenario is parallel-safe by construction.

| Source item (slice) | Source citation | App enforcement point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| S01 Establish working context (happy path) | S01.feature; SUC-001 | `Home.handleSave` → `GET /v1/mill-context` 200 `message.text`; `ContextBanner` renders | `working-context.feature` `@S01 @SUC-001` | covered | Role + role-notice re-grounded — see defects.md |
| S01 Landing: lists populate + pre-select + a11y | S01 land; `millYearDefaults.ts`; NFR1 | `Home` mount fetch/pre-select; `ContextBanner` mount fetch; axe | `working-context.feature` `@S01 @landing @a11y` | covered | Banner populated on landing — defects.md DIV-3 |
| S01 Saved context drives Schedule 1 (HOME-1.5 AC2) | epics-home-page Story-1.5 AC2 | client-side nav preserves `MillYearContext`; `GET /v1/schedule1?millId&year` carries the selection | `context-drives-schedule.feature` `@S01 @drives-schedule` | covered | — |
| S02 Single assigned mill pre-selected | S02.feature (Alt) | `Home` single-mill fallback (`mills.length === 1`) | — | not-applicable | 21-mill delivery data; unit-covered (Story 1.3 Vitest). See defects.md GAP-2 |
| S03 Change working context later | S03.feature (Alt) | `Home.setContext` on a second resolve; `ContextBanner` re-fetch | `working-context.feature` `@S03` | covered | — |
| S04 Missing mill selection | S04.feature (Exc); FLD-001 | Backend `mill-context` 400 `Mill: Value is required.` | — | not-applicable (UI) / covered-by-contract | Not UI-reproducible — defects.md DIV-2 + GAP-1 |
| S05 Missing reporting year selection | S05.feature (Exc); FLD-002 | Backend `mill-context` 400 `Reporting Year: Value is required.` | — | not-applicable (UI) / covered-by-contract | Same as S04 |
| S06 Selected mill is closed — Home saves + banners | S06.feature (Alt) | `mill-context` 200 for closed mill (`millViewable:false`) | `working-context.feature` `@S06` | covered | Home-screen half |
| S06 Selected mill is closed — schedule blocked (=SCH1 S20) | S06 consequence; epics Story-1.5 AC1; ERR-002 | `GET /v1/schedule1` closed mill → 409; page shows verbatim block | `context-drives-schedule.feature` `@S06 @UC-SCH1-001 @S20` | covered | Also flips UC-SCH1-001 S20 → covered |
| S07 No report-status row for mill/year | S07.feature (Alt) | `mill-context` 200 with null statuses; banner mill line only | `working-context.feature` `@S07` | covered | Legacy S07 banner was `[UNKNOWN]`; re-grounded — defects.md SPEC-1 |
| S08 Both mill and year missing | S08.feature (Exc); FLD-001+002 | Backend `mill-context` 400 with BOTH messages | — | not-applicable (UI) / covered-by-contract | Same root cause as S04/S05 |
| S01 Saved context DISPLAYS on the schedule tombstone (banner → tombstone) | S01 display arm; bcgov #227 | `ScheduleTombstone` renders the shared `WorkingContextLines` in the `region[name="Working context"]` landmark on schedule pages | `schedule-tombstone.feature` `@S01 @tombstone @a11y` (Schedule 2) | covered | Ports bcgov `tombstone.spec.ts` S01; a11y clean on Schedule 2 |
| S03 Switching context replaces the tombstone lines | S03 display arm; bcgov #227 | client-side re-nav; `useWorkingContext` re-fetch on the tombstone | `schedule-tombstone.feature` `@S03 @tombstone` | covered | New mill + statuses render; the prior dated line is gone |
| S06 Closed mill's tombstone renders like an open mill | S06 display arm; bcgov #227 | `ScheduleTombstone` header renders even when the schedule body is blocked (409); no closed-mill wording | `schedule-tombstone.feature` `@S06 @tombstone` | covered | Header parity; the block itself is the S06/S20 body concern above |
| S07 No-status pair renders the tombstone mill line only | S07 display arm; bcgov #227 | `WorkingContextLines` suppresses both track lines when statuses are null | `schedule-tombstone.feature` `@S07 @tombstone` | covered | Ports `tombstone.spec.ts` S07 |

## HOME-1.5 acceptance-criteria compliance (this BDD suite)

Note: HOME-1.5 (Story 1.5, done) was originally satisfied by the app team's raw-`@playwright/test`
`nr-ilcr/frontend/e2e` suite. Per its **Pinned Decision 1** that raw-Playwright suite remains the
ratified artifact; this BDD suite is **complementary** and follows the general `nr-e2e-from-gherkin`
structure. Behavioral parity against the ACs:

| HOME-1.5 AC | Covered here | How |
|---|---|---|
| AC1 S01 land + lists populate | ✅ | `@S01 @landing` opens both dropdowns, asserts real options present |
| AC1 S01 save happy | ✅ | `@S01 @SUC-001` |
| AC1 S02 single-mill | ➖ not-applicable | 21-mill data; unit-covered (same disposition as the story) |
| AC1 S03 change + re-save | ✅ | `@S03` (asserts new statuses + old mill gone) |
| AC1 S04/S05/S08 required blocks | ⚠️ contract-only | Not UI-reproducible on current default (DIV-2); 400s proven via API |
| AC1 S06 closed saves + schedule blocked | ✅ | `@S06` (Home banner) + `@S06 @S20` (schedule 409 block) |
| AC1 S07 no-status pair | ✅ | `@S07` |
| AC2 context drives a schedule page | ✅ | `@drives-schedule` — schedule GET carries the saved mill/year |
| AC2 context DISPLAYS on the schedule tombstone | ✅ | `@tombstone` — Schedule 2 header shows the saved mill + both track statuses (banner → tombstone, #227) |
| AC3 AD-10 (verification, not red) | ✅ | Post-implementation assertions of observed behavior |
| AC4 axe WCAG 2.1 AA, zero/triaged | ✅ | `@a11y` on landing + populated-banner + the Schedule 2 tombstone (`wcag2a/2aa/21a/21aa`) → zero violations |
| AC5 CI wired or manual gate documented | ✅ (manual gate) | See **Manual verification gate** below; no CI (app+DB not containerized for CI here) |
| AC6 data-reality confirmed, not invented | ✅ | All anchors grounded via live API 2026-07-30; finding queries pinned in `fixtures/sec/` |

Residual vs the app team's suite: **S04/S05/S08 remain contract-only** here (the mount default pre-selects
both dropdowns, so the empty state is UI-unreachable — DIV-2). This also makes the app team's
browser `S04` **stale** (it assumed the old `514` default was absent). Flagged for BA/PO.

## Manual verification gate (HOME-1.5 AC5)

No CI wiring (the app + delivery Oracle are not containerized in this pipeline). Manual gate:
1. Bring up the stack per `../../../README.md` (backend `:8080` `local` profile + datasource on; Vite `:3000`;
   seeded Docker Oracle `THE/…@localhost:1525/DBDOCK_01`, security off).
2. `cd frontend/e2e && npm test` (the `pretest` hook runs `bddgen`).
   Scope to this UC with `npx playwright test --grep "@UC-SEC-001"`; a11y is included (`@a11y`).
3. Flake gate: `npx playwright test --grep "@UC-SEC-001" --repeat-each=5`.

**Symmetry check:** happy save (S01) + its banner variants (S06 closed still-saves, S07 null-status
mill-line-only) + change/replace (S03) cover the positive/alternative arms; the mirror negative
(required-field S04/S05/S08) is enforced server-side and proven at the contract (GAP-1),
UI-unreachable by DIV-2 — not silently dropped. The context→page arm is covered both ways: it
drives Schedule 1 (AC2) and blocks a closed mill's schedule (S06/S20). No asymmetric silent omission.

**Cross-suite reconciliation (nr-ilcr/frontend/e2e — Story 1.5):** every journey it proved is now either
ported here or explicitly accounted for — `home.spec.ts` S01/S03/S06/S07 ported; its `S04` browser test
**superseded + flagged stale** (default changed `514`→`13050`, DIV-2); S05/S08 same conclusion;
`schedule-context.spec.ts` AC2 + closed-mill block **ported** (`context-drives-schedule.feature`);
`app-shell.spec.ts` smoke subsumed by `openApp`'s Header assertion in every Background; **axe ported**.
The bcgov **banner → tombstone** move (#227) added `tombstone.spec.ts` (the working-context *display*
arm moved off Home onto the schedule pages' `ScheduleTombstone`); its S01/S03/S06/S07 are now **ported**
(`schedule-tombstone.feature`, on Schedule 2). #227 also gutted `home.spec.ts` (display scenarios moved
to the tombstone — still covered here on Home) and added a Vitest `Footer.test.tsx` (unit, not E2E).

**Recorded coverage drop — `tombstone.spec.ts` (Story 1.5, schedule-*page* `ScheduleTombstone`):** deleted
with this migration (it and its only page object `pages/schedule.ts` imported the retired `pages/home.ts`
/ `utils/` modules and were uncollected by the bdd `testDir`). Its four scenarios asserted the
working-context lines on the **schedule page's** header (S01 display + a11y; S03 stale-line replacement on
context switch; S06 closed-mill parity; S07 no-status-row → Mill-line-only) — i.e. the same
`ScheduleTombstone` component, one route deeper than the Home `ContextBanner`. The **data-shape** arms are
reproduced Home-side here (`@S01 @landing`, `@S03`, `@S06`, `@S07` on `working-context.feature`) and the
context→schedule wiring by `context-drives-schedule.feature`; what is **not** re-asserted is the
`ScheduleTombstone`'s own render of those lines on the schedule route. **Decision:** accepted as a
low-risk drop rather than ported — `ScheduleTombstone` is a presentational component fed the same
mill-context payload, unit-covered in Vitest, and re-rendered on every schedule-page Vitest suite; a
dedicated BDD scenario is deferred (re-add if the header regresses). Not an accidental omission.

**Role / permission coverage:** re-grounded — legacy `ILCR_LICENSEE` does not exist (two-group model, PRD
DL-23); scenarios run as the fixed mock authority. Role-gated branches `blocked` (single authority).

**Status values:** `covered` · `not-applicable` (legacy-only / data-shape) · `not-applicable (UI) /
covered-by-contract` (unreachable via browser, verified at the API contract) · `deferred` · `blocked` ·
`divergence` / `bug` (a genuinely-failing `@discovered-*` test — see this UC's `defects.md`).
