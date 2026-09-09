# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S06.feature
# (legacy JSF/PrimeFaces). S06 runs Check Status against a schedule whose camps are all complete.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`; the `schedule5Form:messages` panel is a Carbon
#    notification carrying an explicit severity word, never colour alone.
#  * ONLY ONE MESSAGE APPEARS ON A PASS, NOT TWO — see SPEC-3 in defects.md. The source Gherkin
#    expects both the schedule banner AND a per-camp "All requirements for North Camp have been met.".
#    Legacy emits the schedule banner ALONE: `Schedule5MB.java:324-326` adds `scheduleRequirementsMetMsg`
#    in the pass branch, and the per-camp loop that would add `campRequirementsMetMsg` lives in the
#    `else` branch, unreachable when the schedule passes. The rewrite reproduces that exactly and its
#    own source calls it "deviation (C), contradicting both the epics AC and
#    UC-SCH5-001-detailed.md:151" (Schedule5Service.java:788-791) — so THREE planning documents say the
#    same wrong thing and the code is right. This scenario therefore asserts the per-camp line is
#    ABSENT, which is the only way the distinction can be regression-proof.
#  * Check Status is DISABLED while a camp panel is open (index.tsx:1419): legacy's button was a full
#    postback so its verdict always reflected the screen, whereas the modern check reads only the
#    database and must never contradict visible unsaved input. The page object asserts it is enabled
#    before clicking, so that gate cannot silently swallow the click.
#  * ANCHOR: 17052/2022 (`CHECK_MET_ANCHOR`).

@sch5 @UC-SCH5-001 @check-status
Feature: Report Camp and Access Expenses (Schedule 5) — Check Status
  As a mill reporter
  I want to confirm that all of Schedule 5's required data has been provided
  So that I know the schedule is ready to be submitted

  @p1 @S06
  Scenario: Run Check Status when every camp has its required fields complete
    Given the Schedule 5 anchor "check-met" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I run Schedule 5 Check Status
    Then I should see the message "All requirements for this schedule have been met"
    # SPEC-3: legacy never reaches the per-camp loop on a pass, so this line must NOT appear.
    And I should not see the message "All requirements for North Camp have been met."
