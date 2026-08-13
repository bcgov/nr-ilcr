# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH11-001/gherkin/UC-SCH11-001-S01.feature
# (legacy JSF/PrimeFaces). The React/Carbon app uses the /schedule-11 route reached via Home + side-nav
# and stable Carbon ids, not `schedule11.xhtml` / `addLocationForm:*` naming-container ids.
#
# STRUCTURAL DIVERGENCE (defects.md DIV-1): the legacy scenario ends "When I click the Save
# button [id=schedule11DTForm:btnSaveTop] / Then Data saved successfully". There is NO page-level Save
# button in the React app at all — Add is itself the write (POST /locations, "add-is-save"), so the
# trailing Save click has no counterpart and is not asserted. That is the app's shipped 25.2 contract,
# not a gap in this test; S09 proves the row really is persisted by Add alone.
#
# Persistence is proven by API read-back against the real write path (POST -> GET), not by the toast.

@sch11 @UC-SCH11-001
Feature: Report Basic Silviculture Costs (Schedule 11) — add and save a location
  As a mill reporter
  I want to add a silviculture location with its cost details
  So that the mill's basic silviculture costs are recorded for the reporting year

  @S01 @p0
  Scenario: Add a location with all required fields and both cost values
    Given the Schedule 11 anchor "add" is a pristine editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field        | value       |
      | Location     | E2E S01 add |
      | Enhanced     | Yes         |
      | Biogeo       | primary     |
      | NAR(ha)      | 125.5       |
      | Actual Cost  | 5000        |
      | Planned Cost | 4500        |
      | Comments     | S01 probe   |
    And I click Add
    Then I should see the message "Data saved successfully"
    And the Schedule 11 location "E2E S01 add" is listed
    # Stored record, read back through the app's own GET — entered fields AND the server-derived total.
    And the Schedule 11 location "E2E S01 add" is persisted as:
      | field        | value       |
      | Enhanced     | Yes         |
      | Biogeo       | ESSFdc1     |
      | NAR(ha)      | 125.5       |
      | Actual Cost  | 5000        |
      | Planned Cost | 4500        |
      | Total Cost   | 9500        |
      | Comments     | S01 probe   |
    # Footer Totals recompute to include the new location (BR-08 / CNT-001). The anchor starts pristine,
    # so the single row IS the totals. Values are the page's own display masks (money/area/ratio).
    And the Schedule 11 footer total "NAR(ha)" shows "125.5"
    And the Schedule 11 footer total "Actual Cost ($)" shows "5,000"
    And the Schedule 11 footer total "Planned Cost ($)" shows "4,500"
    And the Schedule 11 footer total "Total Act Plus Plan Cost ($)" shows "9,500"
