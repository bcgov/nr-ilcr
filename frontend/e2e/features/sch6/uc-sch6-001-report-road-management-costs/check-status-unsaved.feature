# Re-grounded from UC-SCH6-001-S22.feature and UC-SCH6-001-S23.feature in
# _bmad-output/implementation-artifacts/tests/UC-SCH6-001/gherkin/ (legacy JSF/PrimeFaces). BR-10:
# Check Status judges the values ON SCREEN, not the ones in the database.
#
# THESE ARE THE TWO SLICES SPEC-1 RECOVERED. The slice catalogue counted 21 and named neither, while
# the Gherkin folder had carried 23 files all along — and the two it had lost were exactly this pair,
# the most defect-prone family in the suite on every other schedule. Authoring to the stated total
# would have dropped precisely the slices most worth having.
#
# ON SCHEDULE 6 THEY ARE EXPECTED **GREEN**, AND THAT IS THE POINT — a REGRESSION GUARD, not a defect
# tracker. Every sibling schedule still reads the database here; Schedule 5's equivalent pair (its
# S24/S25) is a live divergence, issue #476 / app-wide #359. Schedule 6 already ships the fix:
#   * `POST /api/v1/schedule6/check-status` takes the ON-SCREEN values (`Schedule6CheckRequest`, whose
#     own javadoc calls itself "the fix");
#   * `Schedule6Service.checkStatus` evaluates `payloadCandidates(request)` and nothing else;
#   * `handleCheckStatus` posts the row forms explicitly, "never gated on dirtiness";
#   * an earlier DB-reading implementation of the endpoint was deliberately RETIRED in Task 8, for
#     exactly the reason Schedule 5 is still broken.
# So a red here is a real regression. **Do not "align" Schedule 6 with the other schedules.**
#
# THE TWO ARMS FAIL IN OPPOSITE DIRECTIONS, which is why both exist and why neither is redundant:
#
#   S22 — THE FALSE-GREEN ARM. Stored record COMPLETE, broken on screen. An implementation that read
#         the database would answer MET: the reporter is told the schedule is ready while a wrong value
#         is in front of them. That is the dangerous direction — it lets an incomplete schedule be
#         submitted.
#   S23 — THE FALSE-RED ARM. Stored record INCOMPLETE, corrected on screen. Such an implementation
#         would keep reporting the problem: the reporter is told to fix what they have just fixed, and
#         must save to discover otherwise.
#
# An implementation with either fault PASSES THE OTHER ARM. So one arm alone proves nothing, and the
# two cannot be collapsed onto a single stored state — which is why they hold separate anchors rather
# than merely separate scenarios. S22 = 25054/2024, S23 = 9050/2025.
#
# S23 TAKES THE FIRST CELL IN 2025, by arithmetic rather than choice: the extract holds 17 ACT mills
# and sch6 pins 15 of them at 2024 (plus 23050, whose ABSENCE is S08's fixture), so 16050 and 25054
# were the last two and S21/S22 took them. "Year >= 2024 belongs to sch6" is the structural invariant
# the whole fan-out rests on — every other domain pins <= 2023 — so 2025 collides with nothing by
# construction. `draft-anchors.sql` now derives its reporting-period row from the anchor table, so the
# 2025 period is created automatically and Home's year dropdown offers it.
#
# EACH ARM'S PRECONDITION IS PROVED AT THE API BEFORE THE BROWSER IS DRIVEN, and this is not ceremony.
# S22 needs a stored schedule that genuinely PASSES; S23 needs one whose ONLY outstanding requirement
# is the cost. Get either wrong and the scenario's message assertion still passes — for the wrong
# reason — while proving nothing about unsaved edits. So the Givens assert the stored verdict itself
# (MET for S22; ISSUES with exactly the one missing-cost line for S23).
#
# "AND NO SCHEDULE RECORDS ARE CHANGED" IS THE THIRD CLAIM IN BOTH, and it is the one that would
# otherwise go unproven. Check Status is a READ. An implementation that wrote the on-screen payload
# through on its way to a verdict would satisfy every message assertion here while silently saving
# edits the reporter never committed — arguably worse than either failure above. So each arm snapshots
# the stored records before the click and compares them field by field afterwards, rather than counting
# rows: a changed cost on an unchanged number of rows is exactly what a count would miss.
#
# ONE SOURCE SCENARIO IS NOT COVERED, AND IT IS UNREACHABLE RATHER THAN SKIPPED — S22's second arm,
# "an in-range amount that still fails its Check Status requirement". Schedule 6 has no such value.
# Every Check Status rule on this page is a PRESENCE check (`isBlank(areaType)`, `isBlank(tflNumber)` /
# `isBlank(supplyBlock)`, `cost == null` — Schedule6Service:873-890): there is no range bound, no
# cross-field relationship, and volume is not checked at all. So any well-formed in-range cost
# satisfies the requirement — including 0, under the null-only rule (D2 precedent) — and the only
# failing cost is an ABSENT one, which is the first arm below. The legacy scenario's own narrowing note
# assumed a JSF converter/validator stage that this page does not have. Recorded as a coverage gap
# (not-applicable) in defects.md, with this reasoning, rather than dropped or @skipped.

@sch6 @UC-SCH6-001 @check-status-unsaved
Feature: Report Road Management Costs (Schedule 6) — Check Status judges the screen, not the database
  As the Licensee running Check Status
  I want Check Status to judge the amounts I can see on screen
  So that I am neither told the schedule is complete while a value is wrong, nor told to fix something
  I have already fixed

  @p1 @S22
  Scenario: A required amount cleared on screen but not saved is still reported
    Given the Schedule 6 anchor "unsaved-break" is an editable Draft with no road records
    And a complete road maintenance record commented "E2E S22 complete record" already exists on that anchor
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    # Cleared, and deliberately NOT saved. The stored row still carries its cost; the screen does not.
    And I clear the record's cost on screen
    And I run Schedule 6 Check Status
    Then Check Status reports "Road : 1 - TSA or TFL (Cost $) : Value Required"
    And Check Status does not report the schedule as met
    # Check Status is a READ — it must not have written the screen's values through.
    And no Schedule 6 record was changed

  @p1 @S23
  Scenario: An amount supplied on screen but not saved clears its error and passes the schedule
    Given the Schedule 6 anchor "unsaved-fix" is an editable Draft with no road records
    And a road maintenance record with no cost commented "E2E S23 cost-less record" already exists on that anchor, and the schedule is otherwise complete
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 6
    # BEFORE: the stored gap is reported, which is what makes the "after" meaningful rather than an
    # absence that might never have been present.
    And I run Schedule 6 Check Status
    Then Check Status reports "Road : 1 - TSA or TFL (Cost $) : Value Required"
    # Supplied on screen, never saved.
    When I supply the record's cost on screen
    Then the row shows the cost supplied on screen
    When I run Schedule 6 Check Status
    # AFTER: the error is gone AND the schedule now passes — the missing cost was its only gap, which
    # the Given proved at the API.
    Then I should not see the error "Road : 1 - TSA or TFL (Cost $) : Value Required"
    And I should see the message "All requirements for this schedule have been met"
    And no Schedule 6 record was changed
