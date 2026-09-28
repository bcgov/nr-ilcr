# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S01.feature
# (legacy JSF/PrimeFaces). S01 adds a road maintenance record identified by a Timber Supply Area and
# Supply Block, with its volume and cost, then confirms the schedule passes Check Status.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule6.xhtml` -> route `/schedule-6`, reached via Home + side-nav. Every
#    `schedule6AddForm:*` / `schedule6Form:*` NamingContainer id in the legacy scenario is gone:
#      - `tsaNumberOneMenu` / `tsbNumberOneMenu` are `CodeComboBox` (Carbon `ComboBox`) whose options
#        render the code's DESCRIPTION, not the code — so the scenario picks "Arrow TSA" and
#        "Arrow TSA Block B", never "01" / "01B". Supply Block is FILTERED to codes starting with the
#        chosen TSA (utils/codes.ts supplyBlocksFor), so the area type must be selected FIRST.
#      - `vol` / `cos` are Carbon TextInputs. Both re-group ON BLUR (volume via groupInput, cost via
#        groupFixedInput to 0 decimals) and the $/m³ baseline is committed by the same handler, so the
#        step blurs each field rather than typing and moving on.
#      - `schedule6Form:messages` is not a `p:messages` panel; results are Carbon notifications
#        carrying an explicit severity word, never colour alone.
#      - `roadReportDataList` -> the record rows, each a fieldset whose field ids are `row-<recordId>-*`.
#  * THE ADD PANEL DOES NOT SHOW RMG — the one place this scenario asserts something different from
#    where the legacy text pointed. Legacy asserted `schedule6AddForm:RMG`; the React Add panel is
#    passed `rmg=""` deliberately ("`rmg` stays server-derived", components/schedule6/index.tsx:401),
#    so the derived grouping appears on the SAVED ROW instead. The value is asserted, on the row. The
#    panel's `cal` ($/m³) IS mirrored and is asserted where legacy put it. Recorded as defects.md VER-1
#    — verified NOT a defect, because the figure is still shown and is still server-derived.
#  * The legacy scenario stopped at the re-rendered panel and list. This also reads the record back
#    through the API and asserts the SERVER-DERIVED rmg and costPerVolume, so the test proves
#    persistence rather than a re-render: after `Add Report` the page re-seeds its row forms from the
#    response (index.tsx:796-799) and looks identical whether or not anything reached the database.
#  * ANCHOR: 9050/2024, opened by real-test-data-patches/sch6/draft-anchors.sql. By the time Schedule 6
#    was authored the extract had NO usable free Draft mill-year left for any domain — the survey is in
#    that file and in fixtures/sch6/schedule6-test-data.ts. Reporting year 2024 is minted for sch6, and
#    because every key another fixture pins is <= 2023, "year >= 2024 belongs to sch6" is structural.
#
# NOT COVERED HERE (see coverage.md): the per-record Comments field is entered but its own persistence
# rules ride S04 (the schedule-level general comment is a DIFFERENT, 4000-wide column) and the
# comment-length caps ride the validation slices. Volume is deliberately NOT part of Check Status —
# legacy never checks it (Schedule6CheckStatus, commented out at :19) — so a passing Check Status here
# proves the area-type/supply-block/cost trio only.

@sch6 @UC-SCH6-001 @happy-path
Feature: Report Road Management Costs (Schedule 6) — add a road maintenance record by TSA and Supply Block
  As a mill reporter
  I want to add a road maintenance record identified by a Timber Supply Area and Supply Block with its volume and cost
  So that the road maintenance costs feed into interior stumpage costing

  @p0 @S01
  Scenario: Add a TSA/Supply Block road record with volume and cost, then confirm via Check Status
    Given the Schedule 6 anchor "add" is an editable Draft with no road records
    And I will record a road maintenance record commented "E2E S01 road record"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    Then the Schedule 6 record list shows no records
    When I open the Add Road Maintenance report panel
    Then the Add panel is shown with its fields blank
    When I enter the S01 road record
    # The panel mirrors $/m³ from the blurred volume/cost before anything is saved; 48,000 / 12,500 is
    # exactly 3.84, so no rounding decision can make this pass or fail.
    Then the Add panel shows the computed cost per volume "3.84"
    When I submit the Add panel
    Then I should see the message "Data saved successfully"
    And the road record is persisted with its derived figures
    And the new record row shows its derived figures
    And the schedule totals are recomputed from the new record
    When I run Schedule 6 Check Status
    Then I should see the message "All requirements for this schedule have been met"
