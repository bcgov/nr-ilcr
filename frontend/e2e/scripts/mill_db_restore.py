#!/usr/bin/env python3
"""
Undo the two mill-administration writes the app has no endpoint to undo, so the scenarios that make
them can run against the seeded DB and still leave it exactly as they found it.

  forget-import <millId>
      Importing a mill (UC-MILL-001 S02) inserts its ILCR_MILL_STATUS_XREF row and enrols it in the
      current reporting year (one ILCR_MILL_REPORT_STATUS row + eleven ILCR_REPORT_CATEGORY rows,
      MillMaintenanceService.importMill). No endpoint deletes any of them, so the mill would stay
      tracked and the next run would have nothing to import. This deletes all three, children first,
      putting the mill back to "a THE.MILL row and nothing else" — the importable state it is seeded in.

  forget-enrolment <millId>
      Activating a closed mill with NO current-year records enrols it (GAP-6): one
      ILCR_MILL_REPORT_STATUS row + eleven ILCR_REPORT_CATEGORY rows. No endpoint deletes them; this does,
      leaving the xref alone (the status cleanup closes the mill again through the API).

  drop-association <millId> <userGuid>
      Adding a user to a mill (S05) inserts one ILCR_MILL_USER_XREF row, created inactive
      (MillAssociationService.add). There is no delete endpoint, and while the row exists the same add
      only ever answers the S07 duplicate warning. This deletes that one row. A seeded user's ILCR_USER
      account is left alone; the one account the add PROVISIONS (GAP-7, no account beforehand) is
      deleted too, once nothing references it.

GUARDED: each action refuses any mill (and user) outside ALLOWED below — the dedicated seeded mills in
real-test-data-patches/mill/mill-status-anchors.sql, which no extract row and no other domain uses. A
copy-paste can therefore never point this at real data. Both actions are idempotent (nothing to delete
is success) and print what they removed.

Connection and transaction handling mirror sch1_db_restore.py: ORACLE_DSN (default
THE/default@localhost:1525/DBDOCK_01), thin-mode `oracledb`, one explicit commit, rollback on any
failure.

Usage (called by steps/fixtures/mill.ts; also runnable by hand):
    python mill_db_restore.py forget-import    26057
    python mill_db_restore.py forget-enrolment 26061
    python mill_db_restore.py drop-association 26056 E2E00000000000000000000000000006
"""

import contextlib
import os
import re
import sys

import oracledb

# The only targets this script will ever touch. Keep in step with fixtures/mill/mills-test-data.ts.
ALLOWED_IMPORTS = {26057}
ALLOWED_ENROLMENTS = {26061}
ALLOWED_ASSOCIATIONS = {
    (26056, "E2E00000000000000000000000000006"),
    (26063, "E2E00000000000000000000000000007"),
}
# Accounts the ADD provisions (GAP-7). Deleted with the association, and only if no other association
# references them. Every other account is seeded and never touched.
PROVISIONED_ACCOUNTS = {"E2E00000000000000000000000000007"}


def connect() -> oracledb.Connection:
    dsn = os.environ.get("ORACLE_DSN", "THE/default@localhost:1525/DBDOCK_01")
    m = re.match(r"^(?P<user>[^/]+)/(?P<pw>[^@]+)@(?P<rest>.+)$", dsn)
    if not m:
        # Do NOT echo the raw DSN — it carries the password. Report the expected shape only.
        raise SystemExit(
            "ORACLE_DSN is not in the expected user/pw@host:port/service form "
            "(value withheld because it may contain a password)."
        )
    return oracledb.connect(user=m["user"], password=m["pw"], dsn=m["rest"])


@contextlib.contextmanager
def db_connection():
    """A connection that is always closed, and rolled back if anything raises (see sch1_db_restore)."""
    con = connect()
    try:
        yield con
    except BaseException:
        with contextlib.suppress(Exception):
            con.rollback()
        raise
    finally:
        with contextlib.suppress(Exception):
            con.close()


def forget_import(mill_id: int) -> None:
    if mill_id not in ALLOWED_IMPORTS:
        raise SystemExit(f"refusing forget-import on mill {mill_id}: not in ALLOWED_IMPORTS")
    with db_connection() as con, con.cursor() as cur:
        removed = {}
        # Children first: the category rows reference the report-status row (a composite FK on real
        # Oracle), and the report-status row references the xref.
        for table, column in (
            ("THE.ILCR_REPORT_CATEGORY", "ILCR_MILL_ID"),
            ("THE.ILCR_MILL_REPORT_STATUS", "ILCR_MILL_ID"),
            ("THE.ILCR_MILL_STATUS_XREF", "ILCR_MILL_STATUS_XREF_ID"),
        ):
            cur.execute(f"DELETE FROM {table} WHERE {column} = :m", m=mill_id)
            removed[table] = cur.rowcount
        # An association on the imported mill would block the xref delete; none of the import
        # scenarios create one, so this is a guard, not a cleanup.
        con.commit()
        cur.execute(
            "SELECT COUNT(*) FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = :m",
            m=mill_id,
        )
        if cur.fetchone()[0] != 0:
            raise SystemExit(f"mill {mill_id} is still tracked after forget-import")
        print(f"forget-import {mill_id}: " + ", ".join(f"{t}={n}" for t, n in removed.items()))


def forget_enrolment(mill_id: int) -> None:
    if mill_id not in ALLOWED_ENROLMENTS:
        raise SystemExit(f"refusing forget-enrolment on mill {mill_id}: not in ALLOWED_ENROLMENTS")
    with db_connection() as con, con.cursor() as cur:
        cur.execute("DELETE FROM THE.ILCR_REPORT_CATEGORY WHERE ILCR_MILL_ID = :m", m=mill_id)
        cats = cur.rowcount
        cur.execute("DELETE FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = :m", m=mill_id)
        status = cur.rowcount
        con.commit()
        print(f"forget-enrolment {mill_id}: categories={cats}, report-status={status}")


def drop_association(mill_id: int, user_guid: str) -> None:
    if (mill_id, user_guid) not in ALLOWED_ASSOCIATIONS:
        raise SystemExit(
            f"refusing drop-association on {mill_id}/{user_guid}: not in ALLOWED_ASSOCIATIONS"
        )
    with db_connection() as con, con.cursor() as cur:
        cur.execute(
            "DELETE FROM THE.ILCR_MILL_USER_XREF WHERE ILCR_MILL_ID = :m AND USER_GUID = :g",
            m=mill_id,
            g=user_guid,
        )
        n = cur.rowcount
        accounts = 0
        if user_guid in PROVISIONED_ACCOUNTS:
            cur.execute(
                "DELETE FROM THE.ILCR_USER u WHERE u.USER_GUID = :g AND NOT EXISTS "
                "(SELECT 1 FROM THE.ILCR_MILL_USER_XREF x WHERE x.USER_GUID = u.USER_GUID)",
                g=user_guid,
            )
            accounts = cur.rowcount
        con.commit()
        print(f"drop-association {mill_id}/{user_guid}: removed {n}, accounts {accounts}")


def main() -> None:
    args = sys.argv[1:]
    if len(args) == 2 and args[0] == "forget-import":
        forget_import(int(args[1]))
    elif len(args) == 2 and args[0] == "forget-enrolment":
        forget_enrolment(int(args[1]))
    elif len(args) == 3 and args[0] == "drop-association":
        drop_association(int(args[1]), args[2])
    else:
        raise SystemExit(
            "usage: mill_db_restore.py forget-import <millId> | forget-enrolment <millId> | "
            "drop-association <millId> <userGuid>"
        )


if __name__ == "__main__":
    main()
