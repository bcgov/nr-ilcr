# Re-grounded from UC-SCH5-001-S10.feature and -S11.feature (legacy JSF/PrimeFaces). Both slices are
# about not losing work silently: S10 closes a dirty panel, S11 opens a different camp while one is
# dirty. Each must prompt, and each must genuinely discard rather than quietly persist.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`; `schedule5Form:closeBtn2` -> the panel's "Close"
#    button, and the row Edit control is scoped to its camp's row.
#  * CFM-002 and CFM-003 are Carbon `Modal`s with Yes/No, distinguished by heading ("Close camp report"
#    / "Switch camp report"), not PrimeFaces confirmDialogs. Both TEXTS are unchanged from legacy
#    (index.tsx:75, :87-88), so only the control type moved.
#  * DEVIATION (K) is deliberately NOT exercised here. Legacy attached its close confirm
#    UNCONDITIONALLY — no dirty check anywhere (schedule5.xhtml:169, :192, :221, :244) — whereas the
#    rewrite prompts only when the panel is genuinely dirty. Both scenarios below supply a real unsaved
#    change, so both systems prompt and the deviation is out of scope. A "close a CLEAN panel" scenario
#    would land straight on it; that belongs in its own slice with its own adjudication, not smuggled
#    in here.
#  * Both scenarios end with an API read-back. A confirm can be answered correctly on screen while the
#    edit was already flushed — asserting the panel closed proves only that the panel closed.
#  * ANCHORS: 23052/2022 (`DISCARD_CLOSE_ANCHOR`) and 24050/2022 (`CAMP_SWITCH_ANCHOR`).

@sch5 @UC-SCH5-001 @discard-confirm
Feature: Report Camp and Access Expenses (Schedule 5) — discard confirms
  As a mill reporter
  I want to be warned before losing unsaved camp changes
  So that I do not accidentally discard data I intended to keep

  @p1 @S10 @CFM-002
  Scenario: Closing the open camp panel with unsaved changes prompts a discard confirm
    Given the Schedule 5 anchor "discard-close" is an editable Draft with no camps
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    And I name the new camp "Cedar Creek Camp" and set Isolated Camp to "No"
    And I close the camp panel
    Then I should see the confirm text "Any unsaved data will be lost. Are you sure you would like to continue?"
    When I confirm the "Close camp report" dialog
    Then the camp panel is closed
    # The discarded camp was never saved, so the anchor must be exactly as it started.
    And no camps are stored on the anchor

  @p1 @S11 @CFM-003
  Scenario: Opening a different camp while one is dirty prompts a discard confirm
    Given the Schedule 5 anchor "camp-switch" is an editable Draft with no camps
    And camps named "North Camp" and "South Camp" already exist
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "North Camp" camp
    And I enter a "Catering and Food" cost of "9999"
    And I click Edit on the "South Camp" camp
    Then I should see the confirm text "Any unsaved changes to the current camp report will be lost. Are you sure you would like to continue?"
    When I confirm the "Switch camp report" dialog
    Then the "South Camp" panel is populated with the stored values
    And "North Camp" still holds its original Catering and Food cost
