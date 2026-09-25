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

  # -------------------------------------------------------------------------------------------------
  # S20 — MIXED RESULTS, and the only state in which the PER-RECORD "met" line appears at all.
  # Re-grounded from UC-SCH6-001-S20.feature. That line is emitted when the SCHEDULE fails while some
  # individual record passes, so it needs at least two records that disagree — which is why S20 has its
  # own anchor rather than borrowing one of the three above. Every anchor here is asserted record-free
  # at rest, and two scenarios writing to one cell races under `fullyParallel`.
  #
  # VERIFIED AGAINST THE RUNNING ENDPOINT before authoring (two records posted, check-status on the
  # served payload, both records then deleted): outcome "ISSUES", `messages: []`, record 1 met with
  # "All requirements for 1 have been met.", record 2 failing with
  # "Road : 2 - TSA or TFL (Cost $) : Value Required". Both literals byte-identical to the Gherkin.
  #
  # NOTE THE TRAILING PERIOD, and that it is the OPPOSITE of the schedule-level message: the per-record
  # line ends in one (`roadRequirementsMetMsg`, messages.properties:151) while the schedule-level
  # "All requirements for this schedule have been met" does NOT (:191). Both are pinned verbatim; the
  # difference is real, not a transcription slip.
  #
  # THE ORDINALS ARE PART OF THE CLAIM. "for 1" and "Road : 2" say WHICH row passed and which failed,
  # and the row counter is the 1-based DISPLAY position — so the Given creates the complete record
  # FIRST and then asserts the served order, rather than trusting insertion order to survive.
  #
  # ROW 2 IS THE SAME CLASSIFICATION AS ROW 1, missing only the cost, so the finding is attributable to
  # that one field and nothing else. Its cost is ABSENT rather than 0: the check is null-only (D2
  # precedent), so a stored 0 would PASS and this scenario would have no failing record at all — it
  # would then be asserting the absence of the schedule banner against a schedule that legitimately
  # passed, which is a vacuous green.
  @p1 @S20
  Scenario: Check Status reports each record's own result when one passes and one fails
    Given the Schedule 6 anchor "mixed-check" is an editable Draft with no road records
    And two road maintenance records exist on that anchor, the second missing its cost
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I run Schedule 6 Check Status
    Then Check Status reports that row 1 has met its requirements
    And Check Status reports the second row is missing its cost
    # The passing record does NOT earn the schedule a pass — the schedule-level banner must stay away
    # while any record is incomplete.
    And Check Status does not report the schedule as met

  # -------------------------------------------------------------------------------------------------
  # S21 — ONE record, SEVERAL gaps. Re-grounded from UC-SCH6-001-S21.feature.
  # The claim is "list every missing value, not just the first one it finds", and the app satisfies it
  # because `evaluateRecord` accumulates findings into a LIST rather than returning on the first
  # failure (Schedule6Service:873-890). Asserting only one of the two lines would pass against an
  # implementation that stopped early, so both are asserted — and both for the SAME row.
  #
  # Verified by probe before authoring: a TSA record with no supply block AND no cost stores fine
  # (HTTP 200, with `rmg` null because there is no block to derive from), and check-status returns both
  # findings for row 1 in the Gherkin's own order — Supply Block first, then Cost.
  #
  # Neither message is new: both are already pinned in CHECK_LINES for row 1 by S09 and S11, which is
  # why this slice adds a record and an anchor but no fresh literals.
  @p1 @S21
  Scenario: Check Status lists every missing value on a single record
    Given the Schedule 6 anchor "multi-missing" is an editable Draft with no road records
    And a road maintenance record with neither a supply block nor a cost commented "E2E S21 doubly-incomplete record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I run Schedule 6 Check Status
    Then Check Status reports both missing values for that record
    And Check Status does not report the schedule as met
