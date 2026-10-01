# UC-SCH5-001 — accessibility (NFR1 / issue #97's second half; closes e2e GAP-5)
#
# WHY THIS FILE ARRIVED LAST, recorded so the same hole is not left in the next domain. Schedule 5 was
# the ONLY domain in this suite with no accessibility coverage — no @a11y scenario, no axe call — while
# reporting "25 of 25 slices authored". Nothing was skipped: NO SLICE IN THE CATALOGUE ASKS FOR IT.
# Accessibility is an NFR, so a catalogue-driven completeness check cannot see it missing. Logged as
# GAP-5 in defects.md and requested in the PR #479 review.
#
# axe-core runs with wcag2a + wcag2aa + wcag21a + wcag21aa (the 2.0 tags alone silently exclude the
# 2.1-only rules). Every surface issue #97 names is swept: the main page with its camp list, the camp
# panel in both of its enterable modes, BOTH expense sub-pages, and the read-only View — plus the
# context-suppressed guard, which is its own render path.
#
# ONE SCAN PER SCENARIO, following sch4. A scenario that scanned several surfaces in sequence would stop
# at the first violation and silently skip the rest, so a single red would hide every surface behind it.
#
# THE POINTER IS PARKED before every scan (pages/common/axe.ts). axe measures the composited background,
# so a row the mouse happens to rest on is measured HOVERED — which would make the result depend on
# which control the scenario clicked last. That is also why there is no hovered-row scenario here: the
# app-wide row-hover contrast defect is already tracked once, on sch4 (defects.md BUG-1 / issue #314),
# and re-finding it per domain adds noise rather than information.
#
# ANCHORS. Four sweeps SAVE a camp to have something to scan and therefore own a dedicated (mill, year):
# a11y-list (23051/2023), a11y-panel (23052/2023), a11y-subpage-camp (24050/2023) and
# a11y-subpage-access (24051/2023). The other two need none — the new-camp-panel sweep opens a blank
# panel and saves nothing, so it rides the validate-only anchor, and the read-only sweep only GETs the
# seeded camp S19 also reads.

@sch5 @UC-SCH5-001 @a11y
Feature: Report Camp and Access Expenses (Schedule 5) — accessibility (WCAG 2.1 AA)

  As a mill reporter using assistive technology
  I want every Schedule 5 surface to meet WCAG 2.1 AA
  So that I can report camp and access expenses without barriers

  # The list is the landing surface: a Carbon DataTable with per-row actions and the page-level buttons.
  @p1
  Scenario: The Schedule 5 page and its camp list have no WCAG 2.1 AA violations
    Given the Schedule 5 anchor "a11y-list" is an editable Draft with no camps
    And a camp named "E2E A11y Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    Then the "Schedule 5 page and camp list" view has no WCAG 2.1 AA accessibility violations

  # The NEW panel is what a reporter meets first: five descriptor fields and the twelve-category grid,
  # all empty. Nothing is saved, so this rides the validate-only anchor.
  @p1
  Scenario: The New Camp Details panel has no WCAG 2.1 AA violations
    Given the Schedule 5 anchor "validation" is an editable Draft with no camps
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    Then the New Camp Details panel is shown with its descriptor fields blank
    And the "Schedule 5 new camp panel" view has no WCAG 2.1 AA accessibility violations

  # The EDIT panel is a structurally different render from the New one: the fields carry values, the
  # derived totals and $/m³ cells are populated, and the two sub-page links are live.
  @p1
  Scenario: The open camp panel has no WCAG 2.1 AA violations
    Given the Schedule 5 anchor "a11y-panel" is an editable Draft with no camps
    And a camp named "E2E A11y Panel" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "E2E A11y Panel" camp
    Then the "Schedule 5 open camp panel" view has no WCAG 2.1 AA accessibility violations

  # Both sub-pages are swept, and they are not the same page twice: the add-form, the grid's per-row
  # inputs and the totals footer are shared, but the cost BAND and the grid description's validation
  # timing differ per page (S21/S22/S23), so each is its own render.
  @p1
  Scenario: The Other Camp Expenses sub-page has no WCAG 2.1 AA violations
    Given the Schedule 5 anchor "a11y-subpage-camp" is an editable Draft with no camps
    And a camp named "E2E A11y Sub Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "E2E A11y Sub Camp" camp
    And I open the "camp" sub-page
    Then the "camp" sub-page is shown
    And the "Schedule 5 Other Camp Expenses sub-page" view has no WCAG 2.1 AA accessibility violations

  @p1
  Scenario: The Other Access Expenses sub-page has no WCAG 2.1 AA violations
    Given the Schedule 5 anchor "a11y-subpage-access" is an editable Draft with no camps
    And a camp named "E2E A11y Sub Acc" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "E2E A11y Sub Acc" camp
    And I open the "access" sub-page
    Then the "access" sub-page is shown
    And the "Schedule 5 Other Access Expenses sub-page" view has no WCAG 2.1 AA accessibility violations

  # The read-only View is its own render and the one most likely to drift: the descriptors stay
  # TextInputs carrying `readonly`, Isolated Camp becomes a disabled Select, and the category grid
  # collapses to bare cells. axe skips disabled controls, so this state legitimately reports on fewer
  # nodes than the Draft panel — which is why it is swept separately rather than assumed covered.
  @p1 @S19
  Scenario: The read-only camp view has no WCAG 2.1 AA violations
    Given the Schedule 5 read-only anchor holds the seeded camp
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I view the "E2E View Camp" camp
    Then the "Schedule 5 read-only camp view" view has no WCAG 2.1 AA accessibility violations

  # A notification-only page is exactly where a missing landmark or heading hides, so the guard state
  # gets its own sweep rather than riding the main page's.
  @p2 @S16
  Scenario: The context-suppressed state has no WCAG 2.1 AA violations
    When I open Schedule 5 with no working context
    Then the Schedule 5 mill and reporting year guard message is shown
    And the "Schedule 5 context-suppressed state" view has no WCAG 2.1 AA accessibility violations
