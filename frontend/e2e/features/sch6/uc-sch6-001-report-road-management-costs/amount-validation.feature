# Re-grounded from UC-SCH6-001-S13/S14/S15/S16.feature in
# _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/ (legacy JSF/PrimeFaces). Four slices,
# one subject: the Add panel's two numeric fields refuse an entry they cannot store, then accept a
# corrected one. Volume has an invalid-format rule (S13) and a range rule (S14); Cost has the same pair
# (S15, S16).
#
# ALL FOUR MESSAGES MATCH THE SOURCE GHERKIN BYTE-FOR-BYTE — unusually, nothing had to be re-grounded
# about the text. They are declared in `ROAD_MESSAGES` (src/components/schedule6/validation.ts:60-70),
# which that module's header states is transcribed verbatim from the backend bundle so an advisory
# message reads identically to a server rejection. Pinned in the fixture, not inlined here.
#
# WHEN THE ERRORS APPEAR — the one material re-grounding, shared with S12 (defects.md VER-5).
# The legacy scenarios read "When I enter a non-numeric value in the field / Then the system displays
# the error", i.e. per-field ajax validation as you leave the field. In this app NOTHING validates on
# blur: `onBlur` calls `commitRate`, which runs `validateRoadRecord` only to decide whether to advance
# the $ / m³ baseline and then discards the errors (index.tsx:533-542). The messages a reporter sees are
# set by `handleAdd` (index.tsx:682-686). So every error below is asserted after **Add Report**.
#
# THE REFUSALS COST NOTHING, WHICH IS THE OPPOSITE OF S05 — worth stating because the two look alike.
# `handleAdd` returns the moment `validateRoadRecord` reports anything, BEFORE the POST is built, so
# these four are decided entirely in the browser and the spy asserts **zero** mutating requests. S05's
# invalid TFL number costs exactly **one**, because "valid" there means "resolves to an RMG" and only
# the server holds that table. Both scenarios still read the anchor back afterwards: a spy proves no
# request was sent, and only a read proves nothing was stored.
#
# TWO THINGS EACH REJECT ARM ASSERTS BEYOND THE GHERKIN, both of them deliberate app behaviour:
#
#   1. THE TYPO STAYS ON SCREEN. Both masks return text they cannot parse UNCHANGED — "so a typo stays
#      on screen for the user to correct" (utils/number.ts:59-64) — and an in-range-but-refused value
#      still re-groups ("10000000" -> "10,000,000"). A field that blanked or zeroed a bad entry would
#      hide the reporter's own mistake from them, and would still satisfy the error assertion.
#   2. THE $ / m³ CELL DOES NOT MOVE. `commitRate` advances the rate baseline only from an entry that
#      passes the gate (ruled 2026-08-21): committing an unparseable or out-of-range value would show a
#      rate no Save could ever persist. The panel opens at `EMPTY_RATE_INPUTS`, so the cell reads the
#      empty string — `ratioMask(null)` returns '' (index.tsx:86-95).
#
# THE CORRECTION ARMS DO NOT SAVE, and that is why all of them share one anchor. The legacy correction
# scenarios end at "the field accepts the value / the cal field recomputes the cost-per-volume" — they
# never ask for a store. So nothing here writes, and every scenario in this file rides the shared
# VALIDATE-ONLY cell (22050/2024). Only S12's correction arm saves, which is why it alone needed a
# minted cell of its own. Keep this file writer-free: the moment a scenario here saves, it needs its own
# (mill, year) under `fullyParallel`.
#
# EACH CORRECTION ARM RE-CREATES THE ERROR ITSELF. The legacy files' second scenarios continue from the
# first ("Given the system has displayed the error ..."); scenarios here must be independent, so each
# correction arm enters the bad value, submits, then fixes it — the structure S05 established.
#
# THE STALE ERROR IS ASSERTED, NOT IGNORED. Correcting a field does not clear the message: `setAddField`
# only updates the form (index.tsx:610-611) and `addErrors` is rewritten in just two places,
# `handleAdd` and `resetTransient` (index.tsx:563, 684-687). So the corrected value and the old error
# sit on screen together until Add Report is pressed again.
#
# NOTE THIS IS NOT LOGGED AS A DIVERGENCE, and the distinction matters. The legacy Gherkin does not
# assert that the error clears — its correction scenarios end at "the field accepts the value" and "the
# cal field recomputes", both of which are TRUE here and asserted green below. So nothing diverges from
# the spec. Legacy DID clear it as you left the field, but that follows from its per-field ajax
# validation, and choosing submit-time validation instead is the deliberate design already accepted as
# not-a-defect in defects.md VER-3 — with no moment at which the app re-judges a field, there is nothing
# to clear the message. Recorded as **defects.md VER-5**, whose UX consequence BA/QA SETTLED on
# 2026-09-23: the lingering message is accepted and no enhancement is being raised. Making it clear on
# edit would be a new requirement, not a bug fix.
#
# It is asserted rather than ignored so the suite states what the app actually does: if anyone later
# makes the error clear on edit, that step fails and puts the change in front of a human.
#
# WHY THE RANGE SLICES ARE OUTLINES WITH BOTH BOUNDS. The Gherkin states two-sided rules ("outside 0 and
# 9,999,999", "outside -99,999,999 and 99,999,999"), and a scenario that only tried a too-LARGE value
# would leave the floor unproven — the one-armed asymmetry the coverage guidance calls a smell. Cost's
# floor is genuinely negative (a road cost may be negative here, unlike a volume), so `-100000000` is
# the only way to show that bound exists. S14 carries a third row, `100.123`, which is IN range but has
# three decimals: the app resolves that to the SAME range message (validation.ts:130-136, because
# VOLUME is NUMBER(10,2) and >2 decimals trips the backend's `@Digits` onto the same key). It is
# included precisely because the message is shared — a future change that split the two causes would
# otherwise go unnoticed.
#
# NOT COVERED HERE (see coverage.md): a blank volume or cost is VALID at this gate — `validateVolume`
# and `validateCost` both return undefined on blank (validation.ts:121-125, 140-144), because a missing
# cost is a Check Status finding rather than a save failure (S09), not an entry error. The per-record
# Comments 400-character cap is a different field and a different rule.

@sch6 @UC-SCH6-001 @amount-validation
Feature: Report Road Management Costs (Schedule 6) — the volume and cost entries are validated
  As a mill reporter
  I want an unparseable or out-of-range volume or cost to be refused before it is stored
  So that every stored road record carries figures the schedule can actually total

  # ---- S13 — a non-numeric volume ----------------------------------------------------------------
  # "abc" is refused by `parseDecimalInput`, whose accepted syntax is an optional '-', digits with
  # optional comma grouping and an optional '.' fraction (utils/number.ts:105).

  @p1 @S13
  Scenario: A non-numeric volume is refused and nothing is stored
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I will record a road maintenance record commented "E2E S13 rejected non-numeric volume"
    And I have selected that mill and reporting year on the Home page
    And I am watching for Schedule 6 writes
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the volume "abc" with a valid cost
    And I submit the Add panel
    Then I should see the error "Entered volume entry is invalid."
    And the Add panel volume field still reads "abc"
    And the Add panel shows no computed cost per volume
    And no Schedule 6 write was attempted
    And no road record was stored on that anchor

  @p1 @S13
  Scenario: Correcting the volume makes it acceptable and recomputes the rate
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the volume "abc" with a valid cost
    And I submit the Add panel
    Then I should see the error "Entered volume entry is invalid."
    When I correct the volume
    Then the Add panel volume field still reads "16,000"
    And the Add panel shows the computed cost per volume "3.50"
    And the error "Entered volume entry is invalid." is still shown until the next submit

  # ---- S14 — a volume outside 0 to 9,999,999 ------------------------------------------------------

  @p1 @S14
  Scenario Outline: A volume outside 0 and 9,999,999 is refused and nothing is stored — <why>
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I will record a road maintenance record commented "E2E S14 rejected volume <entry>"
    And I have selected that mill and reporting year on the Home page
    And I am watching for Schedule 6 writes
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the volume "<entry>" with a valid cost
    And I submit the Add panel
    Then I should see the error "Entered volume must be between 0 and 9,999,999."
    And the Add panel volume field still reads "<display>"
    And the Add panel shows no computed cost per volume
    And no Schedule 6 write was attempted
    And no road record was stored on that anchor

    Examples:
      | entry    | display    | why                                          |
      | 10000000 | 10,000,000 | maximum plus one                             |
      | -1       | -1         | minimum minus one, the floor the rule states |
      | 100.123  | 100.123    | in range but three decimals, same message    |

  @p1 @S14
  Scenario: Bringing the volume back into range recomputes the rate
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the volume "10000000" with a valid cost
    And I submit the Add panel
    Then I should see the error "Entered volume must be between 0 and 9,999,999."
    When I correct the volume
    Then the Add panel volume field still reads "16,000"
    And the Add panel shows the computed cost per volume "3.50"
    And the error "Entered volume must be between 0 and 9,999,999." is still shown until the next submit

  # ---- S15 — a non-numeric cost ------------------------------------------------------------------

  @p1 @S15
  Scenario: A non-numeric cost is refused and nothing is stored
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I will record a road maintenance record commented "E2E S15 rejected non-numeric cost"
    And I have selected that mill and reporting year on the Home page
    And I am watching for Schedule 6 writes
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the cost "abc" with a valid volume
    And I submit the Add panel
    Then I should see the error "Entered cost is invalid."
    And the Add panel cost field still reads "abc"
    And the Add panel shows no computed cost per volume
    And no Schedule 6 write was attempted
    And no road record was stored on that anchor

  @p1 @S15
  Scenario: Correcting the cost makes it acceptable and recomputes the rate
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the cost "abc" with a valid volume
    And I submit the Add panel
    Then I should see the error "Entered cost is invalid."
    When I correct the cost
    Then the Add panel cost field still reads "56,000"
    And the Add panel shows the computed cost per volume "3.50"
    And the error "Entered cost is invalid." is still shown until the next submit

  # ---- S16 — a cost outside -99,999,999 to 99,999,999 --------------------------------------------

  @p1 @S16
  Scenario Outline: A cost outside -99,999,999 and 99,999,999 is refused and nothing is stored — <why>
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I will record a road maintenance record commented "E2E S16 rejected cost <entry>"
    And I have selected that mill and reporting year on the Home page
    And I am watching for Schedule 6 writes
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the cost "<entry>" with a valid volume
    And I submit the Add panel
    Then I should see the error "Entered cost must be between -99,999,999 and 99,999,999."
    And the Add panel cost field still reads "<display>"
    And the Add panel shows no computed cost per volume
    And no Schedule 6 write was attempted
    And no road record was stored on that anchor

    Examples:
      | entry      | display      | why                                              |
      | 100000000  | 100,000,000  | maximum plus one                                 |
      | -100000000 | -100,000,000 | minimum minus one, and costs may be negative     |

  @p1 @S16
  Scenario: Bringing the cost back into range recomputes the rate
    Given the Schedule 6 anchor "validate-only" is an editable Draft with no road records
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I open the Add Road Maintenance report panel
    And I enter the cost "100000000" with a valid volume
    And I submit the Add panel
    Then I should see the error "Entered cost must be between -99,999,999 and 99,999,999."
    When I correct the cost
    Then the Add panel cost field still reads "56,000"
    And the Add panel shows the computed cost per volume "3.50"
    And the error "Entered cost must be between -99,999,999 and 99,999,999." is still shown until the next submit
