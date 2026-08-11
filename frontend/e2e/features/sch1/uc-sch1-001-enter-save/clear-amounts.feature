# Clearing a previously-saved amount. Not a legacy slice of its own — this concern surfaced while
# re-grounding S01 after backend commit 0b58057 ("restore legacy parity for derived costs") made the
# four volume-only rows user-editable.
#
# Both scenarios are now ordinary GREEN regression guards. The second one was a genuine
# `@discovered-bug` RED from 2026-08-07 to 2026-08-11: `Schedule1Service.writeWritableDetails` guarded
# five volume fields with `!= null`, so a cleared field (the React client sends null for "the user
# emptied this box") was indistinguishable from an omitted one and was silently discarded. Fixed in
# backend commit 3ee9ff2 — those five scalars are now written unconditionally, so a null CLEARS the
# stored value (defects.md BUG-2 / issue #260). The tag is gone because the red is no longer the
# tracking signal; the scenario now guards the fix against regression.
#
# The second scenario also guards defects.md BUG-3 / issue #261 (fixed in the same commit): clearing
# the Subtotal Other Costs volume leaves the shared item-19 row present with a NULL volume, which used
# to make the next GET throw an NPE in `toOtherCosts` and return HTTP 500 — so the schedule became
# unopenable. That state was unreachable through the UI while BUG-2 was live; fixing BUG-2 made it
# reachable, which is why the reopen step at the end of that scenario matters.
#
# The two scenarios own SEPARATE (mill,year) keys on purpose. Both snapshot and restore their target,
# and the suite runs fullyParallel — a shared key makes concurrent snapshot/restore race (observed:
# value bleed between scenarios plus "no backup … snapshot was never taken" restore failures).
# For the same reason `--repeat-each=N` must be run with `--workers=1`: concurrent repeats of one
# scenario collide on its own key (observed 2026-08-11 — 409s, so no success message).

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

  # The five formerly-guarded fields are exercised in ONE scenario: they shared a single root cause
  # (BUG-2), so one scenario guards the whole class, and the assertion names whichever field is still
  # holding a value. The closing reopen is the BUG-3 guard — see the header.
  @p1
  Scenario: Clearing the five volume-only fields blanks them and the schedule still reopens
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
    # BUG-3 guard: the shared item-19 row is now present with a NULL volume — the state that used to
    # make this GET return HTTP 500. Reopening from Home issues a fresh GET; "I open Schedule 1" only
    # succeeds once the Company Logging Costs table renders, so a 500 fails here rather than silently.
    When I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    Then the Schedule 1 "Subtotal Other Costs volume" field should be empty
