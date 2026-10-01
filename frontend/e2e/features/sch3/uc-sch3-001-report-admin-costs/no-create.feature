# DIV-1 — FIXED 2026-08-26 by bcgov/nr-ilcr#296 (`60c24dd`, "fix(schedules-1-3): open a blank,
# usable form when nothing is saved yet"). This scenario was a deliberate `@discovered-divergence` RED
# from 2026-08-24; the tag is now retired and it stands as an ordinary regression guard. NO ASSERTION WAS
# EDITED to make it pass — it always asserted the correct behaviour, which is the whole point of a tracked
# red, and it went green on its own when the fix landed.
#
# WHAT IT GUARDS
# A reporter can START a Schedule 3. Legacy created the summary row on the FIRST Save — what
# `Schedule3MB.isScheduleOpen()` reported on — and the rewrite now matches: `Schedule3Service.getSchedule3`
# serves a 200 empty EDITABLE document when no category-3 summary exists, and Save creates it
# (create-on-absent). Before the fix every operation resolved the summary first and answered 404
# "Schedule not found.", so 87 Draft mill-years out of the extract's 118 could not be entered at all.
#
# WHAT DID *NOT* CHANGE, and why the suite still needs its seed patch: the two cost SUB-PAGES deliberately
# still 404 without a summary (`Schedule3Service.java:1136` — "both sub-pages are reachable only from a
# SAVED Schedule 3"), which restores legacy's ALT-001 save-first gate. See `save-first-gate.feature` (S18,
# S19) for the coverage that fix made possible, and defects.md DIV-1/DIV-3.
#
# This anchor is deliberately NOT patched and nothing writes to it, so it keeps proving the
# create-on-save path rather than the seeded one.

@sch3 @UC-SCH3-001 @no-create
Feature: Report Forest Management Administration Costs (Schedule 3) — starting a schedule that was never saved
  As a mill reporter
  I want to open Schedule 3 for a reporting year I have not yet entered
  So that I can record the mill's administration costs for the first time

  @p0 @S16
  Scenario: A Draft mill-year whose Schedule 3 was never started opens an empty enterable form
    Given the Schedule 3 render-state anchor "never-started"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3 expecting a guard
    Then the Schedule 3 form is displayed for entry
    And I should not see the message "Schedule not found."
