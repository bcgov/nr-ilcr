# UC-SCH5-001-S24 / S25 — BR-11: does Check Status judge the SCREEN or the last SAVED document?
#
# ⚠ THE TWO @discovered-divergence SCENARIOS BELOW ARE DELIBERATELY RED. They track DIV-1 in this
# UC's defects.md, the Schedule 5 instance of the app-wide defect filed as bcgov/nr-ilcr#359. Do not
# weaken them, skip them, or "fix" them by asserting today's behaviour — the failing state IS the
# tracking signal. `npm run test:gate` filters them out of a fresh-failures run.
#
# ---------------------------------------------------------------------------------------------------
# SCHEDULE 5'S INSTANCE PRESENTS DIFFERENTLY FROM EVERY OTHER SCHEDULE'S, AND THAT IS THE FINDING
# ---------------------------------------------------------------------------------------------------
# Schedules 1, 2, 4 and 11 all fail this pair the same way: Check Status answers, and its answer is
# about the last SAVED document, so a reporter gets a confidently wrong verdict. Schedule 5 does NOT
# give a wrong answer. It gives NO answer — the Check Status button is disabled the whole time an
# unsaved edit could exist:
#
#   * the button carries `disabled={!editable || saving || panelOpen}` (index.tsx:1419);
#   * all four check-status descriptors (camp name, road distance, size of camp, associated camp
#     volume) live INSIDE the camp panel, so editing one means the panel is open;
#   * closing the panel to re-enable the button raises the discard confirm and throws the edit away.
#
# So "an unsaved edit plus a clickable Check Status" is not a reachable state in this UI. The
# false-GREEN of #359 cannot be produced here at all.
#
# THAT IS STILL A DIVERGENCE FROM LEGACY, and it is why these stay red rather than being rewritten.
# Legacy's Check Status was a full JSF postback (`ajax="false"`) and the panels shared its form via
# `ui:include`, so UPDATE_MODEL_VALUES applied every on-screen value to the bean BEFORE the action
# ran — legacy answered, and answered about the screen (schedule5.xhtml:40,257;
# Schedule5MB.java:321). The rewrite's `POST /check-status` carries no request body at all, so the
# endpoint cannot see the screen even in principle; disabling the button is what stops it giving a
# wrong answer. The reporter's outcome has changed from "correct verdict including my edits" to
# "cannot run the check until I save".
#
# WHICH DIRECTION THIS FAILS IN, stated plainly because it matters for triage: this is the SAFE
# direction. An incomplete schedule can never look ready here, which is the harm #359 does elsewhere.
# The cost is workflow, not correctness — a reporter must save before they can check. Whether that is
# acceptable is a Ministry call, not the suite's; DIV-1 records it and does not adjudicate it.
#
# BOTH ARMS ARE STILL NEEDED. S24 is the false-GREEN arm (an unsaved violation goes unreported, so a
# bad schedule looks submittable) and S25 the false-RED arm (a correction keeps being reported, so the
# reporter is told to fix what they just fixed). They fail in opposite directions elsewhere, and if
# #359 is ever fixed by giving the endpoint the screen's values, BOTH must go green — one alone would
# mean the fix is half-done.
#
# THE FIELD IS `Size of Camp`, AND NO OTHER WOULD DO. It is check-status-tested AND not required at
# save, so a camp stores cleanly without it and only Check Status objects. The source Gherkin's own
# note records being corrected on this: it first used a category cost, and the twelve camp/access
# expense amounts are not check-status conditions at all (`Schedule5Service.evaluateCamp` tests the
# four descriptors and the four sub-list conditions, nothing else).
#
# ANCHORS: 10050/2023 (S24), 12050/2023 (S25) and 22051/2023 (the green companion below). All three
# mutating — each seeds its own camp through the app's own POST and the cleanup registry deletes it
# again. The third one was minted after the companion first SHARED S24's anchor and the two raced:
# both seed a camp of the same name, so the loser's POST answered 409 and its cleanup then deleted
# the WINNER's camp mid-run, leaving S24 asserting against an empty table while an empty schedule
# vacuously reported "requirements met". A textbook instance of the rule the fixture header states —
# dedication is per SCENARIO, not per slice.

@sch5 @UC-SCH5-001 @check-status-unsaved
Feature: Report Camp and Access Expenses (Schedule 5) — Check Status and unsaved edits
  As the Licensee running Check Status
  I want Check Status to judge the amounts I can see on screen
  So that I am not told the schedule is complete while a value in front of me is wrong

  # S24 — the false-GREEN arm. As stored the camp passes; cleared on screen it must not.
  @discovered-divergence @p1 @S24 @BR-11
  Scenario: Check Status reports the size of camp cleared on screen but not saved [DISCOVERED DIVERGENCE — Check Status cannot see the screen, and is disabled rather than answering; defects.md DIV-1 / issue #359]
    Given the Schedule 5 anchor "check-unsaved-violation" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    # As stored, everything Check Status tests is present.
    And I run Schedule 5 Check Status
    Then I should see the message "All requirements for this schedule have been met"

    # Clear it on screen and check again WITHOUT saving. Legacy reported the field; this app cannot
    # even be asked — `I run Schedule 5 Check Status` fails on the disabled button, which is exactly
    # the divergence and is reported with that wording by the page object.
    When I edit the "North Camp" camp
    And I enter "" in the "Size of Camp" field
    And I run Schedule 5 Check Status
    Then I should see the message "Camp Report Name : North Camp - Size of Camp: Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    # True today and must stay true whatever the verdict becomes: a check is a read.
    And "North Camp" still holds its stored size of camp

  # S25 — the false-RED arm. As stored the camp fails; supplied on screen it must pass.
  @discovered-divergence @p1 @S25 @BR-11
  Scenario: Check Status stops reporting the missing size of camp once it is supplied on screen [DISCOVERED DIVERGENCE — Check Status cannot see the screen, and is disabled rather than answering; defects.md DIV-1 / issue #359]
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
  # GREEN, and the reason the two above are red. This pins the actual mechanism so it cannot drift
  # unnoticed: if a future change enables Check Status while a panel is open WITHOUT teaching the
  # endpoint to read the screen, Schedule 5 would stop being the safe outlier and would start
  # producing #359's confidently-wrong verdict instead. This scenario fails the moment that happens.
  # ---------------------------------------------------------------------------------------------------
  @p2 @S24 @BR-11
  Scenario: Check Status cannot be run while a camp panel is open
    Given the Schedule 5 anchor "check-panel-gate" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    # Closed panel: available.
    Then the Schedule 5 Check Status button is enabled
    # Open panel — clean, so not even a pending edit — and it is already gone.
    When I edit the "North Camp" camp
    Then Schedule 5 Check Status is unavailable
    # Still gone with a real unsaved edit, which is the state S24 and S25 need and cannot reach.
    When I enter "" in the "Size of Camp" field
    Then Schedule 5 Check Status is unavailable
    # And the only way back to an enabled button discards the edit rather than checking it.
    When I close the camp panel
    And I confirm the "Close camp report" dialog
    Then the Schedule 5 Check Status button is enabled
    And "North Camp" still holds its stored size of camp
