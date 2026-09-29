# Defects — UC-MILL-001 Maintain Mills, Status, and User Associations
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

> **Entry ids:** each register numbers independently, so ids carry their register as a prefix —
> `BUG-n` (Bug / Regression), `DIV-n` (Divergence), `GAP-n` (Coverage gap), `SPEC-n` (Spec gap),
> `VER-n` (Verified — not a defect). Cite the prefixed id when raising a ticket.

Environment for all entries: branch `test/mills-administration-e2e` · frontend `:3000` + backend `:8080`
(security off, mock principal, ILCR_ADMIN via `X-Mock-Groups`) · seeded Docker DB
`THE/default@localhost:1525/DBDOCK_01`. Data fixtures pinned in `fixtures/mill/mills-test-data.ts`.
Verified on real data 2026-09-28. The status slices (S03/S04/S12) also need
`real-test-data-patches/mill/mill-status-anchors.sql` applied; it is folded into the CI seed.

**Bug / Regression:** _none._

**Divergences:** _none._

**Coverage gaps:**

- **GAP-1 — Clearing a contact to "(None)" is not tested.**
  - **What's missing:** Choosing "(None)" in either contact dropdown and saving clears that column. No
    scenario does it.
  - **Why it matters:** The legacy slice catalogue excluded this case because legacy showed the same
    message either way. In the new app the outcome IS distinct in storage — the column becomes empty —
    and the page leads both dropdowns with an explicit "(None)" precisely because a blank is a delete.
  - **Plan:** a p2 arm on S01's mill once the remaining slices are in; it restores through the same
    cleanup.
  - **Status:** OPEN

- **GAP-2 — Save-gating for a mill whose head-office indicator was never set is not tested.**
  - **What's missing:** Decision D5 (Story 22.3) disables Save until an administrator explicitly picks
    Yes or No on a mill whose indicator is empty, because legacy silently wrote "No" over it.
  - **Why it is not in S01:** S01's mill already holds "Yes". Every one of the extract's 21 tracked mills
    holds Y or N, so the state needs seeded data — and it is a new-app rule, not part of the legacy
    Gherkin.
  - **Status:** OPEN

- **GAP-3 — "A non-administrator cannot reach Mills" is not tested end to end.**
  - **What's missing:** The Administration menu is hidden from a submitter, and every mills endpoint is
    ADMIN-only. The suite's default identity is a submitter, so this is reachable; it is simply not
    written yet.
  - **Already covered below e2e:** the frontend's `navigation.test.ts` pins the admin-only paths, and
    `MillMaintenanceControllerAuthorizationTest` pins the action on every endpoint.
  - **Status:** OPEN

- **GAP-4 — Mill search by Name and by Status is not tested.**
  - **What's missing:** S01's search step allows Number, Name and/or Status. S01 searches by Number only,
    because that is the criterion that identifies one mill. The search guard slices (S11, S15) are still
    to come.
  - **Status:** OPEN

- **GAP-5 — Accessibility (axe) sweeps for the Mills page are not written yet.**
  - **What's missing:** Story 22.4 asks for WCAG violations to be zero or triaged. No `@a11y` scenario
    exists for this page yet.
  - **Status:** OPEN

- **GAP-6 — Activation's "create the records if missing" branch, and its partial-set refusal, are not
  tested end to end.**
  - **What's missing:** BR-07 says activating a mill creates its current-year report records when they
    are missing. S04 activates a mill that already has the complete set, so it proves the records EXIST
    after activation, not that activation CREATES them. The new-app refusal of a half-written set
    (Story 22.1 D7, `error.mill.activate.partialrecords`, 409) is not exercised either.
  - **Why S04 does not do it:** the create branch writes twelve rows (a report-status row and eleven
    category rows) that no endpoint can delete, so no cleanup could leave the database as it was found.
    Every mill in the extract that could take it would be permanently enrolled, and the next run would
    no longer be testing the create branch.
  - **Already covered below e2e:** the service's NONE / COMPLETE / PARTIAL branches have unit and
    integration tests (Story 22.1).
  - **Plan:** only with a DB-level restore, like Schedule 1's delete scenario
    (`steps/sch1/schedule1DbRestore.ts`). S02 (import) needs the same bridge for the same reason, so the
    two should share it.
  - **Status:** OPEN

**Spec gaps:** _none._

**Verified — not a defect:**

- **VER-1 — The mill's "Last Edited by / on date" and its revision count change every run, and the
  cleanup does not put them back.**
  - **What you will see:** after an e2e run, mill 9171's audit line reads the e2e administrator
    (`dev-admin` locally) and today's date, and its revision count has gone up — even though its
    head-office indicator and contacts are back exactly as they were.
  - **Why it is fine:** every save stamps the acting user and increments the revision, and no endpoint
    writes either one, so no cleanup can restore them. Nothing reads them as fixed values: the scenario
    reads the audit line from the API at run time, the preflight checks only the indicator and contacts,
    and the save's optimistic lock is always given the revision just read.
  - **The same is true of the three status anchors (26050–26052).** Every deactivate or activate, the
    scenario's and the cleanup's, stamps the row and bumps its revision, so their "Last Edited by"
    stops reading the seeded `E2E_SEED_MILLSTAT` after the first run. The per-user rows on 26052 drift
    the same way. The status scenarios read the audit line from the API too, and every status write
    passes the revision just read.
  - **Status:** permanent.

- **VER-2 — The saved message prints the mill number without a thousands separator.**
  - **What you will see:** `Mill 9171 - BCOVEY-TEST has been saved.` Legacy printed `Mill 9,171 - …`.
  - **Why it is fine:** a recorded, ratified deviation (Story 22.1 deviation (E)): the mill number is an
    identifier, not a quantity, and travels as text.
  - **Status:** permanent.
