# NFR1 / issue #170 AC: "WCAG violations are zero or triaged". Runs axe-core with wcag2a + wcag2aa +
# wcag21a + wcag21aa against Schedule 11 in each of its structurally distinct renders — the 2.0 tags
# alone would silently skip every 2.1-only rule (see pages/common/axe.ts).
#
# Each render swept below is genuinely different DOM, not the same page several times:
#   1. EDITABLE with a real row — Add panel (7 controls incl. a Dropdown and a type-ahead ComboBox),
#      the sortable column headers, the per-row Edit/Delete actions, and the footer Totals row.
#   2. The OPEN INLINE EDITOR — seven display cells swapped for inputs whose labels are visually hidden.
#   3. READ-ONLY — no Add panel, no Actions column, disabled Check Status: a different accessible tree
#      whose remaining table must still be navigable.
#   4. The VALIDATION-ERROR state — the deliberate red below.
#   5. A GUARD state — the PageState error notification that replaces the whole body, which must announce
#      itself rather than just render red text.
# (A sixth, the Check-Status result, is swept in `check-status.feature` `@S04`, which carries `@a11y` too so
#  the documented `--grep @a11y` accessibility-only run includes that render state.)
#
# The seeded row in (1)/(2) is what makes those sweeps meaningful: an empty table would leave the row
# controls, the inline editor and the Actions column entirely unexercised.

@sch11 @UC-SCH11-001 @a11y
Feature: Report Basic Silviculture Costs (Schedule 11) — accessibility
  As a mill reporter using assistive technology
  I want Schedule 11 to meet WCAG 2.1 AA
  So that I can record silviculture costs independently

  # The editable page and its inline editor are swept in ONE scenario on ONE seeded row deliberately:
  # both need the same seeded location, and two parallel scenarios seeding the same marker on the same
  # key would be a shared MUTABLE fixture (the read-back asserts exactly one match). Sweeping the closed
  # and open editor states in sequence keeps the scenario self-contained and parallel-safe.
  @p1
  Scenario: The editable Schedule 11 page and its inline row editor have no accessibility violations
    Given the Schedule 11 anchor "a11y" has a seeded location "E2E a11y row"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    Then the Add New Location panel is rendered
    And the Schedule 11 location "E2E a11y row" is listed
    And the "Schedule 11 (editable, with a location)" view has no WCAG 2.1 AA accessibility violations
    # The editor swaps seven display cells for labelled inputs whose labels are visually hidden — exactly
    # the shape that produces unlabelled-control violations if a hideLabel is ever mis-wired.
    When I start editing the Schedule 11 location "E2E a11y row"
    Then the "Schedule 11 (inline row editor open)" view has no WCAG 2.1 AA accessibility violations

  @p2
  Scenario: The read-only Schedule 11 page has no accessibility violations
    Given the Schedule 11 guard anchor "submitted"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    Then the Add New Location panel is not rendered
    And the "Schedule 11 (read-only)" view has no WCAG 2.1 AA accessibility violations

  # ==================================================================================================
  # DELIBERATE RED — do not "fix" this by weakening the assertion.
  #
  # `deferred-work.md` records a CRITICAL app-wide WCAG 4.1.2 defect: Carbon `TextInput`'s invalid state
  # renders `aria-invalid` + `aria-errormessage` with no announcement technique, so a validation error is
  # never announced to assistive technology. It was found by an axe scan of exactly this state during the
  # earlier (since-removed) 25.4 attempt, and that note ends: "Re-cover it with a deliberately-RED
  # accessibility check when the Schedule 11 E2E is (re-)developed." This is that check.
  #
  # It is expected to FAIL until the app-wide fix lands (a visually-hidden role=alert region, or a Carbon
  # change) — the defect is NOT Schedule 11's to fix, and the failure IS the tracking signal. `npm run
  # test:gate` excludes it from a clean run. See defects.md BUG-1.
  # ==================================================================================================
  @discovered-bug @p1
  Scenario: The validation-error state announces its errors to assistive technology
    Given the Schedule 11 validate-only anchor is an editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field    | value   |
      | Enhanced | Yes     |
      | Biogeo   | primary |
      | NAR(ha)  | 50.5    |
    And I click Add
    Then I should see the error "Location: Value is required."
    # The scenario's TITLE claims announcement, so announcement is asserted directly: axe alone would go
    # green if the app dropped the bad `aria-errormessage` without adding any announcement technique — the
    # error would then reach nobody and this scenario would have started passing for the wrong reason.
    # Soft assertion, so the axe sweep below still runs in the same failing scenario (see the step).
    And the error "Location: Value is required." is announced to assistive technology
    And the "Schedule 11 (validation-error state)" view has no WCAG 2.1 AA accessibility violations

  @p2
  Scenario: The Schedule 11 guard state has no accessibility violations
    Given the Schedule 11 guard anchor "no-schedule"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11 expecting a guard message
    Then I should see the error "Schedule not found."
    And the "Schedule 11 (schedule-not-found guard)" view has no WCAG 2.1 AA accessibility violations
