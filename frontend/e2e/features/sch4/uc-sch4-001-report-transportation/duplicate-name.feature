# UC-SCH4-001-S14 (EF1 / BR-02 / ERR-002) — a duplicate location name is refused
#
# RE-GROUNDING NOTE — two deliberate differences from the legacy slice, both recorded in coverage.md:
#   1. WHERE the check runs. Legacy checked the name live on the field's own `f:ajax` listener
#      (`onLocationNameItemChange`) as well as on Save. The rewrite makes the SERVER authoritative: the
#      save PUTs, and a case-insensitive collision comes back as 409 `locationAlreadyExists`, rendered
#      verbatim in the error banner. Same message, same outcome, one fewer round trip while typing.
#   2. WHAT HAPPENS TO THE FIELD afterwards. Legacy RESET the name field to its prior value (ERR-002's
#      own trigger note says so). The rewrite deliberately KEEPS what the reporter typed — Story 10.5's
#      "entered values retained on failure" AC — so the name can be corrected instead of retyped. Logged
#      as DIV-5 (suspected-intentional-change, log-only) and asserted AS BUILT below, so a future change
#      in either direction is caught rather than absorbed.
#
# The case-insensitivity is exercised directly (the saved name differs only by case), which is why the
# slice catalogue needed no separate casing slice.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — a duplicate location name is refused

  As a Licensee
  I want to be told when the name I entered already exists, even in a different case
  So that I don't create two entries for what is really the same location

  @p1 @S14
  Scenario: A name that differs only by case is rejected, then corrected and saved
    Given the Schedule 4 anchor "duplicate-name" is an editable Draft with no locations
    And the Schedule 4 location "E2E Lakeside" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 100    | 400  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I add a new Schedule 4 location
    And I enter "E2E LAKESIDE" as the Schedule 4 location name
    And I save the Schedule 4 location
    # ERR-002 verbatim from the API's ProblemDetail (AD-8) — the client never hardcodes it.
    Then I should see the error "Location Name already exists."
    # As-built (DIV-5): the entered name is still on screen, ready to be corrected.
    And the Schedule 4 location name shows "E2E LAKESIDE"
    And the Schedule 4 panel heading is "New Location"
    # Nothing was created: still exactly the one seeded location.
    And the Schedule 4 anchor stores 1 locations
    And no Schedule 4 location named "E2E LAKESIDE" is stored
    # Recovery: a unique name saves.
    When I enter "E2E Lakeside North" as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And the Schedule 4 location "E2E Lakeside North" is listed
    And the Schedule 4 anchor stores 2 locations

  # BR-02 excludes the location's OWN family from the comparison, so a no-op save and a case-only
  # self-rename must both succeed. Without this the "already exists" guard would make a saved location
  # impossible to re-save — the exact bug the server's `oldName` exclusion exists to prevent.
  @p1 @S14 @S02
  Scenario: Re-saving a location under its own name, or a case variant of it, is allowed
    Given the Schedule 4 anchor "self-rename" is an editable Draft with no locations
    And the Schedule 4 location "E2E Self Rename" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 100    | 400  |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E Self Rename" for edit
    # A no-op save first: the name is unchanged.
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And I should not see the message "Location Name already exists."
    # Then a case-only rename of itself.
    When I enter "E2E SELF RENAME" as the Schedule 4 location name
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    And I should not see the message "Location Name already exists."
    And the Schedule 4 location "E2E SELF RENAME" is listed
    And the Schedule 4 anchor stores 1 locations
