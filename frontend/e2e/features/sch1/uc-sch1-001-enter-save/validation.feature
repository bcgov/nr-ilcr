# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S03..S07.feature
# (legacy JSF/PrimeFaces field validators/converters). MECHANISM DIVERGENCE (see defects.md): the legacy
# app rejected an invalid amount AT ENTRY (the field would not accept it). The React/Carbon app instead
# ACCEPTS the keystrokes, shows an inline Carbon `invalidText` error immediately, and BLOCKS Save
# (handleSave aborts -> no PUT -> "Please correct the highlighted fields before saving."). The guarantee
# the legacy slices protect — invalid cost/volume cannot be carried into a save — is preserved, so each
# scenario asserts that guarantee: the inline error PLUS a proven zero-write (a page.route spy on PUT).
#
# Field parity note (re-verified 2026-08-07): legacy FLD-003 applies to THREE editable 8-digit volumes —
# Forest Mgmt Admin, Subtotal Other Costs, and Subtotal Company Logging. Those first and third fields
# were read-only in an earlier build of this app (the old note here said so), but backend commit 0b58057
# "restore legacy parity for derived costs" made their VOLUMES user-entered again, so all three are
# covered below. The same commit made silviculture 139/140 volume-editable under the 7-digit FLD-002
# rule (`fieldKind` in components/schedule1/validation.ts), so those are covered too.
# Runs against the read-only anchor (24050/2017) — never the mutable pair — because no write ever lands.

@sch1 @UC-SCH1-001
Feature: Report Average Cost of Logging (Schedule 1) — invalid amounts rejected before save
  As a mill reporter
  I want invalid cost and volume amounts flagged inline and kept out of a save
  So that only valid Schedule 1 data can be persisted

  Background:
    Given the read-only Schedule 1 anchor is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    And a spy is watching the Schedule 1 save request

  @p1
  Scenario Outline: Entering "<value>" in <field> shows an inline error and is not saved
    When I enter "<value>" in the Schedule 1 "<field>" field
    Then I should see the error "<message>"
    When I save Schedule 1
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 1 save request should not have been sent

    @S03 @FLD-001
    Examples: cost amount out of range
      | field                              | value     | message                                                 |
      | Standing Tree to Loaded Truck cost | 150000000 | Entered cost must be between -99,999,999 and 99,999,999. |

    @S04 @FLD-004
    Examples: non-numeric cost value
      | field                              | value | message                  |
      | Standing Tree to Loaded Truck cost | abc   | Entered cost is invalid. |

    @S05 @FLD-002
    Examples: volume amount out of 7-digit range (every editable 7-digit volume group)
      | field                                                   | value    | message                                                 |
      | Standing Tree to Loaded Truck volume                    | 15000000 | Entered volume must be between -9,999,999 and 9,999,999. |
      | Actual $ Spent volume                                   | 15000000 | Entered volume must be between -9,999,999 and 9,999,999. |
      | Less Silviculture Admin Costs volume                    | 15000000 | Entered volume must be between -9,999,999 and 9,999,999. |
      | Total Silviculture (As per Financial Statements) volume | 15000000 | Entered volume must be between -9,999,999 and 9,999,999. |

    @S06 @FLD-003
    Examples: volume amount out of 8-digit range (all three editable 8-digit volumes — legacy parity)
      | field                                                  | value     | message                                                   |
      | Subtotal Other Costs volume                            | 150000000 | Entered volume must be between -99,999,999 and 99,999,999. |
      | Forest Management Administration Costs (Sch 3) volume  | 150000000 | Entered volume must be between -99,999,999 and 99,999,999. |
      | Subtotal Company Logging Cost (no Silviculture) volume | 150000000 | Entered volume must be between -99,999,999 and 99,999,999. |

    @S07 @FLD-005
    Examples: non-numeric volume value
      | field                                | value | message                          |
      | Standing Tree to Loaded Truck volume | abc   | Entered volume entry is invalid. |
