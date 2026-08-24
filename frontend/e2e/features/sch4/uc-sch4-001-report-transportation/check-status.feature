# UC-SCH4-001-S28 / S29 / S30 / S31 (EF3 / BR-07) — Check Status
#
# RE-GROUNDING NOTE — the RULE was deliberately pinned to legacy during implementation (Story 10.4
# §Decisions 1-3, delivery-confirmed), and two of the four source slices describe behaviour legacy never
# actually had:
#   - S28 (a missing category COST) is the real rule and is covered here. "Missing" means NULL only —
#     a stored ZERO counts as present (`CheckStatusUtil.checkRequiredCost`), which is asserted below
#     because it is the kind of rule an implementation silently gets wrong in the other direction.
#   - S29 (a missing DISTANCE) — legacy's distance check is COMMENTED OUT (§Decision 2), and the slice
#     catalogue itself flagged the premise as inferred from EF3's example text rather than a cited source
#     line. So the app does NOT fail a location for a missing Distance. Covered here as the app's actual
#     behaviour and logged as SPEC-3 (a SPEC gap: the rule never existed anywhere), NOT as a failing test:
#     asserting the legacy-inferred rule would demand a check nobody has ever implemented.
#   - S30 (missing COMMENTS) — same story (§Decision 3): the comment check is commented out in legacy and
#     there is no comments-required key in the bundle at all, so Comments never block MET. Covered as
#     actual behaviour, logged as SPEC-4 (a SPEC gap, same shape as SPEC-3).
#   - S31 (mixed results) is the real all-or-nothing rule: per-location messages appear together, and the
#     whole-schedule banner appears ONLY when every location passes.
#
# The rendered shape (confirmed against the running app): a passing location gets a success notification
# with SUC-005's "All requirements for <name> have been met."; a failing one gets one WARNING notification
# per missing field, titled "<name> — required" with the subtitle "Value Required"; the schedule banner
# (SUC-006) is separate. Check Status mutates nothing (AD-5), which every scenario here relies on.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — Check Status reports each location's readiness

  As a Licensee
  I want Check Status to tell me which locations still need values
  So that I can complete the schedule before submission

  @p0 @S28
  Scenario: A location missing a category Cost is flagged, then passes once the Cost is supplied
    Given the Schedule 4 anchor "check-missing-cost" is an editable Draft with no locations
    # A stored category with a Volume but NO Cost — the exact state BR-07 exists to catch.
    And the Schedule 4 location "E2E Willow Bend" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   |      |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Willow Bend" is not met
    And I should not see the message "All requirements for this schedule have been met"
    # Recovery, per EF3 step 2-3: fill the Cost, save, re-check.
    When I open the Schedule 4 location "E2E Willow Bend" for edit
    And I enter "3600" in the Schedule 4 "Lakeside Dry Dump" "cost" cell
    And I save the Schedule 4 location
    Then I should see the message "Data saved successfully"
    When I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Willow Bend" is met
    And I should see the message "All requirements for this schedule have been met"

  # "Missing" is NULL, not falsy: a stored Cost of 0 is a real reported figure and must PASS. Legacy's
  # `CheckStatusUtil` keyed on null only, and this is the assertion that keeps the rewrite honest about it.
  @p1 @S28
  Scenario: A stored Cost of zero counts as present
    Given the Schedule 4 anchor "check-zero-cost" is an editable Draft with no locations
    And the Schedule 4 location "E2E Zero Cost" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 0    |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Zero Cost" is met
    And I should see the message "All requirements for this schedule have been met"

  # S31 — the all-or-nothing rule, with both outcomes in ONE response.
  @p1 @S31
  Scenario: One complete and one incomplete location report independently
    Given the Schedule 4 anchor "check-mixed" is an editable Draft with no locations
    And the Schedule 4 location "E2E Complete Loc" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And the Schedule 4 location "E2E Incomplete Loc" is already saved with:
      | category   | distance | volume | cost |
      | Water Dump |          | 500    |      |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Complete Loc" is met
    And the Schedule 4 check-status result for "E2E Incomplete Loc" is not met
    # SUC-006 is withheld while ANY location fails — that is the gate.
    And I should not see the message "All requirements for this schedule have been met"

  # A sub-page ROW with no Cost fails its location too (the rule spans categories AND rows) — the half of
  # BR-07 that the category-only scenarios above cannot reach.
  @p1 @S28 @S11
  Scenario: A sub-page row missing its Cost fails the location
    Given the Schedule 4 anchor "check-row-cost" is an editable Draft with no locations
    And the Schedule 4 location "E2E Row Cost Loc" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And that Schedule 4 location already has these "Towing Total" rows:
      | description      | distance | volume | cost |
      | E2E Costless row | 10       | 200    |      |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    # The categories are all complete, so ONLY the row can be the reason.
    Then the Schedule 4 check-status result for "E2E Row Cost Loc" is not met
    And I should not see the message "All requirements for this schedule have been met"

  # S29 re-grounded — a distance-based category with amounts but NO Distance cannot even be saved (BR-04
  # blocks it), and a fully-empty one is not stored at all, so there is no state in which a "missing
  # Distance" could fail Check Status. This scenario pins the consequence: a location whose distance
  # categories are simply absent passes. See SPEC-3.
  @p1 @S29
  Scenario: A location with no distance-based categories at all still passes Check Status
    Given the Schedule 4 anchor "check-distance" is an editable Draft with no locations
    And the Schedule 4 location "E2E No Distance" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E No Distance" is met
    And I should see the message "All requirements for this schedule have been met"

  # S30 re-grounded — Comments are a SOFT gate: absent comments never block MET, and no
  # comments-required message exists in the bundle to render. See SPEC-4.
  @p1 @S30
  Scenario: A location with blank Comments still passes Check Status
    Given the Schedule 4 anchor "check-comments" is an editable Draft with no locations
    And the Schedule 4 location "E2E No Comments" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 3600 |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I open the Schedule 4 location "E2E No Comments" for edit
    # Confirm the precondition through the UI: the comments field really is empty.
    Then the Schedule 4 comments show ""
    When I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E No Comments" is met
    And I should see the message "All requirements for this schedule have been met"

  # A mill/year with NO locations is vacuously MET (legacy's AND-over-locations). Worth pinning: the
  # opposite choice (ISSUES on an empty schedule) is an equally plausible implementation.
  @p2 @S28
  Scenario: A schedule with no locations passes Check Status
    Given the Schedule 4 anchor "validation" is an editable Draft with no locations
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then I should see the message "All requirements for this schedule have been met"

  # ---------------------------------------------------------------------------------------------------
  # DIV-2 — DELIBERATELY RED. See this UC's defects.md (DIV-2) and issue #326.
  #
  # Legacy named the field a value was required for: `addMessageCheckStatus()` built
  # "Location : <name> - <Field Name> (Cost $) " + "Value Required", and the rewrite's backend still
  # returns the cost-item `code` on every issue for exactly that purpose (Story 10.4 §Decision 4). The
  # page renders only the location name and "Value Required", so a location missing two category Costs
  # shows two IDENTICAL notifications and the reporter cannot tell which lines to fix.
  #
  # Asserted as "the category is named somewhere in the Check Status output" rather than against the legacy
  # JSF string: the notification shape was deliberately re-grounded (title = location, subtitle = message),
  # so pinning the old literal would demand a format nobody intends to restore. What is genuinely missing
  # is the field identity.
  #
  # SCHEDULE 4 IS THE ONLY PAGE THAT DROPS IT (swept 2026-08-19). Legacy named the field on every
  # schedule (FacesUtil.addCheckStatusErrorMessage composed "<label>: <message>"), and every other
  # schedule here still does — Schedules 1/2/3/5/11 compose the label into the message text server-side,
  # and Schedule 8 returns the field separately (Schedule 4's exact shape) then renders it in the
  # notification title (schedule8/CheckStatusResult.tsx:26). So the fix has an in-repo precedent: map
  # issue.code through ALL_CATEGORIES in components/schedule4/validation.ts.
  # ---------------------------------------------------------------------------------------------------
  @p1 @S28 @discovered-divergence
  Scenario: Check Status names which category needs a value [DISCOVERED DIVERGENCE — the field label is dropped; defects.md DIV-2 / issue #326]
    Given the Schedule 4 anchor "check-issue-label" is an editable Draft with no locations
    And the Schedule 4 location "E2E Two Gaps" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   |      |
      | Water Dump        |          | 500    |      |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    # Both gaps ARE reported — two issues on the one location…
    Then the Schedule 4 check-status reports 2 required-value issues for "E2E Two Gaps"
    # …but neither says WHICH category, so these two assertions fail until the label is restored.
    And the Schedule 4 check-status names the "Lakeside Dry Dump" category as the missing one
    And the Schedule 4 check-status names the "Water Dump" category as the missing one
