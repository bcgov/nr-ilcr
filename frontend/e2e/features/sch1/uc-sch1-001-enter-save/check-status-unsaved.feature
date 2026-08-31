# DIVERGENCE — both scenarios here are DELIBERATELY RED. They reproduce defects.md DIV-6, tracked upstream
# as bcgov/nr-ilcr#359, and stay failing until Check Status accounts for what is on screen. Do not weaken
# them, skip them, or "fix" them by asserting the current behaviour: the failing state IS the tracking
# signal. Filter them out of a fresh-failures run with `npm run test:gate`.
#
# WHAT THEY REPRODUCE
# Check Status reports on the LAST SAVED schedule and silently ignores anything typed since. This is the
# same app-wide defect Schedule 3 carries as DIV-6 — 11 of the 12 schedules are affected, Schedule 6 being
# the only correct implementation. Schedule 3's register entry holds the full analysis; this entry is the
# Schedule 1 instance, and one fix turns both green.
#
# Legacy could not behave this way. Its Check Status was a full JSF postback (`ajax="false"`), so
# UPDATE_MODEL_VALUES pushed every submitted field into the bean BEFORE the action ran, and the check
# validated the in-memory schedule. The rewrite's `POST /api/v1/schedule1/check-status` carries no request
# body at all, so the endpoint cannot see the screen even in principle.
#
# BOTH ARMS ARE NEEDED — they fail in OPPOSITE directions:
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

  @discovered-divergence @p1 @S27
  Scenario: Check Status reports a mandatory volume cleared on screen but not saved [DISCOVERED DIVERGENCE — Check Status judges the SAVED schedule, ignoring the screen; defects.md DIV-6 / issue #359]
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

  @discovered-divergence @p1 @S28
  Scenario: Check Status stops reporting a missing volume once it is supplied on screen [DISCOVERED DIVERGENCE — Check Status judges the SAVED schedule, ignoring the screen; defects.md DIV-6 / issue #359]
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
