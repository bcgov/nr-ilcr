# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH3-001/gherkin/UC-SCH3-001-S01.feature
# and -S03.feature (legacy JSF/PrimeFaces). S01 enters the eleven fixed administration cost lines, both
# timber volumes, the Override selection and a comment, then saves; S03 reopens and finds them pre-filled.
#
# WHAT RE-GROUNDING CHANGED
#  * `schedule3.xhtml` -> route `/schedule-3`; every `schedule3Form:*` NamingContainer id is gone. The
#    writable cells now carry stable Carbon ids derived from the cost-item code (`#harvest-27`,
#    `#pop-27`, `#popTimberVolume`, `#crownTimberVolume`, `#overrideHarvestTotalPop`, `#comments`) — so
#    the comments textarea and the Save button, both `[UNKNOWN]` in the legacy Gherkin, are addressable.
#  * The legacy scenario asserted only that amounts "are stored as cost-report detail records". This
#    asserts the full arithmetic — every Crown, both subtotals, Total Costs, both timber costs and all
#    three $/m3 figures — in the UI AND read back through the API, with every figure derived by hand in
#    `fixtures/sch3/schedule3-test-data.ts`. The three Harvest-only lines are included deliberately:
#    Annual Rents (29) and Silviculture Admin (37) store a server-forced PO&P of 0 while Scaling (33)
#    stores nothing and derives its PO&P from the timber-volume ratio (3,750 here).
#  * S03's "reopen" is asserted as a full page RELOAD, not a re-render: after Save the page re-seeds its
#    form from the response, which looks identical whether or not anything reached the database.

@sch3 @UC-SCH3-001 @happy-path
Feature: Report Forest Management Administration Costs (Schedule 3) — enter and save the fixed admin lines
  As a mill reporter
  I want to record the mill's forest management and administration costs for the reporting year
  So that the Harvest, PO&P and Crown figures are persisted and the overhead per unit is calculated

  @p0 @S01 @S03
  Scenario: Enter every fixed admin cost line and both timber volumes, save, and reopen
    Given the Schedule 3 anchor "happy-path"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I enter every fixed admin cost line and both timber volumes
    And I set the Override Harvest and Total PO&P selection to "N"
    And I enter the additional comments
    # The read-only Crown column tracks entry before Save (the derived mirror, defect #291) — legacy
    # recomputed it on each field's own AJAX round-trip.
    Then the "Licenses, Fees, Insurance" line shows Harvest "100000", PO&P "10000" and Crown "90000"
    When I save Schedule 3
    Then I should see the message "Data saved successfully"
    And the stored Schedule 3 holds those amounts
    And the stored Schedule 3 holds the derived figures
    And the stored Schedule 3 holds the comments and Override selection
    And the Schedule 3 page shows the derived figures for those amounts
    # BR-05: Scaling's PO&P is derived from the volume ratio (50,000 / 200,000 x 15,000), never entered.
    And the "Scaling Expense" line shows Harvest "15000", PO&P "3750" and Crown "11250"
    # BR-04: Annual Rents and Silviculture Admin capture no PO&P at all, so the cell is blank rather
    # than the backend's 0.
    And the "Annual Rents" line PO&P cell shows "a dash"
    And the "Annual Rents" line has no PO&P input
    And the "Silviculture Admin Costs" line PO&P cell shows "a dash"
    And the "Silviculture Admin Costs" line has no PO&P input
    # The BOTTOM action bar drives the same save (legacy carried Save twice; neither bar was ever
    # asserted to work). A no-op re-save of the same values must still succeed.
    When I save Schedule 3 from the bottom action bar
    Then I should see the message "Data saved successfully"
    When I reload the Schedule 3 page
    Then the Schedule 3 amounts are pre-filled with what was saved
    And the Schedule 3 page shows the derived figures for those amounts
    And the Schedule 3 comments show "E2E S01 — fixed admin lines and timber volumes."
