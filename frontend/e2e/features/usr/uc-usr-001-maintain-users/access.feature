# Not a legacy slice: every UC-USR-001 scenario is "authenticated as a Ministry Administrator ... authorized
# for the Administration page action", and the retired Story 2.5 (submitter-mill-2-5) made it explicit —
# "A non-admin cannot reach the screen/endpoints (403)". The Users page is behind the ADMIN-only
# MAINTAIN_USERS action in both tiers: the side-nav Administration group is adminOnly
# (routes/-navigation.ts) and every AssignmentApi endpoint answers 403 to anyone else.
#
# Read-only: the refused PATCH names ...15 and the Then proves it wrote nothing.

@usr @UC-USR-001 @access
Feature: Maintain Users — administrators only
  As the ILCR application
  I want Users offered to Ministry Administrators only
  So that no other role can switch an account or its mills

  @p1 @BR-ADMIN
  Scenario: A submitter is not offered Users, and the user administration API refuses them
    Given the read-only user is at rest
    When I open the app as the suite's default submitter
    Then the Administration menu and its Users link are not offered
    And the user administration API refuses a submitter
    When I open the Users page directly as the suite's default submitter
    Then the Users page does not render for them
