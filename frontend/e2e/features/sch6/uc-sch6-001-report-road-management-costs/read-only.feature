# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S17.feature
# (legacy JSF/PrimeFaces). S17: once the Schedules 1-10 report leaves Draft, Schedule 6 is displayed
# but cannot be changed — every control locked, and everything already reported still on screen.
#
# WHAT MAKES A SCHEDULE READ-ONLY HERE IS role x status, NOT status alone — and that is worth stating
# because it is the one way this whole slice could silently invert. `editable` is
# `caller.allows(trackStatus)` (Schedule6Service:126), and the rule is:
#
#     SUBMITTER  edits at Draft (D) only
#     ADMIN      edits at Submitted (S) and Verified (V)
#         ScheduleEditability:63-64 — administrator is deliberately NOT a superset of submitter,
#         because the statuses form a hand-off chain: the ministry is read-only while the mill still
#         owns its Draft.
#
# This suite runs as ILCR_SUBMITTER (pages/common/mockUser.ts), which is what every feature file's
# "As a Licensee" claims, so the anchor is seeded Submitted and the page is locked. Were the suite ever
# switched to ADMIN, that same anchor would become EDITABLE and this scenario would be asserting
# disabled controls on a page that is legitimately editable. So the Given re-asserts `editable: false`
# from the API rather than trusting the status code, and preflight asserts it too.
#
# THE ADD SURFACE IS RE-GROUNDED — the only material difference from the legacy text.
# S17's source lists six Add-FORM fields as disabled (`tsaNumberOneMenu`, `tflNumber`,
# `tsbNumberOneMenu`, `vol`, `cos`, `comAdd`), because legacy rendered that panel inline and always
# present, so "disabled" was the only available lock. The React page renders the panel ONLY while its
# toggle is open (`{showAdd && <AddPanel/>}`, index.tsx:971) and disables the toggle itself
# (`disabled={entryLocked}`, :907). So on a locked schedule the panel is not in the DOM at all and
# cannot be opened.
#
# Asserting those six fields are "disabled" would therefore FAIL against elements that do not exist,
# and asserting them merely absent would pass vacuously against any page at all — the same vacuous-pass
# trap S06-S08 records from the other direction. The faithful pair is: the toggle EXISTS and is
# DISABLED, and the panel is ABSENT. That is strictly stronger than legacy's six inert fields, because
# the entry surface is unreachable rather than merely inactive.
#
# The Gherkin's disabled-FIELD claim is still asserted, on the rows: the row editor shares the very
# same `RoadRecordFields` component as the Add panel (one definition, index.tsx:240-364), and its
# fields ARE present and disabled (`disabled={entryLocked}`, :1020). So the six field names are
# covered where they actually exist on this page. Each row's Delete is asserted disabled too — it is
# gated on the same `entryLocked` and is the control that would do the most damage if it were live.
#
# BOTH INSTANCES OF Save AND Check Status ARE ASSERTED, which the Gherkin asks for explicitly by naming
# saveButton0/saveButton1 and checkStatusButton0/checkStatusButton1. The page mirrors legacy's layout —
# each pair renders above the schedule and again below the General Comment (`actionBar`,
# index.tsx:880-915) — and a page that disabled only the top pair would leave a live Save on a locked
# schedule while passing a first-instance-only check. The count is asserted before the disabled state,
# so a page rendering only one could not satisfy "every instance" trivially.
#
# TWO SEEDED RECORDS, ONE PER BR-02 BRANCH, AND THE TOTALS ARE THE REASON.
# "the existing records, running totals, and general comment remain visible" is the half of this slice
# that needs DATA, and one record would not have been enough: with a single row the totals equal that
# row's own figures, so a page that echoed one row into the totals strip would pass. The fixture is
# therefore two records whose figures make the total rate differ from both:
#
#     TSA 01 / block 01B : 10,000 / 30,000  -> 3.00   (RMG 15, derived from the supply block)
#     TFL 48             : 20,000 / 90,000  -> 4.50   (RMG 10, derived from the RoadGroupLookup table)
#     totals             : 30,000 / 120,000 -> 4.00
#
# Every division is exact, so nothing turns on rounding; 4.00 is neither record's rate; and the two
# rates differ from each other, so the per-row cells cannot be transposed unnoticed. One TSA row and
# one TFL row also prove the read-only render for both classification branches, which derive their RMG
# by different routes. Two rows is deliberately UNDER the 5-row page size: the totals' pagination scope
# is unresolved in legacy source (defects.md SPEC-3), and six rows would force this slice to answer an
# open question instead of testing the read-only render.
#
# THE RECORDS ARE SEEDED IN SQL BECAUSE THE APP REFUSES TO CREATE THEM — and that refusal is this very
# slice's subject: every write to a non-Draft document is rejected. So they arrive through
# `real-test-data-patches/sch6/view-mode-road-records.sql`, mirrored into the CI seed in the same
# change, with `ROAD_MAINTENANCE_REPORT_ID` registered in the parity gate's `parentsByColumn` (the
# gate's own header had named it as the next FK to expect). This is the first ROAD_MAINTENANCE_REPORT
# content the seed has ever carried; every other sch6 anchor is empty at rest.
#
# NO recordId IS PINNED ANYWHERE. The local patch draws ids from `ILCR_REPORT_COMMON_SEQ` while the CI
# seed uses explicit 3100/3101, so the ids genuinely differ between environments — and every row
# locator is built from `row-<recordId>-*`. The Given resolves each record's id AND its display ordinal
# from the served document by matching the per-record COMMENT, which is stable everywhere. Pinning an
# id would pass locally and fail in CI looking nothing like the cause.
#
# THIS SCENARIO WRITES NOTHING, so it needs no cleanup registration and cannot collide with anything:
# it is a pure GET against a cell no other domain pins (the only 2024+ anchors anywhere are sch6's).

@sch6 @UC-SCH6-001 @read-only
Feature: Report Road Management Costs (Schedule 6) — the schedule is read-only once the report leaves Draft
  As a mill reporter
  I want Schedule 6 to be locked once the Schedules 1-10 report is no longer in Draft
  So that reported road maintenance costs cannot be changed after the report has been handed over

  @p1 @S17
  Scenario: A non-Draft report renders Schedule 6 complete but locked
    Given the Schedule 6 report for that mill and year is not in Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    Then every Schedule 6 entry control is disabled
    And every saved record's own fields are disabled
    And the existing road records remain visible with their stored figures
    And the running totals remain visible
    And the general comment remains visible
