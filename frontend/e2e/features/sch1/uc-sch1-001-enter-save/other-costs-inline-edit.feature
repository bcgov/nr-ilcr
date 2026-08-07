# Per-row INLINE EDIT of an existing Other Cost row, plus the batch "Save" that persists the row set.
#
# SPEC GAP (defects.md Spec gap #1): the UC sidecars describe this capability — UC-SCH1-001-slices.md
# names "per-row inline edit of an existing item's description" under the Description rule, and the
# FLD cost rule is triggered by "adding a new Other Cost line item OR editing an existing row's Cost
# inline" — but the derived UC-SCH1-001-S09..S12 `.feature` files only ever cover the ADD form and the
# per-row Remove. The scenario below closes that gap; the `.feature` set stays as authored (it is the
# lossy projection, not the source).
#
# MECHANISM DIVERGENCE (defects.md Divergence #4): the Add form validates client-side and blocks before
# any request (S10/S11 prove a zero-mutation). Inline edits do NOT — `useEditableCostRows.handleSave`
# persists without validating, and its per-row `rowErrors` map is never populated, so the row-level
# `invalid`/`invalidText` props are dead. The guarantee still holds, but SERVER-side: OtherCostSaveRequest
# validates each row (@NotBlank description, cost ±99,999,999) and rejects the whole batch with 400,
# leaving every row untouched. This scenario asserts that preserved guarantee — the error surfaces and
# the record is unchanged — which is why it is GREEN, not a @discovered-* red.
#
# Both arms run in ONE scenario on ONE dedicated key by design: the batch PUT reconciles the WHOLE row
# set (rows absent from the request are DELETED), so two scenarios editing the same schedule in parallel
# would clobber each other's rows, and 12050/2017 is the last free editable Draft in the seed.

@sch1 @UC-SCH1-001 @other-costs @inline-edit
Feature: Report Average Cost of Logging (Schedule 1) — edit an Other Cost line item in place
  As a mill reporter who mistyped an itemized cost
  I want to correct an existing Other Cost row in place and save
  So that the itemized cost is right without deleting and re-adding the row

  @SG-1 @p1
  Scenario: An inline edit persists, and an invalid inline edit is rejected without changing the row
    Given an itemized Other Cost line item exists to edit
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    And I edit the Other Cost "E2E inline edit" cost to "6789"
    And I save the Other Costs
    Then I should see the message "Data saved successfully"
    And the Other Cost "E2E inline edit" is persisted with cost 6789
    # Server-side reject arm: the batch Save is sent, the API refuses it, and nothing changes.
    When I clear the Other Cost "E2E inline edit" description
    And I save the Other Costs
    Then I should see the error "Description: Value is required."
    And the Other Cost "E2E inline edit" is persisted with cost 6789
