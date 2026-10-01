# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S15.feature
# (legacy JSF/PrimeFaces). Five numeric validators, each with a failure and a recovery — collapsed into
# one Scenario Outline because the observable pattern is identical and only the field, bound and message
# differ. The source file scripts them as ten separate scenarios "for full traceability"; the Examples
# table keeps every one of those rows visible while removing nine copies of the same prose.
#
# WHAT RE-GROUNDING CHANGED
#  * `schedule5Form:new*` ids -> the visible Carbon labels; errors render as inline
#    `.cds--form-requirement` text under each field, not in a messages panel.
#  * BLUR IS THE COMMIT POINT — "a field's error appears only once the licensee has left it"
#    (index.tsx:734). A bare fill leaves the field invalid but SILENT.
#  * THE DISTANCE MESSAGE DIFFERS FROM THE SOURCE GHERKIN, AND THE APP IS RIGHT. The Gherkin says
#    "between 0 and 999,999."; the app says "999,999.9". Legacy's text understated its own validator,
#    which accepted up to 999,999.9, and the Ministry ruled the TEXT the defect and confirmed the BOUND
#    (PR #370, 2026-08-27 — the value is in km). Recorded as VER-4; the invalid value 1000000 is above
#    the real bound either way, so the scenario still exercises a genuine rejection.
#  * Recoveries has its OWN band. It is floored at 0 because it is stored positive and SUBTRACTED, so
#    its message is "between 0 and 9,999,999." where the ordinary categories say "-9,999,999 and
#    9,999,999." Wages and Benefits is wider still (±99,999,999) — not exercised here; it is the one
#    category whose legacy input omits costSize="7", and a blanket rule would make any stored camp above
#    9,999,999 un-re-saveable.
#  * ANCHOR: 13050/2023 (`VALIDATION_ANCHOR`) — VALIDATE-ONLY. Nothing here ever saves, so the anchor is
#    one no scenario creates on. That is what makes "the value is not accepted" checkable without a
#    cleanup registry, and it is why S12 (whose recovery arm DOES save) has its own anchor instead.

@sch5 @UC-SCH5-001 @numeric-validation
Feature: Report Camp and Access Expenses (Schedule 5) — numeric range validation
  As a mill reporter
  I want the system to reject numeric entries outside each field's accepted range
  So that only valid distance, size, volume and cost values are stored

  @p1 @S15 @FLD-002
  Scenario Outline: <field> outside its accepted range is rejected, then accepted once corrected
    Given the Schedule 5 anchor "validation" is an editable Draft with no camps
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    And I enter "<invalid>" in the "<field>" field
    Then the "<field>" field shows the error "<message>"
    When I enter "<valid>" in the "<field>" field
    Then the "<field>" field shows no error

    Examples:
      | field                             | invalid  | valid | message                                                |
      | Road Distance to Operating Area   | 1000000  | 12.5  | Entered distance must be between 0 and 999,999.9.      |
      | Size of Camp                      | 1000     | 40    | Entered number of persons must be between 1 and 999.   |
      | Associated Camp Volume            | 10000000 | 5000  | Entered volume must be between 0 and 9,999,999.        |

  @p1 @S15 @FLD-002
  Scenario Outline: <category> cost outside its accepted range is rejected, then accepted once corrected
    Given the Schedule 5 anchor "validation" is an editable Draft with no camps
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I start a new camp
    And I enter a "<category>" cost of "<invalid>"
    Then the "<category>" cost field shows the error "<message>"
    When I enter a "<category>" cost of "<valid>"
    Then the "<category>" cost field shows no error

    Examples:
      | category          | invalid  | valid | message                                                    |
      | Catering and Food | 10000000 | 1000  | Entered cost must be between -9,999,999 and 9,999,999.     |
      | Recoveries        | 10000000 | 300   | Entered cost must be between 0 and 9,999,999.              |
