# UC-SCH4-001-S28 / S29 / S30 / S31 (EF3 / BR-07) — Check Status
#
# RE-GROUNDING NOTE (2026-09-18, issue #465) — the RULE is legacy parity, and legacy's Schedule 4 check
# required the LOCATION DESCRIPTION and nothing else:
#   - Every per-category Cost check in legacy's `Schedule4CheckStatus` sat behind an `isXxxToCheck` flag
#     (`:26-164`). Those flags default to false (`TransportationReportType.java:53-68`), the only
#     assignments in the codebase are the fifteen `setXxxToCheck(false)` calls on load
#     (`Schedule4DAO.java:243-337`), and nothing ever sets one true — so no Schedule 4 Cost was ever
#     required, and `Schedule4MB.java:406-449` / `checkStatusSchedule4.xhtml:52-212` were dead code.
#   - Story 10.4 §Decision 1 had read those dormant flags as an INTENDED "Cost required when the category
#     is stored" rule and built it (the previous version of this file covered it as S28). #465 reversed
#     that: a Volume-only category, or a sub-page row with no Cost, is NOT a finding.
#   - The one reachable legacy failure — a blank description (`Schedule4CheckStatus.java:19-23`) — cannot
#     be produced through the app: the save rejects a blank name (S13 / `locationEmptyOrNull`), as legacy's
#     did. The backend's `Schedule4CheckStatusServiceTest` covers it; nothing here can.
#   - S29 (a missing DISTANCE) — legacy's distance check is COMMENTED OUT (§Decision 2). SPEC-3.
#   - S30 (missing COMMENTS) — same (§Decision 3); no comments-required key exists in the bundle. SPEC-4.
#   - S31 (mixed results) — the all-or-nothing rule survives in the code (`scheduleMet &= met`), but with no
#     producible failing location its failing arm is unreachable from a browser; covered by the backend
#     unit test `mixed_someLocationsPassOthersFail_scheduleNotMet`. What CAN be shown is that every
#     location gets its own SUC-005 line and the SUC-006 banner appears with them.
#
# So every scenario below is a PASS scenario, and their value is in what they pin: the states the old rule
# flagged — Volume without Cost on a category, on a distance category, on a sub-page row — must now pass,
# because a regression back to the §Decision 1 rule would fail exactly these.
#
# The rendered shape (confirmed against the running app): a passing location gets a success notification
# with SUC-005's "All requirements for <name> have been met."; the schedule banner (SUC-006) is separate.
# Check Status mutates nothing (AD-5), which every scenario here relies on.

@UC-SCH4-001 @sch4
Feature: Schedule 4 — Check Status reports each location's readiness

  As a Licensee
  I want Check Status to tell me which locations still need values
  So that I can complete the schedule before submission

  # The state the OLD rule existed to catch (BR-07 as written from §Decision 1) and the exact finding
  # issue #465 reported: a Volume with no Cost. Legacy never reported it, and now neither does the app.
  @p0 @S28
  Scenario: A location with a Volume but no Cost passes Check Status (#465)
    Given the Schedule 4 anchor "check-missing-cost" is an editable Draft with no locations
    And the Schedule 4 location "E2E Willow Bend" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   |      |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Willow Bend" is met
    And I should see the message "All requirements for this schedule have been met"
    And the Schedule 4 check-status shows no required-value issue

  # A stored Cost of 0 is a real reported figure; it passed under the old rule too (legacy's
  # `CheckStatusUtil` keyed on null only) and it passes now. Kept because it is the value an
  # implementation most easily gets wrong in the other direction.
  @p1 @S28
  Scenario: A stored Cost of zero passes Check Status
    Given the Schedule 4 anchor "check-zero-cost" is an editable Draft with no locations
    And the Schedule 4 location "E2E Zero Cost" is already saved with:
      | category          | distance | volume | cost |
      | Lakeside Dry Dump |          | 1200   | 0    |
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 4
    And I check Schedule 4 status
    Then the Schedule 4 check-status result for "E2E Zero Cost" is met
    And I should see the message "All requirements for this schedule have been met"

  # S31 — every location reports on its own line, and the schedule banner comes with them. The second
  # location is the one the old rule would have failed, so this also pins that a complete and a
  # Volume-only location are treated alike.
  @p1 @S31
  Scenario: Two locations, one of them Volume-only, each report met and the schedule banner appears
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
    And the Schedule 4 check-status result for "E2E Incomplete Loc" is met
    And I should see the message "All requirements for this schedule have been met"

  # A sub-page ROW with no Cost — the other half of the old rule ("and per sub-page row"). Legacy's row
  # checks were behind the same dormant flags (`Schedule4CheckStatus.java:96-140`).
  @p1 @S28 @S11
  Scenario: A sub-page row with no Cost passes Check Status (#465)
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
    Then the Schedule 4 check-status result for "E2E Row Cost Loc" is met
    And I should see the message "All requirements for this schedule have been met"

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

  # Ex-DIV-2 / issue #326 ("name the missing field"). Its scenario seeded two Volume-only categories and
  # asserted that each finding named its category. Under #465 there is no finding to name — the labelling
  # itself landed with #326 (`describeIssue()` in `schedule4/index.tsx`) and is covered by Vitest for the
  # one field the API can still report and for a cost-item code should one ever be emitted again. Nothing
  # here can reach it. The `check-issue-label` anchor has been released.
