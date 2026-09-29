# UC-USR-002 — Maintain User from a Mill Record — coverage

**New to these files?** `e2e/coverage-guide.md` explains the columns and the status legend;
`e2e/defects-guide.md` explains the sibling `defects.md`.

**Where the source documents live** (the `ilcr-bmad` planning repo — no relative link resolves):

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-USR-002/gherkin/UC-USR-002-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-USR-002/UC-USR-002-slices.md`

**STATUS 2026-09-29 — COMPLETE. 11 of 11 slices green**, plus the two carry guards Story 23.3 ruled on
(malformed and unresolvable carried user) — Story 23.4 (bcgov/nr-ilcr#162). Open: one divergence for a
ruling (DIV-1, the picker's provider on arrival) and the shared directory dependency (SPEC-1).

---

## Suite state

Measured, never incremented:

```
features/usr/uc-usr-002-user-from-mill/*.feature   2 files — carried-user, maintain-after-arrival
scenarios (bddgen, @UC-USR-002)               12 — carried-user 5, maintain-after-arrival 7
  ...of which pass `npm run test:gate`          12
```

The fixture, the seed patch, the preflight and the DB bridge are shared with UC-USR-001 — see its
`coverage.md` "Suite state".

## Verification runs — the whole `usr` domain (both UCs), 2026-09-29

- **first runs.** UC-USR-001 journey: green first run. The rest of UC-USR-001: 11/12, the red being DIV-1
  as intended. UC-USR-002: 5/12. The 7 reds were the test's own fault: the arrival step did not record
  the carried user as the selected one. It now asserts the user's details rendered and then records
  them. After that fix, 12/12.
- **parallel** (`--grep @usr --workers=4`): **23 passed + 1 red, and the red is DIV-1** (as intended).
- **serial repeats** (`--repeat-each=3 --workers=1`, DIV-1 excluded): **67/69**. The 2 reds were one Windows worker crash
  (`0xC0000409`) in S04 mid-scenario, and the next repeat of S04 correctly refusing the row that crash left
  behind. That repeat's own teardown restored it (the at-rest step registers cleanup before it checks).
  S04 then went **5/5** serially
- **every anchor at rest afterwards**, read at the DB: the twelve users' flags and assignments are exactly
  the seed; ...13/...14 have no account; no row has both dates set. `preflight/usr-anchors.setup.ts`
  (14 checks) green.
- **negative control, persistence.** S02's account PATCH was answered in the browser with a fabricated
  success. The verbatim "has been activated." message rendered and passed. The DB read-back went
  **red** (`Expected "Y", Received "N"`). So the read-back proves the write; the message alone would not.
- **negative control, CI seed.** `ci-seed-parity.setup.ts` was run against R__80 with journey's
  `ILCR_USER` row removed and reactivate's ENDED row given an `ACTIVE_DATE`. The new user-administration
  check went **red**, naming both (`no ILCR_USER row`; `assignments are [26066:BOTH], expected
  [26066:ENDED]`).
- **full gate** (`npm run test:gate -- --workers=2`, 617 tests): **616 passed**. The one red was a Schedule 5
  worker crash (`0xC0000409`), not in this domain. It left two camps on sch5's `duplicate-name` anchor; they
  were deleted through the app's endpoint, and then 234/234 preflights and that feature 3/3 passed. All 23
  `usr` gate scenarios passed. (A first gate attempt at default workers was killed by host memory pressure
  at 265/617 with no reds; hence `--workers=2`.)

## Slices

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| S01 cross-redirect entry with pre-selected user | UC-USR-002-S01.feature; BR-02 | Mills View → `/mill-associations?userGuid=`, consumed; one exact lookup | carried-user `@S01` | covered | VER-1, VER-3; BR-03 VER-2 |
| S02 activate account after arriving | S02.feature; SUC-001 | PATCH {active:true} | after-arrival `@S02` (DB read-back) | covered | — |
| S03 deactivate account, no active assignment | S03.feature; SUC-002 | PATCH {active:false} | after-arrival `@S03` (DB read-back) | covered | — |
| S04 add mill assignment | S04.feature | assign POST, created ACTIVE | after-arrival `@S04` (API read-back) | covered | UC-USR-001 VER-4 |
| S05 add mill already associated | S05.feature; WRN-001 | assign 200 + warning, no row | after-arrival `@S05` | covered | — |
| S06 activate an assignment row | S06.feature | assign re-POST revives | after-arrival `@S06` | covered | — |
| S07 deactivate an assignment row | S07.feature | end PATCH | after-arrival `@S07` | covered | — |
| S08 switch to a different user | S08.feature | picker replaces the carried user (no Change User button) | carried-user `@S08` | covered | re-grounded |
| S09 opened without a carried user (+ Select User) | S09.feature ×2 | default search-first state, no lookup | carried-user `@S09` | covered | — |
| S10 deactivation blocked while active on a mill | S10.feature; ERR-002 | 409, flag unchanged | after-arrival `@S10` (DB read-back 'Y') | covered | — |
| S11 deactivation succeeds after clearing all | S11.feature; SUC-002 | end ×2, then PATCH {active:false} | after-arrival `@S11` (DB read-back 'N') | covered | — |
| Guard — malformed carried user | Story 23.3 AC10 ruling (N) | route drops non-32-hex `userGuid` | carried-user `@GUARD-MALFORMED` | covered | VER-4 |
| Guard — carried user the directory cannot resolve | Story 23.3 ruling (N); retired 2.5 AC4 | `CARRIED_USER_FAILED` error, no selection | carried-user `@GUARD-UNRESOLVED` | covered | VER-4 |
