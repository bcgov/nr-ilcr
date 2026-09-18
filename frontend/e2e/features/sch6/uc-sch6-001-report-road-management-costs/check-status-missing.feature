# Re-grounded from the UC-SCH6-001 Gherkin for S09, S10 and S11 (legacy JSF/PrimeFaces) — the three
# Check Status "Value Required" findings.
#
# WHAT RE-GROUNDING CHANGED
#  * `schedule6Form:checkStatusButton0` -> the first of the two "Check Status" buttons, and the click
#    goes through the shared awaiting helper (pages/common/checkStatus.ts) rather than a bare click.
#    That matters here specifically: each scenario asserts an ABSENCE ("the met banner is not shown"),
#    and an absence asserted immediately after a click can pass against a DOM that has not re-rendered.
#  * The findings are Carbon notifications carrying an explicit severity word ("Action required"),
#    not a `p:messages` panel.
#  * The composed lines are asserted BYTE-FOR-BYTE. They are built server-side exactly as legacy built
#    them: `"Road : " + rowCounter + segment + ": " + text`
#    (Schedule6CheckStatusResolver:112-125). `rowCounter` is the 1-based DISPLAY ordinal, the same
#    number the accordion titles carry — never the recordId.
#
# "TSA or TFL (Cost $)" IS A LEGACY MISLABEL AND IS PRESERVED DELIBERATELY.
# The missing-COST line names the TSA/TFL field (Schedule6MB.checkStatus :172). It is a labelling
# quirk in the original source; Schedule6Service's header lists it among the pinned quirks, and the
# source Gherkin's own note says it is "reproduced exactly as found (not corrected here)". Do not tidy
# it — the text IS the requirement. If it is ever corrected, that is a deliberate product decision and
# this assertion should fail loudly rather than tolerate both.
#
# WHY S10 LOOKS DIFFERENT FROM THE OTHER TWO — it is the one state that cannot be saved.
# Probed 2026-09-17 against the running app:
#   * a TSA record with NO COST           -> 200, stored. S09's state is storable.
#   * a TSA record with NO SUPPLY BLOCK   -> 200, stored. S11's state is storable (a missing Supply
#     Block is a Check Status finding, never a save failure — only its width is enforced on write).
#   * a TFL record with NO TFL NUMBER     -> 400 FLD-002. NOT storable.
# So S09 and S11 seed their state through the app's own POST, while S10 saves a VALID TFL record and
# then blanks the field ON SCREEN without saving. Check Status still reports it, because the endpoint
# evaluates the payload the screen sends rather than the database (Schedule6CheckRequest).
#
# That is worth stating plainly: the backend comment notes this branch was "ported verbatim though it
# is unreachable from persisted rows (legacy view-state-only)". With the payload-based endpoint it is
# reachable again from the screen — which is exactly the legacy behaviour, since legacy's own
# ajax="false" postback evaluated the screen too. S10 is the slice that proves it.
#
# WHAT CHECK STATUS DOES **NOT** REQUIRE, recorded so nobody adds an assertion for it later:
#   * VOLUME is never checked — commented out in legacy (Schedule6CheckStatus:19), ported verbatim.
#   * A cost of ZERO PASSES. The check is null-only (D2 precedent), so 0 is MET, not a finding. S09's
#     record therefore has NO cost at all rather than a cost of 0, and the Given asserts that.
#
# ANCHORS: 22051/2024 (S09), 23051/2024 (S10), 23052/2024 (S11) — all minted, one each, because all
# three save a record to reach their state and a writer cannot share a (mill, year) under
# `fullyParallel`.
#
# NOT COVERED HERE (see coverage.md): a record missing MORE THAN ONE required value at once is S21,
# and a mix of passing and failing records — which is the only way the per-record "met" line is
# emitted — is S20. Both need states no anchor here holds.

@sch6 @UC-SCH6-001 @check-status-missing
Feature: Report Road Management Costs (Schedule 6) — Check Status names the missing value
  As a mill reporter
  I want Check Status to tell me which road record is missing which required value
  So that I can complete the schedule before submitting it

  @p1 @S09
  Scenario: Check Status flags a road record with no cost
    Given the Schedule 6 anchor "check-missing-cost" is an editable Draft with no road records
    And a road maintenance record with no cost commented "E2E S09 cost-less record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I run Schedule 6 Check Status
    # The legacy mislabel: this is the COST finding, but it names the TSA/TFL field.
    Then Check Status reports "Road : 1 - TSA or TFL (Cost $) : Value Required"
    And Check Status does not report the schedule as met

  @p1 @S10
  Scenario: Check Status flags a TFL record whose TFL number has been cleared on screen
    Given the Schedule 6 anchor "check-missing-tfl" is an editable Draft with no road records
    And a valid TFL road maintenance record commented "E2E S10 TFL record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    # The record is left in a state the write path would REFUSE, and never saved. Check Status still
    # describes it, because it evaluates the on-screen payload.
    And I clear the record's TFL number on screen
    And I run Schedule 6 Check Status
    Then Check Status reports "Road : 1 - TFL Number : Value Required"
    And Check Status does not report the schedule as met

  @p1 @S11
  Scenario: Check Status flags a TSA record with no supply block
    Given the Schedule 6 anchor "check-missing-supply-block" is an editable Draft with no road records
    And a road maintenance record with no supply block commented "E2E S11 supply-block-less record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I run Schedule 6 Check Status
    Then Check Status reports "Road : 1 - Supply Block : Value Required"
    And Check Status does not report the schedule as met
