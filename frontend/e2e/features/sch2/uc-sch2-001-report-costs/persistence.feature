# Persistence — the saved schedule survives a full reopen.
#
# NOT a legacy slice of its own: the legacy Basic Flow step 2 ("any previously saved amounts are shown")
# is what S02 diverges FROM, so nothing in the catalogue proves the round-trip independently of the
# client's own post-save repaint. This closes that gap — after Save the page re-seeds its form from the
# response, which would look identical whether or not anything reached the database. A full reload
# refetches the document, so only a genuine write survives it.

@UC-SCH2-001 @sch2
Feature: Schedule 2 — saved figures survive a reopen

  As a Licensee
  I want the figures I saved to still be there when I come back
  So that I can trust the cost report holds what I entered

  @p0 @S01
  Scenario: Saved figures and comments are still present after reloading the page
    Given the Schedule 2 anchor "persist" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I enter the following Schedule 2 values:
      | field                   | value            |
      | Purchased Log Cost cost | 4321             |
      | Less Log Sales volume   | 15               |
      | Less Log Sales cost     | 600              |
      | comments                | survives reload  |
    And I save Schedule 2
    Then I should see the message "Data saved successfully"
    # The reload discards all client state and refetches the document from the server.
    When I reopen Schedule 2
    Then the Schedule 2 "Purchased Log Cost cost" field shows "4,321"
    And the Schedule 2 "Less Log Sales volume" field shows "15"
    And the Schedule 2 "Less Log Sales cost" field shows "600"
    And the Schedule 2 "comments" field shows "survives reload"
    # Delete is offered again on reopen, which independently confirms the summary really exists (BR-08).
    And the Schedule 2 Delete action is available
    And the stored Schedule 2 record is:
      | field                | value           |
      | purchasedLogCostCost | 4321            |
      | lessLogSalesVolume   | 15              |
      | lessLogSalesCost     | 600             |
      | comments             | survives reload |
