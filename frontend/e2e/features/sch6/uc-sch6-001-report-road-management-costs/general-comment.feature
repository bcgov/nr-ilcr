# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S04.feature
# (legacy JSF/PrimeFaces). S04 enters the schedule-level general comment and saves it.
#
# WHAT RE-GROUNDING CHANGED
#  * `schedule6Form:comments` -> `#general-comments`, a Carbon TextArea labelled "General Comments".
#    Addressed by ID rather than by label ON PURPOSE: a record's own field is also labelled
#    "Comments", and these are DIFFERENT COLUMNS with different caps — the general comment lands in
#    ROAD_MAINTENANCE_REPORT.COMMENTS (4000 wide, capped at 3500 in the UI) while a record's lands in
#    ILCR_COST_REPORT_DETAIL.COMMENTS (400) (deviation E). Conflating them is easy and wrong.
#  * `schedule6Form:saveButton0` -> the page-level "Save", which fans the records and the comment out
#    in a single PUT.
#
# THE THING THE LEGACY SCENARIO DOES NOT MENTION, and which shapes this whole slice: saving a general
# comment on an otherwise EMPTY schedule makes the backend insert a bare BR-09 PLACEHOLDER row to
# carry it (Schedule6Service:425) — there is no record to hang it on. Two consequences:
#
#  1. CLEANUP IS NOT A RECORD DELETE. Clearing the comment when it is the only stored thing REMOVES
#     the placeholder (Schedule6Service:428-437, legacy generalCommentRemovedLastRecord), so teardown
#     is a comment-clearing PUT and the anchor is genuinely empty again afterwards. The cleanup then
#     reads BOTH halves back — comment gone AND no rows left — because a PUT that cleared the text but
#     stranded the placeholder would pass a comment-only check while leaving the anchor dirty, which
#     the next run would blame on somebody else.
#  2. THE PLACEHOLDER MUST NOT BE SERVED AS A RECORD, which this scenario asserts. The read side
#     excludes any row whose classification is entirely blank (Schedule6Service:470). If that
#     exclusion broke, the screen would grow a phantom row with no area type, no supply block and no
#     cost, and Check Status would then report it as a failing record. The totals are asserted at zero
#     for the same reason — a phantom row would drag them off zero. S18 (the lone-comment placeholder
#     slice) builds directly on this behaviour.
#
# ANCHOR: 13050/2024 (minted; see real-test-data-patches/sch6/draft-anchors.sql). Its own cell because
# it WRITES, and a writer cannot share a (mill, year) under `fullyParallel`.
#
# NOT COVERED HERE (see coverage.md): the 3500-character cap on this field rides the validation
# slices.
#
# ---------------------------------------------------------------------------------------------------
# S18 (below) — THE SAME PLACEHOLDER, NOW AS THE SUBJECT.
# Re-grounded from UC-SCH6-001-S18.feature. S04 CREATES the comment-only state as a side effect; S18
# opens a page that is ALREADY in it and asserts what a reporter sees. So its Given stores the comment
# through the API rather than by typing: the state has to exist before the browser is driven, and
# entering it through the UI would re-test S04 and then assert S18 on a page that had never reloaded.
#
# TWO RE-GROUNDINGS, both verified against the running app on this slice's own anchor and both
# recorded as defects.md VER-7.
#
#  1. THE THIRD TOTAL IS BLANK, NOT ZERO. The Gherkin says `totalVol`, `totalCos` and `totalCal` all
#     "show zero". Probed: totalVolume 0, totalCost 0, totalCostPerVolume **null**. Volume and cost are
#     real zeros that must still show; the RATE is null because 0/0 is undefined, and `ratioMask(null)`
#     renders the empty string — index.tsx:86-95 states the distinction outright. Asserting "0" on the
#     third would fail; asserting it loosely would hide real behaviour. So two zeroes and a blank.
#
#  2. CHECK STATUS ANSWERS **MET**, which is recorded deviation (d) — legacy reported ISSUES for a
#     comment-only mill/year. Probed: outcome "MET" with the schedule banner and `records: []`. This is
#     the assertion that proves the placeholder exclusion holds END TO END: a row whose classification
#     is entirely blank is not a road record (Schedule6Service:779-784), so it is excluded from both
#     the served document AND the check candidates. Were that filter to break, the schedule would
#     report "a phantom failing row, since a placeholder has no area type, no supply block and no
#     cost" — the service's own words. S04 asserts the exclusion at the API; this asserts the verdict a
#     reporter actually reads.
#
# ANCHOR: 25050/2024, its own cell because S18 WRITES (the comment). Empty at rest for the same reason
# S04's is — clearing the comment removes the placeholder with it, which is what its cleanup PUT does.

@sch6 @UC-SCH6-001 @general-comment
Feature: Report Road Management Costs (Schedule 6) — enter or update the schedule general comment
  As a mill reporter
  I want to record an overall note for the mill and reporting year
  So that context that belongs to no single road record is still captured on the schedule

  @p1 @S04
  Scenario: Enter a general comment for the schedule and save it
    Given the Schedule 6 anchor "general-comment" is an editable Draft with no road records
    And I will set the schedule general comment
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    Then the Schedule 6 record list shows no records
    When I enter the schedule general comment
    And I save the schedule
    Then I should see the message "Data saved successfully"
    And the general comment field retains the text
    And the general comment is persisted without adding a road record

  @p1 @S18
  Scenario: A schedule holding only a general comment shows the empty-records placeholder
    Given the Schedule 6 anchor "comment-only" is an editable Draft with no road records
    And only a general comment is stored for that mill and year
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    # The placeholder, not a phantom row: the BR-09 row carrying the comment has a blank
    # classification, and the read side excludes it from the served records.
    Then the Schedule 6 record list shows no records
    And the general comment field shows the stored comment
    # Two zeroes and a BLANK — the rate is null because 0/0 is undefined. See the header (VER-7).
    And the schedule totals are at rest
    When I run Schedule 6 Check Status
    # Deviation (d): legacy reported ISSUES for a comment-only mill/year; here the placeholder is
    # excluded from the check candidates too, so the schedule passes rather than reporting a phantom
    # failing row with no area type, no supply block and no cost.
    Then I should see the message "All requirements for this schedule have been met"
