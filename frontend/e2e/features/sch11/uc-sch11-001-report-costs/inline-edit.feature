# Re-grounded from UC-SCH11-001-S03.feature.
#
# THE PAGE MODEL (Story 26.2, rulings D1(b)/D4(a)/D5(a) — legacy's again). Every row is a live input while
# the page is editable; there is no per-row Edit mode. A change stays on the page until the page-level Save
# (above AND below the table, as legacy's `btnSaveTop`/`btnSave`), which sends every edited row and every
# flagged delete in ONE atomic PUT. That is exactly the legacy S03 flow — edit the row, then click Save —
# so the source Gherkin's steps map one to one again.
#
# NOT A DIVERGENCE — the source Gherkin was wrong, and has since been corrected (defects.md SPEC-3 and
# VER-6, both CLOSED).
# The legacy S03 scenario asserts:
#   "Then the row's Total Act Plus Plan Cost and Total/NAR(ha) columns recompute immediately via AJAX"
#   "And the footer Totals row recomputes to reflect the updated Actual Cost"   <- BEFORE Save
# LEGACY NEVER DID THAT. Verified against `schedule11.xhtml` itself: the two derived cells are
# `p:inputText ... disabled="true"` (lines 334-352), so their `p:ajax event="change"` can never fire; and
# the editable fields' handlers update only `@this`, their `*OV` indicator and the message panel
# (e.g. line 303 `update="@this actualCostOV :schedule11MessageForm:messages"`) — nothing references a
# total cell or a footer id. Derived values refreshed on the Save re-render (`update=":mainPnl @form"`).
# So BOTH apps refresh derived figures on save; the Gherkin over-read its own source. The app reaches the
# same user-visible behaviour by a different mechanism (server-derived, AD-5 "no client recompute").
# Asserted below as both apps actually behave — post-save.
#
# READING A LIVE ROW. A live row's Actual Cost is an INPUT, so it reads as the raw figure the user types
# over ("5500"); the two derived cells and the footer stay formatted server figures ("10,000").
#
# The reject arm proves the negative with the mutation spy: an invalid row is blocked in the browser (the
# whole-page validation runs before the PUT), so NO further request is fired and the stored row is
# untouched. Asserting only the unchanged read-back would also pass if a request had been sent and
# rejected server-side — a materially different behaviour.

@sch11 @UC-SCH11-001 @inline-edit
Feature: Report Basic Silviculture Costs (Schedule 11) — correct a location in place
  As a mill reporter who mistyped a cost
  I want to correct an existing location row in place
  So that the schedule reflects accurate silviculture data

  @S03 @p1
  Scenario: A row edit persists on Save, and an invalid row blocks the Save without changing anything
    Given the Schedule 11 anchor "inline-edit" has a seeded location "E2E S03 edit"
    And a spy is watching the Schedule 11 location requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    # Seeded at actual 5000 / planned 4500 over 100 ha -> total 9,500.
    Then the Schedule 11 row "E2E S03 edit" shows "9,500" in "Total Act Plus Plan Cost ($)"
    And both Save buttons are disabled
    When I change the Schedule 11 location "E2E S03 edit" field "Actual Cost" to "5500"
    # Typing writes nothing: the change waits for Save, and Check Status waits with it (D7(a)).
    Then no Schedule 11 location mutation should have been sent
    And both Check Status buttons are disabled until the change is saved
    When I save Schedule 11
    Then I should see the message "Data saved successfully"
    # The corrected value and the SERVER-recomputed total, both after the Save — which is also when
    # legacy refreshed them (its Save re-rendered the whole form).
    And the Schedule 11 row "E2E S03 edit" shows "5500" in "Actual Cost ($)"
    And the Schedule 11 row "E2E S03 edit" shows "10,000" in "Total Act Plus Plan Cost ($)"
    And the Schedule 11 footer total "Total Act Plus Plan Cost ($)" shows "10,000"
    And the Schedule 11 location "E2E S03 edit" is persisted as:
      | field        | value |
      | Actual Cost  | 5500  |
      | Planned Cost | 4500  |
      | Total Cost   | 10000 |
    And both Check Status buttons are enabled
    # Reject arm — an out-of-range cost is blocked client-side: the inline error renders, the Save is
    # refused, and no request is sent, so the row keeps the value the successful Save just stored. Saved
    # from the BOTTOM bar, so both of legacy's Save buttons are exercised.
    When I note the Schedule 11 mutation count
    And I change the Schedule 11 location "E2E S03 edit" field "Actual Cost" to "100000000"
    And I save Schedule 11 from the bottom of the page
    Then I should see the error "Please correct the highlighted fields before saving."
    And I should see the error "Entered cost must be between -99,999,999 and 99,999,999."
    And no further Schedule 11 location mutation should have been sent
    And the Schedule 11 location "E2E S03 edit" is persisted as:
      | field        | value |
      | Actual Cost  | 5500  |
      | Total Cost   | 10000 |
