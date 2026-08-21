# UC-SCH4-001-S24 / S25 / S26 / S27 — sub-page add-row validation (the tighter legacy bands)
#
# RE-GROUNDING NOTE — three of these four messages were UNRESOLVED in the source Gherkin, and the rewrite
# supplies all three. Recorded as re-grounding GAINS in coverage.md:
#   - S24 quoted the DEFAULT volume message ("…0 and 9,999,999.") while flagging "(size-6 sub-page
#     variant)". The sub-page band really is size-6, so the message is `volume6DigitValidatorErrorMsg`:
#     "Entered volume must be between 0 and 999,999." — a value legal on the category grid is refused here.
#   - S25's `costSize7ValidatorErrorMsg` was `[UNKNOWN — key name only]`. It resolves to
#     "Entered cost must be between -9,999,999 and 9,999,999."
#   - S27's blank-Description message was `[UNKNOWN — platform-default JSF required message]`. It resolves
#     to the bundle's own `missingRequiredFieldMsg`: "Value Required".
# All three were confirmed identical on the server (400 ProblemDetail) by probe on 2026-08-17, so the
# client copies are mirrors rather than divergences.
#
# Every example runs on ITS OWN anchor. Reaching a sub-page requires a saved location, so each example
# writes — and a Scenario Outline's rows run as separate tests in parallel, which would otherwise have
# several of them creating and cleaning up the same (mill, year).

@UC-SCH4-001 @sch4
Feature: Schedule 4 — invalid sub-page rows are rejected

  As a Licensee
  I want out-of-range and unnamed transportation rows refused
  So that a location's list-based costs cannot carry impossible figures

  @p1 @S24 @S25 @S26 @S27
  Scenario Outline: <name>
    Given the Schedule 4 anchor "<anchor>" is an editable Draft with no locations
    And the Schedule 4 location "E2E Row Range Loc" is already saved with only its name
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Row Range Loc" for edit
    And I open the Schedule 4 "<subpage>" sub-page from the saved location
    And I note the Schedule 4 mutation count
    And I enter the following Schedule 4 row values:
      | description   | distance | volume   | cost   | cycle   |
      | <description> | 8        | <volume> | <cost> | <cycle> |
    And I add the Schedule 4 row
    Then the Schedule 4 row "<field>" field is invalid with "<message>"
    # Client-gated: the POST never left the browser and the table is still empty.
    And no further Schedule 4 write should have been sent
    And the Schedule 4 "<subpage>" table is empty
    And no Schedule 4 "<subpage>" rows are stored for "E2E Row Range Loc"

    Examples:
      | name                                                    | anchor                    | subpage                       | description   | volume  | cost     | cycle   | field       | message                                                 |
      | S24 A row Volume above the size-6 band is rejected      | subpage-validation-volume | Towing Total                  | E2E Range row | 1000000 |          |         | volume      | Entered volume must be between 0 and 999,999.           |
      | S25 A row Cost above the size-7 band is rejected        | subpage-validation-cost   | Towing Total                  | E2E Range row |         | 10000000 |         | cost        | Entered cost must be between -9,999,999 and 9,999,999.  |
      | S26 A Truck Rehaul Cycle above its band is rejected     | subpage-validation-cycle  | Truck Rehaul-Dewater/Transfer | E2E Range row |         |          | 1000000 | cycle       | Entered cycle time must be between 0 and 999,999.       |
      | S27 A row with a blank Description is rejected          | subpage-validation-desc   | Towing Total                  |               | 500     | 1500     |         | description | Value Required                                          |

  # The size-6/size-7 bands are the WHOLE POINT of S24/S25: the same figure that a sub-page row refuses is
  # perfectly legal on the category grid. Proving both sides in one scenario is what makes the tighter band
  # a tested rule rather than a coincidence of two separate numbers.
  @p1 @S24
  Scenario: A volume the category grid accepts is refused on a sub-page row
    Given the Schedule 4 anchor "subpage-band" is an editable Draft with no locations
    And the Schedule 4 location "E2E Band Compare" is already saved with only its name
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Band Compare" for edit
    # 1,000,000 is inside the grid's [0, 9,999,999]…
    And I enter "1000000" in the Schedule 4 "Lakeside Dry Dump" "volume" cell
    Then the Schedule 4 "Lakeside Dry Dump" "volume" cell has no inline error
    # …and outside the sub-page's [0, 999,999].
    When I open the Schedule 4 "Towing Total" sub-page from the saved location
    And I enter the following Schedule 4 row values:
      | description  | volume  |
      | E2E Band row | 1000000 |
    # Unlike the category grid (which marks cells up as you type), the add-row form shows its inline errors
    # only once an Add has been ATTEMPTED — SubPage.tsx gates them on `showErrors`, which `addRow()` sets.
    And I add the Schedule 4 row
    Then the Schedule 4 row "volume" field is invalid with "Entered volume must be between 0 and 999,999."
    And the Schedule 4 "Towing Total" table is empty

  # The recovery arm each exception slice carries: correct the rejected field and the row is added.
  @p1 @S24 @S27
  Scenario: Correcting the rejected row field adds the row
    Given the Schedule 4 anchor "subpage-recovery" is an editable Draft with no locations
    And the Schedule 4 location "E2E Row Recovery" is already saved with only its name
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Row Recovery" for edit
    And I open the Schedule 4 "Towing Total" sub-page from the saved location
    # Blank description AND an out-of-range volume: both are reported on the same attempt.
    And I enter the following Schedule 4 row values:
      | distance | volume  |
      | 8        | 1000000 |
    And I add the Schedule 4 row
    Then the Schedule 4 row "description" field is invalid with "Value Required"
    And the Schedule 4 row "volume" field is invalid with "Entered volume must be between 0 and 999,999."
    And the Schedule 4 "Towing Total" table is empty
    When I enter "E2E Corrected row" in the Schedule 4 row "description" field
    And I enter "500" in the Schedule 4 row "volume" field
    And I enter "1500" in the Schedule 4 row "cost" field
    And I add the Schedule 4 row
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Towing Total" row "E2E Corrected row" is listed
    And the stored Schedule 4 "Towing Total" rows for "E2E Row Recovery" are:
      | description       | distance | volume | cost | cycle | perUnit |
      | E2E Corrected row | 8        | 500    | 1500 |       | 3       |

  # The row bounds must be INCLUSIVE at both ends, like the grid's.
  #
  # Deliberately proves acceptance by ADDING the row rather than by asserting "no inline error": the add-row
  # form does not show inline errors until an Add is attempted, so a no-error assertion before Add would
  # have passed even for an out-of-range value — it would have tested nothing. Both extremes of all three
  # bands go in as two rows, which also keeps this parallel-safe on one anchor (an outline row per bound
  # would need six anchors and six seeded locations).
  @p2 @S24 @S25 @S26
  Scenario: Row volume, cost and cycle values exactly on their bounds are accepted
    Given the Schedule 4 anchor "subpage-bounds" is an editable Draft with no locations
    And the Schedule 4 location "E2E Row Bounds" is already saved with only its name
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Row Bounds" for edit
    And I open the Schedule 4 "Truck Rehaul-Dewater/Transfer" sub-page from the saved location
    # The MAXIMUM of each band: volume 999,999 / cost 9,999,999 / cycle 999,999.
    When I enter the following Schedule 4 row values:
      | description      | volume | cost    | cycle  |
      | E2E Max bounds   | 999999 | 9999999 | 999999 |
    And I add the Schedule 4 row
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Truck Rehaul-Dewater/Transfer" row "E2E Max bounds" is listed
    # The MINIMUM of each band: volume 0 / cost -9,999,999 / cycle 0.
    When I enter the following Schedule 4 row values:
      | description    | volume | cost     | cycle |
      | E2E Min bounds | 0      | -9999999 | 0     |
    And I add the Schedule 4 row
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Truck Rehaul-Dewater/Transfer" row "E2E Min bounds" is listed
    And the stored Schedule 4 "Truck Rehaul-Dewater/Transfer" rows for "E2E Row Bounds" are:
      | description    | distance | volume | cost     | cycle  | perUnit |
      | E2E Max bounds |          | 999999 | 9999999  | 999999 | 10      |
      | E2E Min bounds |          | 0      | -9999999 | 0      |         |
