# UC-SCH4-001-S11 (BR-08 / NAV-005) — maintain the rows already on a sub-page
#
# Covers deleting a row, editing one IN PLACE, the running totals across several rows, and the column
# sort. Two of those are not in the legacy `.feature` set:
#   - IN-PLACE ROW EDIT is a SPEC GAP, not new behaviour: the legacy source enumerates the control (the
#     sub-page dataTable's per-row editable Description/Distance/Volume/Cost cells — technical.md Control
#     Reference, `schedule4TowingTotal.xhtml:91-164`) and the app implements it (PUT .../rows/{id}), but no
#     slice was ever written for it. Covered here; logged as SPEC-1 for the BA.
#   - COLUMN SORT is app-only chrome with no legacy counterpart. Covered lightly (@p2) because it is
#     client-side logic over the rows the reporter is reading, and a broken sort silently misrepresents
#     them.
#
# NAV-005 is a Carbon danger Modal ("Delete row"), and §Decision 4 of Story 10.3 fixed a legacy bug: the
# delete now returns the "Data deleted successfully" semantics for ALL THREE sub-pages, where legacy's
# Other-Transportation delete did not.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — maintain the rows on a sub-page

  As a Licensee
  I want to correct and remove the transportation rows recorded against a location
  So that its list-based costs stay accurate

  @p0 @S11
  Scenario: Delete a sub-page row and watch the running totals recompute
    Given the Schedule 4 anchor "delete-row" is an editable Draft with no locations
    And the Schedule 4 location "E2E Delete Row Loc" is already saved with only its name
    And that Schedule 4 location already has these "Towing Total" rows:
      | description  | distance | volume | cost |
      | E2E Keep row | 10       | 200    | 600  |
      | E2E Drop row | 30       | 300    | 900  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Delete Row Loc" for edit
    Then the Schedule 4 sub-page link "Towing Total" shows 2 rows
    When I open the Schedule 4 "Towing Total" sub-page from the saved location
    Then the Schedule 4 "Towing Total" table lists 2 rows
    And the Schedule 4 "Towing Total" totals show:
      | distance | volume | cost  |
      | 40       | 500    | 1,500 |
    When I delete the Schedule 4 "Towing Total" row "E2E Drop row"
    Then the Schedule 4 row delete confirmation asks "This will delete the current record. Do you want to continue?"
    When I confirm the Schedule 4 row delete
    Then I should see the message "Data deleted successfully"
    And the Schedule 4 "Towing Total" row "E2E Drop row" is not listed
    And the Schedule 4 "Towing Total" row "E2E Keep row" is listed
    And the Schedule 4 "Towing Total" table lists 1 rows
    # The footer recomputed from the remaining row.
    And the Schedule 4 "Towing Total" totals show:
      | distance | volume | cost |
      | 10       | 200    | 600  |
    # BR-08 at the source of truth: only that row's own report went, and the LOCATION is untouched.
    And the stored Schedule 4 "Towing Total" rows for "E2E Delete Row Loc" are:
      | description  | distance | volume | cost | cycle | perUnit |
      | E2E Keep row | 10       | 200    | 600  |       | 3       |
    And the Schedule 4 anchor stores 1 locations

  # Deleting the LAST row returns the table to its empty state and zeroes the group's count on the list —
  # the boundary the two-row case above cannot reach.
  @p1 @S11
  Scenario: Deleting the last row empties the table and clears the group count
    Given the Schedule 4 anchor "delete-last-row" is an editable Draft with no locations
    And the Schedule 4 location "E2E Last Row Loc" is already saved with only its name
    And that Schedule 4 location already has these "Other Transportation" rows:
      | description      | distance | volume | cost |
      | E2E Only the one | 4        | 40     | 160  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Last Row Loc" for edit
    And I open the Schedule 4 "Other Transportation" sub-page from the saved location
    And I delete the Schedule 4 "Other Transportation" row "E2E Only the one"
    And I confirm the Schedule 4 row delete
    Then I should see the message "Data deleted successfully"
    And the Schedule 4 "Other Transportation" table is empty
    And no Schedule 4 "Other Transportation" rows are stored for "E2E Last Row Loc"
    When I go back from the Schedule 4 sub-page
    And I open the Schedule 4 location "E2E Last Row Loc" for edit
    Then the Schedule 4 sub-page link "Other Transportation" shows 0 rows
    # With no rows, the group's amount columns fall back to em dashes rather than showing zeroes.
    And the Schedule 4 sub-page row "Other Transportation" totals show:
      | distance | volume | cost | perUnit | cycle |
      | —        | —      | —    | —       | —     |

  # SPEC GAP coverage — the in-place row edit the legacy source has a control for but no slice.
  @p1 @S11
  Scenario: Correct a row in place and save the sub-page
    Given the Schedule 4 anchor "row-edit" is an editable Draft with no locations
    And the Schedule 4 location "E2E Row Edit Loc" is already saved with only its name
    And that Schedule 4 location already has these "Towing Total" rows:
      | description     | distance | volume | cost |
      | E2E Editable row | 10      | 200    | 600  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Row Edit Loc" for edit
    And I open the Schedule 4 "Towing Total" sub-page from the saved location
    When I change the seeded Schedule 4 row "cost" to "1000"
    # The row's $/m³ and the footer track the in-progress edit BEFORE the save (legacy parity: the totals
    # moved as you typed), so this is the visible difference between "typed" and "saved".
    And I save the Schedule 4 sub-page
    Then I should see the message "Data saved successfully"
    And the Schedule 4 "Towing Total" row "E2E Editable row" shows:
      | distance | volume | cost  | perUnit |
      | 10       | 200    | 1,000 | 5.00    |
    And the stored Schedule 4 "Towing Total" rows for "E2E Row Edit Loc" are:
      | description      | distance | volume | cost | cycle | perUnit |
      | E2E Editable row | 10       | 200    | 1000 |       | 5       |

  # An in-place edit is validated on Save with the same sub-page bounds as the add-row form, and nothing
  # is written when it fails.
  @p1 @S11 @S25
  Scenario: An out-of-range in-place edit blocks the sub-page save
    Given the Schedule 4 anchor "row-edit-reject" is an editable Draft with no locations
    And the Schedule 4 location "E2E Row Reject Loc" is already saved with only its name
    And that Schedule 4 location already has these "Towing Total" rows:
      | description       | distance | volume | cost |
      | E2E Rejected edit | 10       | 200    | 600  |
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Row Reject Loc" for edit
    And I open the Schedule 4 "Towing Total" sub-page from the saved location
    And I note the Schedule 4 mutation count
    And I change the seeded Schedule 4 row "cost" to "10000000"
    And I save the Schedule 4 sub-page
    Then the seeded Schedule 4 row "cost" cell is invalid with "Entered cost must be between -9,999,999 and 9,999,999."
    And no further Schedule 4 write should have been sent
    And the stored Schedule 4 "Towing Total" rows for "E2E Row Reject Loc" are:
      | description       | distance | volume | cost | cycle | perUnit |
      | E2E Rejected edit | 10       | 200    | 600  |       | 3       |

  # The running totals across several rows, including the Cycle column that only Truck Rehaul has.
  @p1 @S05
  Scenario: The running totals sum every row, Cycle included
    Given the Schedule 4 anchor "row-totals" is an editable Draft with no locations
    And the Schedule 4 location "E2E Totals Loc" is already saved with only its name
    And that Schedule 4 location already has these "Truck Rehaul-Dewater/Transfer" rows:
      | description | distance | volume | cost | cycle |
      | E2E Run A   | 15       | 400    | 1200 | 36    |
      | E2E Run B   | 25       | 100    | 300  | 14    |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Totals Loc" for edit
    And I open the Schedule 4 "Truck Rehaul-Dewater/Transfer" sub-page from the saved location
    Then the Schedule 4 "Truck Rehaul-Dewater/Transfer" table lists 2 rows
    And the Schedule 4 "Truck Rehaul-Dewater/Transfer" totals show:
      | distance | volume | cost  | cycle |
      | 40       | 500    | 1,500 | 50    |
    # And the group's grid row shows the same rolled-up figures, with $/m³ from the summed columns
    # (1500/500 = 3.00) rather than a sum of the per-row rates.
    When I go back from the Schedule 4 sub-page
    And I open the Schedule 4 location "E2E Totals Loc" for edit
    Then the Schedule 4 sub-page row "Truck Rehaul-Dewater/Transfer" totals show:
      | distance | volume | cost  | perUnit | cycle |
      | 40       | 500    | 1,500 | 3.00    | 50    |

  # App-only chrome (no legacy counterpart): the three-state column sort. Nulls sort last, so the rows
  # are given distinct values and the assertion is on ORDER, not on the values themselves.
  #
  # `@app-only` instead of an `@S..` traceability tag: there is no source slice to trace to, and marking that
  # explicitly is better than an untagged scenario that reads like an oversight (see coverage.md's row for it).
  @p2 @app-only
  Scenario: The rows table sorts by a column through its three states
    Given the Schedule 4 anchor "row-sort" is an editable Draft with no locations
    And the Schedule 4 location "E2E Sort Loc" is already saved with only its name
    And that Schedule 4 location already has these "Towing Total" rows:
      | description | distance | volume | cost |
      | E2E Alpha   | 30       | 100    | 300  |
      | E2E Zulu    | 10       | 200    | 600  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Sort Loc" for edit
    And I open the Schedule 4 "Towing Total" sub-page from the saved location
    Then the Schedule 4 "Towing Total" "Distance (km)" column is sorted "none"
    When I sort the Schedule 4 "Towing Total" rows by "Distance (km)"
    Then the Schedule 4 "Towing Total" "Distance (km)" column is sorted "ascending"
    And the Schedule 4 "Towing Total" row order is "E2E Zulu, E2E Alpha"
    When I sort the Schedule 4 "Towing Total" rows by "Distance (km)"
    Then the Schedule 4 "Towing Total" "Distance (km)" column is sorted "descending"
    And the Schedule 4 "Towing Total" row order is "E2E Alpha, E2E Zulu"
    # Sorting is display-only: the stored rows are untouched by it.
    And the stored Schedule 4 "Towing Total" rows for "E2E Sort Loc" are:
      | description | distance | volume | cost | cycle | perUnit |
      | E2E Alpha   | 30       | 100    | 300  |       | 3       |
      | E2E Zulu    | 10       | 200    | 600  |       | 3       |
