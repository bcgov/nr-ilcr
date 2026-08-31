import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * The one DB bridge this domain needs: re-apply the Schedule 3 seed patch.
 *
 * WHY IT EXISTS. Schedule 3 has no create path — `Schedule3Service` resolves the category-3
 * `ILCR_REPORT_SUMMARY` first on every operation and 404s when it is absent (see
 * `real-test-data-patches/sch3/draft-anchors.sql`). So the destructive S08 delete scenario, and the
 * BR-09 crown scenario whose cleanup drops the patched Schedule 1, cannot put the summary back through
 * the API. Re-running the patch can: it is idempotent and re-inserts exactly the rows it originally
 * added (guarded per-row on its own existence check), so calling it when nothing is missing is a no-op.
 *
 * WHY NOT python-oracledb (which `steps/sch1/schedule1DbRestore.ts` uses): sch1 needs a genuine
 * snapshot/restore of REAL extract rows, which is a program. This needs to run one fixed, idempotent
 * .sql file, which is exactly what the scaffold's own patch tooling already does — so it reuses the
 * scaffold's sqlplus client selection (a local `sqlplus`, else the bundled Docker wrapper) rather than
 * adding a second DB dependency to the suite.
 *
 * Synchronous and throws on failure, so a failed restore fails the scenario loudly rather than leaving
 * a deleted anchor behind for the next run to trip over.
 */

const PATCH = fileURLToPath(
  new URL('../../real-test-data-patches/sch3/draft-anchors.sql', import.meta.url),
);
const DOCKER_SQLPLUS = fileURLToPath(new URL('../../scripts/docker-sqlplus.sh', import.meta.url));

const DSN = process.env.ORACLE_DSN ?? 'THE/default@localhost:1525/DBDOCK_01';

/** A local `sqlplus` if the host has one, else the scaffold's Docker wrapper (same order as apply-patches.sh). */
function resolveSqlplus(): { command: string; viaDocker: boolean } {
  if (process.env.SQLPLUS) {
    return { command: process.env.SQLPLUS, viaDocker: false };
  }
  try {
    execFileSync(process.platform === 'win32' ? 'where' : 'which', ['sqlplus'], { stdio: 'pipe' });
    return { command: 'sqlplus', viaDocker: false };
  } catch {
    if (!existsSync(DOCKER_SQLPLUS)) {
      throw new Error(
        'No local sqlplus on PATH and no scripts/docker-sqlplus.sh — cannot restore the Schedule 3 ' +
          'seed patch. Install an Oracle client or set SQLPLUS to one.',
      );
    }
    return { command: 'bash', viaDocker: true };
  }
}

/**
 * Re-apply `real-test-data-patches/sch3/draft-anchors.sql`. Idempotent.
 *
 * The DSN is passed on the command line to the same client the scaffold's `apply-patches.sh` chooses;
 * the value comes from `.env` (ORACLE_DSN) and is never logged — a failure message quotes only sqlplus's
 * own stdout, which the script does not echo the connect string into.
 */
export function applySch3Patch(): void {
  const { command, viaDocker } = resolveSqlplus();
  const args = viaDocker
    ? [DOCKER_SQLPLUS, '-S', DSN, `@${PATCH}`]
    : ['-S', DSN, `@${PATCH}`];
  try {
    const out = execFileSync(command, args, { stdio: 'pipe' }).toString();
    // sqlplus exits 0 even when a block raises ORA-*, so the output has to be inspected.
    if (/ORA-\d{5}/.test(out)) {
      throw new Error(out);
    }
  } catch (err) {
    // Two different failures arrive here and they carry their detail in different places:
    //  - the ORA-* path above throws with sqlplus's own stdout as the message;
    //  - a non-zero EXIT gives Node's terse "Command failed: …" and puts the real output on
    //    err.stdout / err.stderr, because execFileSync ran with stdio: 'pipe'.
    // Until 2026-08-28 only `.message` was reported, so the exit-code path promised diagnostics and
    // delivered none — a failed restore said nothing about why (raised in review).
    const e = err as Error & { stdout?: Buffer | string; stderr?: Buffer | string };
    const detail = [e.message, e.stdout?.toString(), e.stderr?.toString()]
      .map((part) => part?.trim())
      .filter((part) => part)
      .join('\n');
    throw new Error(
      `Failed to re-apply the Schedule 3 seed patch (real-test-data-patches/sch3/draft-anchors.sql). ` +
        `The deleted anchor is still missing, so the next run will fail its preflight. sqlplus said:\n${detail}`,
    );
  }
}
