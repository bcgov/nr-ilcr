# NFR1 accessibility evidence for issue #74 AC4: axe-core rides the Playwright suite against the two
# Schedule 1 surfaces named in the story — the Schedule 1 page and the Other Costs sub-page — asserting
# zero WCAG 2.1 AA violations (or a recorded disposition). Read-only anchors, no writes. The axe helper
# (pages/common/axe.ts) runs wcag2a+wcag2aa+wcag21a+wcag21aa and prints any violation for triage.

@sch1 @UC-SCH1-001 @accessibility
Feature: Report Average Cost of Logging (Schedule 1) — accessibility (WCAG 2.1 AA)
  As a Business Analyst / Product Owner
  I want the Schedule 1 surfaces proven accessible rather than assumed
  So that NFR1 has recorded evidence

  @p1
  Scenario: The Schedule 1 page has no WCAG 2.1 AA violations
    Given the read-only Schedule 1 anchor is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    Then the "Schedule 1 page" view has no WCAG 2.1 AA accessibility violations

  @p1
  Scenario: The Other Costs sub-page has no WCAG 2.1 AA violations
    Given the Other Costs "validate" target is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    And I open the Other Costs sub-page
    Then the "Other Costs sub-page" view has no WCAG 2.1 AA accessibility violations
