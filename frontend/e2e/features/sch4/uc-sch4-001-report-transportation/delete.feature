# UC-SCH4-001-S10 (BR-08 / NAV-004) — delete a location and its related rows
#
# RE-GROUNDING NOTE — the confirmation is a Carbon danger Modal ("Delete" / "Cancel") with the heading
# "Delete location", not one of three `p:confirmDialog` variants chosen by row-state branch
# (`:confirm` / `:confirm2` / `:confirm3`), and not a native browser dialog. The body text is the legacy
# NAV-004 wording, with the two legacy message PARTS joined into one sentence:
#   legacy `confirmDeleteMsgPart1` + `confirmDeleteMsgPart2` = "This will delete the current record,"
#   + "Do you want to continue?"  ->  "This will delete the current record. Do you want to continue?"
# (comma replaced by a full stop). Legacy carried BOTH punctuations in its own bundle and used the FULL
# STOP on 21 of 22 pages — including all three Schedule 4 sub-pages; only schedule4.xhtml used the comma.
# So the app matches legacy's majority and its own sub-pages; DIV-6 is CLOSED as not a defect.
#
# BR-08 proper: deleting a location removes the WHOLE FAMILY — the primary report, every distance-category
# child, and every sub-page list row — so the S10 precondition seeds a location that has all three.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — delete a location

  As a Licensee
  I want to delete a location and its related transportation rows
  So that the report does not carry a location that is no longer part of it

  @p0 @S10
  Scenario: Delete a location that has category amounts and a sub-page row
    Given the Schedule 4 anchor "delete" is an editable Draft with no locations
    And the Schedule 4 location "E2E Delete Me" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
      | Truck Barge/Ferry | 50       | 800    | 4000 |
    And that Schedule 4 location already has these "Towing Total" rows:
      | description  | distance | volume | cost |
      | E2E Camp haul | 12.5    | 500    | 1500 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the Schedule 4 location "E2E Delete Me" is listed
    When I delete the Schedule 4 location "E2E Delete Me"
    Then the Schedule 4 delete confirmation asks "This will delete the current record. Do you want to continue?"
    When I confirm the Schedule 4 delete
    Then I should see the message "Data deleted successfully"
    And the Schedule 4 location "E2E Delete Me" is not listed
    And the Schedule 4 location list is empty
    # BR-08 at the source of truth: the whole family is gone — not just the primary report. If the
    # distance child or the sub-page row had survived, the location would still be assembled by the read.
    And no Schedule 4 locations are stored

  # Dismissing the confirmation must be a genuine no-op, not a delayed delete.
  @p1 @S10
  Scenario: Cancelling the confirmation leaves the location untouched
    Given the Schedule 4 anchor "cancel-delete" is an editable Draft with no locations
    And the Schedule 4 location "E2E Keep Me" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 900    | 1800 |
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I note the listed Schedule 4 locations
    And I note the Schedule 4 mutation count
    And I delete the Schedule 4 location "E2E Keep Me"
    And I cancel the Schedule 4 delete
    Then the Schedule 4 delete confirmation is dismissed
    And I should not see the message "Data deleted successfully"
    And the Schedule 4 location list is unchanged
    # No request at all was sent, and the record is still there with its values.
    And no further Schedule 4 write should have been sent
    And the stored Schedule 4 location "E2E Keep Me" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 900    | 1800 | 2       |

  # Deleting ONE of several locations must leave the others alone. The delete targets the family by its
  # primary report id, and the families are distinguished only by LOCATION_DESCRIPTION in storage, so a
  # name-scoped delete that over-matched would take the wrong rows with it.
  @p1 @S10
  Scenario: Deleting one location leaves the others in place
    Given the Schedule 4 anchor "delete-one" is an editable Draft with no locations
    And the Schedule 4 location "E2E Delete One" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 100    | 300  |
    And the Schedule 4 location "E2E Delete One Keep" is already saved with:
      | category   | distance | volume | cost |
      | Water Dump |          | 200    | 600  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the Schedule 4 anchor stores 2 locations
    When I delete the Schedule 4 location "E2E Delete One"
    And I confirm the Schedule 4 delete
    Then I should see the message "Data deleted successfully"
    And the Schedule 4 location "E2E Delete One" is not listed
    And the Schedule 4 location "E2E Delete One Keep" is listed
    And the Schedule 4 anchor stores 1 locations
    And the stored Schedule 4 location "E2E Delete One Keep" is:
      | category   | distance | volume | cost | perUnit |
      | Water Dump |          | 200    | 600  | 3       |
