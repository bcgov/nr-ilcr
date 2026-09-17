# Re-grounded from UC-SCH5-001-S04.feature and -S05.feature (legacy JSF/PrimeFaces). S04 adds an Other
# Camp Expense to an already-saved camp; S05 adds an Other Access Expense from a camp that has NOT been
# saved yet, which forces the save-first confirm (CFM-004) before the sub-page can open.
#
# WHAT RE-GROUNDING CHANGED
#  * THERE IS NO SECOND ROUTE. Legacy navigates to `schedule5CampExpenses.xhtml` /
#    `schedule5AccessExpenses.xhtml`; the rewrite drives the sub-page level with SEARCH PARAMS on the
#    same `/schedule-5` route — `camp` = CAMP_REPORT_ID, `sub` = CAMP|ACCESS (routes/schedule-5.tsx,
#    mirroring Schedule 4). So the URL assertions re-ground to a query string, and the browser Back
#    button steps back to the camp list rather than needing its own nav entry.
#  * `addCampExpenseForm:description` / `:cost` / `:volume` -> Carbon fields whose accessible names keep
#    the legacy trailing colon-space: "Description: ", "Volume: ", "Cost $: ". Both sub-pages are ONE
#    component (`components/schedule5SubPage`) parameterised by kind, so the Gherkin's note that Access
#    "follows the identical pattern" is now structurally true rather than a claim.
#  * `addCampExpenseForm:messages` -> a Carbon notification carrying the severity word "Success".
#  * CFM-004 is a Carbon `Modal` headed "Save camp report" with Yes/No, not a PrimeFaces confirmDialog.
#    Its text is unchanged from the legacy wording (index.tsx:85-86), so only the control type moved.
#  * ANCHORS: 12050/2022 (`SUBPAGE_EXISTING_ANCHOR`) and 13050/2022 (`SUBPAGE_NEW_ANCHOR`).
#
# S05 fills Isolated Camp as well as the name. The legacy scenario mentions only the name, but Isolated
# Camp is a REQUIRED descriptor (S12) and the confirm's auto-save would be rejected without it — that
# rejection is S12's subject, and letting it fire here would fail S05 for another slice's reason.

@sch5 @UC-SCH5-001 @sub-page
Feature: Report Camp and Access Expenses (Schedule 5) — the Other Camp/Access Expense sub-pages
  As a mill reporter
  I want to record list-based camp and access expenses that do not fit a fixed category
  So that all of the camp's costs are captured on Schedule 5

  @p1 @S04
  Scenario: Add an Other Camp Expense to an existing camp via its sub-page
    Given the Schedule 5 anchor "subpage-existing" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "North Camp" camp
    And I open the "camp" sub-page
    Then the "camp" sub-page is shown
    When I add the sub-page row "Generator Fuel" costing "350"
    Then the "camp" list holds "Generator Fuel" with the camp volume defaulted
    When I save the Schedule 5 sub-page
    Then I should see the message "Data saved successfully"
    When I go back to the camp list
    And I edit the "North Camp" camp
    Then the "camp" link shows a count of 1

  @p1 @S05 @CFM-004
  Scenario: Add an Other Access Expense from a new, unsaved camp, confirming the auto-save first
    Given the Schedule 5 anchor "subpage-new" is an editable Draft with no camps
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp named "Elk Ridge Camp" without saving
    And I open the "access" sub-page
    Then I should see the confirm text "The information for the New Camp must be saved before you can add other expenses. Would you like to save the information now?"
    When I confirm the "Save camp report" dialog
    Then the "access" sub-page is shown
    When I add the sub-page row "Ferry Crossing" costing "220"
    And I save the Schedule 5 sub-page
    Then I should see the message "Data saved successfully"
    When I go back to the camp list
    Then "Elk Ridge Camp" is listed in the Existing Camps table
