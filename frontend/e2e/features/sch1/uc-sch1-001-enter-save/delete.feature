# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S13.feature
# (legacy JSF/PrimeFaces). Delete the whole Schedule 1 (BR-08): the React/Carbon app confirms via a Carbon
# danger Modal ("Delete schedule" → primary "Delete"), then DELETE /api/v1/schedule1 removes the summary +
# every detail row (Schedule1Repository.deleteSchedule) and redisplays an empty, read-only schedule with
# the API's verbatim SUC-002 text (AD-8).
#
# Delete is DESTRUCTIVE and the app has no create-on-open path, so this scenario snapshots the target's
# rows to the E2E_BAK_SCH1_* tables before the delete and re-inserts them verbatim on teardown
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
    And the Schedule 1 should no longer exist
    And the Schedule 1 amount and comment fields are read-only
    And the Schedule 1 actions are disabled
