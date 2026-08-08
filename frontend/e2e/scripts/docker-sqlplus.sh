#!/usr/bin/env bash
# Forwards sqlplus invocations into the local seeded-DB Docker container's own Oracle client,
# for hosts (like this dev container) that have no system-wide `sqlplus`/Oracle Instant Client on PATH.
# You don't invoke this directly — apply-patches.sh / teardown-patches.sh auto-select it when no local
# `sqlplus` is found. Container name comes from DB_CONTAINER (see .env.example).
#
# NOT a transparent sqlplus shim: it is a stdin FILTER. The caller connects with `sqlplus /nolog` and a
# `CONNECT <dsn>` command on stdin (so the password never reaches argv), and this wrapper rewrites the
# published host port (1525) to the container-internal listener port (1521) on that CONNECT line as it
# streams through. Consequences: it expects a piped script on stdin (run by hand it will block waiting
# for stdin), and a DSN passed as an argv argument is NOT port-rewritten anymore.
set -euo pipefail

# Default matches the pre-seeded image's container (docker run --name real-data-seeded-db …); override
# with DB_CONTAINER if you named it differently — name yours `real-data-seeded-<app>-db`
# (e.g. `real-data-seeded-ilcr-db`).
CONTAINER="${DB_CONTAINER:-real-data-seeded-db}"
SQLPLUS_BIN="${DB_SQLPLUS_BIN:-/opt/oracle/product/26ai/dbhomeFree/bin/sqlplus}"
# Callers connect with `sqlplus /nolog` and pass the HOST-facing DSN (port 1525, the container's
# published port) via a CONNECT command on STDIN — not in argv, so the password never reaches `ps`.
# Since sqlplus runs inside the container, translate that CONNECT line's host port to the
# container-internal listener port (1521) as the stream passes through. Scoped to CONNECT lines so the
# patch SQL itself is never rewritten. Args ($@, e.g. `-S /nolog`) carry no DSN and pass through as-is.
HOST_PORT="${DB_HOST_PORT:-1525}"
CONTAINER_PORT="${DB_CONTAINER_PORT:-1521}"

sed "/^CONNECT /s#:${HOST_PORT}/#:${CONTAINER_PORT}/#g" \
  | docker exec -i "$CONTAINER" "$SQLPLUS_BIN" "$@"
