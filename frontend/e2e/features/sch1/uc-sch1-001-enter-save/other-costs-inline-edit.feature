# Per-row INLINE EDIT of an existing Other Cost row, plus the batch "Save" that persists the row set.
#
# SPEC GAP (defects.md Spec gap #1): the UC sidecars describe this capability — UC-SCH1-001-slices.md
# names "per-row inline edit of an existing item's description" under the Description rule, and the
# FLD cost rule is triggered by "adding a new Other Cost line item OR editing an existing row's Cost
# inline" — but the derived UC-SCH1-001-S09..S12 `.feature` files only ever cover the ADD form and the
# per-row Remove. The scenario below closes that gap; the `.feature` set stays as authored (it is the
# lossy projection, not the source).
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
# been retracted; the reject arm below now proves the zero-write with the spy rather than inferring it.
#
# Both arms run in ONE scenario on ONE dedicated key by design: the batch PUT reconciles the WHOLE row
# set (rows absent from the request are DELETED), so two scenarios editing the same schedule in parallel
# would clobber each other's rows, and 12050/2017 is the last free editable Draft in the seed.

@sch1 @UC-SCH1-001 @other-costs @inline-edit
Feature: Report Average Cost of Logging (Schedule 1) — edit an Other Cost line item in place
  As a mill reporter who mistyped an itemized cost
  I want to correct an existing Other Cost row in place and save
  So that the itemized cost is right without deleting and re-adding the row

  @S25 @p1
  Scenario: An inline edit persists, and an invalid inline edit is rejected without changing the row
    Given an itemized Other Cost line item exists to edit
    And a spy is watching the Other Costs add request
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    And I edit the Other Cost "E2E inline edit" cost to "6789"
    And I save the Other Costs
    Then I should see the message "Data saved successfully"
    And the Other Cost "E2E inline edit" is persisted with cost 6789
    # Reject arm — proves the negative the way S10/S11 do. `useEditableCostRows.persist` validates EVERY
    # row before sending and returns early, so an invalid inline edit is blocked in the browser: the
    # error renders and NO mutating request is fired. The spy is what makes that a proof rather than an
    # inference; asserting only the unchanged read-back would pass even if a request had been sent and
    # rejected server-side.
    When I note the Other Costs mutation count
    And I clear the Other Cost "E2E inline edit" description
    And I save the Other Costs
    Then I should see the error "Description: Value is required."
    And no further Other Costs mutation should have been sent
    And the Other Cost "E2E inline edit" is persisted with cost 6789
