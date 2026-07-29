# NR ILCR Backend

## Local Run Profile

This project keeps DB wiring off by default for shared developer safety.
Use the `local` Spring profile to enable JDBC repositories and datasource wiring for local runs.

- Base default in `src/main/resources/application.yml`: `ilcr.datasource.enabled` resolves to
  `${ILCR_DATASOURCE_ENABLED:false}` (off unless the env var is set).
- Local overrides in `src/main/resources/application-local.properties`:
  - `ilcr.datasource.enabled=true`
  - `spring.data.jdbc.repositories.enabled=true`

You still need to supply the datasource connection via environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Windows (PowerShell)

```powershell
cd "C:\Users\greg.pascucci\Apps\NRM\ilcr-bmad\nr-ilcr\backend"
$env:SPRING_DATASOURCE_URL = 'jdbc:oracle:thin:@...'
$env:SPRING_DATASOURCE_USERNAME = '...'
$env:SPRING_DATASOURCE_PASSWORD = '...'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

`$env:NAME = 'value'` sets the variable for the current PowerShell session only. Open a new
window and you'll need to set them again (or use the `.env` approach below).

### macOS / Linux (bash)

```bash
cd "/c/Users/greg.pascucci/Apps/NRM/ilcr-bmad/nr-ilcr/backend"
export SPRING_DATASOURCE_URL='jdbc:oracle:thin:@...'
export SPRING_DATASOURCE_USERNAME='...'
export SPRING_DATASOURCE_PASSWORD='...'
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## About `.env`

Spring Boot does **not** load a `.env` file by default.
It reads OS environment variables and JVM/system properties unless additional tooling is added.

If you want `.env` values to apply, load them into your shell before running Maven.

### Windows (PowerShell)

```powershell
Get-Content .env | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
}
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### macOS / Linux (bash)

```bash
set -a
source .env
set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```
