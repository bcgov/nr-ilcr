# Data-independent app-shell smoke — the BDD equivalent of the app team's frontend/e2e app-shell.spec.ts.
# It guards the persistent Layout chrome (Carbon Header, mock-user selector, primary side-nav) that
# renders CLIENT-SIDE with NO backend / delivery DB. Tagged @smoke so CI can run JUST this — the `smoke`
# Playwright project has NO dependency on the seeded-DB `setup` preflight and aborts every /api call, so
# it runs against a frontend-only deploy (`npx playwright test --project=smoke`). This is the every-PR
# coverage that lets the BDD suite stand in for frontend/e2e without the full Oracle stack.

@smoke
Feature: App shell renders without a backend (data-independent smoke)
  As the CI pipeline
  I want a browser smoke that needs no delivery database
  So that every PR keeps end-to-end coverage of the app shell even where Oracle is unavailable

  Scenario: The app shell renders with the backend unavailable
    Given I open the app with no backend available
    Then the app header and mock-user selector are visible
    And the primary navigation shows Home, Schedules, Submissions, and Mill Associations
