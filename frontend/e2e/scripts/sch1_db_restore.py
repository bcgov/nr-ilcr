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

`first-entry` exists for the S02 crown pre-fill precondition. The BR-03 pre-fill only fires when
EVERY stored Schedule 1 detail volume is NULL (Schedule1Service.allVolumesEmpty), and the app cannot
produce that state through the API: `allVolumesEmpty` inspects every stored detail volume, and the PUT
contract does not reach them all. Nulling the VOLUME column directly is therefore still the only way to
reach the first-entry state on real seeded data — always paired with `snapshot` before and `restore`
after, so the schedule is left exactly as found.

(Until 2026-08-11 there was a second reason: the five volume-only fields were guarded by `!= null` on
the write path, so a blanking PUT was a silent no-op — defects.md BUG-2 / issue #260. That is fixed in
backend commit `3ee9ff2`; a null now clears. The first reason above stands on its own.)

Usage (called by the S02/S13/S24 fixtures; also runnable by hand). Every action takes <millId> <year>
and the list below is the complete set the dispatcher accepts:
    python sch1_db_restore.py snapshot      <millId> <year>   # copy summary + details to the backup tables
    python sch1_db_restore.py restore       <millId> <year>   # re-insert them verbatim, then drop the backup
    python sch1_db_restore.py first-entry   <millId> <year>   # null every detail volume + drop item-19 rows
    python sch1_db_restore.py blank-guarded <millId> <year>   # null volumes 143/144/139/140 only
    python sch1_db_restore.py count-volumes <millId> <year>   # PRINTS the count of non-null detail volumes

`count-volumes` is the only READ-ONLY action: it writes nothing and prints a single number on stdout,
which is what S02 reads to prove the crown pre-fill is SERVED and never stored (the GET renders the
pre-filled volumes, so only the stored column can tell the two apart). The others all mutate and must be
paired with `snapshot` before / `restore` after.

Connection: ORACLE_DSN (default THE/default@localhost:1525/DBDOCK_01), thin-mode `oracledb` (no client).
This host has no local sqlplus and the seeded Oracle is reached directly on :1525, so the suite's DB
work here goes through python-oracledb rather than the scaffold's sqlplus wrapper.
"""

import contextlib
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


@contextlib.contextmanager
def db_connection():
    """Yield a connection that is ALWAYS closed, and rolled back if anything raises.

    Every action in this file mutates the shared seeded DB from test setup/teardown, so a failure must
    not leave a half-applied transaction behind. An exception here usually means teardown is *already*
    failing; a connection abandoned mid-transaction would hold row locks until the server times it out,
    and later scenarios would then fail for reasons that have nothing to do with them. Rolling back
    before propagating keeps a failed run's blast radius to the run itself.

    Commits stay EXPLICIT in each action rather than happening on clean exit here — `restore` genuinely
    needs two of them (make the live DB whole first, only then clear its backup), and python-oracledb has
    changed whether `Connection.__exit__` commits between major versions, so relying on that implicit
    behaviour would make the semantics version-dependent. The cursor uses the driver's own context
    manager so it is closed too.
    """
    con = connect()
    try:
        yield con
    except BaseException:
        # BaseException, not Exception: these actions signal "no such schedule" with SystemExit, which
        # must roll back like any other failure rather than slipping past the handler.
        with contextlib.suppress(Exception):
            con.rollback()
        raise
    finally:
        with contextlib.suppress(Exception):
            con.close()


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
    with db_connection() as con, con.cursor() as cur:
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
    with db_connection() as con, con.cursor() as cur:
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
        # Committed BEFORE clearing the backup, deliberately: making the live DB whole is the priority.
        # If the backup-clear below then fails, the schedule is already restored and the leftover backup
        # rows are harmless — `snapshot` deletes any stale backup for a summary before writing a new one.
        # Collapsing both into one transaction would instead roll the restore back and leave the schedule
        # missing rows, which is the worse of the two failure modes.
        con.commit()
        print(f"restore ok: {mill_id}/{year} summaryId={sid} restored to snapshot")
        # Clear this schedule's backup rows now that the live DB is whole again.
        cur.execute(f"DELETE FROM {BAK_SUMMARY} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
        cur.execute(f"DELETE FROM {BAK_DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
        con.commit()


def first_entry(mill_id: int, year: int) -> None:
    """Put a schedule into the genuine FIRST-ENTRY state the BR-03 crown pre-fill requires (S02).

    Two things must hold:

    1. Every stored detail VOLUME is null — `Schedule1Service.allVolumesEmpty`, the pre-fill trigger.
    2. The schedule carries NO item-19 Other-Costs rows, because a real first entry has none — they are
       created later through the sub-page — so removing them is what the state actually looks like.

    Point 2 used to carry a second, harder reason: `toOtherCosts` read the shared row's volume with
    `.map(DetailRow::volume).findFirst()`, and `Stream.findFirst()` throws NPE on a null element, so a
    shared item-19 row with a null volume made the GET return 500 instead of pre-filling (defects.md
    BUG-3 / issue #261). Nulling volumes without removing those rows manufactured a 500, not a first
    entry. Fixed 2026-08-11 in backend commit `3ee9ff2` — the row is selected before mapping to its
    nullable volume — so that trap is gone and point 2 now rests on fidelity to the real state alone.

    Always paired with `snapshot` before and `restore` after, which puts every deleted row back verbatim.
    """
    with db_connection() as con, con.cursor() as cur:
        sid = find_summary_id(cur, SUMMARY, mill_id, year)
        if sid is None:
            raise SystemExit(f"first-entry: no Schedule 1 summary for {mill_id}/{year}")
        cur.execute(
            f"DELETE FROM {DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s AND ILCR_REPORT_COST_ITEM_ID = 19",
            [sid],
        )
        removed = cur.rowcount
        cur.execute(f"UPDATE {DETAIL} SET VOLUME = NULL WHERE ILCR_REPORT_SUMMARY_ID = :s", [sid])
        blanked = cur.rowcount
        con.commit()
        print(
            f"first-entry ok: {mill_id}/{year} summaryId={sid} blanked={blanked} otherCostsRemoved={removed}"
        )


GUARDED_VOLUME_CODES = (143, 144, 139, 140)


def blank_guarded(mill_id: int, year: int) -> None:
    """NULL the volume-only fields 143/144/139/140 at the DB — now a safety net, not the mechanism.

    This existed because `emptyScheduleRequest`'s blanking PUT could not clear those four:
    `Schedule1Service` guarded the scalars with `!= null`, so a null was a silent no-op and the value
    written by S01 survived teardown, drifting the happy-path target from its pinned empty baseline every
    run (defects.md BUG-2 / issue #260). Fixed 2026-08-11 in backend commit `3ee9ff2` — the PUT clears
    them now, so this is idempotent belt-and-braces. Kept because it costs nothing and keeps teardown
    correct if the write path ever regresses; it is the first thing to delete if the suite is made to run
    without python-oracledb.

    Item 19 (the shared Other-Costs volume) is still deliberately NOT reset here — S01 never writes it
    and the seeded target legitimately holds one. Note the old third reason no longer applies: nulling it
    used to trip the `toOtherCosts` NPE (BUG-3 / issue #261), which was fixed in the same commit.
    """
    with db_connection() as con, con.cursor() as cur:
        sid = find_summary_id(cur, SUMMARY, mill_id, year)
        if sid is None:
            raise SystemExit(f"blank-guarded: no Schedule 1 summary for {mill_id}/{year}")
        codes = ", ".join(str(c) for c in GUARDED_VOLUME_CODES)
        cur.execute(
            f"UPDATE {DETAIL} SET VOLUME = NULL "
            f"WHERE ILCR_REPORT_SUMMARY_ID = :s AND ILCR_REPORT_COST_ITEM_ID IN ({codes})",
            [sid],
        )
        blanked = cur.rowcount
        con.commit()
        print(f"blank-guarded ok: {mill_id}/{year} summaryId={sid} rows={blanked}")


def count_volumes(mill_id: int, year: int) -> None:
    """Print how many detail rows hold a non-null VOLUME — lets S02 prove the pre-fill is SERVED ONLY.

    The GET renders the pre-filled volumes, so an API read-back cannot distinguish "pre-filled in the
    response" from "persisted". Only the stored column can, hence this direct count.
    """
    with db_connection() as con, con.cursor() as cur:  # noqa: F841 — `con` used by the CM's cleanup
        sid = find_summary_id(cur, SUMMARY, mill_id, year)
        if sid is None:
            raise SystemExit(f"count-volumes: no Schedule 1 summary for {mill_id}/{year}")
        cur.execute(
            f"SELECT COUNT(*) FROM {DETAIL} WHERE ILCR_REPORT_SUMMARY_ID = :s AND VOLUME IS NOT NULL",
            [sid],
        )
        # Read-only: no commit needed, and the context manager closes the connection either way.
        print(cur.fetchone()[0])


def main() -> None:
    args = sys.argv[1:]
    if len(args) != 3 or args[0] not in ("snapshot", "restore", "first-entry", "blank-guarded", "count-volumes"):
        raise SystemExit(
            "usage: sch1_db_restore.py {snapshot|restore|first-entry|blank-guarded|count-volumes} <millId> <year>"
        )
    action, mill_id, year = args[0], int(args[1]), int(args[2])
    {
        "snapshot": snapshot,
        "restore": restore,
        "first-entry": first_entry,
        "blank-guarded": blank_guarded,
        "count-volumes": count_volumes,
    }[action](mill_id, year)


if __name__ == "__main__":
    main()
