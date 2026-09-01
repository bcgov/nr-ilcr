# Re-grounded from UC-SCH3-001-S17.feature (EF4) — a persistence failure on Save, and the retry that
# then succeeds. Both arms of the legacy slice are covered.
#
# WHAT RE-GROUNDING CHANGED
#  * The failure is induced at the network boundary: the PUT is answered with the app's own 500
#    problem+json carrying ERR-001's text, so the page takes exactly its real failure path. Corrupting
#    data to provoke a genuine DB error would be neither repeatable nor cleanable.
#  * Legacy asserted "no Schedule 3 records are changed". That is asserted here by READING THE RECORD
#    BACK, not by inferring it from the banner — a save that failed on the client but reached the server
#    would look identical otherwise.
#  * The retry arm is NOT intercepted, so it exercises the real backend and proves the entered values
#    survived the failure (the page keeps the form as typed) and then persist.

@sch3 @UC-SCH3-001 @save-error
Feature: Report Forest Management Administration Costs (Schedule 3) — a failed save and its retry
  As a mill reporter
  I want a failed save to tell me so and to change nothing
  So that I can retry it without wondering what was written

  @p1 @S17
  Scenario: A persistence failure on Save writes nothing, and the retry succeeds
    Given the Schedule 3 anchor "retry"
    And the Schedule 3 save will fail
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I enter every fixed admin cost line and both timber volumes
    And I save Schedule 3
    Then I should see the error "Schedule could not be saved."
    And the stored Schedule 3 is still empty
    And the Schedule 3 optimistic-lock token has not moved
    # The typed values are still on screen, so the reporter has nothing to re-key before retrying.
    And the "Licenses, Fees, Insurance" line shows Harvest "100000", PO&P "10000" and Crown "90000"
    When the Schedule 3 save is no longer failing
    And I save Schedule 3
    Then I should see the message "Data saved successfully"
    And the stored Schedule 3 holds those amounts
