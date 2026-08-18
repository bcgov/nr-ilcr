# UC-SCH4-001-S03 / S04 / S05 / S06 (AF2) — the three list sub-pages and the two ways in
#
# RE-GROUNDING NOTE: legacy had three separate views (`schedule4TowingTotal.xhtml`, `…TruckRehaul.xhtml`,
# `…OtherTransportation.xhtml`), each reached by a JSF outcome-string forward, each with its own
# `addXxxForm` naming container. The rewrite renders ONE component at a URL STATE of the same route
# (`/schedule-4?loc=<id>&sub=TOWING|TRUCK_REHAUL|OTHER`), so the browser Back button steps back to the
# location list and a sub-page is refreshable. The add-row fields are one stable id set
# (`#subpage-description`, …) instead of three parallel ones.
#
# The two ways in are the legacy ones, and they behave differently — that difference IS S03 vs S04:
#   - from an UNSAVED new location: NAV-003 ("The information for the New Location must be saved…") and
#     the app SAVES the location first (legacy `goToTowingTotalForNew()` → `save()` then navigate only if
#     no errors);
#   - from a SAVED location: NAV-002 ("Any unsaved data will be lost…") and the panel's unsaved edits are
#     DISCARDED, with no save and no name/duplicate validation (legacy `goToTowingTotal()` →
#     `reloadSchedule()`).
# Both prompts are Carbon Modals now, not `p:confirm` dialogs.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — record list-based transportation on a location's sub-pages

  As a Licensee
  I want to record towing, truck-rehaul and other transportation rows against a location
  So that list-based costs that don't fit the fixed category grid are still reported

  # S03 — the save-first path. The location does not exist yet when the link is clicked.
  @p0 @S03
  Scenario: Opening Towing Total from an unsaved new location saves it first, then adds a row
    Given the Schedule 4 anchor "towing-from-new" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Nechako Bend" as the Schedule 4 location name
    And I click the Schedule 4 "Towing Total" sub-page link
    Then the Schedule 4 save-first confirmation asks "The information for the New Location must be saved before you can add other Transportation. Would you like to save the information now?"
    When I confirm the Schedule 4 save-first prompt
    Then the Schedule 4 "Towing Total" sub-page is open
    # The location was created on the way through — that is the whole point of NAV-003.
    And the Schedule 4 anchor stores 1 locations
    And the Schedule 4 sub-page trail shows "E2E Nechako Bend"
    And the Schedule 4 "Towing Total" table is empty
    When I enter the following Schedule 4 row values:
      | description   | distance | volume | cost |
      | E2E Camp haul | 12.5     | 500    | 1500 |
    And I add the Schedule 4 row
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Towing Total" row "E2E Camp haul" is listed
    # $/m³ per row is server-derived (1500/500 = 3), and the footer totals the columns.
    And the Schedule 4 "Towing Total" row "E2E Camp haul" shows:
      | distance | volume | cost  | perUnit |
      | 12.5     | 500    | 1,500 | 3.00    |
    And the Schedule 4 "Towing Total" totals show:
      | distance | volume | cost  |
      | 12.5     | 500    | 1,500 |
    And the stored Schedule 4 "Towing Total" rows for "E2E Nechako Bend" are:
      | description   | distance | volume | cost | cycle | perUnit |
      | E2E Camp haul | 12.5     | 500    | 1500 |       | 3       |
    # Back returns to the list, where the group label now carries the live count (CNT-001).
    When I go back from the Schedule 4 sub-page
    Then the Schedule 4 location "E2E Nechako Bend" is listed
    When I open the Schedule 4 location "E2E Nechako Bend" for edit
    Then the Schedule 4 sub-page link "Towing Total" shows 1 rows
    # The group's grid row now shows the rolled-up totals where legacy used disabled rollup inputs.
    And the Schedule 4 sub-page row "Towing Total" totals show:
      | distance | volume | cost  | perUnit | cycle |
      | 12.5     | 500    | 1,500 | 3.00    | —     |

  # S04 — the re-fetch path: the location is already saved, so no save happens and any unsaved panel edit
  # is discarded. Asserting the discard is what distinguishes this from S03.
  @p0 @S04
  Scenario: Opening Towing Total from a saved location discards panel edits and adds a row
    Given the Schedule 4 anchor "towing-from-saved" is an editable Draft with no locations
    And the Schedule 4 location "E2E Saved Loc" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Saved Loc" for edit
    # An unsaved panel edit that must NOT survive the navigation.
    And I enter "9999" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I click the Schedule 4 "Towing Total" sub-page link
    Then the Schedule 4 unsaved-changes confirmation asks "Any unsaved data will be lost. Are you sure you would like to continue?"
    When I confirm the Schedule 4 unsaved-changes prompt
    Then the Schedule 4 "Towing Total" sub-page is open
    And the Schedule 4 "Towing Total" table is empty
    When I enter the following Schedule 4 row values:
      | description     | distance | volume | cost |
      | E2E Second haul | 8        | 300    | 900  |
    And I add the Schedule 4 row
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Towing Total" row "E2E Second haul" is listed
    And the stored Schedule 4 "Towing Total" rows for "E2E Saved Loc" are:
      | description     | distance | volume | cost | cycle | perUnit |
      | E2E Second haul | 8        | 300    | 900  |       | 3       |
    # The discarded edit was never written: the category cost is still the seeded 3600, not 9999.
    And the stored Schedule 4 location "E2E Saved Loc" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1200   | 3600 | 3       |
    # The sub-page is a URL STATE of /schedule-4, not a separate view — which is the whole reason the
    # rewrite pushes `?loc=&sub=` — so the BROWSER's Back button returns to the location list too, not just
    # the page's own Back button. Legacy could not do this (a JSF forward carried a flash flag instead).
    When I press the browser Back button
    Then the Schedule 4 location "E2E Saved Loc" is listed

  # S05 — Truck Rehaul is the ONLY sub-page with a Cycle field, and the only one whose group row shows a
  # Cycle total. Both halves of that asymmetry are asserted (here, and by its absence in S03/S06).
  @p1 @S05
  Scenario: A Truck Rehaul row carries its Cycle value
    Given the Schedule 4 anchor "truck-rehaul" is an editable Draft with no locations
    And the Schedule 4 location "E2E Rehaul Loc" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Rehaul Loc" for edit
    And I open the Schedule 4 "Truck Rehaul-Dewater/Transfer" sub-page from the saved location
    When I enter the following Schedule 4 row values:
      | description      | distance | volume | cost | cycle |
      | E2E Rehaul run 1 | 15       | 400    | 1200 | 36    |
    And I add the Schedule 4 row
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Truck Rehaul-Dewater/Transfer" row "E2E Rehaul run 1" shows:
      | distance | volume | cost  | cycle | perUnit |
      | 15       | 400    | 1,200 | 36    | 3.00    |
    And the Schedule 4 "Truck Rehaul-Dewater/Transfer" totals show:
      | distance | volume | cost  | cycle |
      | 15       | 400    | 1,200 | 36    |
    And the stored Schedule 4 "Truck Rehaul-Dewater/Transfer" rows for "E2E Rehaul Loc" are:
      | description      | distance | volume | cost | cycle | perUnit |
      | E2E Rehaul run 1 | 15       | 400    | 1200 | 36    | 3       |
    When I go back from the Schedule 4 sub-page
    And I open the Schedule 4 location "E2E Rehaul Loc" for edit
    Then the Schedule 4 sub-page link "Truck Rehaul-Dewater/Transfer" shows 1 rows
    # The Cycle column is populated for THIS group only.
    And the Schedule 4 sub-page row "Truck Rehaul-Dewater/Transfer" totals show:
      | distance | volume | cost  | perUnit | cycle |
      | 15       | 400    | 1,200 | 3.00    | 36    |

  # S06 — Other Transportation: same shape, no Cycle field at all. Asserting the FIELD IS ABSENT is what
  # proves the sub-page is parameterised rather than rendering a dead input.
  @p1 @S06
  Scenario: An Other Transportation row is added and carries no Cycle field
    Given the Schedule 4 anchor "other-transportation" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Stuart Lake N" as the Schedule 4 location name
    And I open the Schedule 4 "Other Transportation" sub-page from the new location
    Then the Schedule 4 row "cycle" field is not rendered
    When I enter the following Schedule 4 row values:
      | description            | distance | volume | cost |
      | E2E Helicopter support | 5        | 100    | 2500 |
    And I add the Schedule 4 row
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Other Transportation" row "E2E Helicopter support" shows:
      | distance | volume | cost  | perUnit |
      | 5        | 100    | 2,500 | 25.00   |
    And the stored Schedule 4 "Other Transportation" rows for "E2E Stuart Lake N" are:
      | description            | distance | volume | cost | cycle | perUnit |
      | E2E Helicopter support | 5        | 100    | 2500 |       | 25      |

  # Cancelling the NAV-002 prompt must keep the reporter on the panel WITH the unsaved edit intact — the
  # dialog is a question, not a one-way door.
  @p2 @S04
  Scenario: Cancelling the unsaved-changes prompt stays on the panel and keeps the edit
    Given the Schedule 4 anchor "nav-cancel" is an editable Draft with no locations
    And the Schedule 4 location "E2E Nav Cancel" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 500    | 1000 |
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Nav Cancel" for edit
    And I enter "1500" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I note the Schedule 4 mutation count
    And I click the Schedule 4 "Towing Total" sub-page link
    And I cancel the Schedule 4 unsaved-changes prompt
    # Still on the panel, edit still there, nothing written.
    Then the Schedule 4 panel heading is "Edit Location"
    And the Schedule 4 "Lakeside Dry Dump" "cost" cell shows "1,500"
    And no further Schedule 4 write should have been sent
    And the stored Schedule 4 location "E2E Nav Cancel" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 500    | 1000 | 2       |

  # NAV-003's blocked arm: if the new location's name is invalid, the save-first flow must NOT navigate —
  # legacy gated on `!FacesUtil.hasErrorMessages()`. The rewrite gates on the same client validation that
  # blocks Save, so the reporter stays on the panel with the error.
  @p2 @S03 @S13
  Scenario: The save-first flow does not navigate when the new location has no name
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I click the Schedule 4 "Towing Total" sub-page link
    And I confirm the Schedule 4 save-first prompt
    Then the Schedule 4 sub-page is not open
    And I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 4 location name field is invalid with "Location Name can not be empty. Please enter a description."
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored

  # NAV-003's cancel arm, mirroring the NAV-002 one above (symmetry): declining "save and continue" must
  # leave the reporter on the New Location panel with what they typed, and must not save anything.
  @p2 @S03
  Scenario: Cancelling the save-first prompt stays on the new location panel
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Cancelled Save First" as the Schedule 4 location name
    And I click the Schedule 4 "Towing Total" sub-page link
    Then the Schedule 4 save-first confirmation asks "The information for the New Location must be saved before you can add other Transportation. Would you like to save the information now?"
    When I cancel the Schedule 4 save-first prompt
    Then the Schedule 4 sub-page is not open
    And the Schedule 4 panel heading is "New Location"
    And the Schedule 4 location name shows "E2E Cancelled Save First"
    And the Schedule 4 write request should not have been sent
    And no Schedule 4 locations are stored
