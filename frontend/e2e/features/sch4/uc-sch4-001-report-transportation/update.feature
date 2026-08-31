# UC-SCH4-001-S02 (AF1) — edit an existing location, plus the persistence proof
#
# RE-GROUNDING NOTE: legacy opened the saved location in the `schedule4ExistingLocation.xhtml` fragment
# (its own `locationName` / `lakeSideDryDumpCost` id set); the rewrite opens the SAME panel it uses for a
# new location, seeded from the stored family, and sends `id` + `revisionCount` so the write is
# rename-safe and optimistically locked (§Decision 2/3). An edit therefore bumps the revision — asserted
# below, because "the save was applied to the right family" is otherwise invisible.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — edit a saved location

  As a Licensee
  I want to change a saved location's transportation costs
  So that I can correct the mill's reported figures

  @p0 @S02
  Scenario: Change a saved location's category cost and re-save
    Given the Schedule 4 anchor "edit" is an editable Draft with no locations
    And the Schedule 4 location "E2E Edit Location" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the Schedule 4 location "E2E Edit Location" is listed
    And the Schedule 4 location "E2E Edit Location" offers a "Edit" action
    When I open the Schedule 4 location "E2E Edit Location" for edit
    Then the Schedule 4 panel heading is "Edit Location"
    # Seeded from storage: the stored amounts AND the server's $/m³ are on screen before any edit.
    And the Schedule 4 category grid shows:
      | category          | distance | volume | cost  | perUnit |
      | Lakeside Dry Dump | —        | 1,200  | 3,600 | 3.00    |
    When I enter "7200" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    # The edit landed on the same family (no second location created) and the server recomputed $/m³
    # from the new cost (7200/1200 = 6). The revision bump proves the optimistic-lock path was used.
    And the Schedule 4 anchor stores 1 locations
    And the stored Schedule 4 location "E2E Edit Location" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1200   | 7200 | 6       |
    And the stored Schedule 4 location "E2E Edit Location" revision is 2
    # CNT-001: the sub-page group labels carry their live row count even on an untouched location.
    And the Schedule 4 sub-page link "Towing Total" shows 0 rows

  # A second Save after the first must not 409: the panel refreshes its optimistic-lock token from the
  # save response (the rewrite's reason for staying on the record). Legacy had no token at all, so this is
  # a rewrite-specific risk worth its own scenario.
  @p1 @S02
  Scenario: A second consecutive save on the same open panel succeeds
    Given the Schedule 4 anchor "twice-saved" is an editable Draft with no locations
    And the Schedule 4 location "E2E Twice Saved" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 100    | 500  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Twice Saved" for edit
    And I enter "600" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    When I enter "700" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And I should not see the message "This schedule was changed by another user. Please reload and try again."
    And the stored Schedule 4 location "E2E Twice Saved" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 100    | 700  | 7       |

  # Persistence proper: a full page reload re-GETs the document, so what survives is what the DATABASE
  # holds — not client state that merely looked right after the save.
  @p1 @S02
  Scenario: A saved location survives a full page reload
    Given the Schedule 4 anchor "persistence" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Persisted Loc" as the Schedule 4 location name
    And I enter the following Schedule 4 category amounts:
      | category          | distance | volume | cost |
      | Water Dump        |          | 250    | 1000 |
      | Rail Haul         | 12       | 60     | 240  |
    And I enter Schedule 4 comments "survives a reload"
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    When I reopen Schedule 4
    Then the Schedule 4 location "E2E Persisted Loc" is listed
    When I open the Schedule 4 location "E2E Persisted Loc" for edit
    Then the Schedule 4 category grid shows:
      | category   | distance | volume | cost  | perUnit |
      | Water Dump | —        | 250    | 1,000 | 4.00    |
      | Rail Haul  | 12       | 60     | 240   | 4.00    |
    And the Schedule 4 comments show "survives a reload"

  # A rename must move the whole FAMILY (the primary report and every distance-category child share the
  # LOCATION_DESCRIPTION), not orphan the children under the old name — the rewrite's `renameFamily`.
  # Legacy keyed everything on the name, so this risk is new and untested anywhere else.
  @p1 @S02
  Scenario: Renaming a location keeps its distance-category child amounts
    Given the Schedule 4 anchor "rename" is an editable Draft with no locations
    And the Schedule 4 location "E2E Before Rename" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 400    | 800  |
      | Crew Barge/Ferry  | 9        | 90     | 270  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Before Rename" for edit
    And I enter "E2E After Rename" as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the Schedule 4 location "E2E After Rename" is listed
    And the Schedule 4 location "E2E Before Rename" is not listed
    # One location, both categories still attached — the child report followed the rename.
    And the Schedule 4 anchor stores 1 locations
    And the stored Schedule 4 location "E2E After Rename" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 400    | 800  | 2       |
      | Crew Barge/Ferry  | 9        | 90     | 270  | 3       |

  # ---------------------------------------------------------------------------------------------------
  # BUG-4 — DELIBERATELY RED. See defects.md BUG-4.
  #
  # Clearing a distance category to fully-empty must DELETE its child report (§Decision 1's write mirror).
  # It does not: the save reports success and the stored child survives untouched.
  #
  # THIS SCENARIO USED TO PASS, AND SHOULD NOT HAVE. Its assertion was the SUBSET step
  # ("the stored Schedule 4 location … is:") listing only the surviving Lakeside Dry Dump row — so a
  # surviving Truck Barge/Ferry was invisible to it and the removal, which is the scenario's whole point,
  # was never checked. It now uses the EXACT-set step, which fails while the cleared category persists.
  # ---------------------------------------------------------------------------------------------------
  @p0 @S02 @discovered-bug
  Scenario: Clearing a distance category removes it and leaves the rest of the location intact [DISCOVERED BUG — a category cleared to empty is silently discarded; defects.md BUG-4 / issue #335]
    Given the Schedule 4 anchor "clear-category" is an editable Draft with no locations
    And the Schedule 4 location "E2E Cleared Category" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 400    | 800  |
      | Truck Barge/Ferry | 20       | 100    | 500  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Cleared Category" for edit
    And I clear the Schedule 4 "Truck Barge/Ferry" "distance" cell
    And I clear the Schedule 4 "Truck Barge/Ferry" "volume" cell
    And I clear the Schedule 4 "Truck Barge/Ferry" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    # EXACT set: Lakeside must survive AND Truck Barge/Ferry must be gone. The subset step could not see
    # the second half — see the header.
    And the stored Schedule 4 location "E2E Cleared Category" has exactly these categories:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 400    | 800  |

  # ---------------------------------------------------------------------------------------------------
  # BUG-4, the FIXED-category half — DELIBERATELY RED. See defects.md BUG-4.
  #
  # This walks the bug's BOUNDARY in one journey, because the two halves behave differently and a reader
  # needs both to understand the defect:
  #   1. a PARTIAL clear (empty the Cost, leave the Volume) DOES persist — the category still has a value,
  #      so the client keeps sending it and the server upserts the null. This half passes today.
  #   2. clearing the LAST value in that category does NOT persist — the client drops the whole category
  #      from the payload (`buildRequest`'s `if (!anyPresent) return []`) and the server only iterates what
  #      was sent, so the stored row survives and the reporter's correction is silently lost.
  # Both halves are asserted here so the fix cannot satisfy one and break the other.
  # ---------------------------------------------------------------------------------------------------
  @p0 @S02 @discovered-bug
  Scenario: Clearing a fixed category's amounts persists, one field at a time and then entirely [DISCOVERED BUG — a category cleared to empty is silently discarded; defects.md BUG-4 / issue #335]
    Given the Schedule 4 anchor "clear-fixed" is an editable Draft with no locations
    And the Schedule 4 location "E2E Clear Fixed" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 400    | 800  |
      | Water Dump        |          | 7      | 70   |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Clear Fixed" for edit
    # 1. PARTIAL clear — the Volume keeps the category alive, so this half already works.
    And I clear the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the stored Schedule 4 location "E2E Clear Fixed" has exactly these categories:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 400    |      |
      | Water Dump        |          | 7      | 70   |
    # 2. Now empty the LAST value in that category. The amounts must go, not come back.
    When I open the Schedule 4 location "E2E Clear Fixed" for edit
    And I clear the Schedule 4 "Lakeside Dry Dump" "volume" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the stored Schedule 4 location "E2E Clear Fixed" has exactly these categories:
      | category   | distance | volume | cost |
      | Water Dump |          | 7      | 70   |
