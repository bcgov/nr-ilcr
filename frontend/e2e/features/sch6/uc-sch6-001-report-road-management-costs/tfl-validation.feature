# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S05.feature
# (legacy JSF/PrimeFaces). S05 rejects a TFL number that resolves to no interior RMG, then accepts a
# corrected one.
#
# THE ONE MATERIAL BEHAVIOURAL DIFFERENCE — WHEN THE ERROR APPEARS.
# Legacy validated the TFL number by an ajax round-trip AS YOU LEFT THE FIELD, so its scenario reads
# "When I enter an out-of-range TFL number / Then the system displays the error". In the React app the
# client-side gate only catches BLANK and OVER-WIDE entries (validation.ts:170-176) and the input is
# maxLength={2}, so a two-character invalid code passes the client untouched and is rejected by the
# SERVER on submit (400, FLD-002). That is not a shortcoming: "valid" means "resolves to an RMG", and
# only the server can decide it — RoadGroupLookup is server-side. The MESSAGE is byte-identical either
# way, because the client mirrors the backend bundle deliberately so an advisory reads the same as a
# rejection. So the error is asserted after SUBMIT rather than after typing. Recorded as defects.md
# VER-3 — verified NOT a defect, but a real change in when the user sees the message.
#
# WHY "99" AND NOT "42": which TFL numbers are valid is a fixed table in code
# (RoadGroupLookup.rmgByTflNumberCode, a verbatim port of legacy RoadGroupUtil). "42" appears there as
# a DELIBERATELY commented-out case ("not used as described in TFL list v2, ILCR-161"), so a future
# reader could reasonably think it ought to resolve. "99" is in no list anywhere and cannot be
# mistaken for a regression. Both are two characters, which is what makes them reach the server at all.
#
# "AND THE VALUE IS NOT ACCEPTED" IS PROVED, NOT INFERRED — and the proof is a DB read, not the banner.
# An error banner does not establish that nothing was written, so the reject arm reads the anchor back
# and asserts it holds no record. That is the load-bearing assertion.
#
# The companion spy assertion expects exactly ONE mutating request, not zero — and it was written as
# zero first, which failed, so it is worth stating why. The client cannot pre-empt this rejection:
# only the server knows the RMG table, so the client gate lets a two-character invalid code through and
# the POST is answered 400. One request is therefore CORRECT behaviour; the app was right and the first
# version of the assertion was wrong. Pinned at exactly 1 rather than "at least 1" because that also
# catches a silent retry or double-submit, which would store the record twice on a later valid attempt.
# /check-status is excluded from the tally by contract, being a POST that mutates nothing.
#
# WHY TWO SCENARIOS AND TWO ANCHORS. The legacy file has two scenarios, the second beginning "Given
# the system has displayed the error ..." — i.e. continuing from the first. Scenarios here must be
# independent, so the correction arm re-enters the invalid value itself before correcting it. The arms
# CANNOT share an anchor: the reject arm writes nothing and so rides the shared VALIDATE-ONLY cell
# (22050/2024, which S12-S16 will also use), while the correction arm SAVES and therefore needs its own
# (17052/2024) — a writer cannot share a (mill, year) under `fullyParallel`. sch5 had to make exactly
# this split for its own S12.
#
# NOT COVERED HERE (see coverage.md): a BLANK TFL number on the TFL branch is caught client-side by
# the same message and belongs with the required-field slice (S12). The volume and cost rejections are
# S13-S16. All of those ride the same validate-only anchor.

@sch6 @UC-SCH6-001 @tfl-validation
Feature: Report Road Management Costs (Schedule 6) — an invalid TFL number is rejected
  As a mill reporter
  I want a TFL number that is not valid for interior regions to be refused
  So that no road record is ever stored against a TFL that resolves to no resource management grouping

  @p1 @S05
  Scenario: An out-of-range TFL number is refused and nothing is stored
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    # Registered even though this arm must store NOTHING. If the app ever regressed and stored the
    # rejected entry, a comment-less row would be invisible to cleanup and would strand the SHARED
    # validate-only anchor — turning one real failure into every later validation slice failing for
    # the wrong reason.
    And I will record a road maintenance record commented "E2E S05 rejected TFL attempt"
    And I have selected that mill and reporting year on the Home page
    And I am watching for Schedule 6 writes
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I select the TFL area type
    And I enter an out-of-range TFL number with valid amounts
    And I submit the Add panel
    Then I should see the error "Entered TFL number is not valid for Interior Regions."
    And the rejection came from the server, on exactly one attempt
    And no road record was stored on that anchor

  @p1 @S05
  Scenario: Correcting the TFL number lets the record save
    Given the Schedule 6 anchor "tfl-correction" is an editable Draft with no road records
    And I will record a road maintenance record commented "E2E S05 corrected TFL record"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I select the TFL area type
    And I enter an out-of-range TFL number with valid amounts
    And I submit the Add panel
    Then I should see the error "Entered TFL number is not valid for Interior Regions."
    When I correct the TFL number
    And I submit the Add panel
    Then I should see the message "Data saved successfully"
    And the corrected TFL record is persisted
