import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
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
 * IT REUSES `apply-patches.sh`'s INVOCATION CONTRACT, NOT JUST ITS CLIENT CHOICE (rewritten 2026-08-31,
 * raised in PR #402 review). `scripts/docker-sqlplus.sh` is NOT a transparent sqlplus shim: it is a
 * stdin FILTER that rewrites the published host port on a `CONNECT` line as the script streams through,
 * and it runs sqlplus INSIDE the container — where a host path like `@/…/draft-anchors.sql` does not
 * exist. This module used to pass the DSN and `@<host path>` in argv, so on any host without a local
 * `sqlplus` (the documented default for this dev container) the Docker branch could only block waiting
 * on stdin or fail to connect — leaving the shared seeded database WITHOUT the Schedule 3 anchor the S08
 * delete scenario removed, and every later run failing its preflight. It now does exactly what
 * `scripts/apply-patches.sh` does:
 *
 *   - config comes from `.env` at the e2e root (ORACLE_DSN / DB_CONTAINER / SQLPLUS), same file, same
 *     precedence: a real environment variable wins over `.env`, and the built-in defaults fill the rest;
 *   - the patch CONTENT is piped on stdin (never `@file`), so the local and Docker clients behave
 *     identically — `docker exec` cannot read a host path;
 *   - the connection is `sqlplus -S /nolog` plus a `CONNECT` command on stdin, so the password never
 *     reaches argv and cannot be read out of `ps` — and the wrapper's port rewrite, which only ever
 *     matched `^CONNECT ` lines, actually applies;
 *   - `WHENEVER SQLERROR EXIT SQL.SQLCODE` is armed BEFORE `CONNECT`, so a failed connect or any SQL
 *     error exits non-zero instead of running the rest of the script against nothing;
 *   - `SET ECHO OFF` first, so the CONNECT line is never echoed into the output this module quotes;
 *   - `MSYS2_ARG_CONV_EXCL='*'`, so a bare `/nolog` survives Git Bash's POSIX-path rewriting.
 *
 * Synchronous and throws on failure, so a failed restore fails the scenario loudly rather than leaving
 * a deleted anchor behind for the next run to trip over.
 */

const PATCH = fileURLToPath(
  new URL('../../real-test-data-patches/sch3/draft-anchors.sql', import.meta.url),
);
const DOCKER_SQLPLUS = fileURLToPath(new URL('../../scripts/docker-sqlplus.sh', import.meta.url));
const ENV_FILE = fileURLToPath(new URL('../../.env', import.meta.url));

/**
 * `.env` at the e2e root, read the way `apply-patches.sh` sources it: a real environment variable
 * always wins, `.env` fills what the environment does not set, and the caller's defaults fill the rest.
 *
 * Deliberately a five-line parser rather than a dependency: this needs the same three flat `KEY=value`
 * lines the shell script needs (`set -a; . .env`), and `frontend/e2e` has no dotenv in its tree.
 * Quotes are stripped because `.env.example` documents both bare and quoted values; `export ` prefixes
 * are tolerated for the same reason.
 */
function envConfig(): Record<string, string> {
  const fromFile: Record<string, string> = {};
  if (existsSync(ENV_FILE)) {
    for (const raw of readFileSync(ENV_FILE, 'utf8').split(/\r?\n/)) {
      const line = raw.trim().replace(/^export\s+/, '');
      if (line === '' || line.startsWith('#')) continue;
      const eq = line.indexOf('=');
      if (eq <= 0) continue;
      fromFile[line.slice(0, eq).trim()] = line
        .slice(eq + 1)
        .trim()
        .replace(/^(['"])(.*)\1$/, '$2');
    }
  }
  const pick = (key: string, fallback: string): string =>
    process.env[key] ?? fromFile[key] ?? fallback;
  return {
    // Same defaults as scripts/apply-patches.sh, so the two agree with no .env at all.
    ORACLE_DSN: pick('ORACLE_DSN', 'THE/default@localhost:1525/DBDOCK_01'),
    DB_CONTAINER: pick('DB_CONTAINER', 'real-data-seeded-db'),
    SQLPLUS: pick('SQLPLUS', ''),
  };
}

/** Is a container with this name running? The same test `apply-patches.sh` makes before choosing Docker. */
function containerIsRunning(name: string): boolean {
  try {
    const names = execFileSync('docker', ['ps', '--format', '{{.Names}}'], { stdio: 'pipe' })
      .toString()
      .split(/\r?\n/)
      .map((n) => n.trim());
    return names.includes(name);
  } catch {
    return false;
  }
}

/**
 * How to run sqlplus, in `apply-patches.sh`'s own order: an explicit `$SQLPLUS`, else a local `sqlplus`
 * on PATH, else the bundled Docker wrapper — and only if its container is actually up, so "no seeded DB
 * running" fails with that message rather than as an opaque `docker exec` error.
 */
function resolveSqlplus(config: Record<string, string>): { command: string; args: string[] } {
  if (config.SQLPLUS !== '') {
    return { command: config.SQLPLUS, args: [] };
  }
  try {
    execFileSync(process.platform === 'win32' ? 'where' : 'which', ['sqlplus'], { stdio: 'pipe' });
    return { command: 'sqlplus', args: [] };
  } catch {
    if (!existsSync(DOCKER_SQLPLUS)) {
      throw new Error(
        'No local sqlplus on PATH and no scripts/docker-sqlplus.sh — cannot restore the Schedule 3 ' +
          'seed patch. Install an Oracle client or set SQLPLUS to one.',
      );
    }
    if (!containerIsRunning(config.DB_CONTAINER)) {
      throw new Error(
        `No local sqlplus on PATH and no running Docker container named "${config.DB_CONTAINER}" — ` +
          'cannot restore the Schedule 3 seed patch. Start your seeded-DB container (or set ' +
          'DB_CONTAINER in frontend/e2e/.env if it is named differently), install an Oracle client, ' +
          'or set SQLPLUS to one.',
      );
    }
    return { command: 'bash', args: [DOCKER_SQLPLUS] };
  }
}

/**
 * Re-apply `real-test-data-patches/sch3/draft-anchors.sql`. Idempotent.
 *
 * The DSN comes from `.env` (ORACLE_DSN) and is never logged: it is written to sqlplus's stdin behind
 * `SET ECHO OFF`, and a failure message quotes only sqlplus's own output.
 */
export function applySch3Patch(): void {
  const config = envConfig();
  const { command, args } = resolveSqlplus(config);
  const script =
    'SET ECHO OFF\n' +
    'WHENEVER SQLERROR EXIT SQL.SQLCODE\n' +
    `CONNECT ${config.ORACLE_DSN}\n` +
    `${readFileSync(PATCH, 'utf8')}\n` +
    'EXIT\n';
  try {
    const out = execFileSync(command, [...args, '-S', '/nolog'], {
      input: script,
      stdio: ['pipe', 'pipe', 'pipe'],
      env: {
        ...process.env,
        // The wrapper reads the container name from its own environment (see docker-sqlplus.sh), so a
        // .env-only DB_CONTAINER has to be forwarded explicitly — `apply-patches.sh` exports it too.
        DB_CONTAINER: config.DB_CONTAINER,
        MSYS2_ARG_CONV_EXCL: '*',
      },
    }).toString();
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
