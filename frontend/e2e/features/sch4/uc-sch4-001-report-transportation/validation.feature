# UC-SCH4-001-S13 / S19 / S20 / S21 / S22 / S23 — location-panel validation
#
# RE-GROUNDING NOTE — where validation happens and what it looks like:
#   - legacy rejected on each field's own JSF validator and listed one message per failing field in the
#     `p:messages` banner: FLD-001/002/003 for a range, and for a blank required field JSF's `required`
#     message as legacy overrode it — `{0}: Value is required.` (`common/validation.properties:11`), `{0}`
#     being the input's own XHTML `label`. Since the #359 group B change log (Iman, 2026-09-28) the
#     rewrite validates a category cell when it is CHANGED AND LEFT — legacy's `f:ajax event="change"`
#     — no longer on every keystroke (the cell steps here blur after entering, so a cell's own error is
#     asserted right after it), and renders the SAME
#     verbatim range text as Carbon inline `invalidText` under the offending cell; since #359 group B
#     (2026-09-28) the banner that blocks Save lists each failing field in legacy's wording, in panel
#     order, instead of one generic line. The backend enforces the identical bounds/messages (probed
#     2026-08-17), so the client copy is a mirror, not a divergence.
#   - S22/S23's message was `[UNKNOWN]` in the source Gherkin, but legacy's text was never unknown: it is
#     the `validation.properties:11` override above. On the New Location panel these scenarios use, the
#     labels are `schedule4NewLocation.xhtml`'s — `Distance (Km)`, `Truck Barge Ferry (Volume
#     m³)` (legacy printed the `<sup>` markup literally; rendered as m³ here), `Truck Barge Ferry (Cost $)`. The
#     missing cell is ALSO marked inline with the bundle's `missingRequiredFieldMsg` — "Value Required" —
#     the rewrite's own enhancement, client-side and server-side alike.
#   - ERR-001 is inline under Location Name, and (unlike the category cells, which mark up when changed and
#     left) it only appears once a Save has been ATTEMPTED — asserted after the Save click below. Legacy's
#     name input had no change listener.
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
    # A blank name fails JSF `required` on the `Location Name` input (`schedule4NewLocation.xhtml:14`).
    Then the Schedule 4 error banner lists:
      | Location Name: Value is required. |
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
    # Whitespace passes JSF `required` (it is not empty), so legacy's banner carried the bean's own
    # ERR-001 check (`Schedule4MB.java:615`) — the text the name also shows inline.
    Then the Schedule 4 error banner lists:
      | Location Name can not be empty. Please enter a description. |
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
    # The banner carries the same verbatim range text (a Distance also makes its Volume and Cost
    # required, whose own lines the S22 outline below pins).
    Then the Schedule 4 error banner includes "<message>"
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

  # S22 / S23 — BR-04 on the 3 distance-based categories is ALL-OR-NOTHING: once any of Distance,
  # Volume or Cost holds a value, all three are required, and every missing one is reported at once.
  # That is a DELIBERATE FIX OF LEGACY BEHAVIOUR (Iman, 2026-09-29): legacy's conditional `required=`
  # (a Distance requires Volume and Cost; a Volume or Cost requires a Distance) was effectively
  # all-or-nothing but revealed one missing field per attempt — a Volume alone asked only for the
  # Distance, and the Cost only on the next Save. So every row below now asserts BOTH missing cells,
  # inline and in the banner; the S22 rows (a Distance) were already all-at-once and are unchanged in
  # meaning, the S23 rows (an amount) gain the other amount.
  #
  # The MISSING cell is asserted AFTER Save, no longer live (#359 group B change log, legacy parity): a
  # cell is judged only when IT is changed and left, so entering the Distance judges the Distance alone
  # (it passes) and says nothing about the untouched Volume/Cost — as legacy's per-field `f:ajax` did.
  # Save then judges the whole panel, which is where the counterpart is reported, inline and in the
  # banner. Same assertions as before, one step later.
  @p1 @S22 @S23
  Scenario Outline: <name>
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E BR04 Probe" as the Schedule 4 location name
    And I enter "<value>" in the Schedule 4 "Truck Barge/Ferry" "<entered>" cell
    Then the Schedule 4 "Truck Barge/Ferry" "<entered>" cell has no inline error
    When I save the Schedule 4 location
    Then the Schedule 4 "Truck Barge/Ferry" "<missing>" cell is invalid with "Value Required"
    And the Schedule 4 "Truck Barge/Ferry" "<also>" cell is invalid with "Value Required"
    # The banner names each missing field by its New Location panel label, in legacy's wording.
    And the Schedule 4 error banner includes "<banner>"
    And the Schedule 4 error banner includes "<alsoBanner>"
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored

    Examples:
      | name                                                          | entered  | value | missing  | banner                                         | also   | alsoBanner                                     |
      | S22 A Distance with no Volume requires the Volume             | distance | 50    | volume   | Truck Barge Ferry (Volume m³): Value is required. | cost   | Truck Barge Ferry (Cost $): Value is required.    |
      | S22 A Distance with no Cost requires the Cost                 | distance | 50    | cost     | Truck Barge Ferry (Cost $): Value is required.    | volume | Truck Barge Ferry (Volume m³): Value is required. |
      | S23 A Volume with no Distance requires the Distance and Cost  | volume   | 800   | distance | Distance (Km): Value is required.                 | cost   | Truck Barge Ferry (Cost $): Value is required.    |
      | S23 A Cost with no Distance requires the Distance and Volume  | cost     | 4000  | distance | Distance (Km): Value is required.                 | volume | Truck Barge Ferry (Volume m³): Value is required. |

  # BR-04 applies ONLY to the 3 distance-based categories. A fixed category with a Volume but no Cost is
  # perfectly legal (the Data Field Reference is explicit: "no cross-field requirement exists for the 9
  # fixed no-distance categories") — Check Status is what later flags the missing Cost, not Save.
  # Since the on-change validation (#359 group B change log) the untouched Cost is never judged before a
  # Save, so its "no inline error" is only a guard now; the changed Volume's own assertion is the probe.
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
    And the Schedule 4 error banner lists:
      | Entered volume must be between 0 and 9,999,999.          |
      | Entered cost must be between -99,999,999 and 99,999,999. |
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
    Then the Schedule 4 error banner lists:
      | Location Name: Value is required.               |
      | Entered volume must be between 0 and 9,999,999. |
    When I enter "E2E Recovered Loc" as the Schedule 4 location name
    And I enter "1200" in the Schedule 4 "Lakeside Dry Dump" "volume" cell
    And I enter "3600" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the stored Schedule 4 location "E2E Recovered Loc" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1200   | 3600 | 3       |
