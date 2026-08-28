#!/usr/bin/env bash
# Reapply the local-only ILCR DDL (new tables not yet in the seeded THE image) to the running WSL DB.
#
# The seeded Docker image (real-data-seeded-ilcr-db) is the shared "real THE" baseline; our new ILCR
# tables are an additive local layer that is NOT baked into that image. So after a fresh `docker pull`
# / container recreate, the tables are gone and must be reapplied. This reuses the SAME Flyway
# migration files that the IT snapshot uses, so the DDL stays single-sourced.
#
# Usage:  ./scripts/apply-local-ddl.sh [container-name]   (default: real-data-seeded-ilcr-db)
# Requires: the DB container running; sqlplus available inside it; PDB DBDOCK_01 open.
set -euo pipefail

CONTAINER="${1:-real-data-seeded-ilcr-db}"
PDB="${ILCR_LOCAL_PDB:-DBDOCK_01}"
DDL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../backend/src/test/resources/db" && pwd)"

# Local-only additive migrations to (re)apply, in order. Add new V-files here as they are introduced.
MIGRATIONS=(
  "V20260825__the_ilcr_user_and_mill_user_xref.sql"
)

echo "Applying local ILCR DDL to container '${CONTAINER}' (PDB ${PDB})..."
{
  echo "alter session set container=${PDB};"
  echo "whenever sqlerror continue;"   # tolerate 'name already used' on a re-run
  for m in "${MIGRATIONS[@]}"; do
    echo "prompt --- ${m} ---"
    cat "${DDL_DIR}/${m}"
  done
  echo "exit"
} | docker exec -i "${CONTAINER}" sqlplus -s / as sysdba

echo "Done. (Errors like ORA-00955 'name already used' are expected if objects already exist.)"
