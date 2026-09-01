# S18 / S19 — the legacy save-first gate on the two cost sub-pages, COVERED for the first time on
# 2026-08-26 because the defect #296 fix made the state reachable.
#
# WHY THESE SLICES WERE `not-applicable` UNTIL NOW. Legacy refused to open either cost sub-page until the
# schedule had been saved once, warning "The schedule has to be saved before opening other costs"
# (`schedule3.xhtml:265-267`, gated on `!schedule3MB.isScheduleOpen()`). Before #296 the rewrite could not
# reach that state at all: an unsaved Schedule 3 404'd on the PARENT page, so there was no screen from
# which to click a sub-page link. coverage.md dispositioned S18/S19 `not-applicable` on exactly that
# reasoning, and defects.md DIV-3 recorded it.
#
# WHAT #296 CHANGED. The parent page now serves a 200 empty EDITABLE document (that fix closed DIV-1), and
# the sub-pages DELIBERATELY kept their 404 — `Schedule3Service.java:1136`: "both sub-pages are reachable
# only from a SAVED Schedule 3 (legacy ALT-001 …), so 'no summary' there really is not-found". The client
# gates ahead of that 404 with a passive "Save required" modal carrying the verbatim legacy string
# (`components/schedule3/index.tsx:47`, `isScheduleSaved(data)` at `:208`). So the legacy behaviour is back,
# and now testable.
#
# WHAT IS PINNED. The verbatim message and the fact that the reporter STAYS on Schedule 3 — the gate must
# not navigate to a sub-page that would 404, which is the failure this guards against. The modal is
# `passiveModal` (close-only), so there is no Continue path to assert.
#
# THE TWO SCENARIOS DIFFER IN THE MESSAGE THEY EXPECT, AND THAT IS THE POINT. Legacy wrote a separate
# string per link (`:267` vs `:293`); the app has one. S18 passes, S19 is a deliberate red for DIV-7,
# tracked as bcgov/nr-ilcr#373. Both still assert the gate FIRES and that navigation is refused, so the
# behavioural guarantee is covered either way — only the wording is outstanding.
#
# ANCHOR. `never-started` (24051/2015) is the suite's only never-saved anchor and is deliberately
# un-patched. Read-only: this scenario clicks a link that refuses to navigate and saves nothing, so the
# anchor keeps its "no summary" state and keeps proving the create-on-save path for `no-create.feature`.

@sch3 @UC-SCH3-001 @save-first-gate
Feature: Report Forest Management Administration Costs (Schedule 3) — the cost sub-pages require a saved schedule
  As a mill reporter
  I want to be told to save my Schedule 3 before itemizing costs on a sub-page
  So that I am not sent to a page that cannot exist yet

  @p1 @S18
  Scenario: Opening Other Costs from a never-saved Schedule 3 asks me to save first
    Given the Schedule 3 render-state anchor "never-started"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3 expecting a guard
    Then the Schedule 3 form is displayed for entry
    When I try to open the Schedule 3 "Other Costs" sub-page without saving
    Then Schedule 3 tells me to save first
    And I am still on Schedule 3

  # DIVERGENCE — this scenario is DELIBERATELY RED. It reproduces defects.md DIV-7, tracked upstream as
  # bcgov/nr-ilcr#373, and stays failing until legacy's second wording is restored. Do not weaken it, skip
  # it, or "fix" it by asserting the current behaviour: the failing state IS the tracking signal. Filter it
  # out of a fresh-failures run with `npm run test:gate`.
  #
  # The gate FIRES correctly here — only its wording is wrong, which is why S18 above is green. Legacy
  # carried two distinct strings, one per link: `schedule3.xhtml:267` "The schedule has to be saved before
  # opening other costs" for Subtotal Other Costs, and `:293` "The schedule has to be saved before opening
  # Unacceptable costs" for this one. The rewrite routes both links through one generic handler holding one
  # constant (`index.tsx:272` -> `:47`), so this link shows the Subtotal Other Costs string.
  @discovered-divergence @p2 @S19
  Scenario: Opening Included Unacceptable Costs from a never-saved Schedule 3 asks me to save first [DISCOVERED DIVERGENCE — the gate shows the Subtotal Other Costs wording; defects.md DIV-7 / issue #373]
    Given the Schedule 3 render-state anchor "never-started"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3 expecting a guard
    Then the Schedule 3 form is displayed for entry
    When I try to open the Schedule 3 "Included Unacceptable Costs" sub-page without saving
    Then Schedule 3 tells me to save first before Unacceptable costs
    And I am still on Schedule 3
