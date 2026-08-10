# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S25.feature
# (valid inline edit, Alternative) and UC-SCH1-001-S26.feature (the rejection paths, Exception).
#
# Those two slices were derived upstream 2026-08-07 (BCNRS/ilcr-bmad#39). They were NOT newly discovered:
# the slice matrix had folded the per-row Description/Cost into S09/S10/S11 and dispositioned the
# Other-Costs Save button only as "S09 (implicit auto-save on Add)", so the inline-edit path had no
# scenario anywhere. This file mirrors that split — @S25 for the valid edit, @S26 for the rejects — and
# mirrors their SHAPE too: S10 (description) is a plain scenario and S11 (cost) is an Outline, so the
# rejects below are split the same way rather than crammed into one Outline.
#
# VALIDATION IS UNIFORM ACROSS ADD AND EDIT — no divergence. Every mutation funnels through
# `useEditableCostRows.persist`, which validates EVERY row (`validate(row.description, row.values)`),
# populates the per-row `rowErrors` map, and returns BEFORE sending if any row is invalid. So an invalid
# inline edit behaves exactly like an invalid Add: inline error, no request. `handleAdd` additionally
# validates the add form first, then delegates to the same `persist` — which is why Add also surfaces
# errors (it adds and saves in one action). The backend validates the same rules again
# (`OtherCostSaveRequest`: @NotBlank description, cost ±99,999,999) as defence in depth.
#
# An earlier revision of defects.md logged this as "Divergence #4 — inline edits get no client-side
# validation". That was a misreading (`handleSave` looks bare because it delegates to `persist`) and has
# been retracted; the reject arms below prove the zero-write with the spy rather than inferring it.
#
# ANCHOR SPLIT follows the suite's existing S09-vs-S10/S11 pattern: the VALID edit mutates, so it owns the
# dedicated 12050/2017 target and self-cleans by marker. The REJECTS never write (proven with the spy), so
# they share the read-only validate anchor 17052/2016 and leave it exactly as found — which is also what
# makes them parallel-safe despite the batch PUT reconciling the whole row set.

@sch1 @UC-SCH1-001 @other-costs @inline-edit
Feature: Report Average Cost of Logging (Schedule 1) — edit an Other Cost line item in place
  As a mill reporter who mistyped an itemized cost
  I want to correct an existing Other Cost row in place and save
  So that the itemized cost is right without deleting and re-adding the row

  @S25 @p1
  Scenario: An inline edit is persisted by Save, and the row's volume stays shared
    Given an itemized Other Cost line item exists to edit
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    # BR-06 — the Other-Costs volume is held once on Schedule 1, so a row must not carry its own.
    Then the Other Cost "E2E inline edit" row has no editable volume field
    And the Other Cost "E2E inline edit" row shows the shared volume "50,000"
    When I edit the Other Cost "E2E inline edit" cost to "6789"
    And I save the Other Costs
    Then I should see the message "Data saved successfully"
    And the Other Cost "E2E inline edit" is persisted with cost 6789
    # The other half of the inline edit: the DESCRIPTION. Renamed and then renamed back, because the
    # cleanup registry finds this row by matching its description EXACTLY — leaving it renamed would
    # orphan it in the seed.
    When I edit the Other Cost "E2E inline edit" description to "E2E inline edit v2"
    And I save the Other Costs
    Then I should see the message "Data saved successfully"
    And the Other Cost "E2E inline edit v2" is persisted with cost 6789
    When I edit the Other Cost "E2E inline edit v2" description to "E2E inline edit"
    And I save the Other Costs
    Then the Other Cost "E2E inline edit" is persisted with cost 6789

  # Mirrors S10 (the Add-form description reject) on the inline-edit path.
  @S26 @FLD-006 @p1
  Scenario: An inline edit that blanks the description is rejected before saving
    Given the Other Costs "validate" target is an editable Draft
    And a spy is watching the Other Costs add request
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    And I clear the Other Cost "1" description
    And I save the Other Costs
    Then I should see the error "Description: Value is required."
    And the Other Cost add request should not have been sent
    And the Other Cost "1" is persisted with cost 1000

  # Mirrors S11 (the Add-form cost reject Outline) on the inline-edit path.
  @S26 @p1
  Scenario Outline: An inline edit with an invalid cost is rejected before saving — <case>
    Given the Other Costs "validate" target is an editable Draft
    And a spy is watching the Other Costs add request
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    And I edit the Other Cost "2" cost to "<value>"
    And I save the Other Costs
    Then I should see the error "<message>"
    And the Other Cost add request should not have been sent
    And the Other Cost "2" is persisted with cost 2000

    @FLD-001
    Examples: cost out of range
      | case              | value     | message                                                  |
      | cost out of range | 150000000 | Entered cost must be between -99,999,999 and 99,999,999. |

    @FLD-004
    Examples: non-numeric cost
      | case             | value | message                  |
      | non-numeric cost | abc   | Entered cost is invalid. |
