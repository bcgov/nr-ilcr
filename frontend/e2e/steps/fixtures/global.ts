import { test as base } from 'playwright-bdd';

import { HomePage } from '../../pages/common/homePage';
import { AppShellPage } from '../../pages/common/appShell';
import { seedMockUser } from '../../pages/common/mockUser';
import { type ScheduleKey } from '../../fixtures/sch1/schedule1-test-data';

/**
 * Global (cross-domain) fixtures for the ILCR BDD suite.
 *
 * Only things more than one domain touches live here: the per-scenario `world` scratch, and the page
 * objects that are not owned by a subject area (`homePage` — every domain establishes a working context
 * through it; `appShell` — the data-independent shell smoke). Anything a single domain owns belongs in
 * that domain's file instead, so two people adding coverage to two domains never edit the same file.
 *
 * `world` is a per-scenario scratch object for state passed between steps (a key set by a precondition,
 * a value a later Then reads back). Its shape `World` is the UNION of every domain's scratch fields —
 * kept in one place because a single `world` object is shared across all steps of a scenario regardless
 * of which domain contributed them. This is the one deliberate cross-domain coupling left; keep the
 * fields grouped and commented by domain so additions stay conflict-cheap.
 */
export type World = {
  // --- sch1 / sec (shared: both drive a Schedule 1 through the Home working context) ---
  /** The (mill, year) the scenario operates on — set by the precondition step, read by read-back/cleanup. */
  scheduleKey?: ScheduleKey;
  /** The Home Mill-dropdown option text for `scheduleKey`'s mill. */
  millOption?: string;
  // --- sch1 ---
  /** Optimistic-lock token captured when a read-only anchor opens, re-checked to prove no write. */
  revisionAtOpen?: number | null;
  /** Itemized Other-Costs count read on the main page before a round-trip, re-checked after (S09). */
  otherCostsCountBefore?: number;
  /** Other-Costs mutating-PUT tally captured mid-scenario, so a later reject can prove NO FURTHER write
   * was sent even when an earlier step in the same scenario legitimately saved (SG-1 inline edit). */
  otherCostsMutationsBefore?: number;
  // --- sch2 ---
  // NOTE: the (mill, year) and Home option text use the SHARED `scheduleKey` / `millOption` above, so
  // the promoted common step (steps/common/home-context.steps.ts) serves Schedule 2 unchanged.
  /**
   * The Schedule 2 `revisionCount` read at open (absent → null for a never-saved schedule), re-checked
   * after a rejected action to PROVE the optimistic-lock token never moved, i.e. no write happened.
   */
  sch2RevisionAtOpen?: number | null;
  /**
   * Schedule 2 mutating-request tally captured mid-scenario, so a later reject can prove NO FURTHER
   * write was sent even when an earlier step in the same scenario legitimately saved.
   */
  sch2MutationsBefore?: number;

  // --- sch3 ---
  // NOTE: the (mill, year) and Home option text use the SHARED `scheduleKey` / `millOption` above, so
  // the promoted common step (steps/common/home-context.steps.ts) serves Schedule 3 unchanged.
  /** The anchor key the scenario named, so later steps and cleanup need not repeat it. */
  sch3AnchorKey?: string;
  /**
   * The Schedule 3 `revisionCount` read at open, re-checked after a rejected action to PROVE the
   * optimistic-lock token never moved, i.e. no write happened.
   */
  sch3RevisionAtOpen?: number | null;
  /**
   * Schedule 3 mutating-request tally captured mid-scenario, so a later reject can prove NO FURTHER
   * write was sent even when an earlier step in the same scenario legitimately saved.
   */
  sch3MutationsBefore?: number;
  /** The sub-page currently open, by its table title — so later steps need not repeat the label. */
  sch3SubPageTitle?: string;
  /** The description of the sub-page row the scenario created, read back by later Thens. */
  sch3RowDescription?: string;
  /**
   * Browser `alert` messages captured during the scenario (ALT-001, the Annual Rents S111 alert). The
   * handler has to be registered BEFORE the action — Playwright auto-dismisses an unhandled dialog —
   * so the When collects and a later Then asserts.
   */
  sch3Alerts?: string[];

  // --- sch4 ---
  // NOTE: the (mill, year) and Home option text use the SHARED `scheduleKey` / `millOption` above, so
  // the promoted common step (steps/common/home-context.steps.ts) serves Schedule 4 unchanged.
  /** The location a precondition seeded or a step created — read back by later Thens and by cleanup. */
  sch4LocationName?: string;
  /** That location's primary-report id (the write/delete handle, and the sub-page's `?loc=`). */
  sch4LocationId?: number;
  /** The sub-page currently open ("Towing Total" / …), so later steps need not repeat the label. */
  sch4SubPageLabel?: string;
  /** A seeded sub-page row's id, for the in-place row-edit cells (`#row-{id}-{field}`). */
  sch4RowId?: number;
  /**
   * Schedule 4 mutating-request tally captured mid-scenario, so a later reject can prove NO FURTHER
   * write was sent even when an earlier step in the same scenario legitimately saved.
   */
  sch4MutationsBefore?: number;
  /**
   * Listed location names noted before a cancel/reject, re-checked after to prove the list is unchanged.
   * Always taken through the UI (`schedule4Page.listedLocationNames()`) so the baseline and the re-check
   * are the same kind of read.
   */
  sch4ListedBefore?: string[];

  // --- sch6 ---
  // NOTE: the (mill, year) and Home option text use the SHARED `scheduleKey` / `millOption` above, so
  // the promoted common step (steps/common/home-context.steps.ts) serves Schedule 6 unchanged.
  /**
   * The per-record comment the scenario owns — its cleanup handle AND the way later steps find the
   * record it created. A road record has no unique natural key (the same TSA/Supply Block pair may
   * legitimately repeat), so the comment is what identifies "the record this scenario made".
   */
  sch6RecordComment?: string;
  /**
   * The `ROAD_MAINTENANCE_REPORT_ID` of the record the scenario created, read back from the API once
   * the add succeeds. Needed because every row-scoped locator is keyed on it (`row-<recordId>-volume`),
   * so a Then cannot address the new row until a previous step has captured this.
   */
  sch6RecordId?: number;
  /**
   * S17's seeded read-only records, resolved from the served document: each expected fixture entry
   * paired with the record as served and its 1-based DISPLAY ORDINAL.
   *
   * Resolved at scenario time rather than pinned, because neither half is stable in the fixture. The
   * `recordId` differs by environment — the local seed patch draws it from `ILCR_REPORT_COMMON_SEQ`
   * while the CI seed pins explicit ids — and every row locator is built from `row-<recordId>-*`. The
   * ordinal is the position in the served list, which is what the accordion titles and the Check Status
   * lines key on. So the Given matches each record by its per-record COMMENT (stable everywhere) and
   * carries both derived values forward.
   */
  sch6ReadOnlyRecords?: {
    expected: (typeof import('../../fixtures/sch6/schedule6-test-data'))['S17_RECORDS'][number];
    record: { recordId: number; comments: string | null };
    ordinal: number;
  }[];

  // --- sch11 ---
  // NOTE: the (mill, year) and Home option text use the SHARED `scheduleKey` / `millOption` above —
  // Schedule 11 deliberately reuses them so the promoted common step
  // (steps/common/home-context.steps.ts) serves every domain from one definition.
  /** The row marker the scenario seeded/created, so a later Then reads back the right location. */
  sch11Marker?: string;
  /**
   * Location-mutation tally captured mid-scenario, so a later reject can prove NO FURTHER write was
   * sent even when an earlier step in the same scenario legitimately wrote (the inline-edit reject arm).
   */
  sch11MutationsBefore?: number;
  /**
   * Listed row count noted before a cancel/reject, re-checked after to prove the table is unchanged.
   * Always taken through the UI (`schedule11Page.rowCount()`) by the explicit "I note the listed
   * Schedule 11 row count" step, so the baseline and the re-check are the same kind of count.
   */
  sch11RowCountBefore?: number;

  // --- sec ---
  /** The URL of the Schedule 1 GET fired on nav — proves the SAVED Home context drove the request (UC-SEC-001). */
  schedule1RequestUrl?: string;
};

export type GlobalFixtures = {
  homePage: HomePage;
  appShell: AppShellPage;
  world: World;
};

export const globalTest = base.extend<GlobalFixtures>({
  /**
   * THE SUITE'S IDENTITY, declared once for every scenario: `ILCR_SUBMITTER`, the legacy
   * ILCR_LICENSEE that all twelve feature files name in their "As a Licensee" role line. Under the
   * role x status matrix (Story 16.1) this is the only role that may edit a Draft, which is the
   * track every write scenario runs on — and, symmetrically, the role for which the non-Draft
   * anchors really are read-only, as `render-states.feature` asserts.
   *
   * Overriding `page` rather than adding a step is deliberate: the identity is a property of the
   * browser, not of any one Given, and several page objects navigate directly (`openWithNoContext`
   * seeds its own init script and calls `page.goto`) without passing through a shared entry point.
   * See `pages/common/mockUser.ts` for how the choice reaches the backend and why it is seeded
   * instead of inherited from `MOCK_USERS[0]`. A scenario that needs the administrator calls
   * `seedMockUser(page, 'admin')` itself — `pages/common/appShell.ts` is the one that does.
   */
  page: async ({ page }, use) => {
    // No exception any more: since bcgov/nr-ilcr#385 the mock principal carries a directory GUID
    // that is associated with the mills in both e2e databases, so Home offers the submitter its
    // OWN scoped list. This used to also install `grantAdminOnMillList`, which borrowed the
    // administrator for `GET /api/v1/mills` because a mock submitter was offered no mill at all —
    // see `pages/common/mockUser.ts` for why that is gone and what it had cost.
    await seedMockUser(page, 'submitter');
    await use(page);
  },

  homePage: async ({ page }, use) => {
    await use(new HomePage(page));
  },

  appShell: async ({ page }, use) => {
    await use(new AppShellPage(page));
  },

  world: async ({}, use) => {
    await use({});
  },
});
