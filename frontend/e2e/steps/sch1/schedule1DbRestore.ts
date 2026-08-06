import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/**
 * DB snapshot/restore bridge for the destructive S13 delete scenario. Schedule 1 delete removes the
 * summary + all detail rows and the app cannot recreate them, so the only way to run S13 against real
 * seeded data and leave the DB as found is to copy the rows out before the delete and re-insert them
 * after. The actual SQL lives in `scripts/sch1_db_restore.py` (thin-mode oracledb — this host has no
 * sqlplus and reaches the seeded Oracle directly on :1525); this module just shells out to it.
 *
 * `snapshot` is called by the delete precondition (before the UI delete); `restore` by the cleanup
 * fixture (after the scenario). Both are synchronous and throw on failure so residue fails loud.
 */

const SCRIPT = fileURLToPath(new URL('../../scripts/sch1_db_restore.py', import.meta.url));
const PYTHON = process.env.PYTHON ?? 'python';

function run(...args: string[]): void {
  execFileSync(PYTHON, [SCRIPT, ...args], { stdio: 'pipe' });
}

export const snapshotSchedule1 = (millId: number, year: number): void =>
  run('snapshot', String(millId), String(year));

export const restoreSchedule1 = (millId: number, year: number): void =>
  run('restore', String(millId), String(year));
