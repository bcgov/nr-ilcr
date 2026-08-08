# Defects — UC-SEC-001 Establish Working Context (Home)
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

Environment for all entries: branch `e2e-test/uc-sec-001-home-context` · frontend `:3000` + backend `:8080`
(security off, fixed mock authority) · seeded Docker DB `THE/default@localhost:1525/DBDOCK_01`. Data
fixtures pinned in `fixtures/sec/working-context-test-data.ts`. Verified on real data 2026-07-30.

**Bug / Regression:** _none._

**Divergences:**

- **#1 — The Home page no longer shows a role-specific notice.**
  - **What's wrong:** Legacy `home.xhtml` rendered a "User Role Specific Message Section" — a per-role
    notice looked up by the user's role (BR-07), and UC-SEC-001 S01 asserts it appears. The new Home
    (`components/home/index.tsx`) renders no such section at all.
  - **Expected vs actual:** Expected a role-specific notice on Home for the signed-in role. Actual — there
    is no notice element; Home shows only the title, the two dropdowns, Save, and success/error banners.
  - **How we caught it:** Re-grounding S01 against the running app + reading the Home component — no
    role-message markup exists. The legacy role model (`ILCR_LICENSEE`) is also gone (two-group model,
    PRD DL-23).
  - **Is it a defect?** Likely a deliberate drop in the rebuild (the role-notice maintenance screen is not
    in scope, and the role model changed). Not asserted, so no failing test.
  - **Action:** BA/QA to confirm the role-specific notice is intentionally dropped (or file it as missing
    parity). Kept as a documentation item; the S01 scenario is **GREEN** and simply does not assert a
    notice that the app does not render.
  - **Priority / env:** p2. **Status:** OPEN. Found 2026-07-30.
  - **Test:** `working-context.feature` `@S01` (asserts SUC-001 + banner only) — GREEN.

- **#2 — The empty-mill / empty-year states (S04/S05/S08) cannot be produced through the UI.**
  - **What's wrong:** Legacy S04/S05/S08 require clicking Save while a dropdown is still on its
    "Select Mill" / "Select Reporting Year" placeholder, to get the required-field error. In the new app
    the mount default context (`millYearDefaults.ts` → mill 13050 / year 2017) **is present in both list
    endpoints**, so Home **pre-selects both dropdowns on landing**, and Carbon `Dropdown` offers no
    clear-to-placeholder control — so a user can never reach the empty state from the browser.
  - **Expected vs actual:** Expected to be able to leave a dropdown empty and see "Mill: Value is required."
    / "Reporting Year: Value is required." Actual — both dropdowns are always populated; the required-field
    path is unreachable via the UI.
  - **How we caught it:** Re-grounding S04/S05/S08. The required-field **contract still holds** and is
    enforced server-side (validation is backend-authoritative — `handleSave` sends empty params verbatim):
    - `GET /v1/mill-context?millId=&year=2017` → **HTTP 400**, `messages[0].text = "Mill: Value is required."`
    - `GET /v1/mill-context?millId=13050&year=` → **HTTP 400**, `"Reporting Year: Value is required."`
    - `GET /v1/mill-context?millId=&year=` → **HTTP 400**, BOTH messages.
    So the guarantee (a missing selection is rejected with the exact legacy message) is preserved; only the
    UI can't drive it into that state.
  - **Note for the app team:** their `frontend/e2e/home.spec.ts` S04 (a browser test that clicks Save with
    the mill empty) relied on the OLD default (`514`) being ABSENT from the list. With the default now
    `13050` (present), that premise no longer holds — that assertion is **stale** and would fail if run
    live (its `E2E_LIVE_DATA` gate currently hides it).
  - **Is it a defect?** The required-field enforcement is correct (server-side). Whether Home *should* start
    on placeholders (legacy behaviour) instead of pre-selecting a dev default is a parity question.
  - **Action:** BA/QA to confirm: is pre-selecting the default acceptable, or should Home land on empty
    dropdowns (restoring the S04/S05/S08 UI path)? Tracked as Coverage gap #1. **Not** a `@discovered-*`
    red — the guarantee holds at the contract; there is no failing behaviour to track.
  - **Priority / env:** p1. **Status:** OPEN. Found 2026-07-30.
  - **Test:** none at the UI (unreachable); contract proven via API above.

- **#3 — On landing, the working-context banner is already populated (before any Save).**
  - **What's wrong:** Legacy Home held no session working context until the first Save. The new app seeds a
    dev/UAT default context (`millYearDefaults.ts`, explicitly a temporary scaffold), so the banner renders
    the default mill/year immediately on landing and both dropdowns are pre-filled.
  - **Expected vs actual:** Expected an empty banner until a context is saved. Actual — the banner shows the
    default context (mill 999 "ISP TEST" / 2017) on first paint.
  - **How we caught it:** Re-grounding the landing state (`@S01 @landing`). `ContextBanner` fetches
    `GET /v1/mill-context` for the default on mount → 200 → renders.
  - **Is it a defect?** No — it is a known dev-scaffold placeholder (the comment in `millYearDefaults.ts`
    says a real runtime-config/auth-driven context is deferred). It will change when the auth story lands.
  - **Action:** BA/QA aware only; re-verify this landing scenario once real context injection replaces the
    dev default. Kept as a **GREEN** re-grounded test asserting the actual behaviour.
  - **Priority / env:** p2. **Status:** OPEN. Found 2026-07-30.
  - **Test:** `working-context.feature` `@S01 @landing` — GREEN.

**Coverage gaps (not tested at the UI — no app problem):**

- **#1 — S04/S05/S08 required-field validation is contract-covered, not browser-covered.**
  - **Why not:** The default context pre-selects both dropdowns and Carbon has no clear control, so the
    empty state is unreachable from the browser (Divergence #2). The validation itself is enforced
    server-side and proven via the three API calls in Divergence #2.
  - **Future action:** author the browser path if/when Home lands on empty dropdowns, or a mock user whose
    default context is absent from the lists. Meanwhile the contract evidence stands.
  - **Status:** OPEN — `not-applicable (UI) / covered-by-contract` in coverage.md.

- **#2 — S02 single-mill pre-select is not reproducible on delivery data.**
  - **Why not:** The mount single-mill fallback fires only when the list has exactly one mill; the delivery
    extract has 21. Same conclusion the app team recorded (Vitest MSW 1-mill list covers it at unit level).
  - **Status:** OPEN — `not-applicable` in coverage.md.

- **#3 — Accessibility (axe) — NOW COVERED.**
  - **Resolved 2026-07-30:** `@axe-core/playwright` is wired into this suite (`pages/common/axe.ts`,
    tags `wcag2a/2aa/21a/21aa`) and runs on the Home landing state and the populated-banner state
    (`working-context.feature` `@a11y`). Zero WCAG 2.1 AA violations. Note: the truly-empty Home state
    isn't reachable (default pre-fills — Divergence #2/#3), so both axe runs are on a populated Home; the
    HOME-1.5 AC4 intent (Home + banner a11y proven) holds.
  - **Status:** CLOSED (2026-07-30).

- **#4 — Role-gated branches can't be exercised under single-role mock auth.**
  - **Why not:** Security off → one fixed authority per run, so any role-conditional behaviour on Home can't
    be varied. Same as UC-SCH1-001 Coverage gap #1.
  - **Future action:** revisit with FAM auth + finer roles.
  - **Status:** OPEN — `blocked`.

**Spec gaps (the Gherkin is missing / underspecifies scenarios):**

- **#1 — S07 left the banner outcome as `[UNKNOWN]`.** The legacy Gherkin S07 could not confirm from source
  how the `#subMenu` banner renders a null status, and wrote `[UNKNOWN]`. Re-grounded to observed behaviour:
  the banner shows the **Mill line only**, both status lines suppressed, no error. Now asserted green
  (`@S07`). Recorded so the Gherkin's open question is closed by evidence, not fabrication.

**Verified — not a defect:**

- **Role re-grounded to the new two-group model.** The Gherkin authenticates as `ILCR_LICENSEE`; the new app
  has no such role (ratified `ILCR_ADMIN` + `ILCR_SUBMITTER`, PRD DL-23) and runs security-off with a fixed
  mock authority. Save is authorized (live: `GET /v1/mill-context?millId=12050&year=2017` → 200,
  `message.text = "Data saved successfully"`). Deliberate rename, not a defect (same finding as UC-SCH1-001).
- **Home "Save" writes nothing.** It is a read/resolve (`GET /v1/mill-context`) that sets the client-side
  MillYearContext and returns SUC-001 — no report rows are created, so no teardown is needed and scenarios
  are parallel-safe. A deliberate design (client-side context, AR11), not a defect.
