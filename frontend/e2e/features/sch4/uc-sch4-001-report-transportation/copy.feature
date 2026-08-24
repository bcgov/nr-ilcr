# UC-SCH4-001-S07 (AF3 / BR-09 / WRN-001) — copy an existing location
#
# RE-GROUNDING NOTE: copy is a CLIENT-side prefill in the rewrite (there is no server copy endpoint) —
# the New panel opens seeded from the source's amounts with the NAME CLEARED, and Save is an ordinary
# create. Legacy did the same thing through `Schedule4MB.copyLocation()`, so the observable behaviour is
# unchanged: the WRN-001 nudge appears, the amounts are prefilled, and a new unique name is required.
#
# WRN-001 renders as a Carbon WARNING InlineNotification titled "Copy location" — not a `p:messages`
# growl — and carries the source location's name verbatim.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — copy an existing location

  As a Licensee
  I want to copy an existing location's values into a new one
  So that I can reuse similar transportation cost data without re-entering it

  @p1 @S07
  Scenario: Copy a location, give it a new name, and save it as a second location
    Given the Schedule 4 anchor "copy" is an editable Draft with no locations
    And the Schedule 4 location "E2E Copy Source" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
      | Truck Barge/Ferry | 50       | 800    | 4000 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I copy the Schedule 4 location "E2E Copy Source"
    # BR-09 + WRN-001: a copy is a NEW location that must be given its own name first.
    Then the Schedule 4 panel heading is "Copy Location"
    And I should see the warning "To complete copy of Location: E2E Copy Source, provide a new Location Name and invoke save."
    And the Schedule 4 location name is empty
    # The source's amounts came across — including the distance-based category's own Distance.
    And the Schedule 4 "Lakeside Dry Dump" "volume" cell shows "1,200"
    And the Schedule 4 "Lakeside Dry Dump" "cost" cell shows "3,600"
    And the Schedule 4 "Truck Barge/Ferry" "distance" cell shows "50"
    And the Schedule 4 "Truck Barge/Ferry" "volume" cell shows "800"
    And the Schedule 4 "Truck Barge/Ferry" "cost" cell shows "4,000"
    When I enter "E2E Copy Target" as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    # Both locations now exist, and the copy carries its own stored amounts — the source is untouched.
    And the Schedule 4 location "E2E Copy Source" is listed
    And the Schedule 4 location "E2E Copy Target" is listed
    And the Schedule 4 anchor stores 2 locations
    And the stored Schedule 4 location "E2E Copy Target" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1200   | 3600 | 3       |
      | Truck Barge/Ferry | 50       | 800    | 4000 | 5       |
    And the stored Schedule 4 location "E2E Copy Source" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1200   | 3600 | 3       |
      | Truck Barge/Ferry | 50       | 800    | 4000 | 5       |

  # The gap analysis called this combination out and resolved it to S13's outcome (no separate slice):
  # saving a copy without supplying a name is just a blank-name save. Covered here because the COPY path
  # reaches it differently — the name is cleared BY THE APP rather than never typed.
  @p2 @S07 @S13
  Scenario: Saving a copy without naming it is refused
    Given the Schedule 4 anchor "copy-duplicate" is an editable Draft with no locations
    And the Schedule 4 location "E2E Unnamed Copy Src" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 100    | 200  |
    And a spy is watching the Schedule 4 write requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I note the Schedule 4 mutation count
    And I copy the Schedule 4 location "E2E Unnamed Copy Src"
    And I save the Schedule 4 location
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 4 location name field is invalid with "Location Name can not be empty. Please enter a description."
    # Client-gated: no write left the browser, and the anchor still holds only the source.
    And no further Schedule 4 write should have been sent
    And the Schedule 4 anchor stores 1 locations

  # The copy also clones the source's COMMENTS (the rewrite's openCopy does, deliberately — only the name
  # is cleared). Legacy's copyLocation copied the whole record too, so this is parity, and it is worth
  # pinning because "which fields a copy carries" is exactly the kind of thing a refactor drops silently.
  @p2 @S07
  Scenario: A copy carries the source's comments
    Given the Schedule 4 anchor "copy-comments" is an editable Draft with no locations
    And the Schedule 4 location "E2E Comment Source" is already saved with comments "carried onto the copy"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I copy the Schedule 4 location "E2E Comment Source"
    Then the Schedule 4 comments show "carried onto the copy"
    When I enter "E2E Comment Copy" as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the stored Schedule 4 location "E2E Comment Copy" has the comments "carried onto the copy"
