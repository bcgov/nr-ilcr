# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S01.feature
# (legacy JSF/PrimeFaces). S01 adds a new camp with its five descriptors and the fixed-category expense
# amounts, then saves.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`, reached via Home + side-nav. Every
#    `schedule5Form:*` NamingContainer id in the legacy scenario is gone:
#      - the five descriptors are Carbon controls addressed by their VISIBLE labels ("Camp Name",
#        "Road Distance to Operating Area (km)", "Size of Camp (number of persons)", "Associated Camp
#        Volume (m³)", "Isolated Camp" — a `Select` of ""/"No"/"Yes", not a PrimeFaces dropdown).
#      - `newCateringFoodVolume` / `newCateringFoodCost` and their ten siblings are one shared
#        `CategoryGrid`, whose inputs take their accessible name from the grid label: "Catering and Food
#        volume" / "Catering and Food cost". The add and edit panels render the SAME grid, which is why
#        they cannot drift apart.
#      - `schedule5Form:messages` is not a `p:messages` panel; results are Carbon notifications carrying
#        an explicit severity word ("Success"), never colour alone.
#      - `schedule5Form:existingCampDT` -> the "Existing Camps" table.
#  * "the value is propagated into every one of the 11 expense-category Volume fields" is asserted
#    across ALL ELEVEN rather than sampled. GRID_ROWS declares TWELVE categories, of which `recoveries`
#    has no volume cell at all (`hasVolume: false`) — that asymmetry is exactly why the legacy text says
#    eleven, so the count is part of the assertion.
#  * The legacy scenario stopped at "the camp panel redisplays with the recalculated sub-totals". This
#    also reads the record back through the API and asserts the SERVER-DERIVED totals, so the test
#    proves persistence rather than a re-render: after Save the panel re-seeds from the response and
#    looks identical whether or not anything reached the database.
#  * ANCHOR: 9050/2016, opened by real-test-data-patches/sch5/draft-anchors.sql. The extract had no free
#    Draft mill-year left for Schedule 5 — see that file and fixtures/sch5/schedule5-test-data.ts.
#
# NOT COVERED HERE (see coverage.md): the legacy scenario also asserts the per-category "$/m³" column
# recomputes for EACH category. The two totals' $/m³ are asserted; the per-category ones ride S09/S15.

@sch5 @UC-SCH5-001 @happy-path
Feature: Report Camp and Access Expenses (Schedule 5) — add a new camp with descriptors and fixed-category expenses
  As a mill reporter
  I want to add a new logging camp with its descriptors and fixed-category expense amounts
  So that the camp's costs are recorded on Schedule 5 for the reporting year

  @p0 @S01
  Scenario: Add a new camp, enter descriptors and fixed-category costs, and save successfully
    Given the Schedule 5 anchor "add" is an editable Draft with no camps
    And no camp named "Cedar Creek Camp" exists for that mill and year
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    Then the New Camp Details panel is shown with its descriptor fields blank
    When I enter the camp descriptors
    # BR-03 — entering the camp volume fans it out across the grid before anything is saved.
    Then the camp volume "5000" is propagated into all 11 category volume fields
    When I enter the fixed-category costs
    And I save the camp
    Then I should see the message "Data saved successfully"
    And "Cedar Creek Camp" is listed in the Existing Camps table
    And the saved camp carries the expected derived totals
