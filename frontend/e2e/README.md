# <your-app> E2E (BDD — Gherkin + Playwright via playwright-bdd)

Browser-automation end-to-end tests for a BC Gov Natural Resources app, driven against a **running
local stack** (Vite/React frontend → Spring backend → Docker Oracle DB). Self-contained on purpose so
it can live in its own folder or be lifted into the app repo.

This is a **BDD suite**: `.feature` files are the executable spec, `playwright-bdd` (`bddgen test`)
compiles them into native Playwright tests, and a reusable step layer implements the Gherkin against
page objects. Scenarios are re-grounded from each use case's **Gherkin**: that Gherkin defines *what*
each screen must do (authored from the legacy app / the spec); here the routes, fields, and input
values are pinned to the *new* app and the *seeded* DB.

## Layout — organized by domain, then use case

Every artifact lives under its **domain** (a short subject code, e.g. `<DOMAIN>`) and then its **use
case**, so the suite stays navigable as it grows to hundreds of scenarios. The Playwright globs are
recursive, so adding a domain or UC needs no config change.

```
e2e/
  playwright.config.ts   # testDir = defineBddConfig({ features, steps }); baseURL :3000; HTML+JUnit
  features/<domain>/<uc-id-slug>/
    <concern>.feature    # e.g. features/<DOMAIN>/uc-<DOMAIN>-005-create/validation.feature
    coverage.md          # per-UC coverage matrix: source item -> app enforcement -> scenario -> status -> gap
                         #   (how to read one: coverage-guide.md at the e2e root)
    defects.md           # per-UC defect log: findings by register (divergence/bug/coverage-gap/spec-gap/verified)
                         #   (how it works: defects-guide.md at the e2e root)
  coverage-guide.md      # plain-language legend for the coverage.md files (columns, status flags) — BA/QA
  defects-guide.md       # plain-language legend for the defects.md files (registers, tags, how-to-read) — BA/QA
  steps/
    fixtures/            # composition root, SPLIT PER DOMAIN so parallel work doesn't collide
      index.ts           #   mergeTests(...) -> createBdd(test) -> Given/When/Then/expect (import from '../fixtures')
      global.ts          #   cross-domain only: `world` (its `World` type is the union of domain fields),
                         #   homePage, appShell
      <domain>.ts        #   that domain's page objects + cleanup registries + route spies
                         #   Adding a domain = new <domain>.ts + one line in index.ts's mergeTests
    common/*.steps.ts    # cross-domain REUSABLE steps (generic asserts) — no domain vocabulary
    <domain>/*.steps.ts  # domain steps, split by concern (navigation/form/verify); NO DOM selectors
  pages/<domain>/*.ts    # Page Objects (selectors + interactions) — called BY steps
  pages/common/*.ts      # cross-domain browser helpers (mock-auth login, client-side nav, Carbon fields)
  fixtures/<domain>/*-test-data.ts   # known-good test data pinned from the seeded DB (with provenance)
  preflight/anchors.setup.ts   # `setup` project the chromium project depends on: asserts anchors +
                         #   applied-seed presence resolve BEFORE any scenario (fail fast, one clear message)
  scripts/               # apply-patches.sh / teardown-patches.sh (seed patches, auto-discovered);
                         #   docker-sqlplus.sh (stdin filter used BY the seed scripts to run sqlplus inside
                         #     the DB container when no local client; not a standalone drop-in — see its header)
  real-test-data-patches/   # minimal per-domain seed patches (<name>.sql + <name>.teardown.sql) the
                         #   real extract can't supply — applied via scripts/ (see that folder's README)
  .features-gen/         # generated Playwright tests (git-ignored, disposable — never edit/commit)
```

Naming: UC folder = `uc-<domain>-<nnn>-<verb>` (matches the source `UC-<DOMAIN>-<NNN>`), one `.feature`
per concern within it. A step belongs to `steps/<domain>/` if only that domain uses it, or
`steps/common/` if it is genuinely domain-agnostic.

## Prerequisites — bring up the full stack

The steps below describe the typical **BC Gov NR-stack** bring-up (Vite/React frontend → Spring backend →
Docker Oracle DB, with local mock auth). Treat the specific ports, DSN, container name, cache-evict URL,
and env flags as **defaults to adjust for your app** — override them via `.env` (see below).

1. **Seeded Oracle DB — pre-built Docker image + seed patches.** The suite runs against a Docker image
   that already contains the **real extracted test data** — no repo checkout, `docker compose`, or manual
   load step is needed.

   **Step 1 — pull and run the image** (map the Oracle listener to host port **1525**):
   ```bash
   docker run -d --name real-data-seeded-ilcr-db -p 1525:1521 \
     ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded:latest
   ```
   - **Oracle service / user / password** — as published by your image (the scaffold defaults assume
     service `DBDOCK_01`, user/password `THE`/`default`; **JDBC URL**
     `jdbc:oracle:thin:@//localhost:1525/DBDOCK_01`).
   - The image tag and container name may change over time. If your container is named something other
     than the scripts' default (`real-data-seeded-db` — name yours `real-data-seeded-<app>-db`, e.g.
     `real-data-seeded-ilcr-db`), set `DB_CONTAINER` for the sqlplus
     wrapper (see [`.env.example`](.env.example)).

   **Step 2 — apply the seed patches** (once per fresh container; the image does **not** include them).
   These are the minimal seed rows the real extract can't supply (see
   [`real-test-data-patches/`](real-test-data-patches)). 
   
   From **WSL** or a **Git Bash** terminal:
   ```bash
   ./scripts/apply-patches.sh            # tear down again with ./scripts/teardown-patches.sh
   ```
   - **No configuration** — the script auto-detects the client (local `sqlplus`, else your DB container)
     and prints which. Set `DB_CONTAINER` in `.env` if your container isn't the default.
   - **Applying to an already-running backend?** Evict the app's reference-data cache afterward if it
     has one (SCS example: `POST /api/api/internal/cache/evict`) or restart it.
   - Skip this and the seed-dependent scenarios **fail fast in preflight**, with a message telling you to run it.

   **Fixtures & anchors.** The fixtures pin real anchors discovered from this data (record ids, codes,
   keys — each with provenance in its `fixtures/<domain>/*-test-data.ts`). **Re-verify them if the image
   is rebuilt from a fresh extract**, since real data is non-deterministic. The `preflight/` setup fails
   fast with one clear message if an anchor no longer resolves.
2. **Backend** on `:8080` with local mock auth: `security.jwt.enabled=false`, the app's `LOCAL`
   environment flag, JNDI datasource → `localhost:1525/DBDOCK_01`.
   **After loading data into an already-running backend, evict the app's reference-data cache if it has one**
   (SCS example: `POST /api/api/internal/cache/evict`) or restart it — otherwise a startup-warmed cache serves stale
   code lists and create calls 500. (Also: start the DB *before* the backend — the backend's Spring
   context fails to initialize if the Oracle listener isn't up yet, and then every `/api` route 404s.)
3. **Frontend** on `:3000` with `VITE_MOCK_USER=true` (`npm start`). Mock auth auto-logs-in a single
   admin role — no Cognito/login flow needed. (The one app-specific bit — the Landing page's login
   button test-id and route — is a labeled default at the top of `pages/common/authNav.ts`.)

## Install and run

**`.env` is the single config point.** Copy `.env.example` to `.env` and adjust `BASE_URL` (and `ORACLE_DSN` /
`DB_CONTAINER` if you'll run the seed-patch scripts). It's loaded by `playwright.config.ts` (via dotenv) and
auto-sourced by `scripts/apply-patches.sh` / `teardown-patches.sh`.

```bash
cd e2e
cp .env.example .env    # then edit BASE_URL / DB vars for your local stack
npm ci                  # exact versions from the committed package-lock.json
npm test                # everything: regenerates from features (pretest -> bddgen), then runs headless
npm run test:gate       # THE PASS/FAIL GATE — same run, minus the known @discovered-* reds
npm run test:headed     # watch it drive the browser (recommended first run)
npm run bddgen          # just regenerate .features-gen/ from features + steps
npm run report          # open the HTML report
```

**Browser:** by DEFAULT the config uses your **system Google Chrome**, because the CGI proxy blocks the
managed-chromium download. On a box with no system Chrome (fresh WSL/headless), install the managed build
and opt in via `.env`: `npx playwright install chromium` + `E2E_BROWSER_CHANNEL=chromium`.

**`python-oracledb` — only if you run the Schedule 1 scenarios.** A few of them snapshot and restore their
target at the DB (`scripts/sch1_db_restore.py`) because the app's own PUT cannot undo what they do. Set it up
reproducibly with `npm run setup:python` (needs Python 3.9+ on PATH; creates `scripts/.venv` from the pinned
`scripts/requirements.txt`). The runner auto-detects that venv — no `PYTHON` export needed (override with
`PYTHON=/path/to/python`). Every other domain cleans up through the app's own `DELETE` endpoint and needs
none of this. You never configure sqlplus either — the seed-patch scripts auto-detect a client.

### `npm run test:gate` is the gate, not `npm test`

That is the whole point of having two scripts. `npm test` runs EVERY scenario and therefore **exits 1 by
design** whenever a `@discovered-divergence` / `@discovered-bug` red is tracking an open defect — the suite
working as intended, not a broken build. `test:gate` excludes those known reds and exits 0 when nothing new
has broken, so it is the one that is safe to copy-paste and safe to automate.

Which reds exist at any moment is recorded in each UC's `defects.md` (and, when the fix is someone else's,
in `deferred-work.md`) — deliberately not listed here, so this file cannot go stale.

> **Stressing a snapshot/restore scenario?** Pass `--workers=1`. `--repeat-each=N` in parallel does not
> merely go red on the single-owner keys — it can overwrite one repeat's backup with another's
> already-mutated state and restore that as the "baseline", silently drifting the seeded anchor. See the
> ⚠️ note in `features/sch1/uc-sch1-001-enter-save/coverage.md`.

Set `BASE_URL` in `.env` (or inline, `BASE_URL=http://localhost:3001 npm test`) if your ports differ.
Edit `features/` and `steps/` only — `.features-gen/` is regenerated on every `npm test`. An unbound step fails `bddgen`
(before Playwright runs) and names the exact `.feature` line.

### Scenario tags

Every scenario carries two kinds of tag:

- **Priority** — how important the scenario is, used to pick what to run when you can't run the whole suite:

  | Tag | Meaning | Examples |
  |---|---|---|
  | `@p0` | **Critical** — the core happy path must work; if it's red the feature is broken. Runs in every smoke check. | create a record and confirm it persisted |
  | `@p1` | **Important** — key validation, error handling, and required-field rules. | required-field errors, numeric-format errors, server-error handling |
  | `@p2` | **Secondary** — UI niceties and lower-risk branches. | field enable/disable gating, Back-button behavior |

- **Traceability** — `@UC-<DOMAIN>-<NNN>` (the use case) and `@S<NN>` (the source slice), so a scenario
  maps straight back to its Gherkin/spec and to the `coverage.md` matrix.

- **Special handling** — a few tags mark scenarios that aren't a normal pass:

  | Tag | Meaning |
  |---|---|
  | `@discovered-divergence` | **Deliberately RED** — reproduces a divergence (app ≠ legacy spec, suspected defect). Never forced green; logged in the UC's `defects.md` for BA/QA → Jira; flips to green when the app is fixed. |
  | `@discovered-bug` | **Deliberately RED** — reproduces a confirmed bug/regression awaiting a fix (has a Jira ticket). |
  | `@skip` | Genuinely can't be automated yet (e.g. blocked by a single mock admin role) — never used to hide a failure. |

  **Never force green:** a suspected-defect divergence / confirmed bug is a genuinely-failing tagged test,
  never masked with `@skip`, xfail, or a weakened assertion. A failing test does not stop the others
  (Playwright isolates them). For a clean "fresh failures only" pass, use `npm run test:gate` (above).

- **Domain + accessibility** — `@<domain>` (`@sch1`, `@sch11`, `@sec`) scopes a run to one subject area, and
  **every accessibility scenario is `@a11y`**, in every domain. (It is a tag, not the file name: those
  scenarios live in each UC's `accessibility.feature`.)

Filter with Playwright's `--grep` (args after `--` pass through; `pretest` still regenerates first):

```bash
npm test -- --grep @p0                 # smoke: core happy paths only
npm test -- --grep "@p0|@p1"           # smoke + key validation/error handling
npm test -- --grep @UC-<DOMAIN>-005    # one use case
npm test -- --grep @<domain>           # one domain
npm test -- --grep @a11y               # every accessibility sweep (AC4/NFR1)
```

Two things that bite:

> ⚠️ **A bare `npx playwright test` runs the LAST-GENERATED tests, not your current `.feature` files.**
> Only the `npm run …` scripts regenerate first (via their `pretest*` → `bddgen` hooks). Skip `bddgen`
> after editing a feature and you will watch stale scenarios pass under their old names and tags — which
> reads exactly like a successful run. Always go through an npm script, including when scoping by tag.

> ⚠️ **`--grep` is a regex over the test title, not a tag expression.** `--grep "@sch11 and @a11y"` matches
> **nothing** (verified). Combine tags with a regex — `--grep "@sch11.*@a11y"` — or grep a single tag.

At scale you can also filter at *generation* time so only matching scenarios compile:
`npm run bddgen -- --tags @p0` (that regenerates, so the follow-up run is safe).

## CI / manual gate — delivery-DB verification

**This suite is a documented MANUAL gate, not a default CI job.** CI has **no delivery-DB access** (no Oracle
in `docker-compose`; the app's own fixtures are Testcontainers-only), and the suite needs the running
two-process stack plus the seeded Oracle. So run it locally per *Prerequisites*, gate on
**`npm run test:gate`** (see *Install and run* for why that and not `npm test`), and record the run + the HTML
report (`playwright-report/`) as the TEST-review evidence.

If a pipeline ever gains delivery-DB access, wire the full data-backed suite (the `chromium` project) behind
an **env-guarded opt-in job** so it can never run without live data — keep it off the default path.

**What IS safe to run on every PR** is the data-independent `@smoke` project: no `setup`/seeded-DB dependency
and all `/api` aborted, so it guards against a frontend-only deploy with nothing but the frontend served
(see *Notes*).

Re-verify the pinned anchors after any DB re-extract — `preflight/` fails fast, with one actionable message,
if one has drifted.

## Oracle MCP server (data discovery / DB assertions) — optional

An Oracle MCP server (thin-mode `mcp-oracle`, tools `list_tables` / `describe_table` / `run_query`)
lets the test author query the seeded DB directly — to pin known-good anchor values and confirm writes
landed. It is an **authoring/verification** aid, not a test-runtime dependency — the specs stay pure-UI
and portable. Register it project-scoped (e.g. a repo `.mcp.json` for Claude Code) launched portably as
`python -m mcp_oracle`; install once with `python -m pip install mcp-oracle oracledb`, then trust the
project. Point `ORACLE_DSN` / `ORACLE_USER` / `ORACLE_PASSWORD` at your seeded DB (defaults match the
`localhost:1525/DBDOCK_01` `THE`/`default` dev DB — throwaway local creds, not secrets).

> Prereq for the actual queries: the seeded Oracle Docker DB must be up (see step 1 of *Prerequisites*).

## Notes

- **Self-cleaning (fails loud):** a step that creates a record registers its id and the shared fixture
  deletes it on teardown, so each scenario leaves the seeded DB as it found it. Residue **throws**
  rather than silently polluting the shared seed. Two cleanup paths by flow:
  - Resources with a **DELETE endpoint** → registry → `DELETE /api/<your-resource>/{id}`; a `404` is
    treated as already-gone.
  - Resources with **no DELETE endpoint** → delete the row directly at the DB via `sqlplus`
    (`steps/<domain>/*DbCleanup.ts`; DSN + binary env-overridable), then read back through the API to
    prove it's gone (don't trust the delete blindly).
- **Reusable steps:** add a `Given/When/Then` only when no existing phrase fits; repeated behavior is a
  `Scenario Outline` with an `Examples:` table. Selectors live in the page object, never in steps.
- **Tags & coverage:** every scenario is tagged by priority + UC/slice (see *Scenario tags*), and each
  UC folder carries a `coverage.md` matrix mapping every source item to the scenario that covers it (or
  a logged gap) — the durable audit trail behind a green run.
- Permission-gated scenarios (e.g. "no Delete without the delete permission") can't be expressed with a
  single mock admin role yet — a `@skip` scenario documents this; deferred.
- **Data-independent CI smoke (`@smoke`):** the `smoke` Playwright project runs the app-shell smoke
  (`features/shell/app-shell.feature`) with NO `setup`/seeded-DB dependency and all `/api` aborted, so it
  guards every PR against a frontend-only deploy — the BDD equivalent of the app repo's
  `frontend/e2e/app-shell.spec.ts`. Run it alone with **`npm run bddgen && npx playwright test
  --project=smoke`** (only the frontend need be served; no Oracle — and the `bddgen` first for the reason
  in *Scenario tags*). Wire THIS command into the per-PR CI job; gate the full data-backed suite
  (`chromium` project, needs the seeded delivery DB) behind an opt-in/live-data job — see
  *CI / manual gate — delivery-DB verification*.

## Seeded database image — how it's built and refreshed

The DB you run in step 1 is a **pre-built Oracle image preloaded with real data extracted from a dev/test
system** (not a synthetic seed). Developers just pull and run it; a maintainer rebuilds it periodically.
The process:

1. **Extract** real data from a dev/test source DB with your app's extract tooling. The extract is
   typically **one FK hop**, so some referential gaps are expected (a child row whose parent wasn't
   pulled) — a live `INSERT` still enforces FKs, so a create can fail on a *data gap*, not a bug.
   *(ILCR extracts via `ilcr-data-extract` / `extract.sql`.)*
2. **Load** the extract into a base Oracle Free container running locally (the SCS image publishes service
   `DBDOCK_01`, user/password `THE`/`default`).
3. **Snapshot** the loaded container into a tagged image and **push** it to the team packages registry
   **(run the `docker` commands one at a time)**:
   ```bash
   SEEDED_NAME=<your-seeded-local-container-name>
   DEST_IMAGE=ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded

   docker stop -t 120 "$SEEDED_NAME"                          # clean checkpoint BEFORE commit (avoids a fuzzy snapshot)
   docker commit "$SEEDED_NAME" "$DEST_IMAGE:latest"          # may take a few minutes
   docker tag "$DEST_IMAGE:latest" "$DEST_IMAGE:$(date +%F)"  # dated tag pins this known-good snapshot
   docker push "$DEST_IMAGE:latest"
   docker push "$DEST_IMAGE:$(date +%F)"
   ```
   Restart the local container afterward (`docker start "$SEEDED_NAME"`) if you still need it running.
   *(ilcr image: `ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded`.)*
4. **Consume**: developers `docker run -p 1525:1521 <image>` (step 1) and apply the seed patches
   per-container (step 2). Patches are **not** baked into the image — re-apply them on each fresh
   container, or re-snapshot *with* them to bake them in.

**After any re-extract the real data changes**, so **re-verify the pinned fixtures and any seed patches** —
treat a re-extract as a re-ground event (sweep `fixtures/**` and any hardcoded ids in the ledgers). The
`preflight/` setup fails fast if a pinned anchor no longer resolves.

**Windows/WSL note:** building or loading a large Oracle image inflates the WSL virtual disk (`.vhdx`).
Set your disks to sparse, so `wsl --shutdown` can reclaim the space back to your `C:` drive.
