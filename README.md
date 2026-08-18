# NR ILCR Modernization Template

This repository is the local-development scaffold for rebuilding ILCR as a React frontend and Spring Boot backend. It replaces the generated TypeScript backend with a Java 21 Spring Boot executable JAR while keeping the React/Vite frontend and deployment package layout.

## Stack

- Frontend: React, TypeScript, Vite, TanStack Router, Carbon, BC Gov NR theme
- Backend: Java 21, Spring Boot 4.0.6, Maven, executable JVM JAR, JDBC/Hikari Oracle integration
- Local platform: direct Maven/npm runs or Docker Compose with backend, frontend, optional Caddy, and sanitized Oracle env wiring
- Target platform: OpenShift Gold using `backend` and `frontend` deployment packages. Gold is the only intended OpenShift target, but it is not required for current local development.

## Project Layout

```text
backend/     Spring Boot API scaffold
frontend/    React/Vite web app
common/      Shared integration, E2E, and load tests
monitoring/  Observability configuration
```

## Local Development

Prerequisites:

- JDK 21
- Maven 3.9+
- Node 24+
- Docker or Podman for the full local stack
- Optional Oracle dev DB credentials in a local, ignored `.env`

Run the Spring Boot backend directly:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "local"
mvn spring-boot:run
```

The backend listens on `http://localhost:8080` by default.

- Health: `GET /api/health`
- Schedule 1 API: `GET /api/v1/schedule1`

Run the frontend directly against the local backend:

```powershell
cd frontend
npm ci
$env:BACKEND_URL = "http://localhost:8080"
npm run dev
```

The frontend shell includes the default NRS Carbon layout, light/dark theme toggle, side navigation, and a top-right mock user selector. The scaffold personas are:

- Alex Admin: `ILCR_ADMIN`
- Sam Submitter: `ILCR_SUBMITTER`
- Casey Dual Role: `ILCR_ADMIN`, `ILCR_SUBMITTER`

Run the full local stack:

```powershell
copy .env.example .env
docker compose up --build backend frontend
```

The frontend is available at `http://localhost:3000`. In compose, the backend is mapped to `http://localhost:8080` and runs inside the container on port `8080`.

The default backend runtime is secure and Oracle-required; the explicit `local` profile opts out so repository work can start without forcing every local backend boot to validate a database connection.

To validate Oracle on backend startup, put local values in ignored `.env` and set `ILCR_DATASOURCE_ENABLED=true` for Docker Compose:

```powershell
SPRING_DATASOURCE_URL=jdbc:oracle:thin:@//<host>:1521/<service-name>
SPRING_DATASOURCE_USERNAME=<username>
SPRING_DATASOURCE_PASSWORD=<password>
ILCR_DATASOURCE_ENABLED=true
```

For direct Maven runs, the `oracle` Spring profile also enables the datasource:

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "local,oracle"
mvn spring-boot:run
```

Do not commit real database passwords. Put local values in `.env`; the file is git-ignored.

### Corporate SSL/TLS Intercept & Certificate Issues (e.g., Zscaler / PKIX) — Local Workaround

*Note: This is a specific workaround for developers behind a corporate SSL-decryption/packet-inspection gateway (such as Zscaler) and is **not** required for all developers (e.g., if you are on a direct internet connection).*

If your corporate network performs SSL decryption/packet-inspection, Maven inside the isolated Docker container may fail to connect to Maven Central (all dependencies, including JasperReports 7, resolve from Central — the build declares no custom `<repositories>`) with a `PKIX path building failed` error.

The recommended, zero-import workaround is to leverage your Windows host's trusted certificate store by caching the dependencies on Windows once, and mounting your host's `.m2` repository into the container:

1. **Seed the cache on Windows**:
   Run this once inside your Windows terminal to download and cache the libraries (which automatically trusts your corporate certificate):
   ```powershell
   cd backend
   mvn clean install -DskipTests
   ```
2. **Mount the cache in your local environment**:
   Set the `M2_HOME` variable inside your local, ignored `.env` file pointing to your host's `.m2` directory:
   ```properties
   M2_HOME=/mnt/c/Users/<your-username>/.m2
   ```
   Docker Compose will automatically detect this variable and mount your local Windows Maven cache into the container's `/root/.m2` path, bypassing the certificate handshake issues completely!

### Authentication (FAM/Cognito) — local testing

The SPA has two auth modes, selected at runtime by `public/amplify-config.js` (loaded before the
bundle). The repo default is **mock**; deployed environments mount a per-env ConfigMap over it. See
`src/context/auth/` (the `AuthProvider` seam) and `src/config/auth/amplify-initializer.ts`.

**Mock mode (default — no Cognito).** `npm run dev` with the backend running (security off by
default) signs you in automatically. Use the **"Mock user"** dropdown in the header to switch
`ILCR_ADMIN` ↔ `ILCR_SUBMITTER` — it switches both the nav/route-guards **and** the backend mock
principal (via the `X-Mock-Groups` header), so it exercises role gating end to end. This is the
fastest path for manual testing.

**Real FAM/Cognito login (Hosted UI).**

1. Frontend — copy the example config over the default (do **not** commit it; the repo default must
   stay `mockUser: true`):
   ```bash
   cd frontend
   cp amplify-config.local.example.js public/amplify-config.js
   npm run dev            # then hard-refresh the browser (public/ files load at page load)
   ```
2. Backend — run with security on so `/api/v1/me` validates the real ID token:
   ```bash
   cd backend
   ILCR_SECURITY_ENABLED=true COGNITO_REGION=ca-central-1 \
   COGNITO_USER_POOL=ca-central-1_UpeAqsYt4 COGNITO_CLIENT_ID=352pis0ark86dam7ht1jlp9uj5 \
   SPRING_PROFILES_ACTIVE=oracle,openshift ./mvnw spring-boot:run
   ```
3. Open `http://localhost:3000` → FAM Hosted UI → sign in (IDIR/BCeID) → back to the app with your
   real role. Confirm the exact `cognitoDomain` with the FAM admin if the Hosted UI does not load.

**Dev-only testing aids (real session, local dev only — `import.meta.env.DEV`, tree-shaken from every
deployed build):**

- **"View as (dev)"** header dropdown — overrides the role the SPA uses (nav + route guards) so you
  can test both roles without re-logging-in. It is **frontend-only**: the backend still enforces your
  real token, so admin APIs still `403` if your account isn't really in that group.
- A **"viewing as" warning banner** appears whenever an override is active, naming your real role.
- A **Sign out** button (header, Logout icon) runs the Cognito/loginproxy logout chain on a real
  session; hidden in mock mode.

When done with real login: `git checkout -- frontend/public/amplify-config.js`.

## Frontend Shared Conventions

Reusable building blocks and global styles that new schedule/feature pages should adopt rather than
re-implement (paths under `frontend/src`):

- **Schedule tombstone header** — `components/core/ScheduleTombstone`. A two-column page header: left
  is the page identity (`title` + a `subtitle` sub-page label), right is the working-context
  mill/status lines. It replaces the old `PageTitle` header on schedule pages and owns the
  `document.title` side effect. `subtitle` accepts a string or a `string[]` rendered breadcrumb-style,
  so deeper sub-pages can thread their level (e.g. `["Report Tree to Truck Costs", "License", "Sample"]`).
- **Working context** — `components/core/WorkingContext`. `useWorkingContext(millId, year)` fetches
  `GET /v1/mill-context` with the stale-response guards; `WorkingContextLines` renders the three legacy
  lines (`Mill: … - Year: …`, `Sch 1-10 …`, `Sch 11 …`). Both the tombstone and any future banner reuse
  these. The former global `ContextBanner` was removed from `Layout` — mill/status now renders once, in
  the tombstone header.
- **Currency formatting** — `fmtCurrency` in `utils/number.ts`. Thousands-separated with two decimals
  (`1234.5 → "1,234.50"`, `null → "—"`), no `$` sign (the column header carries the unit). Use it for
  `$/m³`/rate and other currency read-only cells; keep `fmt` for plain integer/quantity cells.
- **Footer** — `components/Layout/Footer`, rendered once by `Layout` on every route. Shows the app
  version (left) and the BC Gov copyright/disclaimer/privacy/accessibility links (centred). The version
  comes from `__APP_VERSION__`, inlined from `package.json` by a Vite `define` (see `vite.config.ts`;
  typed in `src/vite-env.d.ts`) — reuse that global for any other build-time constant.
- **Global styles** (`styles/_overrides.scss`, `styles/_custom.scss`) — all Carbon buttons are 40px
  high across size variants (via the `--cds-layout-size-height-local` token, so labels stay centred);
  text areas share the same light-grey field background (`#f4f4f4`) as the text inputs.

## Git Ignore Policy

The repository tracks source, deploy templates, and safe examples such as `.env.example`. It ignores local secrets, certificates, build outputs, dependency folders, coverage reports, Playwright reports, Maven `target/`, and Vite `dist/`.

If a local setting is needed by the team, add a sanitized example to `.env.example` or this README instead of committing a developer-specific `.env`.

## Backend Notes

The backend follows the proven CSP-style JVM deployment path: Spring Boot 4, executable JAR, JDBC/Hikari for Oracle access, Log4j2 logging, actuator health, Maven verification, and CycloneDX SBOM generation. Graal/native-image support is intentionally not part of this scaffold.

FAM authentication is tracked separately. The dashboard currently displays the selected local mock principal only; do not add a parallel users API or auth model in this scaffold. Align route protection, token handling, principal hydration, and role checks with the FAM integration plan before securing feature endpoints.

## OpenShift Status

OpenShift Gold is the destination environment, but the Gold project is not required for this local-dev scaffold. Pull requests always deploy a sandbox environment (zone = PR number mod 50). Merges to `main` deploy to TEST and then, if tests pass, to PROD in the same workflow run — but only while the `ENABLE_OPENSHIFT_DEPLOY` repository variable is `true`; it is left unset until the code is ready for those environments.

Deployed pods fail closed on authentication: JWT enforcement (`ILCR_SECURITY_ENABLED`) and the Oracle datasource (`ILCR_DATASOURCE_ENABLED`) both default to `true` and can be overridden per scope with GitHub variables (environment-first, then repository). The backend refuses to start a deployed pod with security off while the datasource is on (`DeployedSecurityGuard`), so mock auth can never serve real data from a public route; setting both variables to `false` yields a data-less mock-auth smoke deployment.

## Verification

Backend:

```powershell
cd backend
mvn verify
```

Frontend:

```powershell
cd frontend
npm ci
npm run lint
npm run test:cov
npm run build
```

End-to-end tests live in the self-contained `frontend/e2e` package, so they have their own
`node_modules` and are not installed by the `frontend` `npm ci` above. Install them once, then run:

```powershell
cd frontend/e2e
npm ci
npm test          # equivalently, from frontend/: npm run test:e2e
```

`npm run test:e2e` (which shells out to the `e2e` package) requires the full running stack —
frontend `:3000`, backend `:8080`, and the seeded Oracle DB — plus a browser channel. See
[`frontend/e2e/README.md`](frontend/e2e/README.md) for the bring-up. Playwright runs against the local
Vite app by default; set `E2E_BASE_URL` only when intentionally testing a deployed route.
