# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S09.feature
# (legacy JSF/PrimeFaces). S09 pins the one category that SUBTRACTS: Recoveries reduces the Camp Total
# instead of adding to it (BR-04).
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`; `schedule5Form:newCampSubtotalCost` and
#    `:newCampTotalCost` are read-only TABLE CELLS in the shared CategoryGrid, not fields — a derived
#    row renders as text, never as an input (index.tsx:203). They are matched by their verbatim grid
#    label, which keeps legacy's trailing ": ".
#  * The figures render through `masks.ts` `fmtCost`, so 1000 reads as "1,000" while 700 reads as "700".
#  * BLUR IS THE COMMIT POINT. The four derived rows mirror the COMMITTED entry while the panel is
#    editable (#291), not the keystroke — the app's own unit tests pin that ("not per keystroke"). The
#    step therefore fills AND blurs; a bare fill would leave the totals showing the previous figures and
#    the scenario would fail for a reason unrelated to the arithmetic.
#  * ANCHOR: 23051/2022 (`RECOVERIES_ANCHOR`).
#
# THIS SCENARIO NEVER SAVES, exactly like the legacy one — it ends on the on-screen recompute. So it
# also asserts the anchor still holds NO camps at the end, which turns "we did not save" from an
# assumption into an assertion and proves the recompute really is the client-side mirror rather than a
# server round-trip. Recoveries is the volume-less twelfth category (GRID_ROWS `hasVolume: false`), so
# no volume is entered and the `$/m³` column stays blank throughout.

@sch5 @UC-SCH5-001 @recoveries
Feature: Report Camp and Access Expenses (Schedule 5) — Recoveries reduces the Camp Total
  As a mill reporter
  I want the Recoveries amount to reduce the Camp Total rather than add to it
  So that the camp's net cost is accurately reflected

  @p1 @S09 @BR-04
  Scenario: Recoveries Cost reduces the Camp Total instead of adding to it
    Given the Schedule 5 anchor "recoveries" is an editable Draft with no camps
    And no camp named "Cedar Creek Camp" exists for that mill and year
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    And I name the new camp "Cedar Creek Camp" and set Isolated Camp to "No"
    And I enter a "Catering and Food" cost of "1000"
    Then the "Camp Sub-Total: " row shows "1,000"
    When I enter a "Recoveries" cost of "300"
    # BR-04 — Camp Total is Camp Sub-Total MINUS Recoveries, the only subtracting category.
    Then the "Camp Total: " row shows "700"
    And the "Camp Sub-Total: " row shows "1,000"
    And no camps are stored on the anchor
