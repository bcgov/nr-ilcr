# NFR1 / issue #83 AC2 — WCAG 2.1 AA (axe-core) across the structurally distinct Schedule 3 renders.
# There is no legacy slice for accessibility; this is a rewrite-only requirement.
#
# Four renders are swept because each is a different DOM: the populated EDITABLE schedule (inputs, a
# Select, two tables, two count links), the two populated SUB-PAGES (an Add panel plus an
# edit-everything-inline grid, one of them with a disabled meta field), and the READ-ONLY schedule (every
# figure as text, all actions disabled). All four run on anchors nothing writes to, so the sweeps never
# depend on another scenario having run first.
#
# The scan parks the pointer before measuring, so `color-contrast` is judged on the RESTING state — a
# hovered row is measured in its :hover state and would make the result depend on where the previous
# click left the mouse (see pages/common/axe.ts). Hover is a separate, deliberate concern and is already
# tracked app-wide as bcgov/nr-ilcr#314; it is not re-found here.

@sch3 @UC-SCH3-001 @accessibility
Feature: Report Forest Management Administration Costs (Schedule 3) — accessibility
  As a reporter using assistive technology
  I want Schedule 3 and its sub-pages to meet WCAG 2.1 AA
  So that I can record administration costs without a barrier

  @p2 @a11y
  Scenario: The editable Schedule 3 page has no WCAG 2.1 AA violations
    Given the Schedule 3 anchor "a11y"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    Then the "Schedule 3" view has no WCAG 2.1 AA accessibility violations

  @p2 @a11y
  Scenario: The Other Costs sub-page has no WCAG 2.1 AA violations
    Given the Schedule 3 anchor "a11y"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Other Costs sub-page
    Then the "Schedule 3 Other Costs sub-page" view has no WCAG 2.1 AA accessibility violations

  @p2 @a11y
  Scenario: The Included Unacceptable Costs sub-page has no WCAG 2.1 AA violations
    Given the Schedule 3 anchor "a11y"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Included Unacceptable Costs sub-page
    Then the "Schedule 3 Included Unacceptable Costs sub-page" view has no WCAG 2.1 AA accessibility violations

  @p2 @a11y
  Scenario: The read-only Schedule 3 page has no WCAG 2.1 AA violations
    Given the Schedule 3 render-state anchor "readonly-submitted"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    Then the "read-only Schedule 3" view has no WCAG 2.1 AA accessibility violations
