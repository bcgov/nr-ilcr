# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S08.feature
# (legacy JSF/PrimeFaces). S08 — the legacy "save the schedule before opening Other Costs" gate, COVERED
# for the first time on 2026-08-27.
#
# WHY THIS SLICE WAS `not-applicable` FOR A MONTH, AND WHY THAT ENDED. Legacy refused to open the Other
# Costs sub-page until Schedule 1 had been saved once, alerting "The schedule has to be saved before
# opening other costs" (`schedule1.xhtml:497`, on the link variant rendered only when
# `!schedule1MB.isScheduleOpen()`). The rewrite's equivalent branch was genuinely DEAD CODE: it was gated
# on `!data`, while `if (!data) { return null }` sat ABOVE the button that reaches it, so firing the branch
# needed `data` to be null and non-null at once. defects.md GAP-3 recorded that, proved it, and handed it
# to the Schedule 1 dev as "delete the branch or unit-test it — not an E2E concern".
#
# He did neither: defect #296 REWIRED it. An unsaved Schedule 1 now serves a 200 empty editable document,
# so `data` is truthy on a never-saved schedule and the old condition could never fire again — while the
# sub-page controllers still require a summary (`validateScheduleViewable`, deliberately kept, #296 D1).
# The gate is now `if (!data || !isScheduleSaved(data))` (`index.tsx:288`), and Rylan's own commit comment
# at `:280-287` cites this slice by name. So the branch is LIVE, reachable by an ordinary user action, and
# was covered by nothing at any level until this file.
#
# WHAT IS PINNED. The verbatim legacy message, and that the reporter STAYS on Schedule 1 — the whole point
# of the gate is not to send anyone to a sub-page that would 404. The modal is `passiveModal` (close-only),
# so there is no Continue path to assert. Mirrors `sch3`'s `save-first-gate.feature`, which covers the same
# behaviour on the other schedule #296 touched.
#
# ANCHOR. The existing read-only `no-schedule` render-state anchor (16050/2016) — a Draft, active mill-year
# that has never been saved, already asserted by preflight and already used by S21. This scenario clicks a
# link that refuses to navigate and writes nothing, so the anchor keeps its never-saved state and no
# cleanup is needed.

@sch1 @UC-SCH1-001 @save-first-gate
Feature: Report Average Cost of Logging (Schedule 1) — Other Costs requires a saved schedule
  As a mill reporter
  I want to be told to save my Schedule 1 before itemizing Other Costs
  So that I am not sent to a sub-page that cannot exist yet

  @S08 @p1
  Scenario: Opening Other Costs from a never-saved Schedule 1 asks me to save first
    Given the Schedule 1 render-state anchor "no-schedule" is selected
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 1
    Then the Schedule 1 input form is displayed
    When I try to open the Schedule 1 Other Costs sub-page without saving
    Then Schedule 1 tells me to save first
    And I am still on Schedule 1
