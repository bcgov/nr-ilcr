# Re-grounded from UC-SCH3-001-S02.feature (ALT-001) — entering an Annual Rents Harvest amount warns the
# reporter that Annual Rent (Forest Act, S111) is recorded as an UNACCEPTABLE cost, not a standard
# administration cost.
#
# WHAT RE-GROUNDING CHANGED
#  * Legacy fired the alert from the field's `onchange`; the rewrite fires the same `window.alert` from
#    the field's blur handler with the identical literal string. It is a browser dialog either way, so it
#    is captured with a dialog listener registered BEFORE the blur — Playwright auto-dismisses an
#    unhandled dialog, so a listener attached afterwards would find nothing.
#  * The legacy slice also asserted "the annualRentsCrown field reflects the recalculated Crown amount
#    (Harvest minus PO&P)". Annual Rents captures no PO&P at all (BR-04), so the app forces it to 0 and
#    the Crown equals the Harvest — asserted here, together with the blank PO&P cell that makes the
#    absence visible rather than showing the backend's 0.
#  * Runs on the shared read-only anchor: the alert and the recalculation are both client-side, so
#    nothing is written and the scenario proves that.

@sch3 @UC-SCH3-001 @alerts
Feature: Report Forest Management Administration Costs (Schedule 3) — the Annual Rents S111 alert
  As a mill reporter
  I want to be told when I record an Annual Rents amount
  So that I understand it is carried as an unacceptable cost under the Forest Act, S111

  @p1 @S02
  Scenario: Entering an Annual Rents Harvest amount raises the S111 alert and recalculates its Crown
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I note the Schedule 3 write count
    And I enter "25000" into the Annual Rents Harvest field
    Then the alert "Annual Rent (Forest Act, S111) is recorded as an Unacceptable Cost." was shown
    And the "Annual Rents" line shows Harvest "25000", PO&P "—" and Crown "25000"
    # The amount is accepted, and the Included Unacceptable Costs figure follows it immediately (the
    # entered Annual Rents harvest IS an included-unacceptable cost, BR-04).
    And the "Included Unacceptable Costs" row shows "25000", "—" and "25000"
    And no Schedule 3 write was attempted
