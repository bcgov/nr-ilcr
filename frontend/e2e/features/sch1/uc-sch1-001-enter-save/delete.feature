# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S13.feature
# (legacy JSF/PrimeFaces). Delete the whole Schedule 1 (BR-08): the React/Carbon app confirms via a Carbon
# danger Modal ("Delete schedule" → primary "Delete"), then DELETE /api/v1/schedule1 removes the summary +
# every detail row (Schedule1Repository.deleteSchedule) and resets IN PLACE to the blank, EDITABLE form
# with the API's verbatim SUC-002 text (AD-8). Since defect #296 an unsaved schedule is a valid state
# (GET serves the 200 empty editable document; the first save re-creates the summary), so the post-delete
# page is the same blank form S21 opens on — usable for immediate re-entry (legacy AF1), with only the
# Delete gate closed (nothing saved, nothing to delete — the #292 rule).
#
# Delete is DESTRUCTIVE, and although a save can now re-create a summary (#296), it cannot re-create the
# seeded rows verbatim (PKs, audit columns) — so this scenario still snapshots the target's rows to the
# E2E_BAK_SCH1_* tables before the delete and re-inserts them verbatim on teardown
# (scripts/sch1_db_restore.py; round-trip proven byte-identical). It runs against a DEDICATED target
# (25052/2016) that no other scenario touches, so it stays parallel-safe.

@sch1 @UC-SCH1-001 @delete
Feature: Report Average Cost of Logging (Schedule 1) — delete the whole Schedule 1
  As a mill reporter
  I want to delete an incorrectly recorded Schedule 1
  So that I can clear it and start over for the mill and reporting year

  @S13 @p1
  Scenario: Delete Schedule 1 after confirming the prompt
    Given a saved editable Schedule 1 exists for the delete target
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I delete Schedule 1 and confirm the prompt
    Then I should see the message "Data deleted successfully"
    And the Schedule 1 should no longer be saved
    And the Schedule 1 input form is displayed
    And every Schedule 1 amount is blank
    And the Schedule 1 Delete action is not offered
