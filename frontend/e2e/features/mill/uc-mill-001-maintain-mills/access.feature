# BR-10 (slices.md S01 Entry Point Reachability): Mills is an administrator's screen. Not a legacy slice
# of its own — legacy's Background ("I hold the administration permission") assumed it on every file —
# so this is the other side of that assumption, closing defects.md GAP-3.
#
# The suite's default identity IS a submitter (steps/fixtures/global.ts seeds it for every scenario), so
# no identity step is needed: this scenario simply never declares the administrator.
#
# Asserted from both ends, as the rest of this UC is: the side-nav does not offer the Administration
# group, and the mills API refuses the submitter — a read, and a write. The write targets 26055, which
# has an active user: even if authorization regressed, that deactivate would be refused as S12 is and
# write nothing, so this scenario can never damage an anchor.

@mill @UC-MILL-001 @access
Feature: Maintain Mills — administrators only
  As the ILCR application
  I want Mills offered to Ministry Administrators only
  So that no other role can change a mill's status or its users

  @p1 @BR-10
  Scenario: A submitter is not offered Mills, and the mills API refuses them
    When I open the app as the suite's default submitter
    Then the Administration menu and its Mills link are not offered
    And the mill administration API refuses a submitter
