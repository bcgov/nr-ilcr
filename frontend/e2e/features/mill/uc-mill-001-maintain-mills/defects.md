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

**Divergences:**

- **DIV-1 — A search that matches no mill reads as information, not as an error.**
  - **What you will see:** In "Find and select Mill", a zero-match search shows the legacy sentence
    `No mill matching this criteria has been found. …` verbatim, in a blue **info** notification titled
    "No matches". Legacy showed the same sentence as an **error** in the page's message panel (S11).
  - **Why it might be intended:** The backend answers the zero match with a 200, not an error status,
    so that the dialog stays open for a retry (Story 22.1), and the dialog treats "nothing found" as an
    outcome rather than a failure. The text is unchanged. Nothing I found in Story 22.3 rules on the
    severity either way.
  - **What the scenario does:** `search.feature` `@S11` asserts the sentence inside the dialog and does
    not assert the severity, so it passes under either ruling.
  - **Needs:** a product-owner ruling. Keep info (and record it as a deviation), or match legacy with an
    error.
  - **Status:** OPEN — for a ruling, not raised as a bug.

**Coverage gaps:**

- **GAP-1 — Clearing a contact to "(None)" is not tested.**
  - **What's missing:** Choosing "(None)" in either contact dropdown and saving clears that column. No
    scenario does it.
  - **Why it matters:** The legacy slice catalogue excluded this case because legacy showed the same
    message either way. In the new app the outcome IS distinct in storage — the column becomes empty —
    and the page leads both dropdowns with an explicit "(None)" precisely because a blank is a delete.
  - **Plan:** a p2 arm on S01's mill once the remaining slices are in; it restores through the same
    cleanup.
  - **Closed by:** `happy-path.feature` `@GAP-1` (2026-09-29), on its own mill, 26059. Clearing the Division Contact to "(None)" and saving empties that column; the API read-back shows the head office and status untouched.
  - **Status:** CLOSED

- **GAP-2 — Save-gating for a mill whose head-office indicator was never set is not tested.**
  - **What's missing:** Decision D5 (Story 22.3) disables Save until an administrator explicitly picks
    Yes or No on a mill whose indicator is empty, because legacy silently wrote "No" over it.
  - **Why it is not in S01:** S01's mill already holds "Yes". Every one of the extract's 21 tracked mills
    holds Y or N, so the state needs seeded data — and it is a new-app rule, not part of the legacy
    Gherkin.
  - **Closed by:** NOT a scenario, because the state cannot exist in delivery (VER-5). Delivery's audit trigger `IMSXA_B_I_U` copies every INSERT and UPDATE of the xref into `ILCR_MILL_STATUS_XREF_AUDIT`, whose `HEAD_OFFICE_CONTACT_IND` is NOT NULL. So no row can be written with a NULL indicator. Seeding one failed with ORA-01400, and 0 of the extract's 21 rows hold one. D5's gate is purely defensive and stays pinned by the frontend component test `Mills.test.tsx` ("an unset head-office indicator renders no selection and gates Save (D5)").
  - **Status:** CLOSED — not reachable

- **GAP-3 — "A non-administrator cannot reach Mills" is not tested end to end.**
  - **What's missing:** The Administration menu is hidden from a submitter, and every mills endpoint is
    ADMIN-only. The suite's default identity is a submitter, so this is reachable; it is simply not
    written yet.
  - **Already covered below e2e:** the frontend's `navigation.test.ts` pins the admin-only paths, and
    `MillMaintenanceControllerAuthorizationTest` pins the action on every endpoint.
  - **Closed by:** `access.feature` (2026-09-29). The submitter is offered no Administration group and
    no Mills link, and the mills API answers 403 to both a read and a write.
  - **Status:** CLOSED

- **GAP-4 — Mill search by Status, and the import dialog's search by Name, are not tested.**
  - **What's missing:** The mill search allows Number, Name and/or Status. Number is covered (S01 and
    others) and Name is covered (S11's zero-match search). Status is not, and neither is the import
    dialog's Name field.
  - **Why:** A status filter needs a mill whose status stays put for the whole run. Every Closed seeded
    mill is one another scenario activates, so a status search against them would race. It needs a
    dedicated, never-written Closed mill.
  - **Closed by:** `search.feature` `@GAP-4` (Name "E2E-" + Status "Close" lists the never-written Closed mill 9191, every listed row reads Close, and the never-written Active mill 9186 is absent) and `import.feature` `@GAP-4` (the import dialog finds 9188 by name). 2026-09-29.
  - **Status:** CLOSED

- **GAP-5 — The Confirmation modal and the page's notifications are not swept for accessibility.**
  - **What's covered:** axe (WCAG 2.1 AA) sweeps of every page state: nothing selected, the search
    dialog with results, a mill selected, Find and Add User, and the import dialog with results
    (`accessibility.feature`, plus one in `import.feature`).
  - **What's missing:** the import Confirmation modal, and the success, warning and error notifications,
    which only exist after a write.
  - **Closed by:** the import Confirmation modal (opened, scanned, declined — nothing imported), the duplicate-user WARNING, and the search dialog's ERROR (`import.feature`, `accessibility.feature` `@GAP-5`, 2026-09-29). STILL OPEN: the SUCCESS notification, which only appears after a write.
  - **Status:** OPEN (narrowed to the success notification)

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
  - **Closed by:** `status.feature` `@GAP-6` (2026-09-29), on two more seeded mills. 26061 has NO current-year records, so activate ENROLS them: the working context 404s beforehand and resolves afterwards, and Schedule 1 opens. The DB cleanup (`mill_db_restore.py forget-enrolment`) deletes what it wrote. 26062 has the status row ALONE, so activate is refused verbatim with `error.mill.activate.partialrecords`, stays Closed, and is left exactly as found.
  - **Status:** CLOSED

- **GAP-7 — Adding a user who has never had an ILCR account is not tested end to end.**
  - **What's missing:** When S05's user has no `ILCR_USER` row yet, the add creates one first
    (`provisionAccountIfAbsent`) and then the association. S05's user is seeded WITH an account, so
    only the association branch runs.
  - **Why:** An account row is referenced by the account screens too, and the cleanup would have to
    delete it as well. Seeding it keeps S05's cleanup to one row.
  - **Already covered below e2e:** `MillAssociationService` unit and integration tests (Story 22.2).
  - **Closed by:** `associations.feature` `@GAP-7` (2026-09-29). A user with no `ILCR_USER` row is added to 26063; the add provisions the account and then the inactive association (the association's user FK would refuse it otherwise). The cleanup deletes both.
  - **Status:** CLOSED

**Spec gaps:**

- **SPEC-1 — Add User and View depend on a directory that is off in every environment.**
  - **What you will see:** On a real deployment today, "Find and Add User" shows its picker disabled
    with a "directory not available" note, so no user can be added from the Mills page. View opens the
    Users page but says the carried user "could not be looked up, so no user is selected".
  - **Why:** Both ask the NR User Lookup directory (DL-27), which is switched off everywhere
    (`ilcr.user-lookup.enabled: false`) until its service account exists. Without it
    `/api/v1/users/lookup` answers 404.
  - **What the scenarios do:** S05, S07, S10 and the Add dialog's a11y sweep stub that ONE request in
    the browser, with a record in the backend's own `DirectoryUser` shape. Everything after it is the
    real backend: the add, the duplicate check, the association list, the messages and the carried
    user's mills. So they prove the page and backend work once the directory is on, not that the
    directory works.
  - **Needs:** nothing from this suite. Worth knowing when the story is demoed or accepted: these two
    features are not usable until DL-27's directory access is in place.
  - **Status:** OPEN — external dependency.

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

- **VER-3 — The user-association messages name the user by GUID, with the name positions blank.**
  - **What you will see:** `Mill 9184 - E2E-USERS-TEST has been deactivated for user
    E2E00000000000000000000000000002 -  .` Legacy's sentence is `… for user {2} - {3} {4}.`, with the
    user id then first and last name.
  - **Why it is fine:** an association row knows only the user's GUID. The backend deliberately leaves
    the two name positions empty rather than guess them or repeat the GUID
    (`MillAssociationController` `NAME_UNRESOLVED`, Story 22.2). The scenarios pin the sentence
    exactly as served.
  - **Status:** permanent (until the names can be resolved from the directory).

- **VER-4 — S14's import failure is injected, not caused.**
  - **What you will see:** `import.feature` `@S14` answers the ONE import request in flight with the
    service's own failure response (HTTP 500 and `ILCR cannot import the Mill. Please refer to logs.`),
    then lets the retry reach the real backend.
  - **Why it is fine:** the service fails an import only on an integrity error — a concurrent import of
    the same mill, or corrupt leftover report rows — and no real data produces one on demand. The
    scenario proves the page's side: the message, nothing tracked, and a working retry. The rollback
    itself is pinned where it can be caused: `MillMaintenanceIT.aFailedImportRollsBackWithTheLegacyMessage`.
  - **Status:** permanent.

- **VER-5 — A mill whose head-office indicator was never set cannot exist in delivery.**
  - **What you will see:** nothing on the page. Found while seeding GAP-2: an xref row with a NULL
    `HEAD_OFFICE_CONTACT_IND` fails its INSERT with `ORA-01400: cannot insert NULL into
    ("THE"."ILCR_MILL_STATUS_XREF_AUDIT"."HEAD_OFFICE_CONTACT_IND")`, raised by the audit trigger
    `IMSXA_B_I_U`. The xref column itself is nullable with `DEFAULT 'Y'`, but the trigger copies every
    INSERT and UPDATE into the audit table, whose column is NOT NULL.
  - **Why it is fine:** Story 22.3's D5 (Save disabled until the administrator picks Yes or No on a
    never-set indicator) guards a state delivery cannot hold; no extract row holds it. The gate is
    harmless and defensive. It is reachable only in CI's Flyway schema, which has no audit trigger, so
    an e2e scenario for it would test a state production cannot produce.
  - **Status:** permanent (worth telling the 22.3 owner: D5 is defensive, not load-bearing).
