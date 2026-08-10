import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
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

// Prefer the reproducible venv created by `npm run setup:python` (scripts/.venv, pinned oracledb), so a
// fresh checkout runs the restore without depending on the developer's global interpreter state. An
// explicit PYTHON env override wins; otherwise fall back to a bare `python` on PATH.
function resolvePython(): string {
  if (process.env.PYTHON) return process.env.PYTHON;
  const venvPosix = fileURLToPath(new URL('../../scripts/.venv/bin/python', import.meta.url));
  const venvWin = fileURLToPath(new URL('../../scripts/.venv/Scripts/python.exe', import.meta.url));
  if (existsSync(venvPosix)) return venvPosix;
  if (existsSync(venvWin)) return venvWin;
  return 'python';
}

const PYTHON = resolvePython();

function run(...args: string[]): string {
  return execFileSync(PYTHON, [SCRIPT, ...args], { stdio: 'pipe' }).toString();
}

export const snapshotSchedule1 = (millId: number, year: number): void => {
  run('snapshot', String(millId), String(year));
};

export const restoreSchedule1 = (millId: number, year: number): void => {
  run('restore', String(millId), String(year));
};

/**
 * How many detail rows currently hold a non-null VOLUME. S02 asserts 0 to prove the crown pre-fill is
 * SERVED ONLY — the GET renders the pre-filled values, so only the stored column can tell the two apart.
 */
export const countSchedule1Volumes = (millId: number, year: number): number =>
  Number(run('count-volumes', String(millId), String(year)).trim());

/**
 * NULL the volume-only fields (143/144/139/140) a scenario wrote but the blank-fields PUT cannot undo,
 * because `Schedule1Service` treats a null there as "field omitted" (defects.md BUG-2).
 * Called by the `schedule1Restore` cleanup after its restore PUT — without it, S01's writes to those
 * fields survive teardown and the happy-path target drifts from its pinned empty baseline every run.
 */
export const blankGuardedSchedule1Volumes = (millId: number, year: number): void => {
  run('blank-guarded', String(millId), String(year));
};

/**
 * Put a schedule into the genuine first-entry state the S02 crown pre-fill needs: NULL every stored
 * detail volume AND remove its item-19 Other-Costs rows. The app cannot reach this state itself (a
 * blanking PUT is a no-op for the five `!= null`-guarded volume fields — defects.md BUG-2),
 * and nulling volumes WITHOUT removing the Other-Costs rows produces a 500 rather than a pre-fill
 * (BUG-3). Always call `snapshotSchedule1` first and register the key for teardown.
 */
export const makeSchedule1FirstEntry = (millId: number, year: number): void => {
  run('first-entry', String(millId), String(year));
};
