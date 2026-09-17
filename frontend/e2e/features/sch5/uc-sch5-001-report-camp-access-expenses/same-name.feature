# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S08.feature
# (legacy JSF/PrimeFaces). S08 proves BR-02's uniqueness rule is SCOPED to a single (mill, year): a name
# already in use under one mill-year can be reused under another.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`; the descriptors are addressed by their visible
#    Carbon labels rather than `schedule5Form:newCampName` / `:newIsolatedCamp`.
#  * NO CONTEXT SWITCH IS NEEDED, and the scenario deliberately avoids one. The first mill-year only has
#    to HOLD the name, so it is seeded through the API; the entire browser journey stays in the second.
#    Driving a Home re-selection mid-scenario would test the context switcher (that is sec's UC-SEC-001)
#    and would make a BR-02 failure hard to distinguish from a navigation failure.
#  * TWO DEDICATED ANCHORS BY CONSTRUCTION — 22051/2022 and 23050/2022. This is the one slice whose
#    subject IS the pair, so it cannot share. The step asserts the two keys really are distinct rather
#    than trusting the fixture, because if they ever collapsed the scenario would still pass while
#    proving nothing.
#  * The legacy scenario stops once the new row appears. This also re-reads the FIRST mill-year: a save
#    that "succeeded" by moving the camp rather than adding one would satisfy the table assertion just
#    as well.
#
# ONLY TWO FIELDS ARE FILLED, matching the legacy scenario: Camp Name and Isolated Camp. Every other
# descriptor and all twelve category amounts are optional — a blank optional field is CLEARED, not
# invalid (`components/schedule5/validation.ts`). S12 is the mirror that proves these two are required.

@sch5 @UC-SCH5-001 @same-name
Feature: Report Camp and Access Expenses (Schedule 5) — the same camp name under a different mill/year
  As a mill reporter
  I want to reuse a camp name that is already used under a different mill or reporting year
  So that naming conventions stay consistent across mills without being blocked as duplicates

  @p1 @S08 @BR-02
  Scenario: Save a new camp whose name matches a camp under a different mill or reporting year
    Given the Schedule 5 anchor "same-name-b" is an editable Draft with no camps
    And a camp named "North Camp" already exists under a different mill and year
    And no camp named "North Camp" exists for that mill and year
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    And I name the new camp "North Camp" and set Isolated Camp to "No"
    And I save the camp
    Then I should see the message "Data saved successfully"
    And "North Camp" is listed in the Existing Camps table
    And "North Camp" is still stored under the other mill and year
