# DIVERGENCE — both scenarios here are DELIBERATELY RED. They reproduce defects.md DIV-5, tracked upstream
# as bcgov/nr-ilcr#359, and stay failing until Check Status accounts for what is on screen. Do not weaken
# them, skip them, or "fix" them by asserting the current behaviour: the failing state IS the tracking
# signal. Filter them out of a fresh-failures run with `npm run test:gate`.
#
# WHAT THEY REPRODUCE
# Check Status reports on the LAST SAVED data and silently ignores anything typed since. Same app-wide
# defect Schedule 3 carries as DIV-6 — 11 of the 12 schedules are affected, Schedule 6 being the only
# correct implementation. Schedule 3's register entry holds the full analysis; this is the Schedule 11
# instance, and one fix turns them all green.
#
# WHERE "UNSAVED" LIVES ON THIS PAGE — READ THIS BEFORE COPYING THE SHAPE FROM ANOTHER SCHEDULE.
# Schedule 11 has NO page-level Save button (defects.md DIV-1): every row saves itself. So the unsaved state
# is NOT a dirty form, it is a row sitting in the INLINE EDITOR with typed-but-unconfirmed values. That
# state is reachable, and reachable specifically because Check Status is not gated on it: the row ACTIONS
# are disabled while a row is being edited (`schedule11/index.tsx:810`,
# `actionsDisabled={saving || editingId !== null}`) but Check Status is only
# `disabled={!editable || saving}` (`:876`). Verified in source 2026-08-27.
#
# The upstream slices UC-SCH11-001-S21/S22 describe this against `addActualCost` — the ADD panel. That is a
# NEW row, not a stored requirement changed on screen, so it cannot express the rule; the slices are right
# about legacy (which batch-saved a grid behind a page-level Save) and it is the RE-GROUNDING onto the
# rewrite that has to move to the inline editor. Recorded here so nobody "corrects" these scenarios back to
# the Add panel.
#
# BOTH ARMS ARE NEEDED — they fail in OPPOSITE directions: the false-GREEN arm (S21) lets an incomplete
# schedule look ready; the false-RED arm (S22) keeps reporting a cost the reporter has already supplied,
# which is the direction they meet most often.
#
# DEDICATED, SEEDED ANCHORS — AND WHY THEY ARE NOT `check-met` / `check-missing-actual`.
# Both scenarios seed a location, so each needs a mill-year no other scenario writes to. Reusing the S04/S05
# anchors was tried FIRST and is unsafe: their Givens add a location through the API, so a second scenario
# on either collides under `fullyParallel` — observed as red tests on 2026-08-27. The extract had no free
# Draft left either, so `real-test-data-patches/sch11/unsaved-check-anchors.sql` creates these two.
# Preflight asserts both, which doubles as the patch's applied-ness guard.
#
# CLEANUP: each scenario seeds its own location through the API via the shared Given and the registry
# removes it. The inline edit is never confirmed, so it writes nothing.

@sch11 @UC-SCH11-001 @check-status-unsaved
Feature: Report Basic Silviculture Costs (Schedule 11) — Check Status and unsaved edits
  As a Licensee
  I want Check Status to judge the costs I can see on screen
  So that I am not told the schedule is complete while a cost in front of me is missing

  @discovered-divergence @p1 @S21
  Scenario: Check Status reports an Actual Cost cleared in the inline editor but not saved [DISCOVERED DIVERGENCE — Check Status judges the SAVED data, ignoring the screen; defects.md DIV-5 / issue #359]
    Given the Schedule 11 anchor "check-unsaved-violation" has a seeded location "E2E S21 unsaved"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    # As stored the location carries both costs, so Check Status passes.
    And I run Check Status
    Then I should see the message "All requirements for this schedule have been met"
    # Empty the Actual Cost in the inline editor and check again WITHOUT saving the row.
    When I start editing the Schedule 11 location "E2E S21 unsaved"
    And I change the inline "Actual Cost" to ""
    And I run Check Status
    Then the Schedule 11 check status shows verbatim "location  : E2E S21 unsaved - Actual cost: Value Required"
    And I should not see the message "All requirements for this schedule have been met"

  @discovered-divergence @p1 @S22
  Scenario: Check Status stops reporting a missing Actual Cost once it is typed in the inline editor [DISCOVERED DIVERGENCE — Check Status judges the SAVED data, ignoring the screen; defects.md DIV-5 / issue #359]
    Given the Schedule 11 anchor "check-unsaved-fix" has a seeded location "E2E S22 unsaved" with no "actual" cost
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I run Check Status
    Then the Schedule 11 check status shows verbatim "location  : E2E S22 unsaved - Actual cost: Value Required"
    # Supply it in the inline editor and re-check WITHOUT saving the row. It is this location's only
    # outstanding requirement, so the verdict must flip as well as the flag clearing.
    When I start editing the Schedule 11 location "E2E S22 unsaved"
    And I change the inline "Actual Cost" to "12500"
    And I run Check Status
    Then I should not see the error "location  : E2E S22 unsaved - Actual cost: Value Required"
    And I should see the message "All requirements for this schedule have been met"
