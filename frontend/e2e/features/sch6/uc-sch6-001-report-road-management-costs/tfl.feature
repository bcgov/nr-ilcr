# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S03.feature
# (legacy JSF/PrimeFaces). S03 records a road against a Tree Farm Licence instead of a Timber Supply
# Area: choosing TFL switches which half of the classification is live.
#
# WHAT RE-GROUNDING CHANGED
#  * "TFL" is a SYNTHETIC SENTINEL the control prepends to the area-type list, not a served code — the
#    backend does not serve it (areaTypeOptions, mirroring legacy LookUpCacheDAO.java:229-230). So its
#    option text is the literal "TFL", unlike a real TSA whose option text is the code's DESCRIPTION.
#  * `schedule6AddForm:tflNumber` -> `#add-tfl-number`; `tsbNumberOneMenu` -> `#add-supply-block`. The
#    legacy assertions "tflNumber is enabled" / "tsbNumberOneMenu is disabled" carry over directly and
#    are asserted as a PAIR, because either alone would pass on a form that disabled nothing.
#  * THE ADD PANEL DOES NOT SHOW RMG (defects.md VER-1) — it is passed rmg="" deliberately
#    (index.tsx:401). The legacy step asserting `schedule6AddForm:RMG` therefore re-grounds onto the
#    API read-back and the saved row, exactly as it did for S01.
#
# WHY TFL "48": which TFL numbers are valid is a FIXED TABLE IN CODE, not a DB lookup —
# RoadGroupLookup.rmgByTflNumberCode is a verbatim port of legacy RoadGroupUtil.setRmgByTflNumberCode,
# and a TFL is valid IFF it resolves there (Schedule6Service:625). "48" resolves to RMG "10", which
# differs from the TSA path's "15" (supply block 01B) — so a scenario that confused the two branches
# fails instead of passing on a coincidentally equal value.
#
# BEYOND THE LEGACY TEXT: BR-02's counterpart-clear is asserted, not assumed. Choosing TFL must NULL
# both TSA columns on the way in (Schedule6Service:597, Schedule6DAO.java:221-224). A form that merely
# disabled the Supply Block control while still posting a stale value would satisfy every other
# assertion in this scenario, so the stored record is checked for an absent supply block.
#
# ANCHOR: 12050/2024 (minted; see real-test-data-patches/sch6/draft-anchors.sql). Mutating — the
# scenario adds a record and its cleanup registry deletes it again through the app's own DELETE.
#
# NOT COVERED HERE (see coverage.md): an INVALID TFL number is S05 (tfl-validation.feature). Switching
# an ALREADY-SAVED record from TSA to TFL is S19 and is deliberately separate — this slice starts on
# the TFL branch rather than moving onto it.

@sch6 @UC-SCH6-001 @tfl
Feature: Report Road Management Costs (Schedule 6) — record a TFL instead of a TSA
  As a mill reporter
  I want to record a road maintenance entry against a Tree Farm Licence rather than a Timber Supply Area
  So that roads belonging to a TFL are captured with a valid interior TFL number

  @p1 @S03
  Scenario: Select TFL as the area type, enter a valid TFL number, and save the record
    Given the Schedule 6 anchor "tfl" is an editable Draft with no road records
    And I will record a road maintenance record commented "E2E S03 TFL road record"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I select the TFL area type
    Then the TFL number field is enabled and Supply Block is disabled
    When I enter the S03 TFL road record
    # 60,000 / 15,000 is exactly 4.00, so the derived rate cannot turn on a rounding decision.
    Then the Add panel shows the computed cost per volume "4.00"
    When I submit the Add panel
    Then I should see the message "Data saved successfully"
    And the TFL road record is persisted with its derived RMG
