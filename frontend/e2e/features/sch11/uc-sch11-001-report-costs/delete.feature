# Re-grounded from UC-SCH11-001-S07/S08.feature.
#
# STRUCTURAL DIVERGENCE (defects.md DIV-1) — legacy's delete was TWO-PHASE: confirming "Yes"
# showed "Data deleted successfully" immediately while only flagging the row DELETE in memory, and the
# removal reached the database only on a subsequent page-level Save. The React app deletes IMMEDIATELY
# (DELETE /locations/{id}, the shipped 25.2 contract) and echoes the same SUC-002 text, so there is no
# second Save step to assert. The read-back below proves the row is really gone from the database, which
# is the behaviour the legacy scenario needed its extra Save click to reach.
#
# CONFIRM DIALOG (resolves a legacy [UNKNOWN]) — the legacy Gherkin marked the confirmation prompt text
# as [UNKNOWN] because resource key `confirmDeleteMsg` was never captured. In the React app it is a
# Carbon Modal: heading "Delete location", body "This will delete the current record. Do you want to
# continue?", actions "Delete"/"Cancel" — NOT PrimeFaces `.ui-confirmdialog-yes`/`-no`, and NOT a native
# browser dialog, so no page.on('dialog') handler is involved.

@sch11 @UC-SCH11-001 @delete
Feature: Report Basic Silviculture Costs (Schedule 11) — delete a location
  As a mill reporter
  I want to remove a location behind a confirmation
  So that the schedule no longer includes a location that should not be reported

  @S07 @p1
  Scenario: Delete a location and confirm, and the removal is persisted
    Given the Schedule 11 anchor "delete" has a seeded location "E2E S07 delete"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    Then the Schedule 11 location "E2E S07 delete" is listed
    When I delete the Schedule 11 location "E2E S07 delete"
    And I confirm the delete
    Then I should see the message "Data deleted successfully"
    And the Schedule 11 location "E2E S07 delete" is not listed
    # The anchor is pristine apart from the seeded row, so removing it must return the table to its
    # empty state rather than leaving an orphaned/blank row behind.
    And the Schedule 11 table is empty
    # AC3 letter: the footer must refresh too. The anchor held exactly this one row, so every total
    # returns to blank (null renders blank, never "0").
    And the Schedule 11 footer total "NAR(ha)" shows ""
    And the Schedule 11 footer total "Actual Cost ($)" shows ""
    And the Schedule 11 footer total "Total Act Plus Plan Cost ($)" shows ""
    # Proven at the write path, not just the table: the row is really gone from the database.
    And the Schedule 11 location "E2E S07 delete" is gone from the schedule

  @S08 @p1
  Scenario: Cancel the delete confirmation and the row is left unchanged
    Given the Schedule 11 anchor "cancel-delete" has a seeded location "E2E S08 cancel"
    And a spy is watching the Schedule 11 location requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I note the listed Schedule 11 row count
    And I delete the Schedule 11 location "E2E S08 cancel"
    And I cancel the delete
    Then the Schedule 11 delete confirmation is dismissed
    # Cancelling must not even claim to have deleted anything.
    And no Schedule 11 delete confirmation message is shown
    And the Schedule 11 location "E2E S08 cancel" is listed
    And the listed Schedule 11 row count is unchanged
    # Cancelling must fire NO request at all — the spy is what makes that a proof rather than an
    # inference from the row still being on screen.
    And no Schedule 11 location mutation should have been sent
    # And the stored row is byte-for-byte the seeded one: revisionCount 0 means it was never rewritten.
    And the Schedule 11 location "E2E S08 cancel" still holds revision 0
    And the Schedule 11 location "E2E S08 cancel" is persisted as:
      | field        | value   |
      | Enhanced     | Yes     |
      | Biogeo       | ESSFdc1 |
      | Actual Cost  | 5000    |
      | Planned Cost | 4500    |
