# Defects — UC-USR-002 Maintain User from a Mill Record
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

> **Entry ids:** each register numbers independently, so ids carry their register as a prefix —
> `BUG-n` (Bug / Regression), `DIV-n` (Divergence), `GAP-n` (Coverage gap), `SPEC-n` (Spec gap),
> `VER-n` (Verified — not a defect). Cite the prefixed id when raising a ticket.

Environment: as UC-USR-001's `defects.md` (same branch, stack, seed patch and fixture file).

**Bug / Regression:** _none._

**Divergences:**

- **DIV-1 — A user carried from a mill shows the picker set to "IDIR", although the user is a BCeID
  Business licensee.**
  - **What you will see:** After View on the Mills page, the Users page selects the right user, but the
    picker above them reads *Identity provider: IDIR · Search by: User ID* with the BCeID user's name in
    the box. Typing into it then searches the IDIR directory.
  - **Why it might be intended:** The picker only controls the NEXT search; the carried user is looked up
    by GUID as BCeID Business and is shown correctly in the User details table. Nothing in Story 23.3
    rules on what the picker should show for a carried user.
  - **What the scenarios do:** assert the selected user (the details table), not the picker's provider,
    so they pass under either ruling. Low impact: an administrator who wants another user chooses the
    provider anyway.
  - **Needs:** a PO/UX ruling — leave as is, or preset the picker to the carried user's provider.
  - **Status:** OPEN — for a ruling, not raised as a bug.

**Coverage gaps:** _none._ Every live slice is a green scenario (see `coverage.md`).

**Spec gaps:**

- **SPEC-1 — The carried user is resolved through the stubbed directory.** The Users page looks the
  carried GUID up in the NR User Lookup directory, which is off in every environment (DL-27). The suite
  answers that one lookup in the browser; the Mills page, the navigation and everything after arrival are
  real. Same dependency as UC-USR-001 SPEC-1 and UC-MILL-001 SPEC-1. **Status:** OPEN — dependency.

**Verified — not a defect:**

- **VER-1 — The hand-off is carried in the URL and cleared on arrival (BR-02).** Legacy carried the user in
  the session and cleared it after use; here the Mills page's View navigates to
  `/mill-associations?userGuid=<guid>` and the page replaces that with the bare URL once it has read it.
  Observable now, and asserted: the URL is bare, and opening Users again selects no one.
- **VER-2 — BR-03 (release the extra database session) has no equivalent.** The rebuild opens no second
  session for the redirect; there is nothing to release or test.
- **VER-3 — "Activate or Deactivate according to the active flag" (S01) shows both.** UC-USR-001 VER-3 —
  no endpoint reads the account (deviation (O)).
- **VER-4 — A malformed or unresolvable carried user selects no one.** The route drops a `userGuid` that is
  not 32 hex characters before any lookup; a well-formed one the directory cannot resolve (a departed
  user, retired Story 2.5 AC4) shows *"The user carried over from the Mills page could not be looked up,
  so no user is selected."* Both per Story 23.3 ruling (N). Asserted in `carried-user.feature`.
