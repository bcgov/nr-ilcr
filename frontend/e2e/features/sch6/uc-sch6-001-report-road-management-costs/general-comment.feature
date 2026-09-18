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
# slices. The lone-comment schedule's effect on Check Status ordinals and its MET/ISSUES outcome is
# S18's subject — recorded deviation (d), where a comment-only mill/year deliberately flips legacy's
# ISSUES to MET.

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
