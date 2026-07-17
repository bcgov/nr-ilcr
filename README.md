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
mvn spring-boot:run
```

The backend listens on `http://localhost:8080` by default.

- Service root: `GET /api`
- Health: `GET /api/health`
- Temporary scaffold users API: `GET /api/v1/users`, `GET /api/v1/users/{id}`

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

The frontend is available at `http://localhost:3000`. In compose, the backend is mapped to `http://localhost:3001` and runs inside the container on port `8080`.

The current `/api/v1/users` scaffold is read-only in memory. Oracle is available as an opt-in local datasource so repository work can start without forcing every backend boot to validate a database connection.

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

## Git Ignore Policy

The repository tracks source, deploy templates, and safe examples such as `.env.example`. It ignores local secrets, certificates, build outputs, dependency folders, coverage reports, Playwright reports, Maven `target/`, and Vite `dist/`.

If a local setting is needed by the team, add a sanitized example to `.env.example` or this README instead of committing a developer-specific `.env`.

## Backend Notes

The backend follows the proven CSP-style JVM deployment path: Spring Boot 4, executable JAR, JDBC/Hikari for Oracle access, Log4j2 logging, actuator health, Maven verification, and CycloneDX SBOM generation. Graal/native-image support is intentionally not part of this scaffold.

The `/api/v1/users` controller is intentionally temporary and read-only. It preserves a simple frontend and integration-test contract so the team can start wiring real ILCR slices without exposing mutable unauthenticated demo state. Replace it with domain endpoints as ILCR features are implemented.

FAM authentication is tracked separately. Do not add a parallel auth model in this scaffold; align route protection, token handling, and role checks with the FAM integration plan before securing feature endpoints.

## OpenShift Status

OpenShift Gold is the destination environment, but the Gold project is not required for this local-dev scaffold. PR and merge deployment workflows are gated behind the `ENABLE_OPENSHIFT_DEPLOY` repository variable. Leave it unset until Gold routes, namespaces, credentials, and validation checks are ready.

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
npm run test:e2e
```

Run Playwright against the local Vite app by default. Set `E2E_BASE_URL` only when intentionally testing a deployed route.
