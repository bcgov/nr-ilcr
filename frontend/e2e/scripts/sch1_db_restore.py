#!/usr/bin/env python3
"""
Snapshot / restore a whole Schedule 1 (summary + all its cost-report-detail rows) so the destructive
S13 "delete the whole Schedule 1" scenario can run against REAL seeded data and still leave the DB
exactly as it found it.

Why this exists: Schedule 1 delete removes the ILCR_REPORT_SUMMARY row AND every detail row
(Schedule1Repository.deleteSchedule), and the app has no create-on-open path — so a deleted seeded
schedule cannot be recreated through the API. This helper copies the rows into E2E_BAK_SCH1_* backup
tables before the UI delete and re-inserts them afterward (byte-for-byte, `INSERT ... SELECT *`, PKs
preserved). The audit/check triggers stay ENABLED (never globally disabled — that would corrupt other
scenarios writing in parallel); they are safe here: the summary audit trigger is state-gated off for a
Draft and reassigns no PK, and the detail check trigger re-accepts rows that were already valid.

Usage (called by the S13/S24 fixtures; also runnable by hand):
    python sch1_db_restore.py snapshot <millId> <year>
    python sch1_db_restore.py restore  <millId> <year>

Connection: ORACLE_DSN (default THE/default@localhost:1525/DBDOCK_01), thin-mode `oracledb` (no client).
This host has no local sqlplus and the seeded Oracle is reached directly on :1525, so the suite's DB
work here goes through python-oracledb rather than the scaffold's sqlplus wrapper.
"""

import os
import re
import sys

import oracledb

CATEGORY = "1"
SUMMARY = "THE.ILCR_REPORT_SUMMARY"
DETAIL = "THE.ILCR_COST_REPORT_DETAIL"
BAK_SUMMARY = "THE.E2E_BAK_SCH1_SUMMARY"
BAK_DETAIL = "THE.E2E_BAK_SCH1_DETAIL"


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


def ensure_backup_tables(cur) -> None:
    for bak, src in ((BAK_SUMMARY, SUMMARY), (BAK_DETAIL, DETAIL)):
        cur.execute(
            "SELECT COUNT(*) FROM user_tables WHERE table_name = :t",
            [bak.split(".", 1)[1]],
        )
        if cur.fetchone()[0] == 0:
            # Same shape as the real table, no rows — a structural clone for the row copy.
            # The local data-backed suite is fully parallel, so S13 and S24 can both reach this on a
            # fresh DB and race: both see COUNT=0, both issue CREATE TABLE, and the loser hits
            # ORA-00955 (name already used). That collision is benign — the table now exists either
            # way — so treat it as success rather than failing the snapshot.
            try:
                cur.execute(f"CREATE TABLE {bak} AS SELECT * FROM {src} WHERE 1 = 0")
            except oracledb.DatabaseError as err:
                (error_obj,) = err.args
                if error_obj.code != 955:  # ORA-00955: name is already used by an existing object
                    raise


def find_summary_id(cur, table, mill_id, year):
    cur.execute(
        f"SELECT ILCR_REPORT_SUMMARY_ID FROM {table} "
        "WHERE ILCR_MILL_ID = :m AND REPORT_YEAR = :y AND ILCR_CATEGORY_ID = :c",
        {"m": mill_id, "y": year, "c": CATEGORY},
    )
    row = cur.fetchone()
    return None if row is None else row[0]


def snapshot(mill_id: int, year: int) -> None:
    con = connect()
    cur = con.cursor()
    ensure_backup_tables(cur)
    sid = find_summary_id(cur, SUMMARY, mill_id, year)
    if sid is None:
        raise SystemExit(f"snapshot: no Schedule 1 summary for {mill_id}/{year} — cannot snapshot")
    # Overwrite any stale backup for this schedule, then copy the live rows verbatim.
    cur.execute(f"DELETE FROM {BAK_SUMMARY} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
    cur.execute(f"DELETE FROM {BAK_DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
    cur.execute(
        f"INSERT INTO {BAK_SUMMARY} SELECT * FROM {SUMMARY} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid]
    )
    cur.execute(
        f"INSERT INTO {BAK_DETAIL} SELECT * FROM {DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid]
    )
    con.commit()
    print(f"snapshot ok: {mill_id}/{year} summaryId={sid}")


def restore(mill_id: int, year: int) -> None:
    con = connect()
    cur = con.cursor()
    sid = find_summary_id(cur, BAK_SUMMARY, mill_id, year)
    if sid is None:
        raise SystemExit(f"restore: no backup for {mill_id}/{year} — snapshot was never taken")
    # Exact, idempotent restore: drop whatever is live now (deleted by S13, field-modified by S24), then
    # re-insert the snapshot verbatim. Same PK, so detail FKs still line up; works whether the live rows
    # are gone, changed, or unchanged. Detail rows first (FK child), then the summary.
    cur.execute(f"DELETE FROM {DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
    cur.execute(f"DELETE FROM {SUMMARY} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
    cur.execute(
        f"INSERT INTO {SUMMARY} SELECT * FROM {BAK_SUMMARY} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid]
    )
    cur.execute(
        f"INSERT INTO {DETAIL} SELECT * FROM {BAK_DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid]
    )
    con.commit()
    print(f"restore ok: {mill_id}/{year} summaryId={sid} restored to snapshot")
    # Clear this schedule's backup rows now that the live DB is whole again.
    cur.execute(f"DELETE FROM {BAK_SUMMARY} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
    cur.execute(f"DELETE FROM {BAK_DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
    con.commit()


def main() -> None:
    args = sys.argv[1:]
    if len(args) != 3 or args[0] not in ("snapshot", "restore"):
        raise SystemExit("usage: sch1_db_restore.py {snapshot|restore} <millId> <year>")
    action, mill_id, year = args[0], int(args[1]), int(args[2])
    (snapshot if action == "snapshot" else restore)(mill_id, year)


if __name__ == "__main__":
    main()
