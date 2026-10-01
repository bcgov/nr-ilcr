# UC-SCH3-001 — a concurrent edit is REFUSED, not lost (AR11's optimistic lock). Closes coverage gap
# GAP-2; mirrors `sch4` and `sch11`, which both carry the same scenario.
#
# WHY THIS EXISTS EVEN THOUGH THE BACKEND HAS ITS OWN TEST. The service tests prove the ENDPOINT refuses a
# stale `revisionCount`. They say nothing about what the REPORTER sees. If the app swallowed the 409 — or
# showed a generic failure and then re-seeded the form from its own state — a reporter would believe their
# correction saved when it had not, and the other session's figure would look like theirs. That is the
# risk this covers, and a service test cannot.
#
# NO SECOND BROWSER CONTEXT IS NEEDED. The page copies `revisionCount` into React state when it loads, so
# one out-of-band API save moves the stored token past the one the browser holds. `sch4`'s own note
# records that an earlier draft calling for two sessions overstated the cost.
#
# THE ASSERTION HAS THREE HALVES ON PURPOSE:
#   1. the verbatim 409 detail reaches the screen (AD-8 — the client never authors this text);
#   2. the OTHER session's value is the survivor in storage; and
#   3. our rejected value is NOT stored.
# Half 1 alone would pass an app that showed the error and still wrote our value, which is the actual
# lost-update bug this is guarding against.
#
# ANCHOR. `stale-edit` (12050/2018) is its own seeded anchor rather than a shared one, because the
# scenario writes twice. It sits on a mill-year the sch4 suite also pins — the established pattern for
# this suite, declared in `preflight/sch4-anchors.setup.ts`, and safe because Schedule 3 and Schedule 4
# write different categories' rows. The scenario changes ONE fixed cost line and never a timber volume,
# so the BR-09 crown push cannot fire and Schedule 1 is never touched.

@sch3 @UC-SCH3-001 @concurrency
Feature: Report Forest Management Administration Costs (Schedule 3) — a stale save is refused
  As a mill reporter sharing a schedule with a colleague
  I want to be told when the schedule I am editing has changed underneath me
  So that I do not silently overwrite someone else's correction

  @p1 @S01
  Scenario: Saving a Schedule 3 that another session already changed is refused, and their value survives
    Given the Schedule 3 anchor "stale-edit"
    And the Wages line has a saved Harvest of "10000"
    And I have selected that mill and reporting year on the Home page
    # Loading the page captures the schedule's revisionCount into React state.
    When I open Schedule 3
    # Someone else saves first. This succeeds and moves the stored token on.
    And another session changes the saved Wages line Harvest to "4242"
    # Our save now carries the token captured before their change.
    And I enter "7777" into the "Wages/Salaries, incl Benefits" Harvest field
    And I save Schedule 3
    Then I should see the error "This schedule was changed by another user. Please reload and try again."
    # The conflict must NOT be silently swallowed: their 4242 is stored, our 7777 is not.
    And the stored Wages line Harvest is "4242"
