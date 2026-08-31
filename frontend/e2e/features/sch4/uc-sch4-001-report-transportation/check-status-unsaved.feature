# DIVERGENCE — this scenario is DELIBERATELY RED. It reproduces defects.md DIV-8, tracked upstream as
# bcgov/nr-ilcr#359, and stays failing until Check Status accounts for what is on screen. Do not weaken it,
# skip it, or "fix" it by asserting the current behaviour: the failing state IS the tracking signal. Filter
# it out of a fresh-failures run with `npm run test:gate`.
#
# WHAT IT REPRODUCES
# Check Status reports on the LAST SAVED locations and silently ignores anything typed into an open panel.
# Same app-wide defect Schedule 3 carries as DIV-6 — 11 of the 12 schedules are affected, Schedule 6 being
# the only correct implementation. Schedule 3's register entry holds the full analysis; this is the
# Schedule 4 instance, and one fix turns them all green.
#
# WHERE "UNSAVED" LIVES ON THIS PAGE. Schedule 4 saves per LOCATION, from the panel's own Save, while Check
# Status is a page-level action. So the unsaved state is an open location panel holding typed-but-unsaved
# category amounts — the same state DIV-3 is about (closing that panel should warn, and does not).
#
# WHY ONE SCENARIO CARRYING BOTH ARMS, WHEN THE OTHER SCHEDULES GOT TWO.
# An anchor limit, not a shortcut — worth reading before "tidying" this into two scenarios. This suite's
# rule is one dedicated (mill, year) per mutating scenario, enforced by `preflight/sch4-anchors.setup.ts`
# (distinctness, emptiness at rest, and "used in at most one feature file"). The extract has no free Draft
# left: 114 keys are already pinned across the six fixtures and every unclaimed openable pair in Home's
# 2015-2021 range is non-Draft. `check-unsaved` (9050/2015) is therefore SEEDED by
# `real-test-data-patches/sch4/unsaved-check-anchors.sql`, and seeding a second one buys nothing that
# asserting both directions in sequence does not. Tagged for both slices (`@S33 @S34`), which this suite
# already does elsewhere — the sub-page validation outline carries `@S24 @S25 @S26 @S27`.
#
# BOTH DIRECTIONS, in this order:
#   1. false-RED (S34) — supply the missing Cost in the panel, re-check WITHOUT saving: the location must
#      stop being flagged. The direction a reporter meets most often.
#   2. false-GREEN (S33) — empty it again, re-check WITHOUT saving: it must be flagged again. The direction
#      that lets an incomplete schedule look ready.
#
# ZERO WRITES BY THE SCENARIO ITSELF. The panel is never saved and Check Status mutates nothing by contract
# (AD-5). The seeded location is created through the API by the shared Given and removed by the cleanup
# registry.

@UC-SCH4-001 @sch4 @check-status-unsaved
Feature: Schedule 4 — Check Status and unsaved panel edits

  As a Licensee
  I want Check Status to judge the amounts I can see in the open panel
  So that I am neither told a location is incomplete after I have fixed it, nor told the schedule is ready while it is not

  @discovered-divergence @p1 @S33 @S34
  Scenario: Check Status judges the open panel, not the last saved location [DISCOVERED DIVERGENCE — Check Status judges the SAVED locations, ignoring the screen; defects.md DIV-8 / issue #359]
    Given the Schedule 4 anchor "check-unsaved" is an editable Draft with no locations
    # A stored category with a Volume but NO Cost — the state BR-07 exists to catch.
    And the Schedule 4 location "E2E Unsaved Check" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   |      |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Unsaved Check" is not met
    # ARM S34, the false-RED direction: fix it on screen and re-check WITHOUT saving.
    When I open the Schedule 4 location "E2E Unsaved Check" for edit
    And I enter "3600" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Unsaved Check" is met
    # ARM S33, the false-GREEN direction: empty it again and re-check, still WITHOUT saving.
    When I enter "" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Unsaved Check" is not met
    And I should not see the message "All requirements for this schedule have been met"
