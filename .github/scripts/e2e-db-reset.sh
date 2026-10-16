#!/usr/bin/env bash
# Resets the tools-namespace e2e Oracle (PDB ILCR) for reusable-e2e.yml. Needs
# OC_NAMESPACE, NAME, DB_USER, DB_PASS and the tools-oracle port-forward.
#
# Flyway over the port-forward pays ~50 ms per statement (~4.5 min), so after a
# Flyway reset the seeded PDB is kept as snapshot E2E_<key>, and later runs
# clone it back inside the pod. The key hashes the migrations, the pom parts
# that shape them, and this script. Snapshots expire after 24h, only one is
# kept (Oracle Free caps PDBs), and any restore failure falls back to Flyway.
set -euo pipefail

: "${OC_NAMESPACE:?}" "${NAME:?}" "${DB_USER:?}" "${DB_PASS:?}" "${GITHUB_REPOSITORY:?}"

# Only the throwaway e2e Oracle in the tools namespace, built from database/.
# The NRS dev/test/prod databases are outside OpenShift and never reachable here.
image=$(oc -n "$OC_NAMESPACE" get deploy "$NAME-tools-database" -o jsonpath='{.spec.template.spec.containers[0].image}')
if [[ "$OC_NAMESPACE" != *-tools || "$image" != "ghcr.io/$GITHUB_REPOSITORY/database"[:@]* ]]; then
  echo "::error::Target is not the throwaway e2e database; refusing to reset it."
  exit 1
fi

SEED_MAX_AGE_HOURS=24
# Oracle-managed files under the image's datafile root: no path mapping needed.
FILE_DEST=/opt/oracle/oradata

key=$( {
  find backend/src/test/resources/db backend/src/test/resources/db-e2e -type f -print0 \
    | LC_ALL=C sort -z | xargs -0 sha256sum
  sed -n -e '/<parent>/,/<\/parent>/p' -e '/<properties>/,/<\/properties>/p' \
    -e '/<id>e2e-db<\/id>/,/<\/profile>/p' backend/pom.xml
  sha256sum "$0"
} | sha256sum | cut -c1-16 | tr a-f A-F)
SEED="E2E_$key"

# SQL on stdin, run as SYSDBA in the database pod; any error fails the call.
# DEFINE OFF stops an '&' (say, in a password) acting as a substitution variable.
sql() {
  { echo "WHENEVER SQLERROR EXIT FAILURE"
    echo "SET DEFINE OFF"
    cat
    echo "EXIT"
  } | oc -n "$OC_NAMESPACE" exec -i "deploy/$NAME-tools-database" -- sqlplus -S -L / as sysdba
}
run() { printf '%s\n' "$@" | sql; }
query() {
  printf '%s\n' "SET HEADING OFF FEEDBACK OFF PAGESIZE 0 LINESIZE 32767" "$1" | sql | tr -d '[:space:]'
}
# MOUNTED, READONLY, READWRITE, or empty when the PDB does not exist.
pdb_mode() { query "SELECT open_mode FROM v\$pdbs WHERE name = '$1';"; }

close_pdb() {
  local mode
  mode=$(pdb_mode "$1") || return 1
  if [ -n "$mode" ] && [ "$mode" != MOUNTED ]; then
    run "ALTER PLUGGABLE DATABASE $1 CLOSE IMMEDIATE;" || return 1
  fi
}

# ILCR open read-write, recreated empty if a failed clone left it missing.
ensure_ilcr() {
  local mode pw
  mode=$(pdb_mode ILCR)
  case "$mode" in
    READWRITE) ;;
    "")
      echo "PDB ILCR is missing; recreating it empty from PDB\$SEED."
      # The PDB admin is never used.
      pw="P$(openssl rand -hex 15)"
      echo "::add-mask::$pw"
      run "CREATE PLUGGABLE DATABASE ILCR ADMIN USER PDBADMIN IDENTIFIED BY \"$pw\"" \
          "  CREATE_FILE_DEST = '$FILE_DEST'" \
          "  DEFAULT TABLESPACE USERS DATAFILE SIZE 10M AUTOEXTEND ON NEXT 10M MAXSIZE UNLIMITED;" \
          "ALTER PLUGGABLE DATABASE ILCR OPEN READ WRITE;" \
          "ALTER PLUGGABLE DATABASE ILCR SAVE STATE;"
      ;;
    *)
      close_pdb ILCR
      run "ALTER PLUGGABLE DATABASE ILCR OPEN READ WRITE;"
      ;;
  esac
}

# Set the app user's password to the Secret's exact bytes. The image's boot
# script can mangle passwords with special characters, and a snapshot may hold
# an older one. Unlocking clears lockouts from earlier mismatches. The password
# travels over stdin, never argv.
ensure_user() {
  local pass_sql="${DB_PASS//\"/\"\"}"
  # sqlplus can echo a failing line; mask the escaped form too.
  [ "$pass_sql" = "$DB_PASS" ] || echo "::add-mask::$pass_sql"
  run "ALTER SESSION SET CONTAINER=ILCR;" \
      "CREATE USER IF NOT EXISTS $DB_USER IDENTIFIED BY \"$pass_sql\" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;" \
      "GRANT DB_DEVELOPER_ROLE TO $DB_USER;" \
      "ALTER USER $DB_USER IDENTIFIED BY \"$pass_sql\" ACCOUNT UNLOCK;" || return 1
  echo "App user $DB_USER ensured in PDB ILCR."
}

# Clone the snapshot over ILCR. Errors are checked by hand because this runs as
# an `if` condition, where set -e does not apply.
restore_snapshot() {
  if [ "$(pdb_mode "$SEED")" != READONLY ]; then
    close_pdb "$SEED" || return 1
    # A clone source must be read-only: the CDB runs in NOARCHIVELOG mode.
    run "ALTER PLUGGABLE DATABASE $SEED OPEN READ ONLY;" || return 1
  fi
  close_pdb ILCR || return 1
  run "DROP PLUGGABLE DATABASE ILCR INCLUDING DATAFILES;" \
      "CREATE PLUGGABLE DATABASE ILCR FROM $SEED CREATE_FILE_DEST = '$FILE_DEST';" \
      "ALTER PLUGGABLE DATABASE ILCR OPEN READ WRITE;" \
      "ALTER PLUGGABLE DATABASE ILCR SAVE STATE;" || return 1
  close_pdb "$SEED" || true
}

# Snapshot the freshly migrated ILCR as $SEED, replacing any older snapshot.
# ILCR goes read-only for the clone and is always reopened read-write.
take_snapshot() {
  local old status=0
  old=$(query "SELECT LISTAGG(name, ',') FROM v\$pdbs WHERE name LIKE 'E2E!_%' ESCAPE '!';") || return 1
  for s in ${old//,/ }; do
    close_pdb "$s" || return 1
    run "DROP PLUGGABLE DATABASE $s INCLUDING DATAFILES;" || return 1
  done
  close_pdb ILCR || return 1
  run "ALTER PLUGGABLE DATABASE ILCR OPEN READ ONLY;" \
      "CREATE PLUGGABLE DATABASE $SEED FROM ILCR CREATE_FILE_DEST = '$FILE_DEST';" \
      "ALTER PLUGGABLE DATABASE $SEED OPEN READ WRITE;" \
      "ALTER PLUGGABLE DATABASE $SEED CLOSE IMMEDIATE;" || status=1
  # A new PDB must open read-write once before it can be a read-only source.
  close_pdb ILCR && run "ALTER PLUGGABLE DATABASE ILCR OPEN READ WRITE;" || status=1
  return $status
}

flyway_reset() {
  # Retries cover transients: a PDB still opening, a tunnel reconnecting.
  local i
  for i in 1 2 3 4 5; do
    (cd backend && mvn -B -ntp -P e2e-db flyway:clean flyway:migrate \
      -Dflyway.url="jdbc:oracle:thin:@//localhost:1525/ILCR" \
      -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASS") && return 0
    echo "Flyway attempt $i failed; retrying in 20s..."
    sleep 20
  done
  return 1
}

summary() { echo "$1" | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"; }

start=$SECONDS
age=$(query "SELECT TO_CHAR((SYSDATE - creation_time) * 24, 'FM9990.0') FROM v\$pdbs WHERE name = '$SEED';")
if [ -n "$age" ] && awk -v a="$age" -v max="$SEED_MAX_AGE_HOURS" 'BEGIN { exit !(a < max) }'; then
  echo "Snapshot $SEED is ${age}h old; cloning it over ILCR."
  # A user-alignment failure falls back to Flyway like any restore failure.
  if restore_snapshot && ensure_user; then
    summary "E2E database: restored snapshot $SEED (${age}h old) in $((SECONDS - start))s."
    exit 0
  fi
  echo "::warning::Restoring snapshot $SEED failed; falling back to a Flyway reset."
else
  echo "No current snapshot for $SEED; running a Flyway reset."
fi

ensure_ilcr
ensure_user
flyway_reset
summary "E2E database: Flyway reset in $((SECONDS - start))s."
snap=$SECONDS
if take_snapshot; then
  summary "E2E database: saved snapshot $SEED in $((SECONDS - snap))s for later runs."
else
  echo "::warning::Could not save snapshot $SEED; the next run will reset with Flyway again."
  ensure_ilcr
fi
