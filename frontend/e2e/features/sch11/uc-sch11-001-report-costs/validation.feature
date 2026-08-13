# Re-grounded from UC-SCH11-001-S14..S19.feature — every required-field and range rejection.
#
# HOW THE REJECTION IS PROVEN. Each scenario asserts BOTH halves:
#   1. the verbatim message renders, and
#   2. NO mutating request was sent (the spy).
# Half 2 is the one that matters: the app's advisory gate (components/schedule11/validation.ts) runs
# BEFORE the POST and returns early, so a rejected Add never reaches the server. Asserting only the
# message would also pass if a request HAD been sent and rejected server-side — a materially different
# behaviour, and exactly the "prove the negative, don't infer it" rule.
#
# All rejection scenarios share the VALIDATE-ONLY anchor (10050/2019) and register no cleanup, because
# by construction nothing is ever written there. That is deliberate: it is NOT one of the mutating
# anchors, so these scenarios cannot interfere with each other under fullyParallel.
#
# TWO LEGACY OPEN ITEMS ARE RESOLVED HERE (defects.md VER-1/VER-2). The legacy sidecar
# left both marked, and the app's own validation.ts still labels them "PROVISIONAL … confirmed in Story
# 25.4" — i.e. by this work:
#   * S15 Enhanced: legacy had no `label` on the control, so JSF rendered the RAW CLIENT ID
#     ("addLocationForm:addEnhancedIndicator: Value is required."). The new app defines
#     `enhancedIndicatorRequiredErrorMsg=Enhanced: Value is required.` — a deliberate improvement.
#   * S18 NAR range: legacy never overrode the JSF DoubleRangeValidator default, so the message was
#     whatever the JSF impl emitted ([TODO — capture from live app]). The new app defines
#     `netAreaRangeErrorMsg=Entered NAR (ha) must be between 0 and 999,999.9.`
# Both confirmed against the backend bundle AND a live 400 on 2026-08-10. Asserted verbatim below.

@sch11 @UC-SCH11-001 @validation
Feature: Report Basic Silviculture Costs (Schedule 11) — reject invalid location entries
  As a mill reporter
  I want to be told exactly which entered value is unacceptable
  So that I can correct it and add the location

  Background:
    Given the Schedule 11 validate-only anchor is an editable Draft
    And a spy is watching the Schedule 11 location requests
    And I have selected that mill and reporting year on the Home page

  @S14 @p1
  Scenario: Add is rejected when Location is left empty
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field    | value   |
      | Enhanced | Yes     |
      | Biogeo   | primary |
      | NAR(ha)  | 50.5    |
    And I click Add
    Then I should see the error "Location: Value is required."
    And no Schedule 11 location mutation should have been sent

  @S15 @p1
  Scenario: Add is rejected when the Enhanced indicator is not selected
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field    | value          |
      | Location | E2E S15 reject |
      | Biogeo   | primary        |
      | NAR(ha)  | 50.5           |
    And I click Add
    Then I should see the error "Enhanced: Value is required."
    And no Schedule 11 location mutation should have been sent

  @S16 @p1
  Scenario: Add is rejected when Biogeo free text was typed but never chosen from the suggestions
    # Forced selection (BR-09). The prefix typed here ("IDF") returns 20 REAL suggestions — the list is
    # asserted populated before Add is clicked — and none is chosen, which is the slice's actual
    # condition. Typing gibberish that matched nothing would leave the list empty and prove only that a
    # blank field is rejected, never exercising forced selection at all.
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field    | value          |
      | Location | E2E S16 reject |
      | Enhanced | Yes            |
      | Biogeo   | free text      |
      | NAR(ha)  | 50.5           |
    And I click Add
    Then I should see the error "Biogeo/Subzone/Variant: Value is required."
    And no Schedule 11 location mutation should have been sent

  @S17 @p1
  Scenario: Add is rejected when NAR(ha) is left empty
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field    | value          |
      | Location | E2E S17 reject |
      | Enhanced | Yes            |
      | Biogeo   | primary        |
    And I click Add
    Then I should see the error "NAR(ha): Value is required."
    And no Schedule 11 location mutation should have been sent

  # Both directions: legacy's range validator produced the identical message above the maximum and below
  # the minimum, and covering only one would leave half the rule unproven.
  @S18 @p1
  Scenario Outline: Add is rejected when NAR(ha) is <direction> the allowed range
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field    | value          |
      | Location | E2E S18 reject |
      | Enhanced | Yes            |
      | Biogeo   | primary        |
      | NAR(ha)  | <netArea>      |
    And I click Add
    Then I should see the error "Entered NAR (ha) must be between 0 and 999,999.9."
    And no Schedule 11 location mutation should have been sent

    Examples:
      | direction | netArea   |
      | above     | 1000000.0 |
      | below     | -1.0      |

  # Both cost fields share one validator and one message, and both directions produce it — so the outline
  # crosses field × direction rather than testing a single corner.
  @S19 @p1
  Scenario Outline: Add is rejected when <field> is <direction> the allowed range
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field        | value          |
      | Location     | E2E S19 reject |
      | Enhanced     | Yes            |
      | Biogeo       | primary        |
      | NAR(ha)      | 50.5           |
      | <field>      | <cost>         |
    And I click Add
    Then I should see the error "Entered cost must be between -99,999,999 and 99,999,999."
    And no Schedule 11 location mutation should have been sent

    Examples:
      | field        | direction | cost       |
      | Actual Cost  | above     | 100000000  |
      | Planned Cost | below     | -100000000 |
