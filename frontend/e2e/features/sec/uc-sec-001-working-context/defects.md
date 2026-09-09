# Defects — UC-SEC-001 Establish Working Context (Home)
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

> **Entry ids:** each register numbers independently, so ids carry their register as a prefix —
> `BUG-n` (Bug / Regression), `DIV-n` (Divergence), `GAP-n` (Coverage gap), `SPEC-n` (Spec gap).
> Cite the prefixed id when raising a ticket; a bare "#3" is ambiguous across three registers.

Environment for all entries: branch `e2e-test/uc-sec-001-home-context` · frontend `:3000` + backend `:8080`
(security off, fixed mock authority) · seeded Docker DB `THE/default@localhost:1525/DBDOCK_01`. Data
fixtures pinned in `fixtures/sec/working-context-test-data.ts`. Verified on real data 2026-07-30.

**Bug / Regression:** _none._

**Divergences:**

- **BUG-1 — Home fails WCAG 1.4.3 on the admin-authored welcome message. Found 2026-08-21, split into its
  own scenarios 2026-08-24.** The two Home axe sweeps report `color-contrast` on the SAME two nodes,
  `p:nth-child(1) > .headerUnderline` and `p:nth-child(2) > span`, measured against white:

    | Text | Colour | Ratio | WCAG 1.4.3 (4.5:1) |
    |---|---|---|---|
    | "Administrator Welcome Message" | `rgb(51, 204, 0)` | **2.15:1** | FAIL |
    | "Administrator Role" | `rgb(204, 51, 204)` | **4.27:1** | FAIL |

  - **This is CONTENT, not app CSS — the distinction that governs everything else here.** Those colours are
    inline `style` attributes inside the welcome message an administrator authors, stored raw in
    `THE.ILCR_ROLE.MESSAGE_TEXT` and rendered by `home/index.tsx:233` through `dangerouslySetInnerHTML` +
    `sanitizeHtml`. DOMPurify's defaults strip scripts and handlers but KEEP `style`, so authored colours
    reach the page and axe measures them. The values above are legacy-migrated seed data.
  - **A GREEN HERE WILL NOT PROVE THE FIX.** Unlike every other `@discovered-bug` in this suite, the red
    depends on DB content rather than on app code: editing the welcome message also turns it green. That is
    not hypothetical — it happened on 2026-08-21, when the message was edited through the app's TipTap
    editor, whose StarterKit carries no colour mark and therefore silently discarded every colour on save;
    the reds vanished until the database container was rebuilt and the seed restored. Anyone reading a green
    here must confirm WHICH of the two causes produced it.
  - **The app-level defect is the absence of a constraint,** not these two particular colours. Nothing stops
    an administrator authoring any colour, so NFR1 cannot be guaranteed for this page as built. Agreed
    direction (2026-08-24): add a contrast constraint on authored content. Until that lands, these two
    scenarios stay red and excluded from `test:gate`.
  - **Why the scans are their own scenarios.** Until 2026-08-24 each sweep was the last step of a journey
    scenario, so this colour failed `@p0` "select a mill and an opened reporting year and save
    successfully" — and, carrying no `@discovered-*` tag, made `npm run test:gate` red. Splitting them
    keeps both journeys in the gate and green while the contrast stays tracked rather than skipped or
    ignored. Skipping was rejected outright: it would have dropped the `@p0` save journey to hide a colour.
  - **Test:** `working-context.feature` `@a11y @discovered-bug` ×2 (landing, and banner-populated).
  - **Status:** OPEN — confirmed, and the fix is scheduled as a contrast constraint on authored content.

- **DIV-1 — The Home page no longer shows a role-specific notice.**
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
  - **Priority / env:** p2.
  - **Status:** OPEN — with the dev. Found 2026-07-30. Triaged with the dev (2026-08-10): she'll
    double-check with the BA whether the role notice is needed or not, when she gets a chance.
  - **Test:** `working-context.feature` `@S01` (asserts SUC-001 + banner only) — GREEN.

- **DIV-2 — The empty-mill / empty-year states (S04/S05/S08) cannot be produced through the UI.**
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
    dropdowns (restoring the S04/S05/S08 UI path)? Tracked as GAP-1. **Not** a `@discovered-*`
    red — the guarantee holds at the contract; there is no failing behaviour to track.
  - **Priority / env:** p1.
  - **Status:** OPEN — with the dev. Found 2026-07-30. Triaged with the dev (2026-08-10): she'll work on
    this when she gets a chance. Reconciling it is what unblocks the S04/S05/S08 browser tests — see GAP-1.
  - **Test:** none at the UI (unreachable); contract proven via API above.

- **DIV-3 — On landing, the working-context banner is already populated (before any Save).**
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
  - **Priority / env:** p2.
  - **Status:** OPEN — with the dev. Found 2026-07-30. Triaged with the dev (2026-08-10): she'll work on
    this when she gets a chance. Same root cause as DIV-2 (the `millYearDefaults.ts` dev scaffold), so the
    two will likely move together.
  - **Test:** `working-context.feature` `@S01 @landing` — GREEN.

**Coverage gaps (not tested at the UI — no app problem):**

- **GAP-1 — S04/S05/S08 have no browser test, because the empty-dropdown state can't be reached in the UI.**
  - **In plain terms:** slices S04/S05/S08 are the "press Save with nothing selected and get *Mill: Value is
    required.*" cases. We can't write those as browser tests today, because Home always lands with **both
    dropdowns already filled** (from the `millYearDefaults.ts` dev default) and Carbon's `Dropdown` gives no
    way to clear a selection back to its placeholder. So there is no way to *get into* the state those
    slices describe by clicking around.
  - **This is a direct consequence of DIV-2 and DIV-3, not a separate problem** — same root cause, the dev
    default context. It is listed separately only because it is the *testing* consequence, while DIV-2/DIV-3
    are the *behaviour* questions.
  - **The rule itself is not untested:** validation is backend-authoritative and proven by the three API
    calls documented in DIV-2 (each returning the exact legacy message). What's missing is only the
    click-through path.
  - **Status:** OPEN — waiting on DIV-2/DIV-3. Once DIV-2 and DIV-3 are reconciled, QA can then
    implement the S04/S05/S08 browser tests. `not-applicable (UI) / covered-by-contract` in coverage.md
    until then.

- **GAP-2 — S02 single-mill pre-select is not reproducible on delivery data.**
  - **Why not:** The mount single-mill fallback fires only when the list has exactly one mill; the delivery
    extract has 21. Same conclusion the app team recorded (Vitest MSW 1-mill list covers it at unit level).
  - **Status:** OPEN — with the dev. `not-applicable` in coverage.md. Triaged with the dev (2026-08-10): she'll double-check with
    the BA whether this needs browser coverage at all, given the unit test already covers it.

- **GAP-3 — Accessibility (axe) — NOW COVERED.**
  - **Resolved 2026-07-30:** `@axe-core/playwright` is wired into this suite (`pages/common/axe.ts`,
    tags `wcag2a/2aa/21a/21aa`) and runs on the Home landing state and the populated-banner state
    (`working-context.feature` `@a11y`). Zero WCAG 2.1 AA violations. Note: the truly-empty Home state
    isn't reachable (default pre-fills — DIV-2/#3), so both axe runs are on a populated Home; the
    HOME-1.5 AC4 intent (Home + banner a11y proven) holds.
  - **Status:** CLOSED (2026-07-30).

- **GAP-4 — Role-gated branches under mock auth — PREMISE CORRECTED 2026-09-09.**
  - **What this used to say:** "Security off → one fixed authority per run, so any role-conditional
    behaviour on Home can't be varied." That was true when written and **false since #265**
    (2026-08-12): `service/api-service.ts` sends the selected mock user's roles as `X-Mock-Groups`
    and `MockPrincipalFilter` prefers that header over `ilcr.security.mock-role`. The authority is
    per REQUEST, so a role CAN be varied per scenario — `seedMockUser(page, 'admin' | 'submitter')`.
  - **What it cost while it read the other way:** because `findMockUser` falls back to
    `?? MOCK_USERS[0]` and the admin is listed first, the whole suite ran as **`ILCR_ADMIN`** while
    every feature file declared "As a Licensee". Invisible until Story 16.1 made an administrator
    read-only at Draft, at which point ~200 scenarios failed at once and read as an app regression.
    Three documents said the selector could not matter, which is why nobody looked there.
  - **What was still blocked, and no longer is (#385):** anything needing a DIRECTORY identity. The
    mock principal was a `UsernamePasswordAuthenticationToken` with no `custom:idp_user_id`, so a
    submitter's mill SCOPING could not be exercised at all. It now carries a stand-in GUID
    (`ilcr.security.mock-user-guid`) that is associated with the mills in both e2e databases, so the
    scoped query really runs — see GAP-5. What remains out of reach is a *real* FAM identity: the
    GUID is synthetic, so anything reading claims other than `custom:idp_user_id` is still untested
    here.
  - **Status:** OPEN — `substantially unblocked` (2026-09-09). Role variation: available and used.
    Directory-scoped behaviour: available and used, via a synthetic GUID.

- **GAP-5 — A mock submitter was offered NO mill, so the suite borrowed the administrator for
  `GET /v1/mills`. APP-SIDE gap. Found 2026-09-09, CLOSED 2026-09-09 (bcgov/nr-ilcr#385).**
  - **The gap, in the app:** `MillContextController.currentUserGuid()` resolves to `""` for the
    security-off dev principal (not a `Jwt`), and `MillContextService.listMills` fail-closes a
    submitter with a blank GUID to `List.of()`. Eleven lines above it, `validateMillAccess` **exempts
    that same principal** ("mock/security-off (AC6 exemption) — no real directory identity to scope
    by"). So the two mill-scope gates disagree: the identity may WRITE to any mill and is shown none
    to select. Under mock auth that leaves NO usable identity once the Story 16.1 matrix lands — the
    admin, the only role Home offers a mill to, is read-only at Draft. It breaks local dev the same
    way: neither mock user can enter schedule data.
  - **The interim workaround, now DELETED:** `pages/common/mockUser.ts` `grantAdminOnMillList`
    rewrote `X-Mock-Groups` to `ILCR_ADMIN` on `GET /api/v1/mills` **only**, installed by the global
    `page` fixture, with the identity staying `ILCR_SUBMITTER` for every schedule GET, write and
    check-status. It cost real coverage: the dropdown the suite asserted was the ADMIN's list
    (`findAllMills` — every listable mill, closed included), so a submitter's SCOPED list
    (`findMillsForUser`, and the S06 "closed *associated* mills still appear" shape) was not
    covered here at all.
  - **The app fix, taken (#385).** Two candidates were considered. Exempting the mock principal in
    `listMills` as `validateMillAccess` does was rejected: it makes the gates agree but has the
    suite BYPASS the scoped query, so the coverage above stays lost. Instead `MockPrincipalFilter`
    now presents a stand-in directory GUID (`ilcr.security.mock-user-guid`, defaulting to the
    test-scope canonical submitter) in the token's `details` — not its name, which feeds the
    `VARCHAR2(30)` `ENTRY_USERID`/`UPDATE_USERID` audit columns while a FAM GUID is 32 chars, so
    naming the principal after it would `ORA-12899` every save. `MillContextController
    .currentUserGuid()` reads `details` for a non-`Jwt` caller. Unreachable when deployed: that
    filter is not registered with security on, and `DeployedSecurityGuard` forbids security-off
    beside a datasource on a pod.
  - **The data half, which the identity is useless without.** That GUID needs active
    `ILCR_MILL_USER_XREF` rows in BOTH e2e databases, and `R__70_test_scope_canonical_submitter.sql`
    sorts BEFORE `R__80_e2e_anchor_seed.sql` so its set-based association cannot reach the e2e
    mills. Added in each: a set-based `INSERT` at the end of `R__80` for CI, and
    `real-test-data-patches/common/mock-submitter-associations.sql` for the extract. Every mill is
    associated, so the submitter's scoped list is the same SET as the admin's — verified with
    `MINUS` both ways on the extract (0 rows each side), which is what makes the deletion of the
    workaround behaviour-preserving for the dropdown while the query behind it becomes the real one.
  - **Measured, same database, before and after:** `GET /api/v1/mills` as `ILCR_SUBMITTER` against
    the extract returned **0 mills** on the old code and **21** on the fixed code (the admin
    returned 21 in both). The first entry is a `CLS` mill, i.e. the S06 closed-associated shape now
    renders for a submitter rather than only for an admin.
  - **The other reason this mattered:** it broke LOCAL DEV outright. Since Story 16.1 an admin is
    read-only at Draft while a submitter was offered no mill, so neither mock user could enter
    schedule data. Note the default GUID exists only in the seeded databases — against any other
    database, point `ilcr.security.mock-user-guid` at a GUID that database associates.
  - **Status:** CLOSED (2026-09-09). App gap fixed, workaround deleted, submitter scoping now
    covered here rather than only by the backend's own tests.

**Spec gaps (the Gherkin is missing / underspecifies scenarios):**

- **SPEC-1 — CLOSED 2026-08-10. S07's `[UNKNOWN]` banner outcome is answered and asserted.**
  - **What this was, in plain terms:** slice S07 is "pick a mill/year that has no report-status row, and save".
    Whoever wrote the legacy Gherkin couldn't tell from the legacy source what the banner should then show,
    so instead of guessing they wrote `[UNKNOWN]`. This entry existed to record that open question.
  - **The answer, from the running app:** the banner shows the **Mill line only** — both track-status lines
    are suppressed, and no error appears.
  - **Asserted, and not just superficially:** two green scenarios cover it (`working-context.feature` `@S07`
    for the Home banner, `schedule-tombstone.feature` `@S07` for the schedule header). Each asserts the mill
    line **is** visible *and* that the status lines are **absent** (`toHaveCount(0)` on `/- Status:/`), so an
    empty-banner regression can't pass it either. The anchor's "no report-status row" premise is itself
    asserted by `preflight/sec-anchors.setup.ts` on every run.
  - **Why closing is safe here** (and why it differs from a spec gap that stays open): the Gherkin said
    `[UNKNOWN]`, which was *honest* — the marker did its job by stopping someone fabricating an answer.
    Contrast a spec that asserts something **false**, which actively misleads and must be corrected upstream.
    This one just needed answering, and it has been.
  - **One residual, noted not tracked:** the source Gherkin still reads `[UNKNOWN]`, so a future re-ground
    would meet the same question — but the answer is recorded here and pinned by two green tests, so it is a
    lookup, not a re-investigation.
  - **Status:** CLOSED — answered by evidence and covered by test.

**Verified — not a defect:**

- **Role re-grounded to the new two-group model.** The Gherkin authenticates as `ILCR_LICENSEE`; the new app
  has no such role (ratified `ILCR_ADMIN` + `ILCR_SUBMITTER`, PRD DL-23) and runs security-off with a fixed
  mock authority. Save is authorized (live: `GET /v1/mill-context?millId=12050&year=2017` → 200,
  `message.text = "Data saved successfully"`). Deliberate rename, not a defect (same finding as UC-SCH1-001).
- **Home "Save" writes nothing.** It is a read/resolve (`GET /v1/mill-context`) that sets the client-side
  MillYearContext and returns SUC-001 — no report rows are created, so no teardown is needed and scenarios
  are parallel-safe. A deliberate design (client-side context, AR11), not a defect.
- **Banner → tombstone (bcgov #227) — re-grounded GREEN.** The global working-context ContextBanner was
  removed; the mill/status lines a Home Save establishes now render on each schedule page's
  `ScheduleTombstone` header (same `region[name="Working context"]` landmark + `WorkingContextLines`, so
  the exact text is unchanged). bcgov's `tombstone.spec.ts` (S01 display / S03 switch / S06 closed / S07
  no-status) is ported here as `schedule-tombstone.feature` (on Schedule 2), verified green incl. axe.
  An app design move, not a defect — recorded so the ported source is traceable.
- **VER-1 — the tombstone a11y sweep was FLAKY (≈1 run in 4) and blamed Schedule 2 for BUG-1. Suite
  defect, fixed 2026-08-26; the app is fine.** `@S01 @a11y` failed intermittently with
  `color-contrast` "on Schedule 2 tombstone", on the same two nodes as BUG-1
  (`p:nth-child(1) > .headerUnderline`, `p:nth-child(2) > span`).
  - **What actually happened:** the sweep ran on **Home**, not on Schedule 2. Client-side navigation
    flips the URL before the route's content swaps — while Schedule 2 resolved, the router kept Home
    mounted, so `window.location` already read `/schedule-2` while the DOM was still Home-after-Save.
    `SchedulePage.open()` gated on exactly those two things, and both were satisfied by Home: the URL,
    and a visible `region[name="Working context"]` — which Home's PageTitle-hosted ContextBanner renders
    with the SAME landmark and the SAME `WorkingContextLines` text once a context is saved. The tombstone
    line assertions then passed against Home's banner, and axe scanned Home, where the admin-authored
    welcome message lives.
  - **Proof (from the failing run's trace, not inference):** the axe payload reports
    `environmentData.url = http://localhost:3000/schedule-2` with `fromFrame: false`, yet the scanned DOM
    contained Home's `h1 "Mill and Reporting Year"` and `Administrator Welcome Message` and **no**
    Schedule 2 content (no "Purchased", no "Check Status"). The failing node ancestry
    (`… > div:nth-child(2) > div:nth-child(2) > div > p:nth-child(1) > span`) is byte-identical to the
    `Home (banner populated after Save)` sweep's, and differs from the `Home (landing)` sweep's only by
    the success banner that Save inserts.
  - **Why the existing guard missed it:** the URL check was added for this very trap (PR #5 review — "a
    nav that silently stayed on Home would let the tombstone assertions pass falsely"), but a URL is not
    a rendering guarantee under client-side routing.
  - **Fix:** `pages/common/schedulePage.ts` — gate `open()` on the route-specific tombstone heading
    (`heading[level=1][name="Schedule 2"]`, which the outgoing Home page cannot satisfy), and scope
    `context` to `.schedule-tombstone` so Home's identically-labelled banner can never satisfy a
    tombstone assertion. Verified: the a11y scenario failed **2 of 8** repeats before, and **32 of 32**
    tombstone runs passed after.
  - **Why it matters beyond this scenario:** BUG-1's authored-content contrast is real but belongs to
    **Home**. Any a11y sweep that can scan a stale Home DOM inherits it and reports it against the wrong
    page — which is how a tracked, tagged red turned into an **untagged** one that breaks
    `npm run test:gate` on a page that is actually clean. Readiness anchors for client-side navigation
    must be route-specific, never a shared landmark.
  - **Status:** CLOSED 2026-08-26 (suite fix; no app change, no ticket).
