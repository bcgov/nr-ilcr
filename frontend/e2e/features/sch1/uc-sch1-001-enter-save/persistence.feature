# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S23..S24.feature
# (legacy JSF/PrimeFaces). A persistence error on Save must surface the failure, keep the entered values,
# and write nothing (S23); retrying after the failure clears must then succeed and persist (S24), while a
# persistent failure keeps failing with still-no-write (S24 second case).
#
# The transient failure is injected with a transparent page.route that answers the next N main-page save
# PUTs with a 500 ProblemDetail WITHOUT reaching the backend, then lets later PUTs through — so the "no
# write" cases never touch the DB (asserted via a stable revisionCount) and the retry-succeeds case makes a
# real write, which is snapshotted and restored exactly on teardown.

@sch1 @UC-SCH1-001 @persistence
Feature: Report Average Cost of Logging (Schedule 1) — save persistence errors and retry
  As a mill reporter
  I want a failed Save to tell me and keep my entries, and a retry to succeed once the issue clears
  So that a transient persistence error never loses my data or leaves a partial save

  @S23 @p1
  Scenario: A persistence error on Save is surfaced, keeps the entries, and writes nothing
    Given the read-only Schedule 1 anchor is an editable Draft
    And the next 3 Schedule 1 save attempts will fail
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    # FOUR-digit amounts on purpose. The restyle (#237) groups an amount on BLUR, and clicking Save
    # blurs the field — so 7654 is re-rendered as "7,654". Three-digit values would never reach that
    # path, leaving the retained-value assertion passing for the wrong reason.
    And I enter the following Schedule 1 amounts:
      | line item                     | volume | cost |
      | Standing Tree to Loaded Truck | 4321   | 7654 |
    When I save Schedule 1
    Then I should see the error "Schedule could not be saved."
    And the Schedule 1 "Standing Tree to Loaded Truck cost" field still shows "7654"
    And the Schedule 1 "Standing Tree to Loaded Truck volume" field still shows "4321"
    And the Schedule 1 data should be unchanged

  @S24 @p1
  Scenario: Retrying Save succeeds after a transient failure clears
    Given a saved editable Schedule 1 exists for the retry target
    And the next 1 Schedule 1 save attempt will fail
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    And I enter the following Schedule 1 amounts:
      | line item                     | volume | cost |
      | Standing Tree to Loaded Truck | 321    | 654  |
    When I save Schedule 1
    Then I should see the error "Schedule could not be saved."
    When I save Schedule 1
    Then I should see the message "Data saved successfully"
    And the saved Schedule 1 should have line item 12 with volume 321 and cost 654

  @S24 @p1
  Scenario: Retrying Save fails again when the failure is not transient
    Given the read-only Schedule 1 anchor is an editable Draft
    And the next 5 Schedule 1 save attempts will fail
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    And I enter the following Schedule 1 amounts:
      | line item                     | volume | cost |
      | Standing Tree to Loaded Truck | 321    | 654  |
    When I save Schedule 1
    Then I should see the error "Schedule could not be saved."
    When I save Schedule 1
    Then I should see the error "Schedule could not be saved."
    And the Schedule 1 data should be unchanged
