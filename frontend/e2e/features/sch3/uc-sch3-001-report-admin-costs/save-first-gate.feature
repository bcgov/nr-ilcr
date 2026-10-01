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
# (`components/schedule3/index.tsx`, `ALT_SAVE_BEFORE_OTHER_COSTS`, gated on `isScheduleSaved(data)` in
# `openSubPage`). So the legacy behaviour is back, and now testable.
#
# WHAT IS PINNED. The verbatim message and the fact that the reporter STAYS on Schedule 3 — the gate must
# not navigate to a sub-page that would 404, which is the failure this guards against. The modal is
# `passiveModal` (close-only), so there is no Continue path to assert.
#
# THE TWO SCENARIOS DIFFER IN THE MESSAGE THEY EXPECT, AND THAT IS THE POINT. Legacy wrote a separate
# string per link (`:267` vs `:293`) and the app had only one, so S18 passed while S19 was a deliberate red
# for DIV-7 / bcgov/nr-ilcr#373. FIXED 2026-09-18 — the app now carries both strings and selects by the
# link that was clicked, so both scenarios are green with their assertions untouched. They keep separate
# steps on purpose: S18 and S19 once SHARED one step asserting ALT-002's text, which is precisely how the
# suite passed over this defect for a day. Both also still assert the gate FIRES and that navigation is
# refused, which is the behavioural guarantee and was never the part in doubt.
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

  # EX-DIVERGENCE — FIXED 2026-09-18, and it went green on its own. This scenario was a deliberate red
  # for defects.md DIV-7 / bcgov/nr-ilcr#373: the gate FIRED correctly but carried the WRONG wording, which
  # is why S18 above was green throughout. Legacy wrote two distinct strings, one per link, in each link's
  # own `onclick` — `schedule3.xhtml:267` "The schedule has to be saved before opening other costs" for
  # Subtotal Other Costs, and `:293` "The schedule has to be saved before opening Unacceptable costs" for
  # this one — and the rewrite routed both links through one generic handler holding one constant.
  #
  # The fix added the second constant and widened the single gate flag into a nullable blocked-route, the
  # idiom the component already used for its "Leave Schedule 3" confirm, so the modal body selects its
  # message from the link that was clicked (`components/schedule3/index.tsx`, ALT_SAVE_BEFORE_OTHER_COSTS /
  # ALT_SAVE_BEFORE_UNACCEPTABLE). NOT ONE ASSERTION, STEP OR FIXTURE WAS EDITED — only the
  # `@discovered-divergence` tag and the title marker came off, which is the whole design of a red that
  # asserts the correct behaviour. The two strings stay byte-verbatim and DELIBERATELY inconsistent with
  # each other (capital U on "Unacceptable", lowercase "costs"); legacy's inconsistency is the contract, so
  # do not harmonise them here or in the fixtures.
  @p2 @S19
  Scenario: Opening Included Unacceptable Costs from a never-saved Schedule 3 asks me to save first
    Given the Schedule 3 render-state anchor "never-started"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3 expecting a guard
    Then the Schedule 3 form is displayed for entry
    When I try to open the Schedule 3 "Included Unacceptable Costs" sub-page without saving
    Then Schedule 3 tells me to save first before Unacceptable costs
    And I am still on Schedule 3
