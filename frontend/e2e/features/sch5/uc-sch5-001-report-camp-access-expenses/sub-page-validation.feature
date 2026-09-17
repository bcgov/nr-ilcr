# Re-grounded from UC-SCH5-001-S21 / S22 / S23. The three slices that validate the Other Camp and
# Other Access expense sub-pages.
#
# THE TWO SUB-PAGES ARE NOT SYMMETRIC, AND THAT ASYMMETRY IS THE SUBJECT. Every difference below is a
# separately verified legacy fact, each cited at its constant in
# `components/schedule5SubPage/validation.ts`:
#
#   * COST BAND IS PER PAGE, NOT PER CONTROL. Every cost input on the Other Camp page carries
#     `costSize="7"` (add-form :45, grid :79) -> ±9,999,999. NEITHER Other Access input carries one
#     (:36-38, :71-76) -> the ILCRCostValidator default of ±99,999,999. S23 scripts exactly these two
#     bounds and is RIGHT; the committed AC and all three UC documents record it wrongly
#     (deviation (A)). So for once the Gherkin is the accurate document.
#
#   * REQUIRED TIMING DIFFERS ON A GRID ROW, and that IS S21 vs S22. The Access grid's description
#     input carries `<f:ajax event="change">` (:63) so a cleared description reports immediately; the
#     Camp grid's does not (:64-67), so its check is deferred to Save. The rewrite reproduces both
#     (`VALIDATES_ROW_ON_CHANGE`). Validating both on change would erase the distinction the two
#     slices exist to describe — which is why S22 asserts a NEGATIVE (no error on change) that is
#     only meaningful because S21 asserts the positive for the same action.
#
#   * BOTH ADD-FORMS REQUIRE A DESCRIPTION — see SPEC-4 in defects.md. S22's premise is that the Camp
#     add-form does not, so Add succeeds with a blank description and Save is what blocks it. Both
#     add-forms carry `required="true"` (schedule5CampExpenses.xhtml:39,
#     schedule5AccessExpenses.xhtml:32), so a blank description never reaches the grid by that route.
#     S22 is therefore re-grounded onto the route that IS reachable and that its own title describes:
#     add a row, clear its description in the grid, and watch Save block it.
#
# FLD-001'S SUB-PAGE `[UNKNOWN]` IS RESOLVED. Both S21 and S22 carry the required-field text as
# `[UNKNOWN]` because legacy overrode no `requiredMessage`. The app states it: `Value Required` — the
# same `missingRequiredFieldMsg` bundle string Check Status composes its lines from.
#
# EVERY REJECTION IS PROVED AGAINST THE DATABASE, not just the screen. Add COMMITS when it succeeds
# (`handleAdd` -> `save`), so "the row is not added" is a claim about stored state, and after a
# blocked Save the grid still shows whatever was typed — the screen cannot tell "rejected" from
# "accepted and re-rendered". Each scenario reads the camp's served sub-page rows back.
#
# ANCHORS: 25053/2022 (S21), 25054/2022 (S22), 9050/2023 (S23). S23 exercises BOTH pages in ONE
# scenario rather than as an outline, because both arms mutate the same camp on the same anchor and
# the suite runs `fullyParallel` — two examples would race each other.

@sch5 @UC-SCH5-001 @sub-page-validation
Feature: Report Camp and Access Expenses (Schedule 5) — expense sub-page validation
  As a mill reporter
  I want the expense sub-pages to reject a blank description or an out-of-range cost
  So that every listed expense is identifiable and its amount is storable

  # S21 — the ACCESS page. Blank description blocked at Add, and a cleared grid description reports
  # IMMEDIATELY, which is the half of the pair that makes S22's negative meaningful.
  @p1 @S21 @FLD-001
  Scenario: Other Access Expense rejects a blank description, at Add and on change
    Given the Schedule 5 anchor "access-desc-blank" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "North Camp" camp
    And I open the "access" sub-page
    Then the "access" sub-page is shown

    # Add with the Description left blank. RESOLVES the [UNKNOWN]: the text is "Value Required".
    When I enter the sub-page description "" and cost "220"
    And I click the sub-page Add button
    Then the sub-page "Description" field shows the error "Value Required"
    And the "access" list holds 0 rows
    And the stored "access" rows are exactly ""

    # Supply it and the row is added.
    When I enter the sub-page description "Ferry Crossing" and cost "220"
    And I click the sub-page Add button
    Then the "access" list holds 1 rows
    And the stored "access" rows are exactly "Ferry Crossing"

    # The ACCESS grid validates a cleared description ON CHANGE — no Save needed. This is the
    # `f:ajax event="change"` half of the S21/S22 pair.
    When I clear the first sub-page row description
    Then the first sub-page row description shows the error "Value Required"

  # S22 — the CAMP page. Same action, and the error must NOT appear until Save.
  @p1 @S22 @FLD-001
  Scenario: Other Camp Expense defers its blank-description check to the sub-page Save
    Given the Schedule 5 anchor "camp-desc-blank" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "North Camp" camp
    And I open the "camp" sub-page
    Then the "camp" sub-page is shown

    # SPEC-4: the source expects Add to SUCCEED here with a blank description. It does not — the Camp
    # add-form carries required="true" just as the Access one does, so the rejection is identical to
    # S21's and the blank row never reaches the grid by this route.
    When I enter the sub-page description "" and cost "350"
    And I click the sub-page Add button
    Then the sub-page "Description" field shows the error "Value Required"
    And the "camp" list holds 0 rows

    # The reachable route to a blank grid description, and what the slice is actually about.
    When I enter the sub-page description "Generator Fuel" and cost "350"
    And I click the sub-page Add button
    Then the "camp" list holds 1 rows
    And the stored "camp" rows are exactly "Generator Fuel"

    # THE DISTINCTION: clearing it raises NOTHING on the Camp page, where S21's identical action on
    # the Access page raised the error at once.
    When I clear the first sub-page row description
    Then the first sub-page row description shows no error

    # Save is where it surfaces, and the save must be blocked.
    When I save the Schedule 5 sub-page
    Then the first sub-page row description shows the error "Value Required"
    And the stored "camp" rows are exactly "Generator Fuel"

    # Restore it and the save goes through.
    When I set the first sub-page row description to "Generator Fuel"
    And I save the Schedule 5 sub-page
    Then I should see the message "Data saved successfully"
    And the stored "camp" rows are exactly "Generator Fuel"

  # S23, first half — the CAMP page's band is the NARROW one (costSize="7" on every input here).
  #
  # WHY S23 IS TWO SCENARIOS ON TWO ANCHORS rather than one visiting both pages. Returning from a
  # sub-page leaves the camp panel OPEN and DIRTY — its Other-expense figures have moved underneath
  # it — so the next navigation raises a discard confirm ("Switch camp report" on Edit, "Leave camp
  # report" on a sub-page link), and a sub-page Save does not clear it. Answering those confirms is
  # S10/S11's subject; folding it in here would make S23 partly about confirms. Two single-page
  # scenarios say exactly what S23 means, and they parallelise. 22050/2023 was minted for the second.
  @p1 @S23 @FLD-002
  Scenario: The Other Camp Expense sub-page rejects a cost above 9,999,999
    Given the Schedule 5 anchor "subpage-cost" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "North Camp" camp
    And I open the "camp" sub-page
    Then the "camp" sub-page is shown

    When I enter the sub-page description "Generator Fuel" and cost "15000000"
    And I click the sub-page Add button
    Then the sub-page "Cost $" field shows the error "Entered cost must be between -9,999,999 and 9,999,999."
    And the "camp" list holds 0 rows
    And the stored "camp" rows are exactly ""

    When I enter the sub-page description "Generator Fuel" and cost "350"
    And I click the sub-page Add button
    Then the "camp" list holds 1 rows
    And the stored "camp" rows are exactly "Generator Fuel"

  # S23, second half — the ACCESS band is TEN TIMES WIDER. The rejected value has to be an order of
  # magnitude larger for the test to mean anything: 15000000, rejected above, is ACCEPTED here.
  @p1 @S23 @FLD-002
  Scenario: The Other Access Expense sub-page rejects a cost above 99,999,999
    Given the Schedule 5 anchor "subpage-cost-access" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "North Camp" camp
    And I open the "access" sub-page
    Then the "access" sub-page is shown

    When I enter the sub-page description "Ferry Crossing" and cost "150000000"
    And I click the sub-page Add button
    Then the sub-page "Cost $" field shows the error "Entered cost must be between -99,999,999 and 99,999,999."
    And the "access" list holds 0 rows
    And the stored "access" rows are exactly ""

    # The value the CAMP page rejected, accepted here — the two bands proved against each other
    # rather than each in isolation.
    When I enter the sub-page description "Ferry Crossing" and cost "15000000"
    And I click the sub-page Add button
    Then the "access" list holds 1 rows
    And the stored "access" rows are exactly "Ferry Crossing"
