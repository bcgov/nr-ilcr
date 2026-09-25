# Check Status evaluates what is ON SCREEN, including unsaved edits. Both scenarios were DELIBERATELY RED
# until 2026-09-25, reproducing defects.md DIV-2 (bcgov/nr-ilcr#359): Check Status judged the LAST SAVED
# schedule and ignored anything typed since. They went green, unedited, when #359's Schedules 1–3 fix gave
# `POST /api/v1/schedule2/check-status` a body carrying the on-screen values. Schedule 3's DIV-6 holds the
# full analysis.
#
# That is legacy parity: its Check Status was a full JSF postback (`ajax="false"`), so every submitted
# field reached the bean before the check ran.
#
# BOTH ARMS ARE KEPT — the defect failed in OPPOSITE directions: the false-GREEN arm (S17) lets an incomplete
# schedule look ready, which is the one that allows a bad schedule to be submitted; the false-RED arm (S18)
# keeps reporting something the reporter has already fixed, which is the one they meet most often.
#
# NOTE ON WORDING: Schedule 2 renders its Check Status issues as WARNING notifications, not errors (unlike
# Schedules 1 and 3), so these scenarios assert "the warning" — matching S08 in `check-status.feature`.
#
# DEDICATED, SEEDED ANCHORS — AND WHY THEY ARE NOT THE EXISTING CHECK ANCHORS.
# Both scenarios save their own precondition, so each needs a mill-year no other scenario writes to.
# Reusing `check-met` / `saved-incomplete` was tried FIRST and is unsafe: their Givens seed through the API,
# so a second scenario on either collides with S07/S08 under `fullyParallel` — observed as four red tests on
# 2026-08-27. The extract had no free Draft left either (114 keys already pinned across the six fixtures;
# Home only offers 2015-2021; every unclaimed openable pair in that range is non-Draft, which disables Check
# Status), so `real-test-data-patches/sch2/unsaved-check-anchors.sql` creates these two. Preflight asserts
# both, which doubles as the patch's applied-ness guard.

@sch2 @UC-SCH2-001 @check-status-unsaved
Feature: Report Purchased and Private Log Costs and Sales (Schedule 2) — Check Status and unsaved edits
  As a mill reporter
  I want Check Status to judge what is on my screen
  So that I am not told the schedule is fine when what I am looking at is not

  @p1 @S17
  Scenario: Check Status reports the purchased-log cost cleared on screen but not saved
    Given the Schedule 2 anchor "check-unsaved-violation" has a saved schedule
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    # As stored the cost is present, so Check Status passes.
    And I check Schedule 2 status
    Then I should see the message "All requirements for this schedule have been met"
    # Empty it on screen and check again WITHOUT saving.
    When I clear the Schedule 2 "Purchased Log Cost cost" field
    And I check Schedule 2 status
    Then I should see the warning "Purchased/Private Log Costs - Cost: Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    And the stored Schedule 2 revision is unchanged

  @p1 @S18
  Scenario: Check Status stops reporting the missing purchased-log cost once it is supplied on screen
    Given the Schedule 2 anchor "check-unsaved-fix" has a saved schedule with no purchased log cost
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I check Schedule 2 status
    Then I should see the warning "Purchased/Private Log Costs - Cost: Value Required"
    # Supply it on screen and re-check WITHOUT saving. It is this schedule's ONLY outstanding issue, so the
    # verdict must flip as well as the warning clearing.
    When I enter "40000" in the Schedule 2 "Purchased Log Cost cost" field
    And I check Schedule 2 status
    Then I should not see the error "Purchased/Private Log Costs - Cost: Value Required"
    And I should see the message "All requirements for this schedule have been met"
    And the stored Schedule 2 revision is unchanged
