# Check Status evaluates what is ON SCREEN, including unsaved edits. Both scenarios were DELIBERATELY RED
# until 2026-09-25, reproducing defects.md DIV-6 (bcgov/nr-ilcr#359): Check Status judged the LAST SAVED
# schedule and ignored anything typed since. They went green, unedited, when #359's Schedules 1–3 fix gave
# `POST /api/v1/schedule1/check-status` a body carrying the on-screen values. Schedule 3's DIV-6 holds the
# full analysis.
#
# That is legacy parity: legacy's Schedule 1 Check Status described the screen, unsaved edits included
# (the observed behaviour #359 records). No mechanism is claimed — the same `ajax="false"` markup on
# legacy Schedule 5 judged the saved record instead (#476).
#
# BOTH ARMS ARE KEPT — the defect failed in OPPOSITE directions:
#   * the false-GREEN arm (S27): an unsaved violation goes unreported, so a schedule looks ready when it is
#     not. This is the one that lets a bad schedule be submitted.
#   * the false-RED arm (S28): a correction made on screen is still reported as broken. This is the one a
#     reporter meets most often — fix what you were told to fix, re-check, and be told again.
#
# READ-ONLY, ON SHARED CHECK-STATUS ANCHORS. Typing without saving writes nothing, and Check Status mutates
# nothing by contract, so these run on the same read-only anchors as S14/S15 and need no cleanup. Each
# scenario re-reads the schedule afterwards to prove the optimistic-lock token has not moved.
#
# ANCHOR EVIDENCE (probed 2026-08-27 via POST /api/v1/schedule1/check-status):
#   * 24050/2017 `requirements-met` — `requirementsMet: true`, zero errors. The starting point the
#     false-GREEN arm needs: a schedule that passes until the reporter breaks it on screen.
#   * 24051/2016 `missing-line-item-volume` — 22 errors, the first being
#     "Standing Tree to Loaded Truck - Volume: Value Required". The false-RED arm fixes THAT one on screen
#     and asserts only that its own error stops being reported; the other 21 legitimately remain, which is
#     why this arm does not assert "requirements met".

@sch1 @UC-SCH1-001 @check-status-unsaved
Feature: Report Average Cost of Logging (Schedule 1) — Check Status and unsaved edits
  As a mill reporter
  I want Check Status to judge what is on my screen
  So that I am not told the schedule is fine when what I am looking at is not

  @p1 @S27
  Scenario: Check Status reports a mandatory volume cleared on screen but not saved
    Given the Schedule 1 anchor "requirements-met" is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    # As stored this schedule is complete, so Check Status passes.
    When I check Schedule 1 status
    Then I should see the message "All requirements for this schedule have been met"
    # Empty a mandatory volume on screen and check again WITHOUT saving.
    When I clear the "Standing Tree to Loaded Truck" volume
    And I check Schedule 1 status
    Then I should see the error "Standing Tree to Loaded Truck - Volume: Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    And the Schedule 1 data should be unchanged

  @p1 @S28
  Scenario: Check Status stops reporting a missing volume once it is supplied on screen
    Given the Schedule 1 anchor "missing-line-item-volume" is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I check Schedule 1 status
    Then I should see the error "Standing Tree to Loaded Truck - Volume: Value Required"
    # Supply it on screen and re-check WITHOUT saving. Only THIS field's error must go; the schedule's
    # other 21 missing values are genuinely still missing.
    When I enter "500000" into the "Standing Tree to Loaded Truck" volume
    And I check Schedule 1 status
    Then I should not see the error "Standing Tree to Loaded Truck - Volume: Value Required"
    And the Schedule 1 data should be unchanged
