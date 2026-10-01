# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-MILL-001/gherkin/UC-MILL-001-S01.feature
# (legacy JSF/PrimeFaces). S01 selects an existing ILCR mill, updates its head-office indicator and its
# head-office and division contacts, and saves.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/mills.xhtml` -> route `/mills`, reached via Home + side-nav Administration -> Mills. The
#    Administration group is `adminOnly`, so the scenario declares the administrator explicitly (the
#    suite's default identity is the submitter, who is never shown the menu).
#  * Legacy's "WebADE SSO" + "administration permission" Background is the ILCR_ADMIN mock user: security
#    is off in e2e and the role rides the X-Mock-Groups header (pages/common/mockUser.ts). MAINTAIN_MILLS
#    is ADMIN-only, so holding the role IS holding the permission.
#  * The `pdialog` "Find and select Mill" dialog is a Carbon Modal of the same name. Row select is still
#    the select action — there is no OK button — but it is a named button in the Mill Number cell
#    (`Select mill <number>`) rather than a bare `<tr>` click, which was not keyboard-operable.
#  * `form:selectedMillList:0:overrideTotPopVal` / `headOffOneMenu` / `divisionOneMenu` -> three Carbon
#    Dropdowns labelled "Head Office :", "Head Office Contact :" and "Division Contact :". The contact
#    dropdowns lead with an explicit "(None)" option (a blank selection CLEARS the column, so it has to
#    read as a choice).
#  * `form:messagesId` is not a `p:messages` panel; SUC-001 arrives as a Carbon success notification.
#    Its text is legacy's verbatim (`mill.updated`), except that the mill number carries no thousands
#    separator — legacy fed a BigDecimal to MessageFormat and 9171 printed as "9,171" (22.1 deviation (E)).
#  * The legacy file's first scenario (search and select) and second (update and save) are ONE scenario
#    here. The second's `Given I have selected an existing mill` is exactly the first's end state, and
#    running them apart would need a second contact-bearing mill for no extra coverage.
#  * The legacy scenario stopped at the message. This also reads the mill back through the API, because
#    after Save the page installs the RESPONSE's mill and resets its form to it — the panel looks the same
#    whether or not anything reached the database.
#  * BR-09 ("limited to the mill's client-location contacts") is asserted on the option list itself: both
#    dropdowns offer exactly "(None)" plus the mill's own two contacts.
#  * ANCHOR: mill 25050 (9171 BCOVEY-TEST). Only five tracked mills carry any contact at all and all five
#    are schedule anchors; S01 writes only the xref row's indicator/contact columns, which no schedule
#    reads — see fixtures/mill/mills-test-data.ts.
#
# NOT COVERED HERE (see coverage.md): the audit line's "Last Edited by" value is asserted against the
# API read-back, never as a literal — every save stamps it and no endpoint can put it back, so it drifts
# run over run. The head-office Save-gating for a mill whose indicator was never set (D5) is not S01's —
# 25050 holds Y — and rides a later slice.

@mill @UC-MILL-001 @happy-path
Feature: Maintain Mills — update an existing mill's head-office indicator and contacts
  As a Ministry Administrator
  I want to select an existing mill and update its head-office indicator and contacts
  So that the mill's ILCR record stays current

  @p0 @S01
  Scenario: Select an existing mill, change its head-office indicator and both contacts, and save
    Given the S01 mill is at rest with its head-office indicator and contacts
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    Then no mill is selected and only "Select Mill" and "Import Mill" are offered
    When I search for the mill by its number and select it
    Then the "Find and select Mill" dialog closes
    And the Mill Details panel shows the mill's number, name, status and last-edited details
    And "Change Mill" is offered in place of "Select Mill" and "Import Mill"
    And the head-office indicator and both contacts show the mill's saved values
    And both contact lists offer only the mill's own client-location contacts
    When I set Head Office to "No" and swap the head-office and division contacts
    And I save the mill
    Then I should see the message "Mill 9171 - BCOVEY-TEST has been saved."
    And the mill's head-office indicator and contacts are persisted as edited
    And the Mill Details panel shows the saved mill's last-edited details

  # GAP-1. Legacy's slice catalogue excluded a blank contact as "no distinct outcome" (its message is the
  # same), but here a blank IS a delete: the column is cleared, which is why the page leads both
  # dropdowns with an explicit "(None)". On its own mill (26059), so it never races S01.
  @p2 @GAP-1
  Scenario: Choosing "(None)" for a contact and saving clears that contact
    Given the GAP-1 mill is at rest with its head-office indicator and contacts
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    And I clear the Division Contact to "(None)"
    And I save the mill
    Then I should see the message "Mill 9190 - E2E-CONTACTS-TEST has been saved."
    And the mill's division contact is persisted as cleared and nothing else on the panel moved
    And the Division Contact shows "(None)"
