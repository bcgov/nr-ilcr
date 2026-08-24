#!/usr/bin/env bash
# ============================================================================
# Apply the E2E real-test-data seed patches to the local Docker DB.
#
# WHAT IT DOES: runs every `<domain>/*.sql` patch (excluding `*.teardown.sql`)
# under ../real-test-data-patches/. These are the minimal, idempotent seed
# rows the E2E suite needs but the real extract can't supply (see that folder's
# README). They are NOT baked into the DB image, so a fresh container needs them.
#
# AUTO-DISCOVERY: patches are found by convention, so adding a new patch that
# follows `real-test-data-patches/<domain>/<name>.sql` (+ a matching
# `<name>.teardown.sql`) is picked up here with NO edit to this script.
#
# SETUP: nothing to configure. The script AUTO-SELECTS the sqlplus client — a local `sqlplus` on PATH
# if you have one, otherwise the bundled Docker wrapper that runs sqlplus inside your DB container
# (it prints which it chose). Config comes from .env / the environment; defaults match the local seeded DB:
#     ORACLE_DSN    (default THE/default@localhost:1525/DBDOCK_01)
#     DB_CONTAINER  (default real-data-seeded-db — only used by the Docker fallback)
#     SQLPLUS       (advanced: force a specific sqlplus command, bypassing auto-detect)
#
#   Usage:  ./scripts/apply-patches.sh
# ============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCHES_DIR="$HERE/../real-test-data-patches"

# Load .env from the e2e root if present, so ORACLE_DSN / DB_CONTAINER live in one place (standard
# `set -a; . .env` sourcing; the ${VAR:-default} defaults below fill anything .env doesn't set).
ENV_FILE="$HERE/../.env"
# shellcheck disable=SC1090
[ -f "$ENV_FILE" ] && { set -a; . "$ENV_FILE"; set +a; }

ORACLE_DSN="${ORACLE_DSN:-THE/default@localhost:1525/DBDOCK_01}"

# The DSN is sent to sqlplus via a CONNECT command on stdin (see the run loop below) with `/nolog`, so
# the password is NEVER passed as a command-line argument and can't be read from the process list
# (`ps`). For the log line we still print a masked copy (user/pw@host -> user/***@host); the lone
# residual is cosmetic — this sed under-masks a password that itself contains '/' or '@'.
ORACLE_DSN_MASKED="$(printf '%s' "$ORACLE_DSN" | sed 's#/[^@/]*@#/***@#')"

# Auto-select the sqlplus client: an explicit $SQLPLUS override, else a local `sqlplus`, else the Docker wrapper.
DB_CONTAINER="${DB_CONTAINER:-real-data-seeded-db}"; export DB_CONTAINER
if [ -n "${SQLPLUS:-}" ]; then
  echo "Using SQLPLUS override: $SQLPLUS"
elif command -v sqlplus >/dev/null 2>&1; then
  SQLPLUS="sqlplus"
  echo "Using local sqlplus."
elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
  SQLPLUS="$HERE/docker-sqlplus.sh"
  echo "No local sqlplus found -> running via Docker container '$DB_CONTAINER'."
else
  echo "ERROR: no local 'sqlplus' on PATH and no running Docker container named '$DB_CONTAINER'." >&2
  echo "  Fix one of: install an Oracle client (sqlplus), or start your seeded-DB container" >&2
  echo "  (set DB_CONTAINER in .env if it's named differently)." >&2
  exit 1
fi

shopt -s nullglob

echo "Applying E2E seed patches"
echo "  from: $PATCHES_DIR"
echo "  DSN:  $ORACLE_DSN_MASKED   (SQLPLUS=$SQLPLUS)"

found=0
for patch in "$PATCHES_DIR"/*/*.sql; do
  case "$patch" in *.teardown.sql) continue ;; esac
  found=1
  rel="$(basename "$(dirname "$patch")")/$(basename "$patch")"
  echo ""
  echo "-> $rel"
  # Pipe the file CONTENT (not @file) so this works identically for a local sqlplus and for
  # docker-sqlplus.sh (docker exec can't read a host path). Connect via `/nolog` + a CONNECT command on
  # stdin so the password (in $ORACLE_DSN) never lands in argv/`ps`. `SET ECHO OFF` first so the CONNECT
  # line is never echoed into the log (belt-and-braces against a patch that flips `SET ECHO ON`).
  # WHENEVER SQLERROR EXIT (armed before CONNECT) aborts on a failed connect or any SQL error; pipefail
  # + set -e then stop the run instead of silently continuing.
  # MSYS2_ARG_CONV_EXCL='*': on Git Bash (a documented way to run this — see e2e/README.md) MSYS
  # rewrites any argument that looks like a POSIX path, so a bare `/nolog` reaches sqlplus as
  # `C:/Program Files/Git/nolog` and it prints its usage banner instead of connecting. Excluding
  # argument conversion for this one call keeps the same command working on Git Bash, WSL and Linux.
  { printf 'SET ECHO OFF\nWHENEVER SQLERROR EXIT SQL.SQLCODE\nCONNECT %s\n' "$ORACLE_DSN"; \
    cat "$patch"; printf '\nEXIT\n'; } \
    | MSYS2_ARG_CONV_EXCL='*' "$SQLPLUS" -S /nolog | sed 's/^/    /'
done

if [ "$found" -eq 0 ]; then
  echo "No patches found under $PATCHES_DIR — nothing to apply."
  exit 0
fi

echo ""
echo "Done. If the backend was ALREADY running when you applied these, evict its"
echo "reference-data cache so it doesn't serve stale codes (or restart the backend):"
echo "    # SCS example — replace with your app's cache-evict endpoint if it has one:"
echo "    curl -s -X POST http://localhost:8080/api/api/internal/cache/evict"
echo "  Start the DB BEFORE the backend on a cold boot."
