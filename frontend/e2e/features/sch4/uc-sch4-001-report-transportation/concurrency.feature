# UC-SCH4-001 — concurrent edits are rejected, not lost (BR-01 storage + §Decision 3's optimistic lock)
#
# WHY THIS EXISTS EVEN THOUGH AN IT COVERS THE SERVER. `Schedule4WriteIT:292` ("stale revisionCount -> 409,
# no overwrite") proves the ENDPOINT rejects a stale token and does not overwrite. It says nothing about what
# the reporter SEES. If the app swallowed the 409 — or showed a generic failure and then re-seeded the panel
# from its own state — a licensee would believe their correction saved when it had not, and the other
# session's figure would look like theirs. That is the risk this scenario covers, and the IT cannot.
#
# NO SECOND BROWSER CONTEXT IS NEEDED. The panel copies the location's `revisionCount` into React state when
# it opens (`components/schedule4/index.tsx:277`), so one out-of-band API save moves the stored token past
# the one the browser holds. This mirrors `sch11`'s `concurrency.feature`, whose own note records that an
# earlier draft calling for two sessions overstated the cost.
#
# The assertion has two halves ON PURPOSE:
#   1. the verbatim 409 detail reaches the screen (AD-8 — the client never authors this text), and
#   2. the OTHER session's value is the survivor in storage.
# Half 2 is what makes a lost-update bug impossible to pass: an app that showed the error but still wrote
# our value would satisfy half 1 alone.

@UC-SCH4-001 @sch4 @concurrency
Feature: Schedule 4 — a save carrying a stale token is rejected, and the other session's value survives

  As a mill reporter sharing a schedule with a colleague
  I want to be told when the location I am editing has changed underneath me
  So that I do not silently overwrite someone else's correction

  @p1 @S02
  Scenario: Saving a location that another session already changed is rejected, and their value survives
    Given the Schedule 4 anchor "stale-edit" is an editable Draft with no locations
    And the Schedule 4 location "E2E Stale Edit" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1000   | 5000 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    # Opening the panel captures this location's revisionCount into React state.
    And I open the Schedule 4 location "E2E Stale Edit" for edit
    # Someone else saves first. This succeeds and moves the stored token on.
    And another session changes the Schedule 4 location "E2E Stale Edit" "Lakeside Dry Dump" cost to "4242"
    # Our save now carries the token captured before their change.
    And I enter "7777" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    # Verbatim from the 409 ProblemDetail detail.
    Then I should see the error "This schedule was changed by another user. Please reload and try again."
    # The conflict must NOT be silently swallowed: their 4242 is stored, our 7777 is not.
    And the stored Schedule 4 location "E2E Stale Edit" is:
      | category          | distance | volume | cost | perUnit |
      | Lakeside Dry Dump |          | 1000   | 4242 | 4.242   |
