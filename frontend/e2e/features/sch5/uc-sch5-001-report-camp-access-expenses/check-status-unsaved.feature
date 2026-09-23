# UC-SCH5-001-S24 / S25 — BR-11: does Check Status judge the SCREEN or the last SAVED document?
#
# ---------------------------------------------------------------------------------------------------
# FIXED 2026-09-22 (bcgov/nr-ilcr#476). These scenarios were RED from 2026-09-11 and are now GREEN.
# ---------------------------------------------------------------------------------------------------
# NOT ONE ASSERTION CHANGED when they went green, which was the acceptance criterion rather than a
# nice-to-have: they always asserted the CORRECT (legacy) behaviour, so a fix that had needed them
# edited would have been the wrong fix. Only the `@discovered-divergence` tags and the
# `[DISCOVERED …]` title markers came off, and they came off TOGETHER — one alone would have meant
# the fix was half-done (the two arms fail in opposite directions; see below).
#
# What changed in the app: `POST /api/v1/schedule5/check-status` now carries a body — the camp panel
# currently on screen (`Schedule5CheckRequest`) — which the service overlays onto the stored camps
# before running the identical rule. The availability gate that used to stand in for this —
# `disabled={… || panelOpen}` — is gone with it; there is nothing left for it to protect against.
#
# ⚠ THIS DOES NOT RESTORE LEGACY SCHEDULE 5, AND SAYING SO WOULD MISLEAD A REVIEWER. Legacy judges
# the screen on its OTHER schedules; legacy Schedule 5 judges the last SAVED record. That
# inconsistency is legacy's own, confirmed by manual comparison of the two screens. The business area
# ruled in September 2026 that Schedule 5 should match the others, so this is a sanctioned
# divergence from the legacy Schedule 5 screen — checked against that decision, not against
# `schedule5.xhtml`. Do not re-derive a mechanism for the legacy difference from the button markup:
# the obvious lifecycle reading predicts the opposite of what both screens actually do.
#
# ⚠ SCHEDULE 5 ONLY. The app-wide defect is bcgov/nr-ilcr#359 and Schedules 1, 2, 4 and 11 still
# carry it — their own `check-status-unsaved.feature` arms remain deliberately red. Schedule 6 was
# always correct and is the shape this fix followed.
#
# BOTH ARMS, AND WHY BOTH ARE STILL HERE. S24 is the false-GREEN arm (an unsaved violation goes
# unreported, so a bad schedule looks submittable) and S25 the false-RED arm (a correction keeps
# being reported, so the reporter is told to fix what they just fixed). A regression could
# reintroduce either one alone — e.g. sending the panel only when it is dirty would redden S24's
# sibling below while leaving S25 green — so neither arm is redundant.
#
# THE FIELD IS `Size of Camp`, AND NO OTHER WOULD DO. It is check-status-tested AND not required at
# save, so a camp stores cleanly without it and only Check Status objects. The source Gherkin's own
# note records being corrected on this: it first used a category cost, and the twelve camp/access
# expense amounts are not check-status conditions at all (`Schedule5Service.evaluateCamp` tests the
# four descriptors and the four sub-list conditions, nothing else).
#
# ANCHORS: 10050/2023 (S24), 12050/2023 (S25) and 22051/2023 (the unsaved-new-camp scenario). All
# three mutating — each seeds its own camp through the app's own POST and the cleanup registry
# deletes it again. The third was minted after it first SHARED S24's anchor and the two raced: both
# seed a camp of the same name, so the loser's POST answered 409 and its cleanup then deleted the
# WINNER's camp mid-run, leaving S24 asserting against an empty table while an empty schedule
# vacuously reported "requirements met". A textbook instance of the rule the fixture header states —
# dedication is per SCENARIO, not per slice.

@sch5 @UC-SCH5-001 @check-status-unsaved
Feature: Report Camp and Access Expenses (Schedule 5) — Check Status and unsaved edits
  As the Licensee running Check Status
  I want Check Status to judge the amounts I can see on screen
  So that I am not told the schedule is complete while a value in front of me is wrong

  # S24 — the false-GREEN arm. As stored the camp passes; cleared on screen it must not.
  @p1 @S24 @BR-11
  Scenario: Check Status reports the size of camp cleared on screen but not saved
    Given the Schedule 5 anchor "check-unsaved-violation" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    # As stored, everything Check Status tests is present.
    And I run Schedule 5 Check Status
    Then I should see the message "All requirements for this schedule have been met"

    # Clear it on screen and check again WITHOUT saving. The panel stays open throughout — that the
    # button is still reachable here is itself the #476 fix, and `I run Schedule 5 Check Status`
    # asserts the button is enabled before clicking, so this step fails if the gate ever returns.
    When I edit the "North Camp" camp
    And I enter "" in the "Size of Camp" field
    And I run Schedule 5 Check Status
    Then I should see the message "Camp Report Name : North Camp - Size of Camp: Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    # True today and must stay true whatever the verdict becomes: a check is a read.
    And "North Camp" still holds its stored size of camp

  # S25 — the false-RED arm. As stored the camp fails; supplied on screen it must pass.
  @p1 @S25 @BR-11
  Scenario: Check Status stops reporting the missing size of camp once it is supplied on screen
    Given the Schedule 5 anchor "check-unsaved-fix" is an editable Draft with no camps
    And a camp named "North Camp" already exists with no size of camp
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I run Schedule 5 Check Status
    Then I should see the message "Camp Report Name : North Camp - Size of Camp: Value Required"

    # Supply it on screen and re-check WITHOUT saving. It is this camp's ONLY outstanding issue, so
    # the verdict must flip as well as the finding clearing.
    When I edit the "North Camp" camp
    And I enter "40" in the "Size of Camp" field
    And I run Schedule 5 Check Status
    Then I should not see the message "Camp Report Name : North Camp - Size of Camp: Value Required"
    And I should see the message "All requirements for this schedule have been met"
    And "North Camp" still has no stored size of camp

  # ---------------------------------------------------------------------------------------------------
  # The case S24 and S25 cannot reach, and the one an obvious implementation gets wrong.
  #
  # An untouched new camp panel is CLEAN — its form matches its empty baseline — so any send keyed on
  # the panel being DIRTY rather than OPEN would omit it, and the verdict would read "requirements
  # met" over a camp with four missing fields. That is the false-GREEN direction of #359, rebuilt by
  # the fix for it. Legacy evaluated the new camp because it was on screen; so must this.
  #
  # Replaces the panel-gate scenario that stood here while the gate existed: its premise (Check Status
  # is unavailable during an edit) is exactly what #476 removed, and S24 and S25 now assert the
  # button's availability mid-edit as a side effect of using it there.
  # ---------------------------------------------------------------------------------------------------
  @p2 @S24 @BR-11
  Scenario: Check Status includes an unsaved NEW camp that has never been saved
    Given the Schedule 5 anchor "check-unsaved-new-camp" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    # The stored camp alone is complete.
    And I run Schedule 5 Check Status
    Then I should see the message "All requirements for this schedule have been met"

    # Start a camp, name it, and check WITHOUT saving. Nothing else is filled in, so its three
    # numeric descriptors must be reported under the name on screen.
    When I start a new camp
    And I enter "Unsaved Camp" in the "Camp Name" field
    And I run Schedule 5 Check Status
    Then I should see the message "Camp Report Name : Unsaved Camp - Road Distance to Operating Area: Value Required"
    And I should see the message "Camp Report Name : Unsaved Camp - Size of Camp: Value Required"
    And I should see the message "Camp Report Name : Unsaved Camp - Associated Camp Volume: Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    # A check is a read. Asserted against the API, not the table: Check Status does not refetch the
    # camps list, so a table assertion would pass even if the camp HAD been created.
    And no camp named "Unsaved Camp" exists on the anchor
