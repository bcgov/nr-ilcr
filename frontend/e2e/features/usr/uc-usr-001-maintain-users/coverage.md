# UC-USR-001 — Maintain User Accounts and Mill Assignments — coverage

**New to these files?** `e2e/coverage-guide.md` explains the columns and the status legend;
`e2e/defects-guide.md` explains the sibling `defects.md`. Read those first — this file is a ledger, not
a narrative.

**Where the source documents live.** The Gherkin, the slice catalogue and the UC sidecars are in the
**`ilcr-bmad` planning repo**, not here, so no relative link from this file resolves:

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-USR-001/gherkin/UC-USR-001-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-USR-001/UC-USR-001-slices.md`
- Story ACs — `_bmad-output/planning-artifacts/epics.md` Stories 23.1–23.4; the recorded deviations
  (B)–(P) are in `_bmad-output/implementation-artifacts/submitter-mill-2-2/-2-3/-2-4-*.md`

**STATUS 2026-09-29 — 10 of 14 slices green; 1 red on purpose (S06); 3 not testable — S10 not applicable, S05/S14 blocked.** Story 23.4
(bcgov/nr-ilcr#162). S06 (user → mill) is a deliberate red, DIV-1: the feature was never built. S10 is not
applicable (no role filter, VER-5). S05/S14 (ADAM Details) are BLOCKED on the directory, GAP-2. The
Story 23.4 journey is one green scenario covering S13, S07, S01, S09, S08 and S02 in order.

---

## Suite state

Measured, never incremented — re-measure rather than editing these numbers by hand:

```
features/usr/uc-usr-001-maintain-users/*.feature   6 files — journey, assignments, search,
                                                     cross-navigation, accessibility, access
scenarios (bddgen, @UC-USR-001)               12 — journey 1, assignments 3, search 2,
                                                 cross-navigation 1 (@discovered-divergence),
                                                 accessibility 4, access 1
  ...of which pass `npm run test:gate`          11 (the DIV-1 red is excluded by the gate's grep)
preflight/usr-anchors.setup.ts                14 checks (shared with UC-USR-002) — the ten mills
                                                 listed, each of the 12 users at rest, the unknown
                                                 GUID unknown
pinned anchors                                10 mills 26064-26073 + 12 users ...11-...22 (SEEDED)
DB bridge (scripts/usr_db_restore.py)          3 actions — read-account, drop-assignment,
                                                 drop-account
stubbed requests                              1 — GET /api/v1/users/lookup (SPEC-1)
@discovered-divergence / @discovered-bug      1 / 0 (DIV-1)
```

Verification runs are recorded once, for the whole `usr` domain, in
`../uc-usr-002-user-from-mill/coverage.md` — the two UCs share one screen, one fixture and one seed.

---

## Story 23.4 acceptance criteria

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Find a directory user — all-blank search | 23.4 AC1; S13 | DirectoryPicker sends nothing for a blank term | journey `@S13` | covered | VER-1 |
| Find a directory user — no-match search | 23.4 AC1; S07; ERR-001 | picker `role=status` note | journey `@S07`, search `@S07` | covered | VER-2 |
| Activate the account | 23.4 AC1; S01; SUC-001 | PATCH /submitters/{guid} {active:true} | journey `@S01` (DB read-back) | covered | VER-3 |
| Assign mills with dated assignments | 23.4 AC1; S01; BR-07 | POST /mills/{id}/submitters; activeDate set, inactiveDate null | journey (two mills, API read-back of both dates) | covered | — |
| Duplicate-assignment warning on a repeat add | 23.4 AC1; S09; WRN-001 | assign answers 200 + warning, no row | journey `@S09`, a11y `@S09` | covered | — |
| Deactivation blocked while assignments active | 23.4 AC1; S08; ERR-002 | 409 `error.user.deactivate.hasactivemills`, flag unchanged | journey `@S08` (DB read-back 'Y'), a11y `@S08` | covered | — |
| Clear each assignment, then deactivate | 23.4 AC1; S08 recovery, S02; SUC-002 | PATCH end ×2, then PATCH {active:false} | journey `@S02` (DB read-back 'N') | covered | — |
| Mill → associated user, pre-selected | 23.4 AC2; UC-USR-002 S01 | Mills page View → `?userGuid=` consumed | UC-USR-002 carried-user `@S01` | covered | — |
| User → assigned mill, pre-selected | 23.4 AC2; S06; UC-MILL-002 S01 | **none — not built** | cross-navigation `@S06 @discovered-divergence` | red | DIV-1 |
| WCAG violations zero or triaged | 23.4 AC3; 23.3 AC11 | axe, WCAG 2.1 AA | accessibility — 4 scenarios, 5 scans (nothing selected, no-match note, user selected, warning, error) | covered | GAP-1 (success banner) |
| Non-admin refused (UI + API) | retired Story 2.5 AC3 | adminOnly nav; MAINTAIN_USERS 403 | access `@BR-ADMIN` | covered | — |

## Slices

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| S01 find, select, activate, add, deactivate an assignment | UC-USR-001-S01.feature | picker, account PATCH, assign POST, end PATCH | journey `@S01` | covered | — |
| S02 deactivate the account, no active assignment | S02.feature; SUC-002 | PATCH {active:false} | journey `@S02`; UC-USR-002 `@S03` | covered | — |
| S03 re-activate an ended assignment | S03.feature; BR-07 | assign re-POST revives in place | assignments `@S03` | covered | — |
| S04 change to a different user | S04.feature | picker replaces the selection in place (no Change User button) | search `@S04` | covered | re-grounded |
| S05 ADAM details, user with mills | S05.feature | **not built** (23.3 AC9) | — | blocked | GAP-2 |
| S06 jump to a related mill | S06.feature | **not built** (23.3 AC10) | cross-navigation `@S06` | red | DIV-1 |
| S07 no matching user + retry | S07.feature ×2; ERR-001 | picker note; recast text | journey `@S07`; search `@S07` | covered | VER-2 |
| S08 deactivate blocked + recovery | S08.feature ×2; ERR-002 | 409 hasactivemills | journey `@S08`; a11y `@S08` | covered | — |
| S09 mill already assigned + add another | S09.feature ×2; WRN-001 | assign 200 + warning; second mill added | journey `@S09` | covered | — |
| S10 role filter mismatch | S10.feature ×2 | **no role filter** — deviation (D) | — | not-applicable | VER-5 |
| S11 first-time import via activate | S11.feature | activate provisions ACTIVE, LICENSEE | assignments `@S11` (DB read-back) | covered | VER-6 |
| S12 first-time import via add-mill | S12.feature | add provisions 'N', assignment ACTIVE | assignments `@S12` (DB read-back) | covered | VER-3, VER-6 |
| S13 all-blank search lists everyone | S13.feature | **blank search not sent** — deviation (B) | journey `@S13` (asserts no request) | covered (as shipped) | VER-1 |
| S14 ADAM details, user without mills | S14.feature | **not built** (23.3 AC9) | — | blocked | GAP-2 |

## Messages

| Key | Text (as served) | Scenario |
|---|---|---|
| ERR-001 (picker) | `No user matching this criteria has been found. Please ensure the user you are looking for has been granted the ILCR Submitter role.` | journey, search, a11y |
| ERR-002 `error.user.deactivate.hasactivemills` | `User <GUID> -   has an 'Active' 'User To Mill Status' on one or more 'Associated Mills' listed below. All 'User To Mill Status' must be set to 'Inactive' before deactivation of this user is permitted.` | journey, a11y, UC-USR-002 |
| WRN-001 `user.not.associated.to.mill` | `User <GUID> is already associated to mill <name>. Please verify.` | journey, a11y, UC-USR-002 |
| SUC-001 `user.activated` | `User <GUID> -   has been activated.` | journey, assignments `@S11`, UC-USR-002 |
| SUC-002 `user.inactivated` | `User <GUID> -   has been deactivated.` | journey, UC-USR-002 |
| `user.activate.mill` | `Mill <n> - <name> has been activated for user <GUID> -  .` | journey, assignments, UC-USR-002 |
| `user.deactivate.mill` | `Mill <n> - <name> has been deactivated for user <GUID> -  .` | journey, UC-USR-002 |
