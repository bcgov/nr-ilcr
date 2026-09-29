# UC-MILL-001 — Maintain Mills, Status, and User Associations — coverage

**New to these files?** `e2e/coverage-guide.md` explains the columns and the status legend;
`e2e/defects-guide.md` explains the sibling `defects.md`. Read those first — this file is a ledger, not
a narrative.

**Where the source documents live.** The Gherkin, the slice catalogue and the UC sidecars are in the
**`ilcr-bmad` planning repo**, not here, so no relative link from this file resolves:

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-MILL-001/gherkin/UC-MILL-001-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-MILL-001/UC-MILL-001-slices.md`
- Detailed UC / technical sidecar — same directory, `-detailed.md` / `-technical.md`

**STATUS 2026-09-29 — COMPLETE. 14 of 14 live slices** (S01–S05, S07–S15; S06 is retired with the
Associated Auditors panel, DL-23), plus the accessibility sweep — Story 22.4 (bcgov/nr-ilcr#157). Open
items are in `defects.md`: one coverage gap left, GAP-5 (the success notification's a11y scan); one divergence for a ruling
(DIV-1); and one dependency to know about (SPEC-1: the directory behind Add User and View is off in
every environment, so those scenarios stub it).

---

## Suite state

Measured, never incremented — re-measure rather than editing these numbers by hand:

```
features/mill/**/*.feature                    7 files — happy-path, status, associations, search,
                                                 import, accessibility, access
scenarios (bddgen, @UC-MILL-001)              27 — the 14 live slices (19 scenarios, incl. 4 a11y
                                                 sweeps and BR-10 access) + 8 gap scenarios
                                                 (GAP-1, GAP-4 x2, GAP-5 x2, GAP-6 x2, GAP-7)
  ...of which pass `npm run test:gate`          27 (2026-09-29 full gate, 579 scenarios: 567
                                                 green; the 12 reds — 3 mill, 9 schedule — were all
                                                 load failures (a panel not rendered in 10 s, a
                                                 browser that never launched) and all 12 pass re-run
                                                 serially; 219/219 preflights green afterwards)
preflight/mill-anchors.setup.ts               16 checks — one per tracked anchor (14), the import
                                                 mill untracked, and GAP-7's user unassociated
pinned mill anchors                           15 — 25050 (extract, shared) + 26050-26056,
                                                 26058-26063 (SEEDED, tracked) + 26057 (SEEDED,
                                                 importable)
DB bridge (scripts/mill_db_restore.py)        3 actions — forget-import, forget-enrolment,
                                                 drop-association (+ the account it provisioned)
stubbed requests                              1 — GET /api/v1/users/lookup (SPEC-1), and S14's one
                                                 injected import failure (VER-4)
@discovered-divergence / @discovered-bug      0 (DIV-1 is asserted neutrally, not tagged)
```

Verification runs, 2026-09-28 (S01):

- `@UC-MILL-001` → **1 passed**, after one red that was the test's fault (the option locator was page-wide
  and matched the header's mock-user `<select>`; scoped to the dropdown's own listbox)
- `--repeat-each=5 --workers=1` → **5 passed**, 5/5 stable. Serial on purpose: S01 is 25050's only
  writer, and parallel repeats of one scenario would race each other on `REVISION_COUNT`
- anchor confirmed at rest after every run (`preflight/mill-anchors.setup.ts`)
- **negative control, persistence:** the save request rewritten in flight to drop the division-contact
  change → the success message still rendered and the API read-back step went **red**. So the read-back
  proves persistence; the message alone would not have
- **negative control, CI seed:** `ci-seed-parity.setup.ts` run against the pre-change `R__80` → the new
  mill-administration check went **red** naming all five missing pieces (client location on `MILL`, the
  `CLIENT_LOCATION` row, both contacts, the xref panel, the audit stamp)

Verification runs, 2026-09-28 (S03, S04, S12):

- `@UC-MILL-001` → **4 passed** first run (S01 included)
- `--repeat-each=5 --workers=1` → **20 passed**; `--repeat-each=3 --workers=4` → **12 passed**. Each status
  scenario owns its mill, so they are parallel-safe with each other and with S01
- every anchor at rest afterwards (`preflight/mill-anchors.setup.ts`, 4 passed)
- **negative control, persistence:** S03's deactivate answered in flight with a fabricated success (the
  server never called) → the verbatim "has been deactivated." message rendered and passed, and the API
  read-back went **red** (`Expected "CLS", Received "ACT"`). So the read-back proves the status change;
  the message and the swapped button alone would not have
- **negative control, CI seed:** `ci-seed-parity.setup.ts` run against R__80 with only the status block
  removed → **red**, naming all fourteen missing pieces (MILL + xref per mill, the 2025 status row and
  its eleven categories per mill, S12's assignment and its `ILCR_USER` row)

Verification runs, 2026-09-29 (S02, S05, S07–S11, S13–S15, a11y, access):

- first run: 13/18 green. The five reds were all the test's own fault, each fixed at the cause:
  - S05's two cleanups ran in the wrong order. `millDbCleanup` now depends on `millStatusCleanup`, so
    the added row is deleted before the association set is verified.
  - S08/S09 matched "Active" in the row's run-together text. They now match the exact status cell.
  - S10: the Users page drops a non-hex `?userGuid=`, and then CONSUMES a valid one on arrival. The
    synthetic GUIDs became 32-hex, and the carry is proven by the page's exact lookup, not the URL.
  - S11: the number retry kept the earlier Name criterion. `searchByNumber` now clears Name.
- `--repeat-each=3 --workers=4` then went red once, a REAL race: S08 and S09 shared 26053, and each
  one's cleanup checks the mill's whole user set. S09 moved to its own mill (26058).
- after the fixes: all 18 green in parallel (`--workers=4`); **54/54** with `--repeat-each=3 --workers=1`;
  every anchor at rest afterwards (10 preflight checks); the access scenario green. Parallel repeats of
  the SAME scenario race on its own mill by construction, as S01's do, so repeats are run serially
- **negative control, CI seed:** R__80 with a tracking row added for the import mill → the parity gate
  went **red** ("has an ILCR_MILL_STATUS_XREF row, so it is tracked and cannot be imported")

Verification runs, 2026-09-29 (the gap scenarios):

- seeding GAP-2's never-set indicator failed on delivery's audit trigger — the state cannot exist
  there, so GAP-2 was closed as not reachable rather than tested (defects.md VER-5)
- the whole UC: **27/27** green first run (`--workers=4`); **81/81** with `--repeat-each=3 --workers=1`;
  every anchor at rest afterwards (24 preflight/parity checks). Read at the DB after the repeats:
  26061 holds no report rows again, GAP-7's user has no account and no association, and the import
  mill is untracked — so both new DB cleanups really undo what they claim
- **negative control, CI seed:** one category row added for the PARTIAL mill → the parity gate went
  **red** ("pinned as partial … 1 of 11 ILCR_REPORT_CATEGORY rows")

---

## The anchor, and why it is shared

Only five of the extract's 21 tracked mills carry any client-location contact (25050–25054), and all
five are schedule anchors. S01 therefore shares 25050 with sch1/sch4/sch5/sch6. That is safe because S01
writes only the xref row's head-office indicator and two contact ids, which no schedule reads; it never
changes the ACT/CLS status, which is the only xref column a schedule does read. The full reasoning, and
what it means for the status-changing slices still to come, is in `fixtures/mill/mills-test-data.ts`.

---

## S01 — Update an Existing Mill's Head-Office Indicator and Contacts

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Mills is reached from the Administration menu | slices.md S01 Entry Point Reachability; BR-10 | `routes/-navigation.ts` Administration group (`adminOnly`) → `/mills` | happy-path `@S01 @p0` | covered | — |
| The menu (and so the page's visible path) is hidden from a non-administrator | slices.md S01 Entry Point Reachability; BR-10 | `routes/-navigation.ts` `adminOnly` filter; MAINTAIN_MILLS on every endpoint | access `@BR-10 @p1` — no Administration group or Mills link for the submitter; the API answers 403 to a read and a write | covered | — |
| Nothing selected: only Select Mill and Import Mill are offered (STA-001 state 1) | S01.feature sc.1; technical.md STA-001 | `components/mills/index.tsx` `!mill` branch | happy-path `@S01 @p0` | covered | — |
| Select Mill opens the "Find and select Mill" dialog | S01.feature sc.1 | `MillSearchModal.tsx` | happy-path `@S01 @p0` | covered | — |
| Search by Number | S01.feature sc.1 ("Number:, Name:, and/or Status:") | `MillSearchModal.tsx` `millNumber` param | happy-path `@S01 @p0` | covered | — |
| Search by Name | S01.feature sc.1 (same step) | `MillSearchModal.tsx` `millName` param | search `@S11` (a zero-match name) | covered | — |
| Search by Status | S01.feature sc.1 (same step) | `MillSearchModal.tsx` `status` param | search `@GAP-4 @p2` — Name + Status "Close": the Closed mill listed, every row Close, the Active mill absent | covered | — |
| Selecting a result row IS the select action; the dialog closes | S01.feature sc.1; slices.md S01 controls (`rowSelect`) | `MillSearchModal.tsx` `Select mill <n>` button → `onSelect` | happy-path `@S01 @p0` | covered | — |
| Mill Details shows number, name, current status | S01.feature sc.1 | `index.tsx` `mills__identity` | happy-path `@S01 @p0` | covered | — |
| Mill Details shows the last-edited details | S01.feature sc.1 | `index.tsx` "Last Edited by : / on date:" | happy-path `@S01 @p0` (asserted against the API read, never a literal) | covered | VER-1 |
| Change Mill is offered; Select Mill and Import Mill are not | S01.feature sc.1; STA-001 | `index.tsx` `mill && form` branch | happy-path `@S01 @p0` | covered | — |
| The three editable controls show the mill's saved values on select | S01.feature sc.2 Given | `index.tsx` `formFor(mill)` | happy-path `@S01 @p0` | covered | — |
| Head Office / Division Contact options are limited to the mill's client-location contacts | S01.feature sc.2; BR-09 | `MillMaintenanceRepository.findContactOptions` (MILL.CLIENT_NUMBER/LOCN join) | happy-path `@S01 @p0` — exact option list in both dropdowns + the API | covered | — |
| Head-office indicator is changed and persisted | S01.feature sc.2 | `PUT /admin/mills/{id}/contacts` | happy-path `@S01 @p0` — Y → N, API read-back | covered | — |
| Head Office Contact is changed and persisted | S01.feature sc.2 | same | happy-path `@S01 @p0` — 2609 → 2617, API read-back | covered | — |
| Division Contact is changed and persisted | S01.feature sc.2 | same | happy-path `@S01 @p0` — 2617 → 2609 (the swap proves each dropdown writes its own column) | covered | — |
| SUC-001 `Mill {0} - {1} has been saved.` | S01.feature sc.2 Then; slices.md S01 messages; `messages.properties` `mill.updated` | `MillMaintenanceController` resolve → success notification | happy-path `@S01 @p0` — verbatim `Mill 9171 - BCOVEY-TEST has been saved.` | covered | VER-2 |
| After Save the form shows exactly what was saved | 22.3 D-R2 (only a successful save resets the form) | `index.tsx` save → `setForm(formFor(data.mill))` | happy-path `@S01 @p0` | covered | — |
| Save touches nothing but the panel (status, number, name unchanged) | write path | `MillMaintenanceRepository` update | happy-path `@S01 @p0` | covered | — |
| A contact left blank ("(None)") clears that column | slices.md Gap Analysis — Optional-exercised (excluded there as "no distinct outcome") | `index.tsx` NO_CONTACT → `null` → column cleared | happy-path `@GAP-1 @p2` — Division Contact cleared, API read-back | covered | — |
| Save is gated until a never-set head-office indicator is chosen | 22.3 D5 (legacy defect fixed); not in the legacy Gherkin | `validation.ts` `canSaveContacts` | — (the state cannot exist in delivery) | not-applicable | VER-5 |
| Legacy control ids (`form:selectedMillList:0:*`, `form:messagesId`, `millResults`) | S01.feature locator notes | no new-app equivalent — Carbon, located by role/label | — | not-applicable | — |
| "Authenticated via WebADE SSO" | S01.feature Background | no new-app equivalent — security-off mock principal, role via `X-Mock-Groups` | — | not-applicable | — |

---

## The status anchors, and why they are seeded

S03, S04 and S12 change a mill's ACT/CLS status, and a closed mill answers 409 on every schedule — so no
schedule anchor can take one, and every ACT mill in the extract is a schedule anchor. The one unpinned
mill, 14050, is CLS with no current-year report records, so activating it would enrol it: twelve rows no
endpoint removes. So the three mills are seeded (`real-test-data-patches/mill/mill-status-anchors.sql`,
folded into R__80), each with a COMPLETE current-year record set. That set is the load-bearing part:
`activate` then writes the status alone, and every cleanup is an exact API round trip. They carry the
sentinel `E2E_SEED_MILLSTAT`, which both mock-submitter association sets skip — otherwise S03's mill
would gain an active user and S03 would become S12. Details in `fixtures/mill/mills-test-data.ts`.

---

## S03 — Deactivate (Close) an Active Mill With No Active Users

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| An Active mill is selected and its details shown | S03.feature Given | `index.tsx` details panel | status `@S03 @p0` (shared S01 step: number, name, status, audit line vs API) | covered | — |
| Deactivate is offered on an Active mill, Activate is not (STA-001) | S03.feature When; slices.md S03 controls | `index.tsx` `isActive ?` branch — presence, never disabled | status `@S03 @p0` | covered | — |
| SUC-002 `Mill {0} - {1} has been deactivated.` | S03.feature Then; `messages.properties` `mill.expired` | `MillMaintenanceService.deactivate` → notification | status `@S03 @p0` — verbatim `Mill 9181 - E2E-DEACTIVATE-TEST has been deactivated.` | covered | — |
| The mill's status is set to Closed (BR-01, BR-05) | S03.feature Then | `POST /admin/mills/{id}/deactivate` | status `@S03 @p0` — API read-back `CLS` | covered | — |
| The panel shows the new status | S03.feature (implied by the button swap) | `index.tsx` installs the response's mill | status `@S03 @p0` — `Close` (the code table's own label) | covered | — |
| Deactivate is replaced by Activate | S03.feature Then | STA-001 | status `@S03 @p0` | covered | — |
| Schedule viewing is thereafter blocked for every role (BR-06) | S03.feature Then; slices.md Gap Analysis (folded into S03) | `MillContextService` → `MillClosedException` 409 ERR-002; working context `millViewable` | status `@S03 @p0` — Schedule 1 GET 409 + verbatim ERR-002 as submitter AND admin; `millViewable: false` | covered | — |
| "Mill has no active user assignments" precondition | S03.feature Given | `hasActiveAssignment` guard | status `@S03 @p0` precondition + preflight (active set pinned `[]`) | covered | — |

## S04 — Activate a Closed Mill

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| A Closed mill is selected and its details shown | S04.feature Given | `index.tsx` | status `@S04 @p0` | covered | — |
| Activate is offered on a Closed mill, Deactivate is not | S04.feature When; STA-001 | `index.tsx` | status `@S04 @p0` | covered | — |
| SUC-003 `Mill {0} - {1} has been activated.` | S04.feature Then; `mill.activated` | `MillMaintenanceService.activate` | status `@S04 @p0` — verbatim `Mill 9182 - E2E-ACTIVATE-TEST has been activated.` | covered | — |
| The mill's status is set to Active (BR-05) | S04.feature Then | `POST /admin/mills/{id}/activate` | status `@S04 @p0` — API read-back `ACT`; panel shows `Active` | covered | — |
| Activate is replaced by Deactivate | S04.feature Then | STA-001 | status `@S04 @p0` | covered | — |
| Per-category report records EXIST for the current reporting year (BR-07) | S04.feature Then | `ReportingYearService.enrolmentState` COMPLETE | status `@S04 @p0` — working context resolves both tracks for the current year, and Schedule 1 now answers 200 where it answered 409 | covered | — |
| ...CREATED if missing (BR-07, the enrol branch) | S04.feature Then ("created if missing") | `enrolMillInYear` on `EnrolmentState.NONE` | status `@GAP-6 @p1` — context 404 before, resolved after, Schedule 1 opens; DB cleanup deletes the enrolment | covered | — |
| A partial record set refuses the activation (22.1 D7) | new-app rule, not in the legacy Gherkin | `error.mill.activate.partialrecords` 409 | status `@GAP-6 @p1` — verbatim, stays Closed, records left as found | covered | — |

## S12 — Deactivation Blocked by Active Users

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| An Active mill with an active associated user is selected | S12.feature sc.1 Given | Associated Licensee User table | status `@S12 @p0` — the pinned licensee's row reads `Active` and offers Deactivate | covered | — |
| Deactivate is refused with `The selected mill has active users, you must deactivate them first.` | S12.feature sc.1 Then; `error.mill.deactivate.hasactiveusers` | `MillMaintenanceService.deactivate` → 409 | status `@S12 @p0` — verbatim | covered | — |
| The mill's status remains Active (BR-01) | S12.feature sc.1 Then | the guard runs before the write | status `@S12 @p0` — API read-back `ACT`; Deactivate still offered | covered | — |
| Deactivate each remaining active user's row | S12.feature sc.2 When | `Deactivate user <guid>` row button → `POST .../users/{guid}/deactivate` | status `@S12 @p0` — driven by the server's active set; each row flips to `Inactive` | covered | — |
| ...in the `form:auditorList` table too | S12.feature sc.2 When | Associated Auditors panel retired (DL-23) | — | not-applicable | — |
| The mill-level Deactivate then succeeds with SUC-002, and the status is Closed | S12.feature sc.2 Then | as S03 | status `@S12 @p0` — verbatim `Mill 9183 - E2E-BLOCKED-TEST has been deactivated.`; API `CLS`; Activate offered | covered | — |
| The two legacy scenarios as one | S12.feature sc.2 Given is sc.1's end state | — | status `@S12 @p0` (S01 precedent) | covered | — |

---

## The association and import mills

Five more seeded mills from the same patch, one per writer: 26053 (S08), 26058 (S09), 26054 (S13, CLS),
26056 (S05), and 26055, which S07, S10 and the accessibility sweeps share because none of them writes.
26057 is a `THE.MILL` row with no ILCR tracking at all — the extract has no importable mill — for S02 and
S14, which import the same mill and so run `@mode:serial`. Two writes have no delete endpoint: an import
(an xref plus the current-year records) and an added association. Their cleanups delete exactly those
rows at the DB through `scripts/mill_db_restore.py`, which refuses any mill outside the seeded set.

The synthetic licensee GUIDs are 32 hex characters (`E2E` + zeros + the user number), like real
directory GUIDs, because the Users page drops a carried `?userGuid=` that is not.

---

## S02 — Import a Mill from Ministry Client Records

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Import Mill opens the "Find and select Mill to Import" dialog | S02.feature sc.1 | `ImportMillModal.tsx` | import `@S02 @p0` | covered | — |
| Search by Number lists ministry mills not yet tracked (BR-04) | S02.feature sc.1 Then | `GET /admin/mills/importable` anti-join | import `@S02 @p0` — the dialog offers `Import mill 9188`, and the API lists it | covered | — |
| Search by Name in the import dialog | S02.feature sc.1 ("Number and/or Name") | `millName` param | import `@GAP-4 @p2` | covered | — |
| The per-row Import opens CNF-001 with its verbatim question | S02.feature sc.2 | `confirmImportMill`, Confirmation modal | import `@S02 @p0` | covered | — |
| Yes imports; the xref is created Closed with head office Y (BR-03) | S02.feature sc.2 Then | `MillMaintenanceService.importMill` | import `@S02 @p0` — API: 200 where it was 404, `CLS`, `Y` | covered | — |
| Current-year report records are created (BR-03) | S02.feature sc.2 Then | `enrolMillInYear` | import `@S02 @p0` — the working context resolves both tracks for the current year | covered | — |
| The dialog closes and the panel shows the imported mill | S02.feature sc.2 Then | `index.tsx` `adopt(data.mill)` | import `@S02 @p0` — `9188 - E2E-IMPORT-TEST`, `Close`, Activate offered | covered | — |
| No success message (legacy had none) | S02.feature header note | `AdminMillResponse` omits both message fields | import `@S02 @p0` (asserts the panel instead) | covered | — |

## S05 — Add a New User to the Mill as a Licensee

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Add opens "Find and Add User"; a directory search finds the user | S05.feature | `AddUserModal.tsx` → `DirectoryPicker` → `GET /users/lookup` | associations `@S05 @p0` — directory STUBBED | covered | SPEC-1 |
| Choosing the user IS the add | S05.feature; mills.xhtml:298 | `POST /admin/mills/{id}/users` | associations `@S05 @p0` | covered | — |
| `Mill {0} - {1} has been activated for user {2} - {3} {4}.` | S05.feature Then; `user.activate.mill` | `MillAssociationController.toResponse` | associations `@S05 @p0` — verbatim, names blank | covered | VER-3 |
| The association is created INACTIVE (BR-08) | S05.feature Then | `insertInactiveAssignment` | associations `@S05 @p0` — API `ENDED`, exactly one row; the row reads `Inactive` and offers Activate | covered | — |
| The dialog closes and the table refreshes | S05.feature Then | `index.tsx` `setAddOpen(false)`, `loadUsers` | associations `@S05 @p0` | covered | — |
| A first-time user gets an account row (provisioning) | `MillAssociationService.add` | `provisionAccountIfAbsent` | associations `@GAP-7 @p1` — a user with no account is added; the association (FK-bound to the account) is created inactive | covered | — |

## S07 — Duplicate Association Warning

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Adding an already-associated user warns `User {0} is already associated to mill {1}. Please verify.` | S07.feature Then; `user.not.associated.to.mill` | `MillAssociationService.add` existing-pair branch | associations `@S07 @p0` — verbatim warning | covered | — |
| No duplicate association is created (BR-08) | S07.feature Then | same | associations `@S07 @p0` — API holds exactly the at-rest pairs | covered | — |
| The dialog closes; the panel is unchanged | S07.feature Then | `index.tsx` duplicate branch reloads | associations `@S07 @p0` — the table lists exactly the at-rest users | covered | — |
| "…in either the Licensee or the Auditors panel" | S07.feature When | Auditors panel retired (DL-23) | — | not-applicable | — |

## S08 / S09 — Deactivate and Activate an Association

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| An active row offers Deactivate; clicking it ends the association | S08.feature | `POST …/users/{guid}/deactivate` | associations `@S08 @p0` — API `ENDED`; row reads `Inactive`, offers Activate | covered | — |
| `Mill {0} - {1} has been deactivated for user {2} - {3} {4}.` | S08.feature Then; `user.deactivate.mill` | `MillAssociationController` | associations `@S08 @p0` — verbatim | covered | VER-3 |
| An inactive row on an Active mill offers Activate; clicking it activates (BR-02) | S09.feature | `POST …/users/{guid}/activate` | associations `@S09 @p0` — API `ACTIVE`; row reads `Active`, offers Deactivate | covered | — |
| `…has been activated for user…` | S09.feature Then; `user.activate.mill` | same | associations `@S09 @p0` — verbatim | covered | VER-3 |

## S10 — View an Associated User

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| View goes to the Users page (`/ILCR/users.xhtml`) | S10.feature Then | `navigate({ to: '/mill-associations', search: { userGuid } })` | associations `@S10 @p0` — lands on `/mill-associations` | covered | — |
| The user is carried and pre-selected | S10.feature Then (`userSessionMB.userSelected`) | route consumes `?userGuid=` (BR-02 of UC-MILL-002) → exact directory lookup | associations `@S10 @p0` — the page's lookup names exactly that GUID, and the User details show them; directory STUBBED | covered | SPEC-1 |
| The selected user's associated mills load | S10.feature (pre-selected) | `GET /submitters/{guid}/mills` | associations `@S10 @p0` — mill 9186 listed | covered | — |

## S11 — Search Returns No Mill

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| A zero-match search shows ERR-001 verbatim | S11.feature sc.1 Then; `mill.not.found` | 200 + `message` → dialog notice | search `@S11 @p1` — verbatim, inside the dialog | covered | DIV-1 |
| The dialog stays open, with no results | S11.feature sc.1 | `MillSearchModal.tsx` | search `@S11 @p1` | covered | — |
| A revised search finds the mill | S11.feature sc.2 | same | search `@S11 @p1` — 9186 listed | covered | — |

## S13 — Activation Blocked on a Closed Mill

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| Activating a user on a Closed mill is refused with `You must activate the mill before activating any users.` | S13.feature sc.1; `error.user.activate.millinactive` | `MillAssociationService.activate` → `requireMillActive` (under the mill row lock) | associations `@S13 @p0` — verbatim | covered | — |
| The assignment stays inactive (BR-02) | S13.feature sc.1 Then | same | associations `@S13 @p0` — API `ENDED` | covered | — |
| Activate the mill, then the user succeeds | S13.feature sc.2 | S04 then S09 paths | associations `@S13 @p0` — `…has been activated.` then `…activated for user…`; API `ACTIVE` | covered | VER-3 |

## S14 — Import Fails

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| A failed import shows `ILCR cannot import the Mill. Please refer to logs.` | S14.feature sc.1 Then; `failImportingMillMsg` | `importFailed` 500 → rendered in the import dialog | import `@S14 @p1` — the failure injected in flight, the text verbatim | covered | VER-4 |
| No xref is created (rollback) | S14.feature sc.1 Then | one `@Transactional` import | import `@S14 @p1` — still 404 and still importable; the real rollback is `MillMaintenanceIT.aFailedImportRollsBackWithTheLegacyMessage` | covered | VER-4 |
| Retry and succeed | S14.feature sc.2 | same as S02 | import `@S14 @p1` — second Yes imports; CLS / Y | covered | — |
| Legacy closed the dialog on failure | S14.feature sc.2 Given | new app keeps it open over its results (22.3) | import `@S14 @p1` (retries from the open dialog) | covered | — |

## S15 — Non-Numeric Mill Number

| Source item | Source citation | App enforcement / render point | Scenario (tags) | Status | Gap/defect |
|---|---|---|---|---|---|
| A non-numeric number is refused with the converter message | S15.feature sc.1 (text `[UNKNOWN]` there) | 400 + `javax.faces.converter.IntegerConverter.INTEGER`, ported verbatim | search `@S15 @p1` — `Number: 'abc' must be replaced with a number consisting of one or more digits - decimal values are not accepted.` | covered | — |
| The table is not updated | S15.feature sc.1 Then | no results rendered on an error | search `@S15 @p1` | covered | — |
| Correct the number and search | S15.feature sc.2 | same | search `@S15 @p1` — 9186 listed | covered | — |

## Accessibility (Story 22.4 WCAG criterion)

| State scanned (axe, WCAG 2.1 AA) | Scenario | Status | Gap/defect |
|---|---|---|---|
| Mills, nothing selected | accessibility `@a11y` | covered | — |
| "Find and select Mill" with results | accessibility `@a11y` | covered | — |
| Mills, a mill selected (details, editable panel, licensee table) | accessibility `@a11y` | covered | — |
| "Find and Add User" (directory stubbed, so the picker is enabled) | accessibility `@a11y` | covered | — |
| "Find and select Mill to Import" with results | import `@a11y` (serial with the imports) | covered | — |
| The import Confirmation modal (opened, then declined) | import `@a11y` | covered | — |
| The duplicate-user warning notification | accessibility `@GAP-5` | covered | — |
| The search dialog's error notification | accessibility `@GAP-5` | covered | — |
| A success notification (exists only after a write) | — | deferred | GAP-5 |

---

## Not written

S06 (add a user to the Associated Auditors panel) — the panel is retired, not deferred (DL-23; 22.2
deviation (A)).
