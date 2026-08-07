# Clearing a previously-saved amount. Not a legacy slice of its own — this concern surfaced while
# re-grounding S01 after backend commit 0b58057 ("restore legacy parity for derived costs") made the
# four volume-only rows user-editable. Schedule1Service.writeWritableDetails guards FIVE volume fields
# with `!= null` so a request that OMITS them leaves the stored value alone; but the React client always
# SENDS them, with null meaning "the user cleared this field" — so a cleared field is silently discarded.
#
# The first scenario is the mirror arm and passes: an ordinary line-item volume clears normally. The
# second is the broken arm and is a genuine RED (@discovered-bug), tracking defects.md Bug/Regression #2
# until the app is fixed. It is NOT skipped, weakened, or xfailed — the red IS the tracking signal.
# Filter it out of a clean CI run with `--grep-invert @discovered-bug`.
#
# The two scenarios own SEPARATE (mill,year) keys on purpose. Both snapshot and restore their target,
# and the suite runs fullyParallel — a shared key makes concurrent snapshot/restore race (observed:
# value bleed between scenarios plus "no backup … snapshot was never taken" restore failures).

@sch1 @UC-SCH1-001 @clear-amounts
Feature: Report Average Cost of Logging (Schedule 1) — clearing a saved amount
  As a mill reporter who entered a figure by mistake
  I want to clear a saved amount and save
  So that the schedule no longer reports a value I did not mean to enter

  # The working arm: codes 12–18 and silviculture 1/2 pass their volume through as null, so clearing them
  # blanks the stored row. This is the behaviour the guarded fields are measured against.
  @p1
  Scenario: Clearing a saved line-item volume blanks it
    Given the clear-amounts target is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I enter "4321" in the Schedule 1 "Standing Tree to Loaded Truck volume" field
    And I save Schedule 1
    Then I should see the message "Data saved successfully"
    And the saved Schedule 1 should have line item 12 with volume 4321
    When I clear the Schedule 1 "Standing Tree to Loaded Truck volume" field
    And I save Schedule 1
    Then I should see the message "Data saved successfully"
    And the saved Schedule 1 volume for row 12 should be empty

  # The broken arm — expected to FAIL until Bug/Regression #2 is fixed. All five guarded fields are
  # exercised in ONE scenario: they share a single root cause, so one honest red tracks the defect
  # without five separate browser runs, and the assertion names whichever field is still holding a value.
  @discovered-bug @p1
  Scenario: Clearing the guarded volume fields is silently discarded
    Given the guarded-fields clear target is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I enter the following Schedule 1 field values:
      | field                                                   | value |
      | Forest Management Administration Costs (Sch 3) volume   | 4322  |
      | Subtotal Company Logging Cost (no Silviculture) volume  | 4323  |
      | Less Silviculture Admin Costs volume                    | 4324  |
      | Total Silviculture (As per Financial Statements) volume | 4325  |
      | Subtotal Other Costs volume                             | 4326  |
    And I save Schedule 1
    Then I should see the message "Data saved successfully"
    When I clear the following Schedule 1 fields:
      | field                                                   |
      | Forest Management Administration Costs (Sch 3) volume   |
      | Subtotal Company Logging Cost (no Silviculture) volume  |
      | Less Silviculture Admin Costs volume                    |
      | Total Silviculture (As per Financial Statements) volume |
      | Subtotal Other Costs volume                             |
    And I save Schedule 1
    Then I should see the message "Data saved successfully"
    And the saved Schedule 1 volumes for the following rows should be empty:
      | row |
      | 143 |
      | 144 |
      | 139 |
      | 140 |
      | 19  |
