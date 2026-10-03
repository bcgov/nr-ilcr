# Defects — UC-USR-001 Maintain User Accounts and Mill Assignments
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

> **Entry ids:** each register numbers independently, so ids carry their register as a prefix —
> `BUG-n` (Bug / Regression), `DIV-n` (Divergence), `GAP-n` (Coverage gap), `SPEC-n` (Spec gap),
> `VER-n` (Verified — not a defect). Cite the prefixed id when raising a ticket.

Environment for all entries: branch `test/user-mill-administration-e2e` · frontend `:3000` + backend
`:8080` (security off, mock principal, ILCR_ADMIN via `X-Mock-Groups`) · seeded Docker DB
`THE/default@localhost:1525/DBDOCK_01` with `real-test-data-patches/usr/user-admin-anchors.sql` applied
(folded into the CI seed). Data fixtures pinned in `fixtures/usr/users-test-data.ts`. Verified on real
data 2026-09-29.

**Bug / Regression:** _none._

**Divergences:**

- **DIV-1 — From a user, you cannot jump to one of their assigned mills.**
  - **What's wrong:** On the Users page, each row of a user's Associated Mills offers only Activate or
    Deactivate. There is no way to open that mill on the Mills page, and the Mills page would not
    pre-select one anyway: its route reads no carried mill (`frontend/src/routes/mills.tsx` has no
    search parameter at all).
  - **Expected vs actual:** Story 23.3's third criterion and Story 23.4's round-trip criterion both say
    "user → assigned mill lands on the Mills page pre-selected" (legacy UC-USR-001 S06, a View button
    on the mill row, and UC-MILL-002 S01). Actual: no control, no destination. The other direction
    (mill → user) works and is green (UC-USR-002 S01).
  - **How it was caught:** `cross-navigation.feature` `@S06 @discovered-divergence` failed at "I open
    mill "9199" from the selected user's associated mills" with *"the Associated mills row for 9199
    offers no way to open the mill"*. It looks for a link or a button named for the mill, so it does not
    dictate the control.
  - **History:** Story 23.3 wrote this as AC10 "[BLOCKED — do not build]" because the Mills page did not
    exist yet, and closed as done with AC10 unbuilt. Story 22.3 then built the Mills page and the
    mill → user half, but not this half. Nothing recorded it as deferred after that.
  - **Fix:** bcgov/nr-ilcr#527 (Story 23.3 AC10) — a View button on every Associated Mills row, and the
    Mills route consuming `?millId=` with a replace-navigation. Run 2026-09-29 against the seeded stack
    with #527 merged locally: the scenario went green, and the tag and red-title marker came off. The
    scenario now drives the whole round trip (Users → Mills → Users) and axe-scans the carried-mill state.
    **#522 must merge after #527**, or `test:gate` goes red on this scenario.
  - **Status:** RESOLVED by bcgov/nr-ilcr#527, test green 2026-09-29 — CLOSED on BA/QA confirmation.

**Coverage gaps:**

- **GAP-1 — The success notification is not scanned for accessibility.**
  - **What's missing:** Every other page state is axe-scanned (`accessibility.feature`), including the
    warning and error banners. A success banner exists only after a real write.
  - **Why it's low-risk:** It is the same Carbon `InlineNotification` component as the warning and
    error, which scan clean; only its `kind` differs.
  - **Plan:** add a scan to the end of a writing scenario (the journey), if the team wants it. UC-MILL-001
    carries the same gap (its GAP-5).
  - **Status:** OPEN — deferred.

- **GAP-2 — The ADAM Details dialog (S05, S14) cannot be tested: it was not built.**
  - **What's missing:** Legacy's ADAM button showed the user's directory name, phone, email and their
    first mill's address (blank for a user with no mills). The Users page has no such button.
  - **Why:** Story 23.3 wrote it as AC9 and marked it BLOCKED on the directory's account-detail call,
    which Story 23.1/2.3 did not implement (its deviation (F): only the search and exact-lookup
    operations ship). It is gated on DL-27 onboarding like the rest of the directory.
  - **Plan:** none until the account-detail call exists; then S05/S14 become scenarios here. Not written
    as a red test because nothing on the page can be driven — the feature is absent by recorded
    decision, not broken.
  - **Status:** BLOCKED — on DL-27 / the directory account-detail operation.

**Spec gaps:**

- **SPEC-1 — Every scenario stubs the directory, because it is off in every environment.**
  - **What it means:** Finding a user needs the NR User Lookup API, which is switched off everywhere
    (`ilcr.user-lookup.enabled: false`, DL-27): the lookup endpoint does not exist, and the picker says
    *"Directory search is not enabled in this environment yet"* and disables itself. So on a real
    deployment today, **nobody can find a user on this page at all**.
  - **What the suite does:** the one request it does not send to the backend is `GET
    /api/v1/users/lookup`, answered in the browser with the backend's own `DirectoryUser` shape
    (`pages/usr/usersPage.ts` `stubUserDirectory`). The account, the assignments, every message and every
    read-back are real. The same stub is UC-MILL-001's SPEC-1.
  - **Needs:** DL-27 onboarding (service account + Vault secret). Then the stub should be retired for an
    environment that has it.
  - **Status:** OPEN — dependency, tracked on DL-27.

**Verified — not a defect** (recorded deviations, asserted as shipped):

- **VER-1 — A blank search lists nobody (S13).** Legacy listed every eligible user. The directory refuses
  a blank search, so the picker never sends one — deviation (B), Story 2.3; the lost "browse everyone"
  affordance has no owner or ticket. The journey proves no request is sent and no
  message shows. *Note:* `epics.md` Story 23.1 still states the legacy behaviour.
- **VER-2 — The no-match message is reworded (S07).** It reads "…granted the ILCR Submitter role."
  instead of "…granted either the AUDITOR or LICENSEE role in ADAM." — deviation (C): the legacy text
  names retired WebADE concepts.
- **VER-3 — The account's Role and Active are "—", and both Activate and Deactivate are offered, until the
  first account write.** No endpoint reads the account — deviation (O), Story 23.3. The suite therefore
  proves every account write at the DB (`scripts/usr_db_restore.py read-account`), and S12's "the row
  shows Activate, not Deactivate" is asserted as "both, and the flag is 'N' in the DB".
- **VER-4 — Adding a mill here creates the assignment ACTIVE; adding a user on the Mills page creates it
  INACTIVE.** Two different shipped surfaces (AssignmentService vs MillAssociationService, UC-MILL-001
  BR-08). Both match their own legacy screens; recorded so nobody "fixes" one to match the other.
- **VER-5 — There is no Role filter (S10).** The directory cannot be queried by FAM role, so the picker
  filters by identity provider instead — deviation (D), confirmed 2026-08-25. S10 is not applicable.
- **VER-6 — First-time accounts: activate creates ACTIVE, add-mill creates INACTIVE (S11, S12).** The
  legacy asymmetry Story 23.1 says to replicate exactly (DEC-2026-08-11 §4.4 — `ACTIVE_IND` gates
  nothing), plus deviation (L) (activate provisions). Both asserted at the DB.
- **VER-7 — The messages name the user by GUID with the name positions empty** (`User <GUID> -   has
  been activated.`). Deviation (H): the names need the directory. Asserted verbatim.
