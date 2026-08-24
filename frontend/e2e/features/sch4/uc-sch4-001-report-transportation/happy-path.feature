# UC-SCH4-001-S01 / S08 / S09 — add a dump location (Happy Path + the two optional-entry alternatives)
#
# Re-grounded from the legacy slices onto the React app: route /schedule-4 (not schedule4.xhtml), ONE
# panel for New/Edit/Copy/View (not two `ui:include` fragments with `new`-prefixed twin ids), Carbon ids
# keyed by the legacy cost-item code (`40-volume`, `47-distance` — not `newLakeSideDryDumpVolume`), and
# every success string comes from the API's own `message.text` (AD-8) rather than a JSF `p:messages` panel.
#
# S01 is the BR-05/BR-06 proof. It asserts the empty document first, then the entered values, then reads
# the STORED record back through the API — so a success banner alone can never make it pass. The figures
# are pinned in fixtures/sch4/schedule4-test-data.ts from a real probe of this anchor, never predicted.
#
# The category grid has 12 amount rows (9 fixed + 3 distance-based) interleaved with the 3 list sub-page
# group rows at their legacy code positions (43 Towing, 46 Truck Rehaul, 55 Other); dead code 54 is
# absent. S01 pins that whole order once, here, rather than in every scenario.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — add a new dump location with its transportation costs

  As a Licensee
  I want to add a dump location with its fixed-category and distance-based transportation costs
  So that those costs contribute to the mill's interior stumpage costing

  @p0 @S01
  Scenario: Add a location with a fixed and a distance-based category, then confirm with Check Status
    Given the Schedule 4 anchor "happy-path" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    Then the Schedule 4 location list is empty
    And the Schedule 4 Add New Location button is enabled
    When I add a new Schedule 4 location
    Then the Schedule 4 panel heading is "New Location"
    # The grid's legacy row order, interleaved sub-page groups and all — pinned once.
    And the Schedule 4 grid rows are in legacy order
    And the Schedule 4 sub-page link "Towing Total" shows 0 rows
    When I enter "E2E Cluculz Creek" as the Schedule 4 location name
    And I enter the following Schedule 4 category amounts:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
      | Truck Barge/Ferry | 50       | 800    | 4000 |
    And I enter Schedule 4 comments "E2E happy path — Schedule 4"
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    # The rewrite STAYS on the saved record and re-opens it in edit mode (refreshing the optimistic-lock
    # token), where legacy redisplayed it through the ExistingLocation fragment. Same outcome: the saved
    # location is on screen and immediately re-editable.
    And the Schedule 4 panel heading is "Edit Location"
    And the Schedule 4 location "E2E Cluculz Creek" is listed
    # BR-05/AD-6 at the source of truth: $/m³ is computed SERVER-side (3600/1200 = 3, 4000/800 = 5) and
    # was never sent by the client. Read back through the API, so this proves storage + derivation rather
    # than client state. (What the just-saved PANEL shows is a separate matter — see divergence DIV-4 in
    # nav-and-recompute.feature.)
    And the stored Schedule 4 location "E2E Cluculz Creek" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1200   | 3600 | 3       |
      | Truck Barge/Ferry | 50       | 800    | 4000 | 5       |
    And the stored Schedule 4 location "E2E Cluculz Creek" has the comments "E2E happy path — Schedule 4"
    And the stored Schedule 4 location "E2E Cluculz Creek" revision is 1
    # Re-opening the location renders the recomputed figures the server returned.
    When I go back from the Schedule 4 panel
    And I open the Schedule 4 location "E2E Cluculz Creek" for edit
    Then the Schedule 4 category grid shows:
      | category          | distance | volume | cost  | perUnit |
      | Lakeside Dry Dump | —        | 1,200  | 3,600 | 3.00    |
      | Truck Barge/Ferry | 50       | 800    | 4,000 | 5.00    |
    # BR-07 / SUC-005 + SUC-006: every stored category carries a Cost, so the location AND the whole
    # schedule pass.
    When I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Cluculz Creek" is met
    And I should see the message "All requirements for this schedule have been met"

  # S08 — every category amount is optional (the Data Field Reference's "category itself optional"), so a
  # name-only location is a legitimate save: it reserves the entry before the figures are available.
  @p1 @S08
  Scenario: Save a location with only its name and no category amounts
    Given the Schedule 4 anchor "name-only" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E Fraser Flats" as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the Schedule 4 location "E2E Fraser Flats" is listed
    # No category rows at all — a blank category is NOT persisted as a zero-amount row.
    And the stored Schedule 4 location "E2E Fraser Flats" carries no amounts
    # BR-07: a location with no stored category has nothing to require, so Check Status passes it.
    When I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Fraser Flats" is met

  # S09 — the 30-character boundary is accepted and stored untruncated. The bound is the DB column width
  # (TRANSPORTATION_REPORT.LOCATION_DESCRIPTION is VARCHAR2(30)) and the server enforces it too
  # (@Size(max=30) -> 400 "Location Name can not exceed 30 characters."), but the field carries
  # maxLength={30}, so the browser cannot submit an over-long name at all — see the second scenario.
  @p2 @S09
  Scenario: A 30-character location name is accepted and stored in full
    Given the Schedule 4 anchor "name-boundary" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "AbcdefghijAbcdefghijAbcdefg304" as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the Schedule 4 location "AbcdefghijAbcdefghijAbcdefg304" is listed
    When I go back from the Schedule 4 panel
    And I open the Schedule 4 location "AbcdefghijAbcdefghijAbcdefg304" for edit
    Then the Schedule 4 location name shows "AbcdefghijAbcdefghijAbcdefg304"

  # The other side of the boundary: typing a 31st character is REFUSED BY THE INPUT (maxLength), so the
  # name silently stops at 30 rather than reaching the server's @Size guard. Covering it here is what
  # makes the server-side "can not exceed 30 characters" message's UI-unreachability a documented fact
  # rather than an untested assumption (see coverage.md).
  #
  # Runs on the VALIDATE-ONLY anchor, not on "name-boundary": nothing is written here, and sharing a
  # mutating anchor with the scenario above would let two parallel scenarios (and two cleanups) collide.
  @p2 @S09
  Scenario: The location name field refuses a 31st character rather than sending it
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "AbcdefghijAbcdefghijAbcdefg304X" as the Schedule 4 location name
    Then the Schedule 4 location name shows "AbcdefghijAbcdefghijAbcdefg304"
    And the Schedule 4 write request should not have been sent
