# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S20.feature
# (legacy JSF/PrimeFaces). S20 runs Check Status against a camp with a required value missing, then
# fixes it and runs again.
#
# WHAT MAKES THIS SLICE POSSIBLE AT ALL — two different rule sets, and the gap between them.
# Saving a camp requires only Camp Name and Isolated Camp (S12 proves that). Check Status tests a
# DIFFERENT list: camp name, road distance, size of camp, associated camp volume, and the four
# sub-list conditions (`Schedule5Service.evaluateCamp`). Isolated Camp is not among them. So a camp
# with no road distance stores perfectly happily and only Check Status objects — which is exactly the
# state a licensee reaches by saving early and coming back later. The precondition seeds through the
# app's own POST, so it is that state and not a hand-built one.
#
# THE COMPOSED MESSAGE IS ASSERTED BYTE-FOR-BYTE, and it is easy to get wrong by one character.
# `Schedule5CheckStatusResolver` builds `"Camp Report Name : " + campName + segment + ": " + text`,
# where each segment carries a LEADING space and NO trailing one (:40-50). Schedule 6's equivalent
# segments carry both, so its lines read "Road : 1 - TFL Number : Value Required" with a space either
# side of the final colon, while Schedule 5's do not. The resolver's own comment warns that copying
# Schedule 6's map wholesale gets all eight of these lines wrong by one byte. Measured against the
# running app 2026-09-10, not transcribed from the source Gherkin.
#
# THE SECOND ARM CONTRADICTS THE SOURCE GHERKIN, AND SPEC-3 PREDICTED IT. S20 scripts
# "All requirements for North Camp have been met." (SUC-005) after the fix. That line is UNREACHABLE
# here: once the only camp passes, the SCHEDULE passes, and the pass branch returns the schedule
# banner with `camps: []` — the per-camp loop lives in the `else` branch (Schedule5Service:821-825,
# legacy Schedule5MB.java:324-326). The per-camp "met" line is only ever emitted when the schedule
# FAILS and some individual camp passes. This is the THIRD confirmation of SPEC-3, after S06 and the
# entry itself; probed directly on 2026-09-10 (outcome MET, one message, camps: []). This scenario
# therefore asserts the schedule banner AND the absence of the per-camp line, so the distinction is
# regression-proof rather than resting on a code comment.
#
# ANCHOR: 25052/2022 (`CHECK_MISSING_ANCHOR`). Mutating — the scenario creates the camp, edits it and
# the cleanup registry deletes it again through the app's own DELETE.

@sch5 @UC-SCH5-001 @check-status
Feature: Report Camp and Access Expenses (Schedule 5) — Check Status finds missing required values
  As a mill reporter
  I want Check Status to tell me exactly which required value is missing
  So that I can complete the schedule before it is submitted

  @p1 @S20 @FLD-003
  Scenario: Check Status names the camp's missing required field, and stops once it is supplied
    Given the Schedule 5 anchor "check-missing" is an editable Draft with no camps
    And a camp named "North Camp" already exists with no road distance
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I run Schedule 5 Check Status
    # FLD-003 — the composed line, with the camp NAME (not an id) and no space before the last colon.
    Then I should see the message "Camp Report Name : North Camp - Road Distance to Operating Area: Value Required"
    # The schedule banner must NOT appear while an issue stands.
    And I should not see the message "All requirements for this schedule have been met"

    # Correct it the way a licensee would — reopen the camp, fill the field, save.
    When I edit the "North Camp" camp
    And I fill in the missing road distance and save
    And I should see the message "Data saved successfully"
    And I close the camp panel
    And I run Schedule 5 Check Status
    Then I should see the message "All requirements for this schedule have been met"
    # SPEC-3: with the only camp now passing, the SCHEDULE passes — and the pass branch never enters
    # the per-camp loop. The source Gherkin's SUC-005 line is unreachable by this route.
    And I should not see the message "All requirements for North Camp have been met."
    And I should not see the message "Camp Report Name : North Camp - Road Distance to Operating Area: Value Required"
