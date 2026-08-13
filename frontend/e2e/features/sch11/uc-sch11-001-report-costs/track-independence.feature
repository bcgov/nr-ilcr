# Re-grounded from UC-SCH11-001-S10.feature — BR-02's negative-assertion side.
#
# This is the scenario the 25.4 ticket calls out explicitly: "track independence holds (1–10 Submitted
# while silviculture stays Draft-editable)". It is also the hardest anchor in the suite — the seeded
# delivery DB contains exactly ONE (mill, year) where the Schedule 1–10 track has advanced past Draft
# while the silviculture track independently remains Draft: 23050 / 2016 (Sch 1-10 = "S", Sch 11 = "D").
# The precondition asserts BOTH sides from /v1/mill-context plus editable:true from the schedule11 GET,
# so if a re-extract moves either status this fails as an obvious re-ground rather than silently passing
# while proving nothing.
#
# Backend confirmation of the mechanism (Schedule11Service): `editable = callerMayEdit ∧ trackStatus == D`
# — the 1–10 track is not read at all, mirroring legacy's UserSessionMB.disableUserInputSchedule11().

@sch11 @UC-SCH11-001
Feature: Report Basic Silviculture Costs (Schedule 11) — editability independent of the Schedule 1-10 status
  As a mill reporter whose Schedule 1-10 report is already submitted
  I want to keep recording silviculture locations
  So that the two workflow tracks do not block each other

  @S10 @p0
  Scenario: Add a location while the Schedule 1-10 report is past Draft
    Given the Schedule 1-10 track is past Draft while the silviculture track is still Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    # The editing surface is fully live even though the 1-10 track has moved on.
    Then the Add New Location panel is rendered
    And the Check Status button is enabled
    When I fill the Add New Location panel:
      | field        | value          |
      | Location     | E2E S10 indep  |
      | Enhanced     | No             |
      | Biogeo       | primary        |
      | NAR(ha)      | 40.5           |
      | Actual Cost  | 1200           |
      | Planned Cost | 1000           |
    And I click Add
    Then I should see the message "Data saved successfully"
    # No business-exception message may appear as a result of the 1-10 status.
    And I should not see the message "This Mill is not active for the current Reporting Year. Please select another mill from the Home Page."
    And the Schedule 11 location "E2E S10 indep" is persisted as:
      | field        | value |
      | Enhanced     | No    |
      | NAR(ha)      | 40.5  |
      | Actual Cost  | 1200  |
      | Planned Cost | 1000  |
      | Total Cost   | 2200  |
