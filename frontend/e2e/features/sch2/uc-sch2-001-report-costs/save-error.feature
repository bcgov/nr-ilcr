# UC-SCH2-001-S12 — Save Fails, Persistence Error (EF4)
#
# HOW THIS IS EXERCISED HONESTLY: the real database cannot be made to fail on demand from a browser test,
# and the thing this slice is about is the PAGE's behaviour when the save fails — the error is shown, the
# entered values are kept so the reporter can retry, and nothing is persisted. So the PUT is failed at the
# browser edge with a bodyless 500, which is precisely the shape that makes the page fall back to its own
# ERR-003 wording (`extractDetail` finds no ProblemDetail.detail). Because the request never leaves the
# browser, nothing reaches the backend — which the scenario then PROVES by API read-back rather than
# assuming.
#
# The retry arm is the legacy slice's own recovery scenario: "clicking Save again retries and, if the
# underlying error is resolved, proceeds as Basic Flow step 7."

@UC-SCH2-001 @sch2
Feature: Schedule 2 — a failed save is reported and loses nothing

  As a Licensee
  I want a failed save to tell me and keep my entries
  So that I can retry without typing everything again

  @p1 @S12
  Scenario: A server-side save failure shows the error, keeps the entries, and persists nothing
    Given the Schedule 2 anchor "save-error" is an unsaved editable Draft
    And the next Schedule 2 save will fail on the server
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I enter the following Schedule 2 values:
      | field                   | value |
      | Purchased Log Cost cost | 31000 |
      | Less Log Sales volume   | 12    |
      | Less Log Sales cost     | 900   |
    And I save Schedule 2
    # ERR-003, verbatim.
    Then I should see the error "Schedule could not be saved."
    And I should not see the message "Data saved successfully"
    # The entered values are still on screen — the reporter does not have to re-key them.
    And the Schedule 2 "Purchased Log Cost cost" field shows "31,000"
    And the Schedule 2 "Less Log Sales volume" field shows "12"
    And the Schedule 2 "Less Log Sales cost" field shows "900"
    # BR-02: the transaction is rolled back, so no records exist at all.
    And no Schedule 2 record is stored

  # The legacy slice's own recovery scenario — "clicking Save again retries and, if the underlying error
  # is resolved, proceeds as Basic Flow step 7" — and an explicit requirement of issue #78, which lists
  # the critical journeys as "enter/save/update/RETRY (S01–S04, S12)".
  #
  # The failure is injected for exactly ONE request, which is how "the underlying persistence error has
  # been resolved" is modelled: the retry is not intercepted at all, so it reaches the real backend and
  # writes for real. That is what makes this scenario worth having over the one above — it proves the
  # page recovers rather than being left in a wedged state after a failure.
  @p1 @S12
  Scenario: Retrying the save after the error clears succeeds and persists the entries
    Given the Schedule 2 anchor "retry" is an unsaved editable Draft
    And the next Schedule 2 save will fail on the server
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I enter the following Schedule 2 values:
      | field                   | value |
      | Purchased Log Cost cost | 27500 |
      | Less Log Sales volume   | 9     |
      | Less Log Sales cost     | 450   |
    And I save Schedule 2
    Then I should see the error "Schedule could not be saved."
    And no Schedule 2 record is stored
    # Same values, still on screen — the reporter simply presses Save again.
    When I save Schedule 2
    Then I should see the message "Data saved successfully"
    And I should not see the message "Schedule could not be saved."
    And the stored Schedule 2 record is:
      | field                | value |
      | purchasedLogCostCost | 27500 |
      | lessLogSalesVolume   | 9     |
      | lessLogSalesCost     | 450   |
