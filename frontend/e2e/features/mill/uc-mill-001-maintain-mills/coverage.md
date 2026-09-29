# UC-MILL-001 — Maintain Mills, Status, and User Associations — coverage

**New to these files?** `e2e/coverage-guide.md` explains the columns and the status legend;
`e2e/defects-guide.md` explains the sibling `defects.md`. Read those first — this file is a ledger, not
a narrative.

**Where the source documents live.** The Gherkin, the slice catalogue and the UC sidecars are in the
**`ilcr-bmad` planning repo**, not here, so no relative link from this file resolves:

- Gherkin — `_bmad-output/implementation-artifacts/tests/UC-MILL-001/gherkin/UC-MILL-001-S<nn>.feature`
- Slice catalogue — `_bmad-output/planning-artifacts/requirements/use-cases/UC-MILL-001/UC-MILL-001-slices.md`
- Detailed UC / technical sidecar — same directory, `-detailed.md` / `-technical.md`

**STATUS 2026-09-28 — IN PROGRESS. 4 of 15 slices** (S01, S03, S04, S12). Story 22.4 (bcgov/nr-ilcr#157)
is authored a slice at a time; the slices not yet reached are listed at the bottom so the ledger shows the
whole scope. Accessibility (the story's WCAG criterion) has not been started — `defects.md` GAP-5.

---

## Suite state

Measured, never incremented — re-measure rather than editing these numbers by hand:

```
features/mill/**/*.feature                    2 files — happy-path, status
scenarios (bddgen, @UC-MILL-001)              4  — S01, S03, S04, S12
  ...of which pass `npm run test:gate`          4  (2026-09-28 full gate: 531/544; all 13 reds were
                                                 schedule-domain load failures — all 13 green re-run
                                                 serially, the last 2 once their own cleanups had
                                                 cleared residue the crashed runs left behind)
preflight/mill-anchors.setup.ts               4 checks (one per anchor; the 3 status anchors
                                                 also check active users + current-year records)
pinned mill anchors                           4  — 25050 (9171 BCOVEY-TEST, extract, shared)
                                                 + 26050/26051/26052 (9181-9183, SEEDED), all mutating
@discovered-divergence / @discovered-bug      0
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
| The menu (and so the page's visible path) is hidden from a non-administrator | slices.md S01 Entry Point Reachability; BR-10 | `routes/-navigation.ts` `adminOnly` filter; MAINTAIN_MILLS on every endpoint | — | deferred | GAP-3 |
| Nothing selected: only Select Mill and Import Mill are offered (STA-001 state 1) | S01.feature sc.1; technical.md STA-001 | `components/mills/index.tsx` `!mill` branch | happy-path `@S01 @p0` | covered | — |
| Select Mill opens the "Find and select Mill" dialog | S01.feature sc.1 | `MillSearchModal.tsx` | happy-path `@S01 @p0` | covered | — |
| Search by Number | S01.feature sc.1 ("Number:, Name:, and/or Status:") | `MillSearchModal.tsx` `millNumber` param | happy-path `@S01 @p0` | covered | — |
| Search by Name, and by Status | S01.feature sc.1 (same step) | `MillSearchModal.tsx` `millName` / `status` params | — | deferred | GAP-4 |
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
| A contact left blank ("(None)") clears that column | slices.md Gap Analysis — Optional-exercised (excluded there as "no distinct outcome") | `index.tsx` NO_CONTACT → `null` → column cleared | — | deferred | GAP-1 |
| Save is gated until a never-set head-office indicator is chosen | 22.3 D5 (legacy defect fixed); not in the legacy Gherkin | `validation.ts` `canSaveContacts` | — | deferred | GAP-2 |
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
| ...CREATED if missing (BR-07, the enrol branch) | S04.feature Then ("created if missing") | `enrolMillInYear` on `EnrolmentState.NONE` | — | deferred | GAP-6 |
| A partial record set refuses the activation (22.1 D7) | new-app rule, not in the legacy Gherkin | `error.mill.activate.partialrecords` 409 | — | deferred | GAP-6 |

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

## Not yet reached (Story 22.4 scope)

S02 import · S05 add a licensee user · S06 add an auditor · S07 duplicate warning · S08 deactivate an
association · S09 activate an association · S10 view the user · S11 zero-match search · S13 activation
blocked on a closed mill · S14 import fails · S15 non-numeric mill number. The story's acceptance criteria
name S01–S05, S07, S08, S11, S12 and S15; S06 is retired (DL-23), and S09, S10, S13 and S14 are in the UC
but not in the story's journey — to be ruled on when reached.
