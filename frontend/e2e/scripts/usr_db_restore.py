#!/usr/bin/env python3
"""
Read the one thing the Users page API cannot, and undo the two writes it has no endpoint to undo, so the
user-administration scenarios can run against the seeded DB and still leave it exactly as they found it.

  read-account <userGuid>
      Prints the account's ILCR_USER.ACTIVE_IND ('Y' / 'N'), or NONE when the user has no account.
      The API exposes no account read (AssignmentApi has five operations and none returns an account,
      Story 23.3 deviation (O)), so a scenario that must PROVE an account write — or prove that a
      refused deactivation left the flag alone — reads it here.

  drop-assignment <millId> <userGuid>
      Adding a mill on the Users page inserts one ILCR_MILL_USER_XREF row (AssignmentService.assign).
      There is no delete endpoint, and while the row exists the same add only ever revives it or answers
      the duplicate warning. This deletes that one row.

  drop-account <userGuid>
      The first-time imports (UC-USR-001 S11 activate, S12 add-mill) PROVISION an ILCR_USER row. There
      is no delete endpoint, and while it exists the next run is no longer a first time. This deletes the
      row, and refuses while any assignment still references it (drop those first).

GUARDED: each write refuses any pair or account outside the ALLOWED sets below — the dedicated seeded
users and mills in real-test-data-patches/usr/user-admin-anchors.sql, which no extract row and no other
domain uses. A copy-paste can therefore never point this at real data. Every action is idempotent
(nothing to delete is success) and prints what it did.

Connection and transaction handling mirror mill_db_restore.py: ORACLE_DSN (default
THE/default@localhost:1525/DBDOCK_01), thin-mode `oracledb`, one explicit commit, rollback on any failure.

Usage (called by steps/fixtures/usr.ts and steps/usr/usersApi.ts; also runnable by hand):
    python usr_db_restore.py read-account    E2E00000000000000000000000000011
    python usr_db_restore.py drop-assignment 26064 E2E00000000000000000000000000011
    python usr_db_restore.py drop-account    E2E00000000000000000000000000013
"""

import contextlib
import os
import re
import sys

import oracledb

# The only targets this script will ever write. Keep in step with fixtures/usr/users-test-data.ts
# (ADDABLE_ASSIGNMENTS, PROVISIONABLE_ACCOUNTS).
ALLOWED_ASSIGNMENTS = {
    (26064, "E2E00000000000000000000000000011"),
    (26065, "E2E00000000000000000000000000011"),
    (26067, "E2E00000000000000000000000000014"),
    (26067, "E2E00000000000000000000000000019"),
}
PROVISIONABLE_ACCOUNTS = {
    "E2E00000000000000000000000000013",
    "E2E00000000000000000000000000014",
}
GUID = re.compile(r"^[0-9A-Fa-f]{32}$")


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


def read_account(user_guid: str) -> None:
    if not GUID.match(user_guid):
        raise SystemExit(f"refusing read-account on {user_guid!r}: not a 32-hex GUID")
    with db_connection() as con, con.cursor() as cur:
        cur.execute("SELECT ACTIVE_IND FROM THE.ILCR_USER WHERE USER_GUID = :g", g=user_guid)
        row = cur.fetchone()
        print(row[0] if row else "NONE")


def drop_assignment(mill_id: int, user_guid: str) -> None:
    if (mill_id, user_guid) not in ALLOWED_ASSIGNMENTS:
        raise SystemExit(
            f"refusing drop-assignment on {mill_id}/{user_guid}: not in ALLOWED_ASSIGNMENTS"
        )
    with db_connection() as con, con.cursor() as cur:
        cur.execute(
            "DELETE FROM THE.ILCR_MILL_USER_XREF WHERE ILCR_MILL_ID = :m AND USER_GUID = :g",
            m=mill_id,
            g=user_guid,
        )
        n = cur.rowcount
        con.commit()
        print(f"drop-assignment {mill_id}/{user_guid}: removed {n}")


def drop_account(user_guid: str) -> None:
    if user_guid not in PROVISIONABLE_ACCOUNTS:
        raise SystemExit(f"refusing drop-account on {user_guid}: not in PROVISIONABLE_ACCOUNTS")
    with db_connection() as con, con.cursor() as cur:
        cur.execute("SELECT COUNT(*) FROM THE.ILCR_MILL_USER_XREF WHERE USER_GUID = :g", g=user_guid)
        if cur.fetchone()[0] != 0:
            raise SystemExit(
                f"refusing drop-account on {user_guid}: an assignment still references it"
            )
        cur.execute("DELETE FROM THE.ILCR_USER WHERE USER_GUID = :g", g=user_guid)
        n = cur.rowcount
        con.commit()
        print(f"drop-account {user_guid}: removed {n}")


def main() -> None:
    args = sys.argv[1:]
    if len(args) == 2 and args[0] == "read-account":
        read_account(args[1])
    elif len(args) == 3 and args[0] == "drop-assignment":
        drop_assignment(int(args[1]), args[2])
    elif len(args) == 2 and args[0] == "drop-account":
        drop_account(args[1])
    else:
        raise SystemExit(
            "usage: usr_db_restore.py read-account <userGuid> | "
            "drop-assignment <millId> <userGuid> | drop-account <userGuid>"
        )


if __name__ == "__main__":
    main()
