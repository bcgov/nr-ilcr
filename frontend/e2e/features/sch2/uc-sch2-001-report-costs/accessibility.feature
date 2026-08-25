# NFR1 / Story 3.4 AC2: "axe runs on the Schedule 2 page (edit + view modes) with zero violations or
# triaged exceptions". Runs axe-core with wcag2a + wcag2aa + wcag21a + wcag21aa — the 2.0 tags alone
# would silently skip every 2.1-only rule (see pages/common/axe.ts).
#
# Each render swept below is genuinely different DOM, not the same page several times:
#   1. EDITABLE — three numeric inputs with visually-hidden labels inside table cells, the comments
#      textarea with its character counter, and both action bars: FIVE buttons, because Delete sits on
#      the bottom bar only (legacy's asymmetry, restored by nr-ilcr #292). On a never-saved schedule
#      Delete is disabled and carries an `aria-describedby` reason (a visually-hidden span), so the
#      sweep also covers that association.
#   2. READ-ONLY — no inputs at all: the value cells become plain text and the comments textarea is
#      replaced by a heading + paragraph. A different accessible tree whose table must still be navigable.
#   3. The CHECK-STATUS result — a warning notification that must announce itself, not just render amber.
#   4. A GUARD state — the PageState notification that replaces the whole body.
#
# DELIBERATELY NOT DUPLICATED HERE — the validation-error state.
# Sweeping it would re-find one already-triaged, app-wide defect: Carbon `TextInput`'s invalid state wires
# `aria-errormessage` to an element it never announces (axe rule `aria-valid-attr-value`, impact critical),
# so a field error never reaches assistive technology. It is a `@carbon/react` issue present in EVERY
# schedule page's validation-error state, already tracked in `deferred-work.md` and carried as the standing
# red in `features/sch11/uc-sch11-001-report-costs/accessibility.feature` (BUG-1). One red per app-wide
# defect is the tracking signal; a second copy per schedule would degrade it into noise. Recorded as a
# Coverage gap in this UC's defects.md with that cross-reference, so the omission is explicit and
# reversible — when the app-wide fix lands, both go green together.

@sch2 @UC-SCH2-001 @a11y
Feature: Schedule 2 — accessibility

  As a mill reporter using assistive technology
  I want Schedule 2 to meet WCAG 2.1 AA
  So that I can record purchased-log costs and log sales independently

  @p1
  Scenario: The editable Schedule 2 page has no accessibility violations
    Given the Schedule 2 anchor "a11y" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    # Values are typed but never saved: the sweep needs populated inputs (an empty form leaves the
    # grouped-number formatting and the counter unexercised), and this anchor is never written to.
    And I enter the following Schedule 2 values:
      | field                   | value              |
      | Purchased Log Cost cost | 25000              |
      | Less Log Sales volume   | 8                  |
      | Less Log Sales cost     | 750                |
      | comments                | accessibility pass |
    Then the "Schedule 2 (editable, populated)" view has no WCAG 2.1 AA accessibility violations

  @p2
  Scenario: The read-only Schedule 2 page has no accessibility violations
    Given the Schedule 2 read-only anchor "submitted" is selected
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    Then the Schedule 2 fields are read-only
    And the "Schedule 2 (read-only)" view has no WCAG 2.1 AA accessibility violations

  @p2
  Scenario: The Check Status result announces itself accessibly
    Given the Schedule 2 anchor "check-missing" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I check Schedule 2 status
    Then I should see the warning "Purchased/Private Log Costs - Cost: Value Required"
    And the "Schedule 2 (Check Status result)" view has no WCAG 2.1 AA accessibility violations

  @p2
  Scenario: The Schedule 2 guard state has no accessibility violations
    Given the Schedule 2 guard anchor "closed-mill"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2 expecting a guard message
    Then I should see the error "This Mill is not active for the current Reporting Year. Please select another mill from the Home Page."
    And the "Schedule 2 (closed-mill guard)" view has no WCAG 2.1 AA accessibility violations
