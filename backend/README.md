# NR ILCR Backend

Spring Boot 4.1 / Java 21 REST API for ILCR. See the [root README](../README.md) for the stack overview and the
local frontend setup.

## Local Run

The backend starts with **no database and security off** by default, so it boots without credentials:

| Setting (env var) | Default | Effect |
|---|---|---|
| `ILCR_DATASOURCE_ENABLED` | `false` | `true` wires the Hikari datasources and Spring Data JDBC repositories |
| `ILCR_SECURITY_ENABLED` | `false` | `true` validates FAM/Cognito ID tokens; `false` builds a mock principal from the `X-Mock-Groups` header |
| `SPRING_PROFILES_ACTIVE` | none (`.env.example` and Compose set `local`, which has no profile-specific settings) | `oracle` composes a TCPS URL from `ORACLEDB_HOST`/`ORACLEDB_PORT`/`ORACLEDB_SERVICENAME` and turns the datasource on; `openshift` adds the Cognito issuer settings |

To run against an Oracle database with a plain JDBC URL (for example the seeded local image on port 1525), set:

- `ILCR_DATASOURCE_ENABLED=true`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Windows (PowerShell)

```powershell
cd backend
$env:ILCR_DATASOURCE_ENABLED = 'true'
$env:SPRING_DATASOURCE_URL = 'jdbc:oracle:thin:@//localhost:1525/DBDOCK_01'
$env:SPRING_DATASOURCE_USERNAME = '...'
$env:SPRING_DATASOURCE_PASSWORD = '...'
.\mvnw.cmd spring-boot:run
```

`$env:NAME = 'value'` sets the variable for the current PowerShell session only. Open a new window and you will
need to set them again, or use the `.env` approach below.

### macOS / Linux (bash)

```bash
cd backend
export ILCR_DATASOURCE_ENABLED=true
export SPRING_DATASOURCE_URL='jdbc:oracle:thin:@//localhost:1525/DBDOCK_01'
export SPRING_DATASOURCE_USERNAME='...'
export SPRING_DATASOURCE_PASSWORD='...'
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`; health is at `/api/health`.

## About `.env`

Spring Boot does **not** load a `.env` file by default. It reads OS environment variables and JVM/system
properties. To apply the values from the repository-root `.env` (copied from `.env.example`), load them into
your shell before running Maven.

### Windows (PowerShell)

```powershell
Get-Content ..\.env | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
}
.\mvnw.cmd spring-boot:run
```

### macOS / Linux (bash)

```bash
set -a
source ../.env
set +a
./mvnw spring-boot:run
```

## Tests

| Command | Runs |
|---|---|
| `./mvnw verify` | Unit tests, JaCoCo, Checkstyle, Spotless. **Integration tests are skipped** |
| `./mvnw verify -P integration-test` | Integration tests (`*IT`) only, against a Testcontainers Oracle Free (needs Docker) |
| `./mvnw verify -P all-tests` | Both |
| `./mvnw verify -P integration-test -Dit.test=<ClassName>IT -DfailIfNoSpecifiedTests=false` | One integration test class |

Run `./mvnw spotless:apply` before verifying; `spotless:check` runs at `validate` and fails the build on
formatting.

The integration tests build their database with Flyway from `src/test/resources/db/`. Its
[`README.md`](src/test/resources/db/README.md) explains the fixture conventions: `R__` repeatable seeds,
DDL-only `V*` files, and per-track id blocks. Flyway is a test-scope dependency; the application itself runs no
DDL.
