# Check Status and unsaved changes — re-grounded by Story 26.2 (ruling D7(a), deviation (C)).
#
# WHAT THESE SCENARIOS USED TO BE. Both were DELIBERATELY RED, reproducing defects.md DIV-5 (tracked
# upstream as bcgov/nr-ilcr#359): Check Status reported on the LAST SAVED data and silently ignored a cost
# typed into the old per-row inline editor, because the button was never gated on that editor. The same
# app-wide defect is Schedule 3's DIV-6; Schedule 3's register entry holds the full analysis.
#
# WHAT CHANGED ON THIS PAGE. Story 26.2 rebuilt Schedule 11 to legacy's page model (every row live, one
# page-level Save), and Scho ruled D7(a): Check Status keeps judging the SAVED data, and BOTH Check Status
# buttons are disabled while any edit or flagged delete is unsaved — with a screen-reader reason, "Save your
# changes before checking status", since a disabled button cannot take focus. A verdict over unsaved work
# therefore cannot be produced here any more, so neither the false-GREEN (S21) nor the false-RED (S22) the
# divergence described is reachable on Schedule 11. That is a DEVIATION from legacy, which evaluated its
# unsaved in-memory model (`Schedule11MB.java:154-176`) — recorded as the story's deviation (C) — and it is
# the Schedule 11 instance of DIV-5 CLOSED; #359 stays open for the other schedules.
#
# So these are now GREEN scenarios of the ruled behaviour: the change greys Check Status (with its reason),
# the Save re-enables it, and the verdict then describes what was saved. Both arms are kept because they
# still cover opposite directions — a cost removed (S21) and a cost supplied (S22).
#
# The upstream slices UC-SCH11-001-S21/S22 describe this against `addActualCost` — the ADD panel. That is a
# NEW row, not a stored requirement changed on screen, so it cannot express the rule; the slices are right
# about legacy and it is the RE-GROUNDING onto the rewrite that moves it to an existing row.
#
# DEDICATED, SEEDED ANCHORS — AND WHY THEY ARE NOT `check-met` / `check-missing-actual`.
# Both scenarios seed a location, so each needs a mill-year no other scenario writes to. Reusing the S04/S05
# anchors was tried FIRST and is unsafe: their Givens add a location through the API, so a second scenario
# on either collides under `fullyParallel` — observed as red tests on 2026-08-27. The extract had no free
# Draft left either, so `real-test-data-patches/sch11/unsaved-check-anchors.sql` creates these two.
# Preflight asserts both, which doubles as the patch's applied-ness guard.
#
# CLEANUP: each scenario seeds its own location through the API via the shared Given and the registry
# removes it, including any cost the Save wrote.

@sch11 @UC-SCH11-001 @check-status-unsaved
Feature: Report Basic Silviculture Costs (Schedule 11) — Check Status and unsaved changes
  As a Licensee
  I want Check Status never to judge a schedule while my changes are unsaved
  So that I am not told the schedule is complete, or incomplete, on the strength of a change I have not saved

  @p1 @S21
  Scenario: Clearing an Actual Cost greys Check Status until the Save, and the check then flags it
    Given the Schedule 11 anchor "check-unsaved-violation" has a seeded location "E2E S21 unsaved"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    # As stored the location carries both costs, so Check Status passes.
    And I run Check Status
    Then I should see the message "All requirements for this schedule have been met"
    # Empty the Actual Cost without saving: the verdict above no longer describes the screen.
    When I change the Schedule 11 location "E2E S21 unsaved" field "Actual Cost" to ""
    Then I should not see the message "All requirements for this schedule have been met"
    And both Check Status buttons are disabled until the change is saved
    When I save Schedule 11
    Then I should see the message "Data saved successfully"
    And both Check Status buttons are enabled
    When I run Check Status
    Then the Schedule 11 check status shows verbatim "location  : E2E S21 unsaved - Actual cost: Value Required"
    And I should not see the message "All requirements for this schedule have been met"

  @p1 @S22
  Scenario: Supplying a missing Actual Cost greys Check Status until the Save, and the check then passes
    Given the Schedule 11 anchor "check-unsaved-fix" has a seeded location "E2E S22 unsaved" with no "actual" cost
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I run Check Status
    Then the Schedule 11 check status shows verbatim "location  : E2E S22 unsaved - Actual cost: Value Required"
    # Supply it without saving: the flag above no longer describes the screen.
    When I change the Schedule 11 location "E2E S22 unsaved" field "Actual Cost" to "12500"
    Then I should not see the error "location  : E2E S22 unsaved - Actual cost: Value Required"
    And both Check Status buttons are disabled until the change is saved
    When I save Schedule 11
    Then I should see the message "Data saved successfully"
    When I run Check Status
    # It is this location's only outstanding requirement, so the verdict flips as well as the flag clearing.
    Then I should not see the error "location  : E2E S22 unsaved - Actual cost: Value Required"
    And I should see the message "All requirements for this schedule have been met"
