# Re-grounded from UC-SCH3-001-S08.feature (AF4) — delete the whole Schedule 3.
#
# WHAT RE-GROUNDING CHANGED
#  * The confirmation is a Carbon danger Modal ("Delete schedule" -> primary "Delete"), not a
#    PrimeFaces `confirmDialog` and not a native browser dialog. Its body text is unchanged.
#  * Legacy redisplayed "an empty Schedule 3" after the delete. The rewrite has NO create path — the
#    DELETE removes the summary row itself, so the schedule stops existing entirely and re-opening it
#    reports "Schedule not found." That is asserted as the app actually behaves, and the wider
#    consequence (a reporter cannot start a Schedule 3 at all) is tracked separately as DIV-1.
#  * Because the delete is DESTRUCTIVE and unrecoverable through the API, this scenario owns a dedicated
#    (mill, year) and its teardown re-applies the seed patch to put the summary back
#    (steps/sch3/schedule3DbRestore.ts). Never share this anchor.
#  * The cancel arm runs on the read-only `validate` anchor: cancelling writes nothing, so it needs no
#    anchor of its own and proves the no-op with the mutation spy.

@sch3 @UC-SCH3-001 @delete
Feature: Report Forest Management Administration Costs (Schedule 3) — delete the whole schedule
  As a mill reporter
  I want to delete a Schedule 3 I recorded in error
  So that its administration costs no longer count towards the mill's reported overhead

  @p1 @S08
  Scenario: Delete Schedule 3 after confirming the prompt
    Given the Schedule 3 anchor "delete"
    And Schedule 3 has been saved with every fixed line and both timber volumes
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I delete Schedule 3 and confirm the prompt
    Then I should see the message "Data deleted successfully"
    And Schedule 3 no longer exists for that mill and year
    # RE-GROUNDED 2026-08-26 (defect #296): the page no longer strands the reporter on a read-only blank.
    # It serves an empty EDITABLE form so they can start again immediately, with Delete gated off because
    # there is nothing left to delete.
    And the Schedule 3 form is displayed for entry
    And the Schedule 3 Delete action is not offered

  @p2 @S08
  Scenario: Cancelling the delete confirmation changes nothing
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I note the Schedule 3 write count
    And I open the delete confirmation
    Then the delete confirmation asks "This will delete the current record. Do you want to continue?"
    When I cancel the delete confirmation
    Then no Schedule 3 write was attempted
    And the Schedule 3 optimistic-lock token has not moved
    And the Schedule 3 actions are enabled
