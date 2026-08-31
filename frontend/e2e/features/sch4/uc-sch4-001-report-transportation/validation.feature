# UC-SCH4-001-S13 / S19 / S20 / S21 / S22 / S23 — location-panel validation
#
# RE-GROUNDING NOTE — where validation happens and what it looks like:
#   - legacy rejected on each field's own JSF validator and surfaced FLD-001/002/003 (and a
#     platform-default "required" message for BR-04) in the `p:messages` panel. The rewrite validates on
#     every keystroke and renders the SAME verbatim text as Carbon inline `invalidText` under the offending
#     cell, then blocks Save with an advisory banner. The range TEXT is unchanged, so these scenarios still
#     pin the legacy contract strings — and the backend enforces the identical bounds/messages (probed
#     2026-08-17), so the client copy is a mirror, not a divergence.
#   - S22/S23's message was `[UNKNOWN]` in the source Gherkin (a platform-default JSF `required` message
#     keyed to the field's `label`, not a literal in `messages.properties`). The rewrite RESOLVES it: the
#     missing counterpart cell is marked with the bundle's own `missingRequiredFieldMsg` — "Value Required"
#     — client-side and server-side alike. Recorded as a re-grounding GAIN in coverage.md.
#   - ERR-001 is inline under Location Name, and (unlike the category cells, which mark up as you type) it
#     only appears once a Save has been ATTEMPTED — asserted after the Save click below.
#
# Every scenario here runs on the validate-only anchor and PROVES no write was attempted — a rejection
# that merely failed to show a success banner would prove nothing. Nothing is ever persisted on that
# anchor, which is why several scenarios can share it safely under `fullyParallel`.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — invalid location entries are rejected

  As a Licensee
  I want invalid names and out-of-range amounts refused before they are stored
  So that the transportation report cannot carry impossible figures

  Background:
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page

  @p1 @S13
  Scenario: A blank location name is refused
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I save the Schedule 4 location
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 4 location name field is invalid with "Location Name can not be empty. Please enter a description."
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored

  # A whitespace-only name is the same rejection (the app trims before validating), which the legacy
  # slice's `CoreUtil.isNullOrEmptyString` also did. Cheap to cover and easy to regress.
  @p2 @S13
  Scenario: A whitespace-only location name is refused
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "   " as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored

  # S19 / S20 / S21 — one representative category per validator, exactly as the slice catalogue chose
  # ("same pattern, not independently sliced" for the other 8 fixed / 2 distance categories). The outline
  # covers all three field kinds and both ends of each signed range in one place.
  @p1 @S19 @S20 @S21
  Scenario Outline: A category <field> of <value> is rejected
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Range Probe" as the Schedule 4 location name
    And I enter "<value>" in the Schedule 4 "<category>" "<field>" cell
    Then the Schedule 4 "<category>" "<field>" cell is invalid with "<message>"
    When I save the Schedule 4 location
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored

    Examples:
      | category          | field    | value      | message                                                  |
      | Lakeside Dry Dump | volume   | 10000000   | Entered volume must be between 0 and 9,999,999.          |
      | Lakeside Dry Dump | volume   | -1         | Entered volume must be between 0 and 9,999,999.          |
      | Lakeside Dry Dump | cost     | 100000000  | Entered cost must be between -99,999,999 and 99,999,999. |
      | Lakeside Dry Dump | cost     | -100000000 | Entered cost must be between -99,999,999 and 99,999,999. |
      | Truck Barge/Ferry | distance | 1000000    | Entered distance must be between 0 and 999,999.9.        |
      | Truck Barge/Ferry | distance | -1         | Entered distance must be between 0 and 999,999.9.        |

  # The bounds themselves must be INCLUSIVE — an off-by-one in either direction would otherwise reject a
  # legitimate figure, and no scenario above would notice.
  @p2 @S19 @S20 @S21
  Scenario Outline: A category <field> of <value> is accepted on its bound
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "<value>" in the Schedule 4 "<category>" "<field>" cell
    Then the Schedule 4 "<category>" "<field>" cell has no inline error

    Examples:
      | category          | field    | value     |
      | Lakeside Dry Dump | volume   | 0         |
      | Lakeside Dry Dump | volume   | 9999999   |
      | Lakeside Dry Dump | cost     | -99999999 |
      | Lakeside Dry Dump | cost     | 99999999  |
      | Truck Barge/Ferry | distance | 0         |
      | Truck Barge/Ferry | distance | 999999    |

  # S22 / S23 — BR-04 is BIDIRECTIONAL on the 3 distance-based categories: a Distance makes Volume and
  # Cost required, and either amount makes Distance required. The outline covers both arms of the mirror
  # (the symmetry the slice catalogue split into two slices), and both are marked on the MISSING cell.
  @p1 @S22 @S23
  Scenario Outline: <name>
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E BR04 Probe" as the Schedule 4 location name
    And I enter "<value>" in the Schedule 4 "Truck Barge/Ferry" "<entered>" cell
    Then the Schedule 4 "Truck Barge/Ferry" "<missing>" cell is invalid with "Value Required"
    When I save the Schedule 4 location
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored

    Examples:
      | name                                                          | entered  | value | missing  |
      | S22 A Distance with no Volume requires the Volume             | distance | 50    | volume   |
      | S22 A Distance with no Cost requires the Cost                 | distance | 50    | cost     |
      | S23 A Volume with no Distance requires the Distance           | volume   | 800   | distance |
      | S23 A Cost with no Distance requires the Distance             | cost     | 4000  | distance |

  # BR-04 applies ONLY to the 3 distance-based categories. A fixed category with a Volume but no Cost is
  # perfectly legal (the Data Field Reference is explicit: "no cross-field requirement exists for the 9
  # fixed no-distance categories") — Check Status is what later flags the missing Cost, not Save.
  @p2 @S22
  Scenario: A fixed category accepts a Volume with no Cost
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "1200" in the Schedule 4 "Lakeside Dry Dump" "volume" cell
    Then the Schedule 4 "Lakeside Dry Dump" "cost" cell has no inline error
    And the Schedule 4 "Lakeside Dry Dump" "volume" cell has no inline error

  # A fully-empty distance category is also legal — BR-04 only fires once one of the three is entered.
  @p2 @S22 @S23
  Scenario: A completely empty distance category raises nothing
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Empty Distance" as the Schedule 4 location name
    Then the Schedule 4 "Truck Barge/Ferry" "distance" cell has no inline error
    And the Schedule 4 "Truck Barge/Ferry" "volume" cell has no inline error
    And the Schedule 4 "Truck Barge/Ferry" "cost" cell has no inline error

  # Two independent validators must both report on the same attempt, not just the first one found.
  @p2 @S19 @S20
  Scenario: Two out-of-range cells report their own errors together
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Two Errors" as the Schedule 4 location name
    And I enter the following Schedule 4 category amounts:
      | category          | distance | volume   | cost      |
      | Lakeside Dry Dump |          | 10000000 | 100000000 |
    And I save the Schedule 4 location
    Then the Schedule 4 "Lakeside Dry Dump" "volume" cell is invalid with "Entered volume must be between 0 and 9,999,999."
    And the Schedule 4 "Lakeside Dry Dump" "cost" cell is invalid with "Entered cost must be between -99,999,999 and 99,999,999."
    And I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored

  # The recovery arm every exception slice carries: correct the highlighted field and the save goes
  # through. It genuinely writes, so it owns its own anchor rather than the shared validate-only one.
  @p1 @S13 @S19
  Scenario: Correcting the highlighted fields lets the save through
    Given the Schedule 4 anchor "validation-recovery" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "10000000" in the Schedule 4 "Lakeside Dry Dump" "volume" cell
    And I save the Schedule 4 location
    Then I should see the error "Please correct the highlighted fields before saving."
    When I enter "E2E Recovered Loc" as the Schedule 4 location name
    And I enter "1200" in the Schedule 4 "Lakeside Dry Dump" "volume" cell
    And I enter "3600" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the stored Schedule 4 location "E2E Recovered Loc" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1200   | 3600 | 3       |
