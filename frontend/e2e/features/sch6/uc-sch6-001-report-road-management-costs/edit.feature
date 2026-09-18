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
#    POST /records. Seeding the row in SQL instead would need an explicit-id ROAD_MAINTENANCE_REPORT
#    row plus its ILCR_COST_REPORT_DETAIL children mirrored into the CI seed, and
#    ROAD_MAINTENANCE_REPORT_ID is not yet a parent column in preflight/ci-seed-parity.setup.ts. This
#    is the same "the scenarios' own Givens save the state they then edit" pattern sch4 and sch5 use.
#
# BEYOND THE LEGACY TEXT, and both earn their place:
#  * The edit must UPDATE IN PLACE, asserted as exactly one row surviving. The page-level Save posts
#    every served record in one PUT, so a bug that treated an edited row as new would leave the old one
#    behind AND still display the new figures — the only visible symptom would be doubled totals.
#  * The fields S02 does NOT touch (area type, supply block, and the RMG derived from it) are asserted
#    unchanged, so a PUT that blanked what it was not asked to change cannot pass.
#
# NOT COVERED HERE (see coverage.md): changing an existing record's AREA TYPE is S19's subject
# (TSA -> TFL on a saved record) and is deliberately not mixed in — S02 is about the amounts. Optimistic
# locking on a stale revisionCount is its own concern and is not part of this slice.

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
