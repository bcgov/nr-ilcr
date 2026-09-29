# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-MILL-001/gherkin/UC-MILL-001-S02.feature
# and -S14.feature (legacy JSF/PrimeFaces). Import a ministry mill into ILCR, and the import that fails.
#
# WHAT RE-GROUNDING CHANGED
#  * `sdialog` -> the Carbon Modal "Find and select Mill to Import". The icon-only per-row Import button
#    is named `Import mill <number>`; its `p:confirm` is the Confirmation modal (CNF-001), whose question
#    the backend serves (`confirmImportMill`) and which is asserted verbatim.
#  * No success message exists for an import (legacy had none, and none is invented): the imported
#    mill's details appearing in the Mill Details panel IS the confirmation.
#  * BR-03 ("xref created Closed with head office Yes; current-year report records created") is read back
#    through the API — the status, the indicator, and the current year's report-status row.
#  * S14: "the import transaction is set to fail" -> the ONE import request is answered in flight with the
#    service's own failure response (HTTP 500, ERR-003 verbatim). No real data can make the service's
#    integrity failure happen on demand. This proves the page's side — the message, nothing tracked, and
#    a working retry; the rollback itself is pinned by the backend's integration test (defects.md VER-4).
#  * S14's error stays IN the dialog, which stays open over its results (22.3: a refusal must not close
#    over the rows the administrator is working through) — legacy closed the dialog and posted the error
#    to the page. So the retry is a second Import click, not a reopen.
#  * Each legacy file's two scenarios are ONE here.
#  * ANCHOR: 26057 (9188 E2E-IMPORT-TEST), a seeded THE.MILL row with no ILCR tracking — the extract has
#    no importable mill at all. Nothing an import writes has a delete endpoint, so the cleanup deletes the
#    xref and the report rows at the DB (scripts/mill_db_restore.py forget-import). S02 and S14 import the
#    SAME mill, so the feature is `@mode:serial` (playwright-bdd: one worker, in order) — they must never run side by side.

@mill @UC-MILL-001 @import @mode:serial
Feature: Maintain Mills — import a mill from ministry client records
  As a Ministry Administrator
  I want to bring a ministry mill that ILCR does not yet track into ILCR
  So that it can be reported on

  @p0 @S02
  Scenario: Search the ministry records for a mill not yet tracked, and import it
    Given the import mill is not tracked in ILCR
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open Import Mill and search for the import mill by its number
    Then the importable results list the import mill
    When I import the mill and confirm "The mill will be imported into ILCR. Are you sure you would like to continue?"
    Then the "Find and select Mill to Import" dialog closes
    And the Mill Details panel shows the imported mill with status "Close"
    And "Activate" is offered and "Deactivate" is not
    And the imported mill is tracked as Closed with head-office indicator "Y"
    And the imported mill has its report records for the current reporting year

  @p1 @S14
  Scenario: An import that fails says so and tracks nothing, and a retry imports the mill
    Given the import mill is not tracked in ILCR
    And the next import request fails on the server
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open Import Mill and search for the import mill by its number
    And I import the mill and confirm "The mill will be imported into ILCR. Are you sure you would like to continue?"
    Then the import dialog shows the error "ILCR cannot import the Mill. Please refer to logs."
    And the import mill is still not tracked in ILCR
    When I import the mill and confirm "The mill will be imported into ILCR. Are you sure you would like to continue?"
    Then the "Find and select Mill to Import" dialog closes
    And the Mill Details panel shows the imported mill with status "Close"
    And the imported mill is tracked as Closed with head-office indicator "Y"

  @p1 @a11y
  Scenario: The Find and select Mill to Import dialog and its confirmation have no WCAG 2.1 AA violations
    Given the import mill is not tracked in ILCR
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open Import Mill and search for the import mill by its number
    Then the importable results list the import mill
    And the "Find and select Mill to Import (with results)" view has no WCAG 2.1 AA accessibility violations
    # GAP-5: the screen's only confirmation, scanned open, then declined, so nothing is imported.
    When I start importing the mill and am asked "The mill will be imported into ILCR. Are you sure you would like to continue?"
    Then the "Import confirmation" view has no WCAG 2.1 AA accessibility violations
    When I answer No to the confirmation
    Then the import mill is still not tracked in ILCR

  # GAP-4, the import dialog's Name criterion. Serial with the imports above, like every scenario here.
  @p2 @GAP-4
  Scenario: The import dialog finds an importable mill by name
    Given the import mill is not tracked in ILCR
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open Import Mill and search for the import mill by its name
    Then the importable results list the import mill
