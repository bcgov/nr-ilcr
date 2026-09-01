import { mergeTests } from '@playwright/test';
import { createBdd } from 'playwright-bdd';

import { globalTest } from './global';
import { sch1Test } from './sch1';
import { sch2Test } from './sch2';
import { sch3Test } from './sch3';
import { sch4Test } from './sch4';
import { sch11Test } from './sch11';
import { secTest } from './sec';

/**
 * ============================================================================
 * SINGLE COMPOSITION ROOT for the whole BDD suite.
 * ============================================================================
 *
 * Each domain owns its own fixtures file (`./<domain>`); this merges them into the single `test` that
 * `createBdd` binds Given/When/Then to. Step files import Given/When/Then/expect FROM HERE, never from
 * 'playwright-bdd' or '@playwright/test' directly. Every fixture stays LAZY (only instantiated when a
 * scenario touches it) and per-scenario (no shared mutable module state) so scenarios remain
 * order-independent and parallel-safe.
 *
 * WHY THE SPLIT: this was one 260-line file holding every domain's fixtures. That makes the composition
 * root a merge-conflict hotspot the moment two people add coverage for two different domains at once —
 * a problem the sibling SCS suite hit for real once its equivalent file reached ~1,460 lines
 * (BCNRS/scs-bmad#41, which this mirrors). Splitting early keeps parallel domain work cheap.
 *
 * HOW TO ADD A DOMAIN: create `./<domain>.ts` exporting
 * `export const <domain>Test = base.extend<<Domain>Fixtures>({ ... })` — its page-object fixture(s) and
 * a per-scenario cleanup registry that undoes whatever the scenario created/mutated (failing loud on
 * residue) — then add it to `mergeTests` below. Put a fixture in `./global` ONLY if more than one
 * domain uses it; a `world` field goes in the `World` union there (grouped under its domain's comment).
 */
export const test = mergeTests(
  globalTest,
  sch1Test,
  sch2Test,
  sch3Test,
  sch4Test,
  sch11Test,
  secTest,
);

export type { World } from './global';
export type { OtherCostsCleanup } from './sch1';
export type { Sch2Cleanup } from './sch2';
export type { Sch3Cleanup } from './sch3';
export type { Sch4Cleanup } from './sch4';
export type { Sch11Cleanup } from './sch11';

export const { Given, When, Then } = createBdd(test);
export { expect } from '@playwright/test';
