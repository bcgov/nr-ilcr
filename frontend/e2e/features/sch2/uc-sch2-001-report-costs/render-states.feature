# UC-SCH2-001-S06 / S09 / S10 / S11 — render states and context guards
#
# RE-GROUNDING NOTES:
#   - S06/S11 Delete: legacy omitted the Delete button from the DOM entirely (its `rendered` condition
#     required both `!disableReportEdits()` and `isScheduleOpen()`), and the slice states Delete is
#     "either absent or present, never disabled". The React page instead renders it DISABLED. Same
#     user-visible outcome — delete cannot be initiated — by a different mechanism. Recorded as a
#     re-grounding in coverage.md, and asserted as "unavailable" so the SLICE's intent is what is pinned.
#   - S09/S10: legacy suppressed the input form and showed a pre-form `.ui-messages-error` banner. The
#     rewrite renders a Carbon InlineNotification with an explicit severity word in the title, and the
#     table is genuinely absent — so "the input form is suppressed" is still directly assertable.
#   - the `not-found` scenario is an ADDITION, not a transcription: the legacy UC excluded ERR-004
#     ("Schedule not found.") as unreachable, but the rewrite's context guard reaches it whenever the
#     mill/year carries no report-status row. See coverage.md (re-grounding gains).

@UC-SCH2-001 @sch2
Feature: Schedule 2 — render states and context guards

  As a Licensee
  I want Schedule 2 to tell me plainly when it cannot be edited
  So that I know whether to act here or fix my working context first

  # ---- structural render (anchor-independent) --------------------------------------------------------

  @p1 @S06
  Scenario: A never-saved schedule renders both action bars and the legacy row order
    Given the Schedule 2 anchor "delete-unavailable" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    Then Save and Check Status are each rendered twice
    And the Schedule 2 rows are in legacy order

  # ==================================================================================================
  # DELIBERATE RED — do not "fix" this by weakening the assertion.
  #
  # BR-08 / S06: Delete must not be offered for a schedule that has never been saved. It IS offered.
  #
  # Root cause (components/schedule2/index.tsx:245):
  #     const deletable = editable && data.revisionCount !== null
  # Jackson is configured non_null, so an unsaved schedule's GET OMITS `revisionCount` entirely — it
  # arrives as `undefined`, and `undefined !== null` is true, so the gate always opens. The TypeScript
  # interface declares `revisionCount: number | null` (not optional), which is why the compiler never
  # caught the absent case.
  #
  # This is an APP defect, not a test or spec problem, so the test stays red — the failure IS the
  # tracking signal, and it will go green on its own when the gate is fixed. `npm run test:gate`
  # excludes it so CI stays clean. See defects.md BUG-1, which also records the second manifestation
  # (Delete stays enabled after a successful delete, same root cause).
  #
  # Nothing here writes: the assertion is on the disabled state of a control, on an anchor no scenario
  # saves to.
  # ==================================================================================================
  @discovered-bug @p1 @S06
  Scenario: Delete is not offered for a never-saved schedule
    Given the Schedule 2 anchor "delete-unavailable" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    Then the Schedule 2 Delete action is unavailable

  # ---- S11: not editable outside Draft ----------------------------------------------------------------

  # Both non-Draft track codes are exercised, so the read-only render is proven from both sides of the
  # mirror rather than from Submitted alone.
  @p1 @S11
  Scenario Outline: A schedule outside Draft renders read-only with its stored values
    Given the Schedule 2 read-only anchor "<track>" is selected
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    # STA-001: every editable control is gone, the values display as text, and no action can be taken.
    Then the Schedule 2 fields are read-only
    And the Schedule 2 actions are disabled
    And the Schedule 2 Delete action is unavailable
    And the Schedule 2 document shows:
      | row                          | volume   | cost   | perUnit   |
      | Purchased/Private Log Costs: | <vol25>  | <cos25> | <per25>  |
      | (less) Log Sales:            | <vol26>  | <cos26> | <per26>  |
    And the Schedule 2 comments show "—"

    Examples:
      | track     | vol25 | cos25 | per25 | vol26 | cos26 | per26 |
      | submitted | 10    | 500   | 50.00 | 9     | 450   | 50.00 |
      | verified  | 3,000 | 300   | 0.10  | 300   | 300   | 1.00  |

  # ---- S09: no working context ------------------------------------------------------------------------

  @p1 @S09
  Scenario: With no mill and reporting year selected the form is suppressed
    When I open Schedule 2 with no working context
    Then the mill and reporting year guard message is shown
    And the Schedule 2 input form is not displayed

  # ---- S10 + the not-found addition: context guards ---------------------------------------------------

  @p1 @S10
  Scenario: A mill that is closed for the reporting year blocks the schedule
    Given the Schedule 2 guard anchor "closed-mill"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2 expecting a guard message
    # ERR-002, rendered verbatim from the API's ProblemDetail.detail (never hardcoded in the page).
    Then I should see the error "This Mill is not active for the current Reporting Year. Please select another mill from the Home Page."
    And the Schedule 2 input form is not displayed

  @p2 @S10
  Scenario: A mill and year with no report status blocks the schedule
    Given the Schedule 2 guard anchor "not-found"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2 expecting a guard message
    Then I should see the error "Schedule not found."
    And the Schedule 2 input form is not displayed
