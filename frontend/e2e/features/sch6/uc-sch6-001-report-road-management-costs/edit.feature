# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/UC-SCH6-001-S02.feature
# (legacy JSF/PrimeFaces). S02 changes the volume and cost on a record that already exists and saves
# with the page-level Save.
#
# WHAT RE-GROUNDING CHANGED
#  * `roadAccord` -> a Carbon `Accordion`, one `AccordionItem` per record titled
#    "Road Maintenance report Id: <ordinal>". THE ORDINAL IS THE 1-BASED POSITION in roadRecords[], not
#    the recordId (index.tsx:1009-1012) — legacy's `rowCounter` means the same thing, and it is what
#    the check-status lines key on, so confusing the two reads as an off-by-one in the app.
#  * EXPANDING IS REQUIRED, and not just to mirror the legacy step. Rows start COLLAPSED (no `open`
#    prop) and Carbon renders every item's children into the DOM regardless of which panel is expanded
#    (index.tsx:479). So an assertion on a collapsed row's field passes without the reporter being able
#    to see it. Every row assertion in this suite therefore expands first and waits on VISIBILITY.
#  * "I change the row's volume/cost field" -> `#row-<recordId>-volume` / `-cost`, filled (replacing,
#    which is what an edit means) and BLURRED. The blur is load-bearing: both fields re-group on blur
#    and the $/m³ baseline is committed by that same handler, so asserting the recomputed rate without
#    blurring reads a stale figure and looks like a derived-figure defect.
#  * `schedule6Form:saveButton0` -> the page-level "Save". Legacy carried Save twice (above the
#    schedule and below the General Comment) and the React page mirrors that, so the page object takes
#    the first — which is the `saveButton0` the legacy scenario names.
#  * `schedule6Form:messages` -> Carbon notifications carrying an explicit severity word.
#  * ANCHOR: 10050/2024, opened by real-test-data-patches/sch6/draft-anchors.sql. EMPTY AT REST like
#    every other mutating anchor — the Given creates the record it then edits through the app's own
#    POST /records, the same "the scenarios' own Givens save the state they then edit" pattern sch4 and
#    sch5 use. Creating through the API stays preferred over seeding a row in SQL even though the seed
#    route now works (S17 added ROAD_MAINTENANCE_REPORT_ID to the parity gate's parentsByColumn on
#    2026-09-18), because an app-created record is one whose shape the write path itself produced.
#    Only S17 has to seed, its page being non-Draft and so refusing every write.
#
# BEYOND THE LEGACY TEXT, and both earn their place:
#  * The edit must UPDATE IN PLACE, asserted as exactly one row surviving. The page-level Save posts
#    every served record in one PUT, so a bug that treated an edited row as new would leave the old one
#    behind AND still display the new figures — the only visible symptom would be doubled totals.
#  * The fields S02 does NOT touch (area type, supply block, and the RMG derived from it) are asserted
#    unchanged, so a PUT that blanked what it was not asked to change cannot pass.
#
# NOT COVERED HERE (see coverage.md): optimistic locking on a stale revisionCount is its own concern
# and is not part of either slice in this file.
#
# ---------------------------------------------------------------------------------------------------
# S19 (below) — THE OTHER KIND OF EDIT: the CLASSIFICATION, not the amounts.
# Re-grounded from UC-SCH6-001-S19.feature. S02 deliberately leaves this alone so that a failure in
# either slice is unambiguous; here the amounts are instead held CONSTANT across the switch
# (40,000 / 10,000 = 4.00 exactly, before and after), so the RMG moving from "15" to "10" cannot be
# mistaken for an amounts recalculation — and the unchanged rate doubles as proof the PUT did not
# disturb what it was not asked to touch.
#
# THE SAME BR-02 TOGGLE AS S03, NOW ON A SAVED ROW. Choosing "TFL" enables the row's TFL number field
# and disables its Supply Block, because the row editor renders the very same `RoadRecordFields`
# component as the Add panel (one definition, index.tsx:240-364) — which is exactly why the
# counterpart-clear has to be proved at the DATABASE and not on screen.
#
# THE LOAD-BEARING ASSERTION IS THE COUNTERPART-CLEAR ON AN **UPDATE**. A PUT that set the TFL side
# while leaving the old TSA and Supply Block populated would store a row belonging to both branches at
# once — and the screen would look perfectly correct, because the form disables the Supply Block
# control regardless of what is stored behind it. S03 proves this clearing happens on an INSERT; only
# this slice proves it happens on an UPDATE, which is a different code path
# (updateRoadRecord vs insertRoadReport).
#
# ANCHOR: 25052/2024, its own cell because S19 writes. Empty at rest; its Given creates the TSA record
# through the app's POST and asserts it really started on the TSA branch — a record that arrived
# already on the TFL branch would make every later assertion pass while testing nothing.

@sch6 @UC-SCH6-001 @edit
Feature: Report Road Management Costs (Schedule 6) — edit an existing road maintenance record
  As a mill reporter
  I want to change the volume and cost on a road maintenance record I already saved
  So that I can correct or update previously reported road maintenance data

  @p1 @S02
  Scenario: Change the volume and cost on an existing record and save
    Given the Schedule 6 anchor "edit" is an editable Draft with no road records
    And a road maintenance record commented "E2E S02 road record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    Then the record row shows the amounts it was created with
    When I change the record's volume and cost
    # Before any save: 90,000 / 20,000 is exactly 4.50, so the client-side derivation is proved on its
    # own, independently of the round-trip that follows.
    Then the record row shows the recomputed cost per volume "4.50"
    When I save the schedule
    Then I should see the message "Data saved successfully"
    And the edited amounts are persisted
    And the schedule totals are recomputed from the edited record

  @p1 @S19
  Scenario: Switch an existing record's area type from TSA to TFL and save
    Given the Schedule 6 anchor "reclassify" is an editable Draft with no road records
    And a TSA road maintenance record commented "E2E S19 reclassified record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    And I switch the record's area type to TFL
    # BR-02 on a saved row, both halves — either alone would pass on a form that disabled nothing.
    Then the row's TFL number is enabled and its Supply Block is disabled
    When I save the schedule
    Then I should see the message "Data saved successfully"
    # The RMG is re-derived from the TFL code via RoadGroupLookup, not from a supply block: "48" gives
    # "10", deliberately different from the block-derived "15" it started with.
    And the row shows its re-derived RMG
    And the reclassified record is persisted with its re-derived RMG
