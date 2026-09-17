# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S03.feature
# (legacy JSF/PrimeFaces). S03 copies a saved camp into a new pre-filled panel, renames it, and saves.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`; the Copy control is scoped to the camp's row in the
#    "Existing Camps" table rather than addressed through `schedule5Form:existingCampDT`.
#  * The copy panel is the NEW-camp panel pre-filled, so it is headed by the `New Camp Details` literal
#    and not by the source camp's name (`panelMode` is 'copy', which is neither 'edit' nor 'view').
#    Asserting that is what distinguishes a copy from having merely reopened the source.
#  * WRN-001'S `[UNKNOWN]` IS RESOLVED. The source Gherkin flagged the `{0}` substitution as
#    unconfirmed — "no live app exists for this project" — and assumed it interpolated the source camp
#    name. Confirmed here in both directions: the bundle template is
#    `sch5.copy.msg=To complete copy of Camp: {0}, provide a new Camp Name and invoke save.`
#    (messages.properties:253), and the app resolves it over HTTP with `arg: camp.campName`
#    (index.tsx:713-718) rather than hardcoding a sentence. So the assumed text was correct.
#  * The legacy scenario stops once the new row appears. This also asserts the SOURCE camp is still
#    there — a copy that silently renamed would satisfy "the new name is listed" just as well.
#  * ANCHOR: 10050/2022 (`COPY_ANCHOR`).

@sch5 @UC-SCH5-001 @copy
Feature: Report Camp and Access Expenses (Schedule 5) — copy an existing camp
  As a mill reporter
  I want to copy an existing camp's values into a new camp record
  So that I can report a similar camp without re-entering every field

  @p1 @S03 @WRN-001
  Scenario: Copy an existing camp, rename it to a unique value, and save successfully
    Given the Schedule 5 anchor "copy" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I copy the "North Camp" camp
    Then I should see the warning "To complete copy of Camp: North Camp, provide a new Camp Name and invoke save."
    And the new camp panel is pre-filled from "North Camp"
    When I rename the new camp to "North Camp Annex"
    And I save the camp
    Then I should see the message "Data saved successfully"
    And "North Camp Annex" is listed in the Existing Camps table
    And both "North Camp" and "North Camp Annex" are stored
