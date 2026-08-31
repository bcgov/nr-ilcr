# Defects — UC-SCH3-001 Report Forest Management Administration Costs (Schedule 3)

> How this log works (registers, tags, glossary): [defects-guide.md](../../../defects-guide.md)

All findings below were reproduced on the local seeded delivery DB
(`THE/…@localhost:1525/DBDOCK_01`), app repo branch `test/schedule-3-e2e`. Each entry carries its own
verification date; the newest re-verification of the whole set is **2026-08-27**, against the branch after
the upstream merge that brought in defects #296, #298 and the Epic 24 parity work.

Scenario/test counts are deliberately **not** repeated here — [`coverage.md`](coverage.md) holds the single
authoritative "Suite state" block. Entries name their scenarios and tags instead, which do not move when a
count does.

**Bug / Regression:** _none._

**Divergences:**

- **DIV-1 — a reporter cannot START a Schedule 3: 87 mill-years that require one cannot be entered at all.**
  - **What's wrong:** In the old system, opening Schedule 3 for a mill and year that had never been
    filled in gave you an empty form to type into, and the schedule came into existence when you first
    hit Save. In the new system that mill-year shows the error **"Schedule not found."** and no form at
    all — there is no way to start a Schedule 3 from the screen.
  - **Expected vs actual:** Expected — an empty, enterable Schedule 3. Actual — HTTP 404 "Schedule not
    found." on every Schedule 3 operation for that mill-year, and the page renders the error banner with
    the form suppressed.
  - **How big is it (measured on the delivery extract, 2026-08-25):** the extract marks Schedule 3 as
    **required** for 118 mill-years (`ILCR_REPORT_CATEGORY` rows with category `'3'`) but only **31**
    have ever been started (`ILCR_REPORT_SUMMARY` rows with category `'3'`). **87 of those are on a
    Draft track**, i.e. they are exactly the mill-years a reporter is supposed to be able to fill in
    today. None of them can be.
  - **How we caught it (verified on real data 2026-08-25):** probed `GET /api/v1/schedule3` for every
    mill × reporting year (357 requests): 28 answer 200, 322 answer 404, 7 answer 409 (closed mill). Of
    the 98 pairs on a Draft track only 15 open Schedule 3. Reproduced through the browser on mill 8888
    (millId 24051) / 2015 — a Draft mill-year whose `ILCR_REPORT_CATEGORY` row says Schedule 3 IS
    required. Read-only: nothing was written.
  - **Why (technical):** `Schedule3Service` resolves the category-3 `ILCR_REPORT_SUMMARY` before doing
    anything and throws `ScheduleNotFoundException` when it is absent — on the read
    (`Schedule3Service.java:170`) and on every write via `requireEditableSummary` (`:1070`), including
    both sub-resources and check-status. Legacy created that row on the first Save, which is what
    `Schedule3MB.isScheduleOpen()` reported on. This is **not** an app-wide convention: Schedule 2's
    save inserts its own summary (`Schedule2Repository:200`, `MERGE INTO … ILCR_REPORT_SUMMARY`).
  - **Is it a defect?** Yes — confirmed, and already ticketed from the Schedule 1 side.
  - **Action:** none needed from QA — the ticket below already covers it. Kept as a genuinely-failing
    `@discovered-divergence` test that asserts the correct behaviour (an enterable form) and writes
    nothing.
  - **Ticket:** [bcgov/nr-ilcr#296](https://github.com/bcgov/nr-ilcr/issues/296) (pre-existing, raised
    from Schedule 1) — *"Schedule 1 and 3: Show empty data set if data does not exist for current mill
    year."* Its title names **both** schedules, so this entry is the Schedule 3 half of the same defect
    rather than a new one — and the fix is expected to land in both pages together.
  - **Knock-on effect on this suite:** because a Schedule 3 cannot be created through the app, this
    suite cannot create its own test data either. `real-test-data-patches/sch3/draft-anchors.sql` seeds
    the one row legacy's first Save would have written on 16 mill-years. **When #296 is fixed, most of
    that patch can be retired** and the scenarios can create their own schedules.
  - **FIXED 2026-08-26 — verified, and it behaved exactly as a tracked red should.** The fix landed on
    `main` as `60c24dd` ("fix(schedules-1-3): open a blank, usable form when nothing is saved
    yet (#296)") and was merged into this branch. `Schedule3Service.getSchedule3` now serves a
    200 empty EDITABLE document instead of throwing `ScheduleNotFoundException`, Save creates the summary
    on absent, and Delete/Check Status stopped 404ing too. **`no-create.feature` went green on its own —
    no assertion was edited**, only the `@discovered-divergence` tag retired, which is the whole design of
    a red that asserts the correct behaviour. Verified live on the un-patched anchor: `GET
    /api/v1/schedule3?millId=24051&year=2015` answers 200 `editable: true` where it answered 404
    "Schedule not found." the day before.
  - **THREE KNOCK-ONS, all handled on this branch:**
    1. **The 404 was load-bearing in this suite, in four places** — see VER-2 below. "Absent" no longer
       means 404 for Schedules 1 and 3, so every proxy built on that inverted silently.
    2. **The sub-pages deliberately KEPT their 404** (`Schedule3Service.java:1136`: "both sub-pages are
       reachable only from a SAVED Schedule 3"), which restores legacy's save-first gate and makes S18/S19
       testable for the first time — see DIV-3, and `save-first-gate.feature`.
    3. **The seed patch is now partly retirable.** Part 1 (the empty category-3 summaries) exists only
       because a Schedule 3 could not be created through the app; Save creates one now, so a future pass
       could let mutating scenarios create their own anchors. NOT done here, deliberately: the read-only
       check-status anchors still need their seeded amounts (part 3), the crown anchor still needs its
       category-1 Schedule 1 (part 2), and retiring part 1 would re-open the parallel-safety analysis for
       every mutating scenario. `restoreAnchor` already stopped needing the patch for the delete path.
  - **Priority / env:** p0 · branch `test/schedule-3-e2e` · local seeded DB · Chrome.
  - **Status:** CLOSED (fixed and verified) 2026-08-26. Found 2026-08-24; triaged against the
    pre-existing ticket #296; fixed by that ticket's PR and re-verified here by the suite going green.
  - **Test:** `features/sch3/uc-sch3-001-report-admin-costs/no-create.feature` (S16) — GREEN, tag
    retired, assertions untouched.

- **DIV-2 — RETRACTED (author error): the Override switch DOES suppress the Harvest≥PO&P check on every
  fixed line in legacy too, so the app is faithful.**
  - **What this entry claimed:** that setting "Override Harvest / Total PO&P" to Yes silences the
    Harvest-must-be-at-least-PO&P check more widely than the spec describes — on all eleven fixed cost
    lines rather than only on the itemized other-acceptable rows — and that BA/QA had to decide whether
    the app or the requirements sidecar was wrong.
  - **Why it is wrong (checked against the legacy application source 2026-08-25, `docs/nr-ilcr-2.0.4`):**
    legacy applies the override to the fixed lines too, exactly as the app does. The suppression simply
    does not live where I looked:
    - `managedBean/Schedule3MB.checkStatus()` reads a PRE-COMPUTED flag per line
      (`!checkedSchedule3.getLicensesFeesInsurance().isHarvestGreaterThanPop()`), which is why that
      method carries no override guard on the fixed lines and guards only the other-acceptable rows
      (line 312). I read that absence as "legacy did not suppress here".
    - the flag is computed one layer up, in `service/Schedule3CheckStatus.java:33-56`, which calls
      `isHarvestCostGreaterThanPopCost(overrideTotPop, line)` per line — and that method (`:64-72`) opens
      with `if (overrideTotPop) return true;`, an unconditional pass. The same class repeats the guard in
      `isScheduleValid` (`:78-103`) as `(!overrideTotPop && !…isHarvestGreaterThanPop())`.
    - **Scope corrected 2026-08-26 (full re-derivation at the repo owner's request):** that is **8 of the
      11** fixed lines, not all 11 — codes 27, 28, 30, 31, 32, 34, 35, 36, i.e. exactly the lines carrying
      a PO&P cost. An earlier version of this entry said "every fixed line", which overstated it. The
      three harvest-only lines are exempt in legacy for visible reasons: **29 Annual Rents** has its whole
      harvest-vs-PO&P block COMMENTED OUT in the bean (`Schedule3MB.java:196-206`) and its PO&P forced to
      `BigDecimal.ZERO` by the DAO (`Schedule3DAO.java:179`); **33 Scaling** has a derived,
      `disabled="true"` PO&P display field (`schedule3.xhtml:186`, computed in `Schedule3DO.java:174`);
      **37 Silviculture Admin** has no PO&P input at all (`<h:inputHidden>`, DAO sets ZERO at `:269`).
      The app encodes the same split — `CHECK_LINES` marks those 8 `hasPop=true` and `continue`s past
      BR-03 for 29/33/37 (`Schedule3Service.java:104-116, 934-945`) — so the retraction is **strengthened**
      by the correction, not weakened.
    - **Also verified in the same pass, and worth keeping:** `CostType.harvestGreaterThanPop` initialises
      to **`true`** (`CostType.java:22`) and is only computed when BOTH values are present, so a missing
      value produces only the missing-field error and never a spurious harvest-below-PO&P one — with or
      without the override. The app matches (`harvest != null && pop != null && harvest < pop`). And the
      other-acceptable comparison is **per row/group** in both — legacy
      `CheckStatusUtil.isOtherHarvestCostLessThanPopCost:101-110` returns true if ANY row has
      Total < its PO&P, raising ONE message labelled "Subtotal Other Costs (Harvest Total $)"; the app's
      `evaluateOtherAcceptableGroups` (`:1014`) does the same per group. Despite the "Subtotal" label,
      neither compares the summed subtotal.
    - **Legacy carries TWO verdicts, and the override is honoured in both.** The Schedule 3 screen shows
      the bean's message-derived `allRequirementsMet`; the consolidated submission gate uses
      `passedCheckStatus`/`isScheduleValid` plus sub-page conditions, whose other-acceptable clause
      repeats the override guard (`CheckStatusMB.isSchedule3OtherCostTotalCostGreater:453-460`). The two
      are NOT identical — `isScheduleValid` additionally requires a non-null PO&P for **Scaling** and
      **Silviculture Admin**, with no message counterpart, so a schedule whose PO&P + Crown volumes sum to
      zero (division returns null → null derived Scaling PO&P) would fail the gate while the screen
      reported all requirements met. Not comparable to the rewrite yet: its consolidated Check Status page
      is still a placeholder (`frontend/src/routes/check-status.tsx`). Recorded here so whoever builds it
      does not inherit the assumption that one verdict served both surfaces.
    So the app's `if (!override && …)` in `Schedule3Service.appendFixedLineCheckErrors` reproduces legacy
    precisely. The backend's own code comment cited this class and was right; I discounted it.
  - **What actually misled me:** the requirements sidecar describes BR-10 as applying to the
    other-acceptable rows only, and pairs the override with just the `Subtotal Other Costs (Harvest
    Total $)` row in its check-status table. That is the sidecar being narrower than the code it was
    derived from — a documentation gap, not an app defect. Recorded as **SPEC-1** below so the sidecar can
    be corrected; nothing is wrong with the application.
  - **Method note (worth keeping):** reading the managed bean alone was not enough, because legacy
    computes check-status in a service and the bean only renders the result. "The guard is absent here"
    needs the whole call chain before it is a safe conclusion.
  - **Status:** RETRACTED (author error) 2026-08-25. Raised 2026-08-25, retracted the same day on the
    legacy source, at the repo owner's request to double-check it. The number is retained and never
    reused.
  - **Test:** `check-status.feature` (S12 and its mirror) — GREEN, and unchanged. The scenarios assert
    the app's real behaviour (the other-acceptable row is not flagged, the fixed line is not flagged, the
    schedule passes), which is now confirmed to be correct legacy behaviour rather than a deviation.

- **DIV-3 — the legacy "save the schedule before opening the cost sub-pages" ALERT is gone. The
  navigate-away confirmation it sat beside is NOT — that is preserved.**
  - **Scope corrected 2026-08-25** after the repo owner checked the running app. An earlier version of
    this entry was headed "the warnings no longer exist", which over-claimed: a confirmation IS shown
    before you leave Schedule 3 for a sub-page. Only the *save-first* alert is gone. The per-row delete
    confirmation this entry also used to bundle in is a separate defect, now **DIV-5**.
  - **What's wrong:** legacy refused to open either cost sub-page until the schedule had been saved once,
    warning "The schedule has to be saved before opening other costs" (and the unacceptable-costs
    equivalent). That alert has no counterpart in the new app.
  - **What is NOT wrong — the guarantee is intact:** legacy had TWO controls on those links, not one
    (`webapp/schedule3.xhtml`): an `…EditsEnabledAlert` variant carrying the save-first `alert()`
    (`:265-267`), and an `…EditsEnabled` variant carrying
    `<p:confirm message="#{msg.confirmNavigationMsg}">` (`:270-273`). The rewrite keeps the second: an
    editable schedule shows the "Leave Schedule 3" modal with the verbatim legacy text before navigating.
    This suite asserts that text on **every** sub-page entry (`pages/sch3/schedule3Page.ts`
    `openSubPage`), so its loss would fail the suite immediately.
  - **Why the alert cannot exist here** *(as written 2026-08-24 — SUPERSEDED, see the re-opening below;
    S18/S19 are `covered` today, not `not-applicable`)*: it was gated on `!schedule3MB.isScheduleOpen()` —
    "the schedule has never been saved". In the rewrite a Schedule 3 that can be opened at all already
    exists (that is DIV-1), so the condition is unreachable by construction. S18 and S19 were therefore
    dispositioned `not-applicable` in coverage.md rather than covered.
  - **Is it a defect?** No — an accepted re-grounding. The data-loss risk the alert existed to prevent is
    covered by the navigate-away confirm, which is present, asserted, and verbatim. Worth revisiting if
    #296 gives Schedule 3 a create path: an unsaved schedule would become reachable and the save-first
    gate would become meaningful again.
  - **RE-OPENED AND RE-CLOSED 2026-08-26 — the #296 fix brought the save-first gate BACK, so this
    entry's central claim expired.** This entry rested on "the condition is unreachable by construction:
    a Schedule 3 that can be opened at all already exists". That was true only while an unsaved schedule
    404'd. Since #296 the parent page opens unsaved AND the two sub-pages deliberately keep their 404
    (`Schedule3Service.java:1136`), so the client now gates them with a passive **"Save required"** modal
    carrying the verbatim legacy string `The schedule has to be saved before opening other costs`
    (`components/schedule3/index.tsx:47`, gated on `isScheduleSaved(data)` at `:208`). Legacy's ALT-002 has
    a counterpart again, and S18/S19 stopped being `not-applicable`: they are covered by
    `save-first-gate.feature` as of this branch. The navigate-away confirm this entry also defends is
    unchanged and still asserted on every sub-page entry.
  - **WHY THIS ENTRY EVER EXISTED, since it now reads like an error and was not one (added 2026-08-27).**
    When it was raised on 2026-08-24, an unsaved Schedule 3 answered **HTTP 404** on the PARENT page. The
    save-first alert's precondition is "you are looking at a Schedule 3 that has never been saved" — and
    there was no such screen to look at, so no link to click and no alert to fire. That was DIV-1, and it
    made this gate unreachable rather than missing. The entry was TRUE when written, and it stopped being
    true the moment #296 removed the 404 — the parent page opens unsaved, the sub-pages kept their 404, and
    the client re-acquired the gate. Nothing about the finding was wrong; it **expired**. Schedule 1's
    GAP-3 expired the same day for the same reason. The lesson is not "check harder before raising" but
    "an entry whose premise is another open defect must be re-read when that defect closes".
  - **CORRECTION 2026-08-27 — this entry claimed "the app now matches legacy on both halves", and the
    ALT-003 half is NOT matched.** Legacy wrote TWO different strings, one per link:
    `schedule3.xhtml:267` "…before opening other costs" and `:293` "…before opening **U**nacceptable
    costs". The rewrite routes both links through one generic handler with one constant, so the Included
    Unacceptable link shows the Other Costs wording. Split out as **DIV-7** below. The claim was wrong
    because it was written from the *app* side — one modal, one string, gate restored — without going back
    to the legacy page to count the strings.
  - **Action:** none for this entry. The wording half is DIV-7.
  - **Priority / env:** p2 · local seeded DB · Chrome.
  - **Status:** CLOSED (accepted re-grounding, then superseded by the #296 fix) 2026-08-26. Found
    2026-08-24; scope corrected 2026-08-25 after the repo owner verified the navigation confirm against
    the running stack; the save-first half became reachable and covered 2026-08-26.
  - **Test:** `render-states.feature` (S15 + the read-only sub-page scenario), plus the navigate-away
    text asserted on every sub-page entry in `pages/sch3/schedule3Page.ts` — all GREEN.

- **DIV-4 — RETRACTED (author error): legacy ALSO adds 1 to the Included Unacceptable count for a
  non-zero Annual Rents amount, so the app is faithful.**
  - **What this entry claimed:** that the "Included Unacceptable Costs (n):" link reads one higher than
    the number of rows on the sub-page because the app adds Annual Rents to the count, where legacy
    counted only the itemized rows.
  - **Why it is wrong (checked against the legacy application source 2026-08-25):**
    `service/domain/Schedule3DO.getNumberOfUnacceptableCosts()` (`:395-403`) carries the literal comment
    *"add 1 to unacceptable costs total if there is a value for annual rent"* and does exactly that —
    `numberOfUnacceptabeCosts = unaccecptableCosts.size();` then `+= 1` when the Annual Rents harvest
    cost is non-null **and** `compareTo(BigDecimal.ZERO) != 0`. The app's
    `unacceptableRows.size() + (annualRentsHarvest != null && annualRentsHarvest != 0 ? 1 : 0)` is the
    same rule, including the same zero test. The money total matches too:
    `getUnaccecptableCostsTotals()` (`:383-388`) adds `getUnaccecptableCostsAnnualRents().getTotalCost()`
    to the row sum, which is exactly what the sub-page's Totals footer shows.
  - **What actually misled me:** the sidecar renders CNT-001 as the bare expression
    `#{schedule3MB.schedule3.numberOfUnacceptableCosts}` without saying what that getter does, so I took
    "the count" to be self-evidently the row count. A count that deliberately does not match the visible
    row list is legacy behaviour — arguably still confusing on screen, but not a divergence, and not this
    suite's call to redesign.
  - **Status:** RETRACTED (author error) 2026-08-25. Raised 2026-08-25, retracted the same day on the
    legacy source, at the repo owner's request to double-check it. The number is retained and never
    reused.
  - **Test:** `unacceptable-costs.feature` (S05) — GREEN, and unchanged. It asserts "(1)" with no rows
    and "(2)" with one row, now confirmed correct legacy behaviour.

- **DIV-5 — removing an itemized cost row on either sub-page deletes it immediately, with no
  confirmation and no undo. Legacy asked first.**
  - **What's wrong:** on both Schedule 3 cost sub-pages each row carries a small trash-can button.
    Clicking it deletes that row and saves the change straight away — one mis-click destroys a recorded
    cost with no prompt and no way back. The old system asked "This will delete the current record. Do
    you want to continue?" first.
  - **Expected vs actual:** Expected a confirm-before-delete prompt, then the delete (legacy
    `confirmDeleteMsg`). Actual — the row disappears on click, the whole row set is persisted via one
    `PUT …?intent=delete`, and the API's "Data deleted successfully" is echoed afterwards.
  - **How we caught it (verified on real data 2026-08-25):** re-grounding S04. Removing the added row on
    the Other Costs sub-page returns SUC-002 with no dialog rendered at any point.
  - **Why (technical):** the trash button's `onClick` goes straight to `useEditableCostRows.removeRow` →
    `persist(next, 'delete')` (`hooks/useEditableCostRows.ts:270-283`); no dialog is involved. The
    behaviour lives in the SHARED `EditableSubPageLayout` / `useEditableCostRows` components rather than
    on the Schedule 3 pages — so the defect is shared, and so is the fix.
  - **Is it a defect? Yes — confirmed against the legacy application source (2026-08-25), not just the
    sidecar.** `webapp/schedule3SubtotalOtherCosts.xhtml:94-96` — the per-row Delete is a
    `p:commandButton` carrying
    `<p:confirm header="Confirmation" message="#{msg.confirmDeleteMsg}" icon="ui-icon-alert" />`. Legacy
    did prompt. The new app is also **internally inconsistent**: the whole-schedule Delete kept its
    "Delete schedule" confirm modal, so the app confirms the large destructive action and not the small
    one.
  - **The same defect as Schedule 1's, and now ONE ticket for both.**
    `features/sch1/uc-sch1-001-enter-save/defects.md` **DIV-3** records it for Schedule 1's Other Costs
    sub-page. That entry had been waiting on exactly this check — whether legacy really prompts, its own
    evidence being captured sidecars rather than legacy code. Closed 2026-08-26 by reading all three
    legacy views at source: `schedule1OtherCosts.xhtml:94-96`,
    `schedule3SubtotalOtherCosts.xhtml:94-96` and `schedule3IncludedUnacceptableCosts.xhtml:80-82` each
    carry the same `p:confirm` on their per-row Delete, and `messages.properties:31` resolves
    `confirmDeleteMsg`. Three affected pages, one shared hook, one ticket.
  - **The sibling comparison that makes it a defect rather than a house style:** every other row-level
    delete in the app confirms first — Schedule 4 sub-page rows (`schedule4/SubPage.tsx:471`), Schedule 5
    camps (`:1406`), Schedule 8 rate rows (`RatesPage.tsx:375`), Schedule 11 locations (`:1037`), and
    Schedules 7A/7B/9/10 through the shared `components/core/ConfirmDeleteModal`. Only the three pages
    built on `useEditableCostRows` do not.
  - **Ticket:** [bcgov/nr-ilcr#362](https://github.com/bcgov/nr-ilcr/issues/362) — *"Deleting an itemized
    cost row on the Schedule 1 and 3 cost sub-pages destroys it with no confirmation, unlike legacy and
    every other schedule"*, labelled `bug`, filed by the repo owner 2026-08-26. Its repro runs on the
    extract anchor **727 Updated Mill E2E / 2017** with no test-data patch applied (verified: row added
    and saved, Remove clicked → **0 dialogs**, row already gone after a reload, on this schedule and on
    Schedule 1). The filed issue deliberately omits two things the registers keep, as they are their
    home: why the suites missed it (Schedule 1's S12 had been re-grounded onto the defect — see that
    entry) and the related-ticket comparison (#292, #296 — neither concerns confirming a destructive
    action).
  - **Priority / env:** p1 · branch `test/schedule-3-e2e` · local seeded DB · Chrome.
  - **Status:** OPEN — confirmed and triaged by raising a ticket. Dev to gate
    `useEditableCostRows.removeRow` behind a confirmation in the shared `EditableSubPageLayout`, without
    disturbing the whole-schedule Delete or the "Leave Schedule 3" prompt (both asserted by this suite);
    QA re-verifies and closes this entry and Schedule 1's DIV-3 together when the fix lands. Found
    2026-08-24 (bundled inside DIV-3), split out and legacy-source-confirmed 2026-08-25 at the repo
    owner's direction; ticketed 2026-08-26.
  - **Test:** `features/sch3/uc-sch3-001-report-admin-costs/row-delete-confirm.feature` (S04,
    `@discovered-divergence`) — and, tracking the same ticket from the other side, Schedule 1's
    `other-costs.feature` `@S12 @discovered-divergence`. Both assert that a confirmation is *shown*, not
    any particular chrome, so one fix turns both green with no test change.

- **DIV-6 — Check Status judges the SAVED schedule and silently ignores what is on screen. Legacy judged
  the screen. Affects 11 of the 12 schedules.**
  - **What's wrong:** change something — the Override switch, any amount — and press Check Status without
    saving, and the answer describes the *stored* schedule, not the one in front of you. Nothing says so.
    Reported by the repo owner 2026-08-25: selecting "No" for Override Harvest ⁄ Total PO&P and pressing
    Check Status shows no errors; pressing Save first and then Check Status shows them.
  - **Expected vs actual:** Expected — the verdict describes the screen (legacy). Actual — the verdict
    describes the last saved state; with Override stored as "Y" the two stored BR-03 violations stay
    suppressed and the page still reports "All requirements for this schedule have been met" even after
    the reporter has switched Override to "No" on screen. A **false green**: the schedule is declared
    ready while the screen says otherwise. The mirror case is just as bad — fix a flagged field, press
    Check Status, and the same error is still reported.
  - **How we caught it (verified on real data 2026-08-25):** the repo owner found it by hand; reproduced
    as a scenario on the seeded `check-override` anchor (mill 20171/2021, Override "Y" + a stored
    Wages/Salaries 40,000-vs-50,000 violation + an other-acceptable 1,000-vs-2,500 violation). Check
    Status passes; switching Override to "No" on screen and re-checking still does not report either
    violation. Read-only — nothing is saved, and the unmoved optimistic-lock token is asserted to prove
    it.
  - **Why (technical):** the request carries no client state, by contract.
    - `POST /api/v1/schedule3/check-status` declares only `@RequestParam millId` and `@RequestParam year`
      — **no `@RequestBody`** (`Schedule3Api.java:85-87`).
    - the client posts no payload: `useScheduleMutations.checkStatus` is
      `api().post(url(suffix))` with no second argument (`useScheduleMutations.ts:77-78`).
    - the service therefore reads the database: `repository.findSummary(...)`,
      `repository.findDetails(...)`, and `override = OVERRIDE_YES.equals(summary.location())`
      (`Schedule3Service.java:889-895`).
  - **Legacy could not behave this way.** Its Check Status button was `ajax="false"`
    (`webapp/schedule3.xhtml:38` and `:421`) — a full form postback. JSF applied every submitted field to
    the bean during UPDATE_MODEL_VALUES *before* `checkStatus()` ran, including `overrideTotPopVal`, which
    is bound straight to `#{schedule3MB.schedule3.overrideTotalPop}` (`:323-324`). `checkStatus()` then
    validated that in-memory object (`Schedule3MB.java:158-159` →
    `ilcrService.schedule3CheckStatus(schedule3)`) and persisted nothing. So legacy evaluated the screen
    without saving it. (Legacy's *separate* consolidated Check Status page, `CheckStatusMB` /
    `checkStatus.xhtml`, is the one that reads stored data — that is the submit gate, a different
    surface.)
  - **The false green, captured from the failing run's DOM snapshot (2026-08-25):** at the moment of
    failure the page holds both of these at once —
    `row "Override Harvest ⁄ Total PO&P $ … No"` (the dropdown reads **No** on screen) and
    `status: text: Requirements met All requirements for this schedule have been met`. Two BR-03
    violations are stored. So the reporter is told the schedule is ready while looking at the switch that
    should be exposing both of them.
  - **The team has already ruled on this rule, and fixed it once.**
    `docs/superpowers/specs/2026-08-21-schedule-6-corrections-design.md:54-58` — *"Check Status evaluates
    the submitted on-screen values, not the database. Legacy's Check Status was an `ajax="false"` full
    postback that populated the managed bean from the screen and evaluated that, persisting nothing. The
    shipped implementation reads the DB, which was near-equivalent while an Edit button meant at most one
    row could be unsaved; with correction 4 the two diverge on every keystroke."* That reasoning applies
    with MORE force to Schedule 3, whose whole form is editable at once — there is no Edit button
    limiting the unsaved surface to one row, so screen and database diverge on the first keystroke.
  - **Existing tickets searched (2026-08-25):** no open or closed issue covers this root cause.
    Adjacent but distinct: **#326** (Schedule 4's Check Status messages omit the field name), **#322**
    (Check Status should be disabled outside Draft on Schedules 4 and 8), and **#293** (CLOSED — added
    the bottom-row Check Status button on Schedules 4 and 6). None of them concerns WHICH data the check
    evaluates.
  - **Is it a defect? Yes — and the repo already says so in its own words.** Schedule 6 is built the
    other way and documents the rule: "`request` carries the on-screen values (Task 6): legacy's
    `ajax="false"` postback applied the screen to the model before evaluating
    (`Schedule6MB.checkStatus` :139-140), so **the verdict must describe the screen, not the database**"
    (`Schedule6Api.java:100-103`). Its endpoint takes `@Valid @RequestBody Schedule6CheckRequest` and its
    page posts a body (`components/schedule6/index.tsx:754`). So this is not a matter of interpretation:
    one schedule implements the rule and eleven do not.
  - **Scope — 11 of 12 schedules (wire contract verified for all; end-to-end proven for Schedule 3):**

    | Schedule | check-status request | Verdict |
    |---|---|---|
    | 6 | `@RequestBody Schedule6CheckRequest`, page posts a body | **correct — the precedent for the fix** |
    | 1, 2, 3, 4, 5, 8 | no body; shared `useScheduleMutations.checkStatus` posts no payload | affected |
    | 7a, 7b, 9, 10, 11 | no body; each page's own `.post(CHECK_STATUS_PATH + query)` with no second argument | affected |

    Schedule 8 has a second surface with the same shape — its sample sub-page posts
    `/v1/schedule8/pages/{pageId}/check-status?…` with no body (`schedule8/SamplePage.tsx:239-244`).
  - **What this entry does NOT claim.** For Schedule 3 the behaviour is proven end-to-end by the failing
    scenario. For the other ten, only the *wire contract* was read — an endpoint that receives no client
    state cannot evaluate one, which is sufficient for the defect, but two page-level details were not
    checked per schedule: whether each page can actually be dirty at the moment Check Status is pressed,
    and whether it discards a previously-rendered verdict when the user edits (7a, 7b and 9 carry a
    comment saying they do, which would reduce the false-green window without fixing the underlying
    evaluation). Worth a look per page when the ticket is picked up.
  - **Action:** **repo owner is raising ONE ticket covering every affected schedule** (draft prepared
    2026-08-25 in the Bugfix Task house style). Kept as a genuinely-failing test on Schedule 3, asserting
    the correct behaviour — RED until the verdict describes the screen. The fix has a working in-repo
    model to copy (Schedule 6): send the on-screen values and evaluate those.
  - **Register — CONFIRMED as a Divergence (repo owner, 2026-08-25).** I had flagged this as arguably
    belonging in Bug / Regression, since behaviour legacy had was lost. The repo owner ruled it stays a
    Divergence and is to be framed as such: the rewrite made a structural choice (a stateless
    check-status endpoint) that differs from legacy's postback model, and the ticket says so in those
    terms. So the id stays `DIV-6` and the scenario keeps `@discovered-divergence`.
  - **Ticket:** [bcgov/nr-ilcr#359](https://github.com/bcgov/nr-ilcr/issues/359) — *"Check Status should
    evaluate the on-screen values, as legacy did — 11 of 12 schedules evaluate the saved record instead"*,
    labelled `bug`, filed by the repo owner 2026-08-25. (An earlier attempt, **#358**, was filed with `gh`
    and is CLOSED: filing through the CLI bypasses the Bugfix Task template, so no label was applied and
    this account cannot add one. File through the web form.) The filed issue deliberately omits three
    things this entry keeps, as the register is their home: the DOM/request evidence dump, why the suite
    missed the defect, and the related-ticket comparison.
  - **Priority / env:** p1 · branch `test/schedule-3-e2e` · local seeded DB · Chrome.
  - **Status:** OPEN — confirmed and triaged by raising a ticket. Dev to send the on-screen values with the
    check-status request and evaluate those, following Schedule 6 (`Schedule6CheckRequest`), across the
    eleven affected schedules; QA re-verifies and closes this entry then. The `@discovered-divergence`
    scenario asserts the CORRECT behaviour, so it is RED today and goes green on its own when the fix
    lands, at which point its tag comes off. No test change is needed. Found 2026-08-25 by the repo owner;
    legacy-source-confirmed and scoped across the app the same day.
  - **THIS ENTRY IS THE ANALYSIS HOME for the whole family.** Schedules 1, 2, 4 and 11 each carry a short
    POINTER entry — **sch1 DIV-6, sch2 DIV-2, sch4 DIV-8, sch11 DIV-5** — holding only their own scenarios,
    anchors and re-groundings. The reasoning lives here and only here; keep it that way, because two copies
    of one caveat diverged inside a single session on ilcr-bmad PR #92.
  - **COVERAGE IS NOW COMPLETE — TEN scenarios across five domains** (nine of them written 2026-08-27,
    alongside the pre-existing `@S12` below). Both arms exist
    everywhere, because they fail in OPPOSITE directions: the false-GREEN arm lets an incomplete schedule
    look ready (how a bad schedule gets submitted), and the false-RED arm keeps reporting what the reporter
    has already fixed (the direction they meet most often).

    | Domain | Scenarios | Pointer entry |
    |---|---|---|
    | sch3 (here) | `@S12` Override · `@S25` cleared amount · `@S26` the mirror | — |
    | sch1 | `@S27` / `@S28` | sch1 DIV-6 |
    | sch2 | `@S17` / `@S18` | sch2 DIV-2 |
    | sch4 | `@S33 @S34` (one scenario, both directions — anchor limit) | sch4 DIV-8 |
    | sch11 | `@S21` / `@S22` (inline row editor, NOT the Add panel) | sch11 DIV-5 |

    Ex-**GAP-4** tracked the absence of these and was CLOSED by writing them.
  - **CLOSE-OUT CHECKLIST — QA must not close this family on the fix alone.** When #359 lands all TEN go
    green on their own. Then, per domain: retire the `@discovered-divergence` tag AND the `[DISCOVERED …]`
    title marker together, close that domain's pointer entry with the date and the fixing PR, and correct its
    coverage.md count. Leave the assertions alone — a red that goes green by itself is the whole design. Two
    things remain genuinely outstanding, and neither blocks closing the above:
    1. **Schedule 6 deserves the inverse test** — it is the one page built correctly
       (`@RequestBody Schedule6CheckRequest`), so a green scenario there protects the precedent the fix copies
       from. No sch6 suite exists yet.
    2. **The six schedules with no suite** (5, 7a, 7b, 8, 9, 10 — plus Schedule 8's sample sub-page as a
       second surface), picked up as each suite is built.

    The recipe, for whoever writes those: assert the violation appears without a save **and** that the
    optimistic-lock token has not moved, so a fix cannot satisfy the test by quietly saving. Seed the anchor
    so it PASSES Check Status as stored — that is what makes the unsaved-edit change observable.
  - **Test:** `features/sch3/uc-sch3-001-report-admin-costs/check-status-unsaved.feature` — **three**
    scenarios, all `@discovered-divergence`: `@p1 @S12` (the Override input), `@p1 @S25` (a mandatory amount
    cleared on screen) and `@p1 @S26` (the mirror — correcting a flagged Harvest). S25 CLEARS a field rather
    than typing a violation because the only at-rest-PASSING anchor passes *because* Override is "Y", which
    legitimately suppresses the Harvest≥PO&P comparison — the required-field checks it does not suppress.

- **DIV-7 — the "save the schedule first" gate on the Included Unacceptable Costs link shows the OTHER
  COSTS wording. Legacy had two different messages, one per link; the rewrite has one.**
  - **What's wrong, in plain terms:** open a Schedule 3 that has never been saved and click *Included
    Unacceptable Costs*. You are correctly told to save first — but the message talks about "other costs",
    which is the *other* sub-page. A reporter who has just clicked "Included Unacceptable Costs" is told
    about a page they did not click. Nothing is lost or mis-saved; it is a wrong-wording defect.
  - **Expected vs actual:** expected `The schedule has to be saved before opening Unacceptable costs`
    (note the capital U). Actual `The schedule has to be saved before opening other costs`.
  - **Why (technical):** legacy put the string in each link's own `onclick`, and wrote it twice —
    `webapp/schedule3.xhtml:267` on `subtotalOtherCostsEditsEnabledAlert` and `:293` on the Included
    Unacceptable equivalent, both rendered only when `!schedule3MB.isScheduleOpen()`. The rewrite has ONE
    generic `openSubPage` handler (`components/schedule3/index.tsx:272`) that sets one flag, and one modal
    body rendering one constant, `ALT_SAVE_BEFORE_SUB_PAGE` (`:47`, `:685`). Fix: a second constant plus
    the sub-page identity the handler already receives.
  - **How we caught it (verified against legacy source and the running app 2026-08-27):** while re-reading
    DIV-3's re-closure, which asserted "the app now matches legacy on both halves". Swept every legacy
    `.xhtml` for `saved before opening` — exactly **three** hits: `schedule1.xhtml:497` and
    `schedule3.xhtml:267`/`:293`. So Schedule 3 needs two strings and has one. Confirmed live: the S19
    scenario now fails on the message and passes on everything else, which is the shape of a wording-only
    defect.
  - **Scope — Schedule 3 ONLY, checked rather than assumed:** Schedule 1 has one such link and one legacy
    string, matched verbatim (`components/schedule1/index.tsx:44`), so it is unaffected. No other legacy
    page has a save-first alert at all; Schedules 4 and 8 use the different
    `confirmNavigationFromNew{Camp,Transportation}` save-now prompt, which is not this pattern.
  - **Ticket:** [bcgov/nr-ilcr#373](https://github.com/bcgov/nr-ilcr/issues/373) — *Schedule 3's "Included
    Unacceptable Costs" save-first message says "...before opening other costs" instead of legacy's
    "...before opening Unacceptable costs"*, labelled `bug`, filed by the repo owner 2026-08-27. The filed
    issue deliberately omits two things this entry keeps, as the register is their home: the captured DOM
    evidence (its Screenshots block ships empty, as #324's and #359's do) and the explanation of why the
    suite missed it — see the last bullet below. Neither was lost; do not "restore" either into the ticket.
  - **Priority / env:** p2 · branch `test/schedule-3-e2e` · local seeded DB · Chrome.
  - **Status:** OPEN — confirmed and triaged by raising a ticket. Dev to add a second message constant
    beside `ALT_SAVE_BEFORE_SUB_PAGE` (`components/schedule3/index.tsx:47`) holding legacy's
    `:293` wording verbatim, and to carry the blocked route alongside the existing `subPageBlockedOpen`
    flag (`:138`) so the modal body at `:685` selects on it — the handler already receives the route;
    QA re-verifies and closes this entry when the fix lands. The `@discovered-divergence` scenario asserts
    the CORRECT behaviour, so it is RED today and goes green on its own, at which point only its tag comes
    off. No test change is needed. Found 2026-08-27 while auditing DIV-3's own re-closure claim.
  - **Test:** `save-first-gate.feature` `@discovered-divergence @p2 @S19` ×1 (S18, the Subtotal Other Costs
    arm, is green). Read-only: the scenario clicks a link that refuses to navigate, so it writes nothing and
    needs no cleanup.
  - **Why the suite missed it for a day:** S18 and S19 were written together and shared one step,
    `Schedule 3 tells me to save first`, which asserted ALT-002's text. S19 therefore *passed* against the
    wrong message — a shared step hid a per-link difference. They now use separate steps.

- **VER-2 — the suite had FOUR places that read "HTTP 404" as "this schedule does not exist". The #296
  fix removed that meaning, and every one of them inverted at once. Re-grounded 2026-08-26; the app is
  right and was right.**
  - **What broke, and it was our proxy rather than the app's behaviour:** an unsaved (or just-deleted)
    Schedule 1/3 now answers 200 with an empty EDITABLE document. The four sites were: the BR-09
    crown-anchor preflight; the two crown Given steps ("Schedule 1 has / has never been opened"); the
    post-delete assertion ("Schedule 3 no longer exists"); and `restoreAnchor`'s repair path, which keyed
    off `status === 404` to decide the summary was missing — so after the destructive S08 it skipped the
    repair and the following sub-page PUT failed with "Schedule not found.", surfacing as a cleanup
    failure rather than a test failure.
  - **The durable signal, and why:** `revisionCount != null` — the optimistic-lock token the server
    issues only once the summary row exists, which the backend omits when absent. This is the app's own
    predicate (`utils/schedule.ts` `isScheduleSaved`, whose comment records that the loose `!=` is
    load-bearing because the omitted field reads `undefined`). Added as `schedule1IsSaved` /
    `schedule3IsSaved` in `steps/sch3/schedule3Api.ts` so the rule lives in one place.
  - **`restoreAnchor` got simpler, not just fixed:** Save now creates on absent, so the app itself can
    rebuild a deleted summary. The SQL patch is still called, but only for the crown anchor's category-1
    Schedule 1.
  - **Two post-delete assertions were re-grounded, and this was checked against LEGACY rather than
    against the fix's own commit message** — the same discipline the sch1 S12 episode taught. Legacy's
    `Schedule3MB.delete()` (`:125-136`) deletes, RE-READS the schedule (`schedule3 =
    getIlcrService().getSchedule3(...)`), messages "Data deleted successfully" and comments "Stay on the
    same page". Editability there is gated only on `disableReportEdits()` →
    `userSessionMB.disableUserInput()` (track status / role — nothing about summary existence), and Delete
    is `rendered="#{!disableReportEdits() and isScheduleOpen()}"` (`schedule3.xhtml:426`). So legacy's
    post-delete screen was a blank EDITABLE form with Delete withdrawn — exactly what the app does now.
    The OLD app (404, then a read-only blank strand) was the divergence; #296 restored legacy, and our
    assertions had been pinned to the divergence. Re-grounding onto restored-legacy behaviour is
    sanctioned; a new `@discovered-*` red here would have asserted something legacy never did.
  - **Status:** CLOSED (re-grounded) 2026-08-26. No app defect at any point.
  - **Test:** `crown-push.feature` `@S07`, `delete.feature` `@S08`, `preflight/sch3-anchors.setup.ts` —
    all GREEN after re-grounding.

**Coverage gaps (not tested yet — no app problem):**

- **GAP-1 — "the schedule is read-only because you are not a Licensee" cannot be tested (BR-01, S15).**
  - **Why not:** the local stack runs with security off — a mock principal grants one role with every
    action — so there is no way to be signed in *without* `EDIT_SCHEDULE`. The read-only half of BR-01
    that depends on the **track status** IS covered (both Submitted and Verified); only the
    role-dependent half is unreachable. The server-side gate itself exists and is covered by the
    backend's own `Schedule3AuthorizationIT` / `Schedule3WriteAuthorizationIT`.
  - **This is the cross-cutting deferral, not a Schedule 3 finding.** It is owned by `deferred-work.md`
    → *"Deferred (cross-cutting): role-gated behaviour cannot be E2E-tested under single-role mock auth
    (2026-08-12)"*, which lists `sch1` GAP-1, `sch2` GAP-1, `sch3` GAP-1, `sch4` GAP-1, `sch11` GAP-6 and
    `sec` GAP-4 — this entry is the same gap on this page. Do not re-litigate it per page.
  - **RE-CHECKED AGAINST THE CODE AND THE RUNNING STACK 2026-08-26 — and the old reason no longer holds.**
    That deferral's premise was that role behaviour is unreachable because mock auth stamps one authority
    and the two roles grant the same actions. Both halves have since changed, so do not repeat them:
    - **The roles DO diverge now.** `security/SchedulePermissions.java` grants `ADMIN` six actions
      (`VIEW_SCHEDULE`, `EDIT_SCHEDULE`, `MAINTAIN_CODE_TABLES`, `OPEN_REPORTING_YEAR`,
      `EDIT_HOME_CONTENT`, `MAINTAIN_USERS`) and `SUBMITTER` two (`VIEW_SCHEDULE`, `EDIT_SCHEDULE`).
    - **The mock-user selector DOES drive the backend principal now.** The SPA sends the chosen role as
      `X-Mock-Groups` (`service/api-service.ts:38`) and `MockPrincipalFilter` builds the authority from
      that header, falling back to `ilcr.security.mock-role` (default `ILCR_SUBMITTER`). Verified live:
      `GET /api/v1/code-tables` answers **403** as `ILCR_SUBMITTER` and **200** as `ILCR_ADMIN`.
    - **What is still true, and is the real reason this stays uncovered:** no *schedule* action differs by
      role. Both roles hold exactly `VIEW_SCHEDULE` + `EDIT_SCHEDULE`, and `GET /api/v1/schedule3`
      answers **200** for both (same probe). So there is no role branch on this screen to test — the gap
      is missing *behaviour*, not a missing *capability* in the harness. Legacy's Licensee-only edit has
      no counterpart because the role model itself changed (two FAM groups, PRD DL-23), which is why this
      is not filed as a divergence.
    - Role-gated behaviour that DOES exist is admin-surface, not schedule-surface (Administration nav is
      `adminOnly` in `routes/-navigation.ts:94`, and code-table writes 403 a submitter) — owned by the
      UC-CODE-001 / admin suites, not by this one.
  - **When it is done** (per that entry): QA authors E2E tests for these coverage gaps and runs them
    against the running app, once a schedule-level role branch actually exists — then this `Status:`
    moves. The trigger to watch is `SchedulePermissions.ROLE_ACTIONS`: the day `EDIT_SCHEDULE` stops
    being granted to both roles, this becomes testable in a single run by switching the mock user, and it
    should be covered rather than deferred again.
  - **Status:** OPEN — `blocked` in coverage.md. A gate should treat this as **waived**, not failing.
  - **Test:** none today, by environment limitation rather than by choice.

- **GAP-2 — the optimistic-lock conflict (two people saving the same Schedule 3) is not covered.**
  - **Why not:** it is a rewrite-only guarantee (AR11: a stale `revisionCount` is refused with HTTP 409)
    with no counterpart in the legacy Gherkin, so no slice asks for it. It is genuinely worth an E2E —
    Schedules 4 and 11 both have one — but it needs a dedicated anchor, and Schedule 3's anchors are
    currently seeded one-per-scenario (see DIV-1's knock-on note), so adding it costs a new patched
    mill-year rather than a new scenario.
  - **CLOSED 2026-08-26 — written.** `concurrency.feature` covers it, and the anchor cost less than this
    entry predicted: no second browser context is needed (the page copies `revisionCount` into React
    state on load, so ONE out-of-band API save makes the browser's token stale — the same shortcut `sch4`
    and `sch11` use), and the "new patched mill-year" is one extra line in `draft-anchors.sql`. The new
    `stale-edit` anchor (12050/2018) follows this suite's established pattern: seeded on a mill-year only
    **sch4** pins, declared with its reason in `preflight/sch4-anchors.setup.ts`. It is the narrowest such
    share so far — both anchors are mutating — which is safe because Schedule 3 writes category-3 rows and
    Schedule 4 writes category-4 `TRANSPORTATION_REPORT` rows, and the scenario deliberately edits a cost
    line and never a timber volume, so BR-09 cannot reach Schedule 1.
  - **What it asserts, and why three halves:** the verbatim 409 detail reaches the screen (AD-8), the
    other session's value is the survivor in storage, and ours is not. The first alone would pass an app
    that showed the error and still wrote our value — which is the actual lost-update bug worth guarding.
  - **Status:** CLOSED (covered) 2026-08-26. Found 2026-08-25 as a gap; closed by writing the test.
  - **Test:** `concurrency.feature` `@p1 @S01` — GREEN.

- **GAP-3 — the sub-page "discard unsaved edits" prompt on Back is not asserted.**
  - **Why not:** the equivalent prompt on the way IN to a sub-page IS asserted (every navigation
    crosses it, and its verbatim text is checked). The Back-with-unsaved-edits variant needs a scenario
    that deliberately leaves a row edited and then walks away, which only fits on the one mutating
    sub-page anchor whose scenario is already the longest in the suite.
  - **CLOSED 2026-08-26 — written, and it needed no new anchor at all.** The premise that it required a
    second mutating sub-page anchor was wrong: an in-place row edit lives in React state and only Save
    persists it (Add and Remove persist immediately, and the scenario does neither), so the whole
    scenario writes nothing and shares the READ-ONLY `check-oa-pop` anchor and its seeded
    other-acceptable group. The final API read-back is what proves that claim rather than assuming it.
  - **What it asserts:** the "Leave page" warning appears carrying the verbatim legacy
    `confirmNavigationMsg` text (`useEditableCostRows.handleBack:293-298`); Cancel keeps you on the page
    with the edit intact, so the guard is not a one-way door; and Continue leaves WITHOUT writing —
    checked at the API, because a "discard" that quietly persisted would look identical on screen.
  - **Status:** CLOSED (covered) 2026-08-26. Found 2026-08-25 as a gap; closed by writing the test.
  - **Test:** `subpage-back.feature` `@p2 @S04` — GREEN.

- **GAP-4 — CLOSED (covered) 2026-08-27. The "Check Status on UNSAVED edits" scenarios were written, for
  every domain that has a Check Status.**
  - **Closed the way a coverage gap is supposed to close: by writing the tests.** Nine scenarios across five
    domains — sch3 `@S25`/`@S26` beside its existing `@S12`, sch1 `@S27`/`@S28`, sch2 `@S17`/`@S18`,
    sch11 `@S21`/`@S22`, sch4 `@S33 @S34`. All are deliberate `@discovered-divergence` reds tracking
    [#359](https://github.com/bcgov/nr-ilcr/issues/359); DIV-6 above owns the analysis and the close-out
    checklist. Measured 2026-08-27: 400 tests, 375 passing, 25 tracked reds, zero untagged failures.
  - **What it took, recorded because the next person will hit the same wall.** Three of the five domains had
    NO anchor available: 114 (mill, year) keys were already pinned across the six fixtures, Home offers only
    reporting years 2015-2021, and every unclaimed openable pair in that range is non-Draft — which disables
    Check Status. So `real-test-data-patches/{sch2,sch11,sch4}/unsaved-check-anchors.sql` seed five dedicated
    Draft mill-years. Two prerequisites were only found by trying: the Home year range above, and that a
    report-status row alone is NOT enough — a working mill-year also carries **eleven**
    `ILCR_REPORT_CATEGORY` rows, without which the page opens but the first save fails 500
    (`DataIntegrityViolationException`).
  - **Still outstanding, and NOT this gap:** Schedule 6's inverse green test, and the six schedules with no
    suite at all (5, 7a, 7b, 8, 9, 10). Both are line items in DIV-6's close-out checklist, where they belong
    — a gap in this UC's register cannot track another UC's missing suite.
  - **Status:** **CLOSED (covered) 2026-08-27.** Raised 2026-08-25 as a test-design blind spot; the source
    slices were recovered upstream by ilcr-bmad PR #92 on 2026-08-27 and the scenarios written the same day.
  - **Test:** `check-status-unsaved.feature` in each of the five domains — see the table in DIV-6.

  *The original analysis follows, kept because DIV-6's close-out checklist is written from it.*

- **GAP-4 (original text) — the "Check Status on UNSAVED edits" slices are missing everywhere except the one
  Override case in this suite. GATED on the fix for [#359](https://github.com/bcgov/nr-ilcr/issues/359).**
  - **Why not:** test-design blind spot, not a weak assertion. All four original Check Status scenarios
    (S09–S12) seed or save first and *then* check, so screen and database always agreed and the whole
    class of "does the verdict describe what I am looking at" was never asked. DIV-6 exists because the
    repo owner asked it by hand. The single scenario now covering it
    (`check-status-unsaved.feature`, S12) pins **one** input — the Override dropdown, on Schedule 3.
  - **What is still uncovered:**
    - **the amount variant on Schedule 3** — type a Harvest below its PO&P, or clear a required cost, and
      press Check Status without saving. Same defect, different input; DIV-6's repro notes it holds, but
      no scenario asserts it.
    - **the mirror case** — fix a flagged field and re-check: the stale error is still reported. The
      false-*red* direction, and it is the one a reporter hits most often.
    - **the other ten affected schedules** (1, 2, 4, 5, 7a, 7b, 8, 9, 10, 11 — and Schedule 8's sample
      sub-page as a second surface). Suites exist today for **sch1, sch2, sch4 and sch11**, so those four
      can take a mirrored scenario as soon as it is worth writing; the rest wait for their suites.
      **The SOURCE side is done as of 2026-08-27:** ilcr-bmad PR #92 recovered the missing rule (BR-12 here)
      and projected two arms per schedule across all twelve UCs — `UC-SCH1-001-S27/S28`,
      `UC-SCH2-001-S17/S18`, `UC-SCH3-001-S25/S26`, `UC-SCH4-001-S33/S34`, `UC-SCH11-001-S21/S22` and the
      equivalents on the seven UCs with no suite yet. Each of the four existing suites now carries those
      slices as `deferred` in its own coverage.md, pointing here. So this gap is now "write the tests",
      with nothing left to author upstream.
    - **Schedule 6 deserves the inverse test** — it is the one page built correctly
      (`@RequestBody Schedule6CheckRequest`), so a green scenario there protects the precedent the fix
      copies from.
  - **Why it is gated, and not just "deferred":** every one of these scenarios asserts behaviour the app
    does not have yet, so writing them now adds ten-plus `@discovered-divergence` reds that all track a
    single ticket. That buys no information the one existing red does not already give, and a wall of
    tracked reds is how a gate stops being read. **Write them once #359 lands**, where they become
    ordinary green regression tests that stop the fix being reverted per-schedule.
  - **Future action:** repo owner intends to add these after the #359 fix; QA then covers the remaining
    schedules as their suites are built. Reuse this suite's shape — assert the violation appears without
    a save, *and* that the optimistic-lock token has not moved, so a fix cannot satisfy the test by
    quietly saving. Seed the anchor so it PASSES Check Status as stored, which is what makes the
    unsaved-edit change observable.
  - **Status:** OPEN — **blocked on [#359](https://github.com/bcgov/nr-ilcr/issues/359)**; not startable
    before the fix lands. A gate should treat this as waived, not failing: the behaviour is tracked by
    DIV-6's deliberate red in the meantime.
  - **Test:** none yet — tracked as a `deferred` row in coverage.md. See **DIV-6**.

**Spec gaps (the Gherkin is missing scenarios its own source docs list):**

- **SPEC-1 — the sidecar describes BR-10 (the Override switch) as narrower than the legacy code it was
  derived from.**
  - **What's missing:** `UC-SCH3-001-technical.md` and `-detailed.md` both state BR-10 as "when Override
    Harvest/Total PO&P is set to Yes, the Harvest-greater-than-or-equal-to-PO&P check on the
    **other-acceptable costs** is not enforced", and the check-status table pairs the override only with
    the `Subtotal Other Costs (Harvest Total $)` row. S12 is written to match. In the legacy application
    the override ALSO suppresses that check on **the eight fixed cost lines that carry a PO&P cost**
    (codes 27, 28, 30, 31, 32, 34, 35, 36) — `service/Schedule3CheckStatus.java:33-56` computes each of
    those lines' flags through `isHarvestCostGreaterThanPopCost(overrideTotPop, line)`, which returns
    `true` unconditionally when the override is on (`:64-72`). The other three fixed lines (29 Annual
    Rents, 33 Scaling, 37 Silviculture Admin) have no PO&P cost to compare and carry no such check in
    either app, so the accurate statement of BR-10 is "the 8 PO&P-bearing fixed lines **and** the
    other-acceptable groups", not "all eleven lines" and not "other-acceptable only". Scope re-derived
    from the legacy source 2026-08-26.
  - **The app is correct:** `Schedule3Service.appendFixedLineCheckErrors` reproduces the legacy rule
    exactly; we covered it anyway (S12 asserts the fixed line is not flagged and the schedule passes).
    A paperwork mismatch, not a bug — and the reason **DIV-2** was raised and then retracted.
  - **FIXED 2026-08-26 — the source documents now state BR-10 correctly.** Corrected in the `ilcr-bmad`
    planning repo at the repo owner's direction, after the full legacy re-derivation:
    - `UC-SCH3-001.md` and `-detailed.md` BR tables — BR-10 restated as "not enforced **anywhere it
      would otherwise apply** — the eight PO&P-bearing fixed lines (27, 28, 30, 31, 32, 34, 35, 36)
      **and** the other-acceptable-cost rows", with the real citations
      (`Schedule3CheckStatus.isHarvestCostGreaterThanPopCost:64-72`, `isScheduleValid:78-103`,
      `Schedule3MB.checkStatus:312-316`, `CheckStatusMB:453-460`) replacing the single bean reference.
    - `-detailed.md` step 24 and the AF5 Check Status step — both widened from "other-acceptable-cost
      rows" to both arms.
    - both check-status message tables (`-detailed.md`, `-technical.md`) — a footnote now says BR-10
      applies to **every** "both keys" row, not only `Subtotal Other Costs`, since those rows key off the
      flag the override forces to pass.
    - `-technical.md` BR-03 rule row — now names the eight lines, records that BR-10 suppresses all
      eight, and adds the `CostType.java:22` default-`true` semantics (a missing value yields only
      `missingRequiredFieldMsg`, never a harvest-vs-PO&P error).
    - `-technical.md` other-acceptable rule row — now states the comparison is **per row**, one message,
      and that the summed subtotal is never compared (`CheckStatusUtil:101-110`).
    - `-slices.md` BR-10 row, and a SCOPE NOTE on the `UC-SCH3-001-S12.feature` slice recording that it
      covers one arm of a broader rule, with a pointer to this suite's scenario which covers both.
  - **No app or test change followed, by design:** the app was already correct and S12 already asserted
    both arms. This entry was always a paperwork defect, and the paperwork is what moved.
  - **Status:** CLOSED (source documents corrected) 2026-08-26. Found 2026-08-25 as the by-product of
    DIV-2's retraction; scope re-derived from the legacy source and the docs fixed 2026-08-26.
  - **Test (covers it anyway):** `check-status.feature` (S12 and its mirror) — GREEN.

The 26 slices otherwise reconcile
cleanly against the slice catalogue's own Gap Analysis (62 fields, 12 business rules — **BR-12** "Check
Status evaluates what is on screen, including unsaved edits" was recovered upstream 2026-08-27 by ilcr-bmad
PR #92 together with its two arms S25/S26 — 5 preconditions,
4 combinations) and against the technical sidecar's message catalog. Every item that catalogue
deliberately excluded was re-checked against the new app rather than inherited — see coverage.md,
"Deliberately excluded by the slice catalogue".

**Verified — not a defect:**

- **VER-3 — "Schedule 3 doesn't show the save-first modal like Schedule 1 does." It does. The mill-year it
  was tested on already had a SAVED Schedule 3. Re-checked live 2026-08-27; DIV-3 stays CLOSED.**
  - **How the confusion arises, and it will arise again:** on **727/2021** Schedule 1 has never been saved
    (`revisionCount` absent) while Schedule 3 **has** been saved — that mill-year carries a category-3
    summary seeded by `draft-anchors.sql`, and at the time this was investigated it was this suite's
    `retry` anchor, re-saved by every `save-error` run (43 revisions). So on one screen the gate fires and
    on the other it correctly does not, on what looks like the same mill-year. That anchor is also the
    mill-year defect #296 reproduces, which is why sch1's `no-schedule` was deliberately re-grounded onto
    it — so it is the single most likely pair to be compared by hand.
    - **Update 2026-08-31 (PR #402 review):** 727/2021 now carries the READ-ONLY `check-empty` anchor
      instead. `retry` was moved to 22050/2019 because it is mutating and its save carries a Crown Timber
      volume, which arms Schedule 1's BR-09 volume pre-fill on a mill-year sch1 pins as "every Schedule 1
      amount is blank" (S21). The confusion this entry describes is unchanged — Schedule 3 is still saved
      there and Schedule 1 still is not — only the revision count is now stable rather than climbing.
  - **Evidence the gate is present on Schedule 3** (captured DOM from the S19 red on the genuinely
    never-saved `never-started` anchor, 24051/2015 = *Mill 8888 CGI TEST MILL8*): `dialog "Save required"` →
    `heading "Save required"` → `paragraph: The schedule has to be saved before opening other costs`. Note
    that S19 fails on the *wording*, not on the dialog's absence — the failure is itself the proof the modal
    renders. Confirmed by hand by the repo owner the same day.
  - **Legacy re-read in full, both link blocks** (`schedule3.xhtml:265-278` and `:291-304`) — perfectly
    symmetric, three variants each, and the app matches all three: `…EditsEnabledAlert`
    (`!disableReportEdits() and !isScheduleOpen()`) → the passive modal, no navigation; `…EditsEnabled`
    (`… and isScheduleOpen()`) → the "Leave Schedule 3" confirm, then navigate; `…EditsDisabled`
    (`disableReportEdits()`) → navigate with no confirm, covered by `render-states` `@p2 @S15`. The ONLY
    thing legacy has that the app lacks is ALT-003's separate wording, which is **DIV-7** — that entry
    stands, and this is not a reason to widen it.

- **HOW TO TELL A NEVER-SAVED SCHEDULE FROM A SAVED ONE — you cannot do it by looking at the form.**
  Since #296 an unsaved schedule renders a full blank editable form *on purpose*, and a saved schedule can
  be blank too (this suite's cleanup restores every mutating anchor to empty). Three reliable tells:
  1. **The Delete button.** Enabled = saved; **greyed out = never saved**. Defect #292 decision 1 kept
     legacy's rule (no delete without a persisted record) but changed the mechanism from legacy's
     *not rendered* to *disabled*, so the button is always there — look at whether it is live. A
     screen-reader-only hint, `Available once the schedule is saved`, renders only in the
     editable-but-unsaved state. Asserted on both schedules (`the Schedule 3 Delete action is not offered`
     → `toBeDisabled()`).
  2. **Click a sub-page count link.** "Save required" modal and you stay put = never saved. The
     "Leave Schedule 3" discard confirm, then it navigates = saved.
  3. **Definitive — ask the API.** `GET /api/v1/schedule3?millId=<id>&year=<yyyy>` and read
     `revisionCount`: absent = never saved, any number = saved. **0 counts as SAVED** (a freshly seeded
     summary carries 0), which is why the app's predicate is a loose `revisionCount != null`
     (`utils/schedule.ts isScheduleSaved`) and not a truthiness test. The backend omits null fields
     (`default-property-inclusion: non_null`), so an unsaved document serves `undefined` — a strict `!==`
     here was defect #292. In the DB it is simply whether a category-`3` `THE.ILCR_REPORT_SUMMARY` row
     exists for that mill-year.

- **The Crown column, both subtotals and all three $/m³ figures move as you type, before Save.** This
  looked like the derived figures being computed on the client (which would contradict "the server owns
  every stored figure"). It is a deliberate display-only mirror added for defect #291 so the read-only
  cells track entry the way legacy's per-field AJAX did; the Save response replaces every figure, and
  nothing derived is ever sent on a write. The happy path asserts the mirror *before* Save and the
  server's own figures *after* it, so the two can never silently diverge. (Verified 2026-08-25.)

- **Scaling Expense shows a PO&P amount nobody typed.** Its PO&P is derived server-side from the two
  timber volumes — `round(popTimberVolume ÷ (popTimberVolume + crownTimberVolume) × scalingHarvest)`,
  3,750 for the happy path's 50,000 / 150,000 / 15,000 — and is deliberately read-only. Legacy did the
  same (`Schedule3DO.getScalingExpense`); it is not a stray write. (Verified 2026-08-25.)

- **Annual Rents and Silviculture Admin Costs show a blank PO&P cell while the API returns 0.** Legacy
  captured no PO&P for those two lines at all (BR-04, a hidden input), so the page renders an em dash
  rather than the backend's 0 — showing "0" would claim a value the reporter never entered. Both the
  blank cell and the absent input are asserted. (Verified 2026-08-25.)

- **A mis-grouped number like "9,9,9" is accepted as 999.** Found while probing for a non-numeric
  rejection. The page strips every comma before parsing, so mis-grouped digits pass; the app's own
  stricter `parseDecimalInput` (used for the derived-figure mirror) would reject them. Legacy's
  `DecimalFormat.parse` was laxer still — it silently accepted junk suffixes — so this is not a
  regression, and the genuinely non-numeric case IS covered. (Verified 2026-08-25.)

- **The suite's cross-domain anchor guard was passing without checking most anchors.** While adding
  Schedule 3, `preflight/sch4-anchors.setup.ts`'s "Cross-domain anchors are globally distinct" check
  reported only one of this domain's 15 shared mill-years. It matched only the object-literal
  `{ millId, year }` form, so it had never seen sch4's own 48 table anchors either (they use the
  positional `at(MILL_x, id, year)` builder). The scan now covers both forms and every deliberate share
  is declared with its reason. This is the same silent-under-scanning class as that file's own VER-8
  note, one level down — worth remembering as a pattern, not just a fix. (Verified 2026-08-25.)
  **It happened a third time, for a third reason:** all three patterns required `millId` and `year` to be
  ADJACENT, and `sec` interleaves two properties between them, so that whole domain had always been
  invisible. Fixed 2026-08-28 by moving the scan to `preflight/anchor-keys.ts` and pairing on the enclosing
  braces; recorded in full as **sch4's VER-9**, together with the CI-seed drift found alongside it.
