# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S01.feature
# (legacy JSF/PrimeFaces). The React/Carbon app uses the /schedule-1 route reached via Home + side-nav,
# stable Carbon input ids (not PrimeFaces control ids), and role ILCR_SUBMITTER (the legacy ILCR_LICENSEE
# role does not exist — see defects.md). Success + persistence are asserted against the real write path
# (PUT /api/v1/schedule1 -> GET read-back).

@sch1 @UC-SCH1-001
Feature: Report Average Cost of Logging (Schedule 1) — enter and save line items
  As a mill reporter
  I want to enter cost and volume amounts for the logging line items and save them
  So that the mill's average logging cost data is recorded for the reporting year

  @S01 @p0
  Scenario: Enter cost and volume amounts and save Schedule 1
    Given the Schedule 1 for the test mill and reporting year is an editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 1
    And I enter the following Schedule 1 amounts:
      | line item                     | volume | cost |
      | Standing Tree to Loaded Truck | 100    | 5000 |
      | Actual $ Spent                | 200    | 300  |
    And I enter Schedule 1 comments "E2E S01 happy-path probe"
    And I save Schedule 1
    Then I should see the message "Data saved successfully"
    And the saved Schedule 1 should have line item 12 with volume 100 and cost 5000
    And the saved Schedule 1 should have Actual Spent silviculture with volume 200 and cost 300
    And the saved Schedule 1 comments should be "E2E S01 happy-path probe"
