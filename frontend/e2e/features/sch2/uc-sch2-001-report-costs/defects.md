# Defects — UC-SCH2-001 Report Purchased and Private Log Costs and Sales (Schedule 2)
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

**First authored: 2026-08-13** (Story 3.4). Every entry below was verified against the running app and the
seeded local delivery DB on that date — none are carried forward from another UC on trust.

**Headline: one genuine Schedule 2 bug found (BUG-1) — now FIXED and CLOSED — and one real divergence.**
BUG-1 was the **Delete** button being offered on a schedule that has never been saved, contradicting
BR-08/S06. It was a Schedule 2 fault (unlike Schedule 11's red, which is an app-wide Carbon defect), it was
found by this suite, and this suite's diagnosis — absent-vs-null under Jackson `non_null`, fix by comparing
`== null` — is exactly what shipped in nr-ilcr #292 on 2026-08-24. The deliberate RED that tracked it has
been retired: the scenario now runs inside `npm run test:gate` as the regression barrier.
Beyond that, Schedule 2 behaved correctly on every path exercised, including the full derived-figure
arithmetic, both Check Status arms, the context guards and the rollback-on-failure path.

Because Schedule 2 was **rebuilt rather than ported**, one behaviour genuinely differs from the legacy
Gherkin — **DIV-1**, derived totals refresh on Save rather than as you type. It is almost certainly
intended (it follows ratified architecture decisions AD-5/AD-6) but it is user-visible, so it is recorded
for BA/QA rather than assumed away.

Two findings looked like divergences at first and were **disproved** by checking the service's documented
null-propagation and the delete contract. Both now sit in *Verified — not a defect* (**VER-1** absent-vs-zero
figures, **VER-2** idempotent delete) so the Divergence register lists only live differences.

**Triaged 2026-08-14.** BUG-1 → [#292](https://github.com/bcgov/nr-ilcr/issues/292), DIV-1 →
[#291](https://github.com/bcgov/nr-ilcr/issues/291), GAP-3 → [#298](https://github.com/bcgov/nr-ilcr/issues/298);
all three sit with the dev, and QA confirms and closes each status line once fixed. No application source
was changed while authoring this suite.

**Correction 2026-08-26.** This line pointed GAP-3 at **#297**, a duplicate raised twelve minutes before
#298 and since **closed**. The live ticket is [#298](https://github.com/bcgov/nr-ilcr/issues/298) — the
number GAP-3's own entry already carried — and the link above is corrected to it. **Two of the three
triaged items are closed on GitHub** — BUG-1 by nr-ilcr #292 and DIV-1 by #291, both verified CLOSED.
**GAP-3 is closed in the code but its ticket is still OPEN:** the covering tests are on
`fix/bugfix-298-schedule2-error-fallback-tests`, unmerged, and
[#298](https://github.com/bcgov/nr-ilcr/issues/298) reads OPEN. This sentence originally said all
three were "fixed and closed", which is the same conflation of *gap closed* with *ticket closed* that
the correction above exists to fix.

---

## Bug / Regression

- **BUG-1 — Delete is offered on a Schedule 2 that has never been saved.**
  _Found by this suite · Schedule 2's own fault · no data loss · misleading confirmation._
  - **What's wrong:** open Schedule 2 for a mill and year where no Schedule 2 has ever been saved. The page
    correctly shows an empty form — but the **Delete** button is active. There is nothing to delete.
    Pressing it opens the "This will delete the current record. Do you want to continue?" confirmation and,
    on confirming, the page reports **"Data deleted successfully"** for a record that never existed.
  - **The same fault has a second face:** immediately *after* a successful delete, Delete stays active
    rather than greying out, so the same already-deleted schedule can be "deleted" repeatedly, each time
    reporting success.
  - **Expected vs actual:** BR-08 / slice S06 say Delete is only offered when the schedule has actually
    been saved and is still editable — the legacy screen did not render the button at all until then.
    Actual: Delete is active for any editable schedule, saved or not.
  - **Evidence:** `frontend/src/components/schedule2/index.tsx:245` —
    `const deletable = editable && data.revisionCount !== null`. The intent is right, but the API omits null
    fields from its JSON entirely (Jackson `non_null`), so an unsaved schedule's `revisionCount` is
    **absent** and arrives as `undefined`. `undefined !== null` is `true`, so the gate always opens.
    Confirmed live 2026-08-13 — `GET /api/v1/schedule2?millId=25053&year=2017` returns no `revisionCount`
    key at all (`keys: editable, lessLogSales, millId, netPurchased, purchasedLogCost,
    purchasedWoodOverhead, subtotal, totalAverage, totalCompanyLogging, trackStatus, year`).
  - **Why the type checker missed it:** `frontend/src/interfaces/Schedule2Response.ts` declares
    `readonly revisionCount: number | null` — **not** optional — so the absent case was never modelled. A
    likely fix is to make it optional and compare with `data.revisionCount == null`, which catches both null
    and undefined. Note `buildRequest` already handles absence correctly via `doc.revisionCount ?? 0`, so
    only this gate is affected.
  - **Impact:** no data loss — the backend's delete is idempotent, so deleting a non-existent schedule is a
    server-side no-op (see VER-2). The harm is a misleading success message and a control that contradicts
    the documented rule: a reporter can be told data was deleted when nothing existed.
  - **Priority / env:** p1 · any never-saved Schedule 2 · local seeded delivery DB.
  - **Ticket:** [bcgov/nr-ilcr#292](https://github.com/bcgov/nr-ilcr/issues/292) — *"[BUGFIX]: Schedule 2
    Delete button should be hidden if now Schedule 2 exists."*
  - **Status:** **CLOSED 2026-08-24 — fixed in nr-ilcr #292** (branch `fix/bugfix-292-schedule2-delete-guard`).
    The fix is the one this entry proposed: a loose `!= null`, now behind `utils/schedule.isScheduleSaved`
    so the rule has a single home, plus the response interface typed as the wire actually sends it
    (`revisionCount?`, and the `CostBlock` members and `comments` likewise, since `non_null` omits those
    too). Delete also moved to the bottom action bar only, restoring the legacy asymmetry
    (schedule2.xhtml:35-36 vs :172-178) — which is why this suite's own `deleteButton` locator had to move
    from `.first()` to `.last()`. **Both faces** are fixed and pinned.
  - **Test:** `render-states.feature` `@p1 @S06` — "Delete is not offered for a never-saved schedule". The
    `@discovered-bug` tag is REMOVED, so it now runs in `npm run test:gate` and guards the fix instead of
    tracking the bug. The second face is pinned in `delete.feature`'s P0 journey, which now asserts that
    Delete goes unavailable again after a successful delete (that assertion was previously declined
    precisely because the button stayed enabled).
  - **What the app fix also had to close, found in its own code review:** the first attempt gated the
    button correctly but left face 2 reachable — the post-delete reload was the only thing that closed the
    gate, and the in-flight lock is released when the DELETE settles, not when the reload lands. So during
    the reload (or forever, if it failed) Delete re-enabled on a deleted record. Now the optimistic-lock
    token is dropped the instant the delete succeeds. Two Vitest cases cover the delayed and the failed
    reload; this suite has no equivalent because it cannot delay a response mid-journey.
  - **Related backend change:** the idempotent no-op DELETE used to answer 200 with *"Data deleted
    successfully"* for a record that never existed (recorded below as VER-2, correctly, as not a data
    defect). It now answers 200 with `noDataToDeleteInfoMsg` — *"No saved data was found, so nothing was
    deleted"* — so the misleading confirmation is gone for every client, not just for the UI whose button
    is now greyed. See VER-2's note.

_No OTHER bugs found._ Every write, guard, validation and derived figure behaved as the contract specifies.
For the record, four things were probed directly against the API looking for trouble and each behaved
correctly: the full derived chain recomputes from entered values (Net Purchased = Subtotal − Log Sales,
Total Average = Net Purchased + Total Company) and matches the rendered figures to the cent; a second save
**overwrites** the two detail records rather than inserting a duplicate pair (revision 1 → 2); a blank cost
persists a real NULL that Check Status then reports; and DELETE restores the anchor to byte-identical
at-rest state.

---

## Divergence

> The app genuinely differs from what the legacy-derived Gherkin describes. **We do not change app source
> to match the spec, and we do not silently drop the spec's version** — each item below is asserted as the
> app actually behaves, and the legacy expectation is recorded here.

- **DIV-1 — Derived figures refresh on Save, not as you type.**
  - **What's different:** on the legacy screen, typing a cost or volume immediately updated the Subtotal,
    Net Purchased and Total Average lines — the totals moved as you typed. In the rebuilt page the totals do
    **not** move while you type; they update when you press **Save**.
  - **Expected vs actual:** slices S13/S14/S15 recovery arms say that after correcting a value "the
    `subtotalCal` / `netPurchasedCal` / `totalAverageCal` element updates" — i.e. on the field's own change
    event. Actual: those figures update only after a successful Save.
  - **How we caught it:** `components/schedule2/index.tsx` renders every derived cell from `data` (the
    server document) while typing only mutates `form`, so no client recomputation exists. Confirmed in the
    browser — the at-rest figures are still on screen after entry and before the Save.
  - **Is it a defect?** Almost certainly not. Every derived and carried figure is now computed server-side
    and never recomputed in the browser (architecture decisions **AD-5/AD-6**; the `Schedule2Service`
    javadoc is explicit that derived values are "computed here … never accepted from a client"). The legacy
    live-update depended on a per-keystroke AJAX round-trip the rewrite deliberately does not make.
  - **Why it is raised anyway:** it is a real, user-visible behaviour change from the spec this suite is
    written against, and only BA/QA can confirm that trading live totals for server-authoritative ones is
    acceptable to reporters. If it is, this entry moves to *Verified — not a defect* and the spec is
    annotated.
  - **Priority / env:** p2 (informational) · local seeded delivery DB.
  - **Ticket:** [bcgov/nr-ilcr#291](https://github.com/bcgov/nr-ilcr/issues/291) — *"[BUGFIX]: Automatic
    Recalculation for Schedule 1, 2, 3 and 4."* Raised app-wide, not just for Schedule 2; the behaviour
    difference is explained in a comment on that issue.
  - **Status:** **RESOLVED 2026-08-21** — recalculation-on-blur restored across Schedules 1-4 on
    `fix/bugfix-291-auto-recalculation`. Spine AD-5 was amended: the "computed server-side, never
    accepted from a client" rule governs authority and persistence, and does not forbid a display-only
    mirror that keeps read-only cells tracking entry before Save. The mirror lives in one `derived.ts`
    per schedule, is never sent on a write, and is superseded by the server echo on every Save.
    `happy-path.feature` was updated as this entry instructed — its pre-save block now carries the
    recalculated figures, deliberately identical to the post-save block, so the scenario asserts
    mirror-vs-server agreement against a real backend rather than pinning the divergence. Trigger was
    on **blur**, not per keystroke, matching the legacy `f:ajax event="change"` handlers.
    Record: `_bmad-output/implementation-artifacts/defect-291-automatic-recalculation-schedules-1-4.md`.
  - **Test:** `happy-path.feature` `@p0 @S01` now asserts the RECALCULATED figures after entry and before
    the Save, and the same figures again after it — so it fails if the mirror and the server ever
    disagree, and fails again if recalculation-on-blur is removed. Frontend unit and RTL coverage sits in
    `components/schedule{1,2,3,4}/__tests__/derived.test.ts` and each page's `#291` tests, with
    expectations transcribed from the backend service tests.

- **DIV-2 — Check Status judges the SAVED schedule and ignores unsaved on-screen edits (APP-WIDE, 11 of 12
  schedules).**
  - **This entry is a POINTER, on purpose.** The full analysis — what legacy did, why the rewrite cannot,
    the app-wide sweep and the fix direction — lives in **ONE** place:
    **`sch3/defects.md` DIV-6** (`features/sch3/uc-sch3-001-report-admin-costs/defects.md`). Do not restate it here. Two copies
    of the same reasoning diverged inside a single session on ilcr-bmad PR #92, so this register carries only
    what is genuinely local to Schedule 2.
  - **What's wrong, in one line:** Check Status reports on the last saved Schedule 2 and silently ignores
    anything typed since, so the purchased-log cost can be empty on screen while the schedule is reported
    complete — or supplied on screen and still reported missing.
  - **Ticket:** [bcgov/nr-ilcr#359](https://github.com/bcgov/nr-ilcr/issues/359) — the same ticket for every
    affected schedule. One fix turns all of these green.
  - **Local facts (this is what belongs here):**
    - **Scenarios:** `check-status-unsaved.feature` `@discovered-divergence @p1 @S17` (the false-GREEN arm)
      and `@S18` (the false-RED arm). Both are needed: they fail in OPPOSITE directions.
    - **Anchors:** two SEEDED, dedicated mill-years — `check-unsaved-violation` (23052/2015) and
      `check-unsaved-fix` (23052/2016), created by
      `real-test-data-patches/sch2/unsaved-check-anchors.sql`. That patch's header records why the extract
      could not supply them. Reusing `check-met` / `saved-incomplete` was tried first and collides with
      S07/S08 under `fullyParallel`, because their Givens seed through the API.
    - **Re-grounding note:** Schedule 2 renders Check Status issues as **warning** notifications, not errors
      (unlike Schedules 1 and 3), so these scenarios assert "the warning" — matching S08.
  - **Priority / env:** p1 · local seeded DB · Chrome.
  - **Status:** OPEN — confirmed and triaged against the shared ticket. Dev to send the on-screen values with
    the check-status request and evaluate those, following Schedule 6's `Schedule6CheckRequest`; QA
    re-verifies and closes this entry when the fix lands. The scenarios assert the CORRECT behaviour, so they
    go green on their own, at which point their tags and `[DISCOVERED …]` title markers come off together.
    No test change is needed. Added 2026-08-27.
  - **Test:** `check-status-unsaved.feature` ×2 — both RED by design.

---

## Coverage gap

> Things the use case asks for that this suite does **not** currently assert, each with the reason. None
> of these is a known fault; they are honest holes.

- **GAP-1 — There is no role-dependent Schedule 2 behaviour reachable from a browser.**
  - **Why not:** the mock auth used by the E2E environment grants a single role per process, so "a user
    without the Schedules permission is denied" cannot be produced from a browser.
  - **Already covered where it belongs:** server-side enforcement **is** present and is covered by
    `Schedule2AuthorizationIT`, `Schedule2WriteAuthorizationIT` and `Schedule2CheckStatusAuthorizationIT`.
  - **The legacy catalogue excluded the same item for a different reason** — it found no documented in-page
    behaviour for a direct-navigation bypass, so there was nothing to slice.
  - **This is the cross-cutting deferral, not a Schedule 2 finding.** It is owned by `deferred-work.md`
    → *"Deferred (cross-cutting): role-gated behaviour cannot be E2E-tested under single-role mock auth
    (2026-08-12)"*, which lists this entry alongside `sch1` GAP-1, `sch11` GAP-6 and `sec` GAP-4. Do not
    re-litigate it per page.
  - **When it is done** (per that entry): QA authors E2E tests for these coverage gaps and runs them against
    the running app, once the role-specific behaviours are actually implemented — then this `Status:` moves.
  - **Status:** OPEN — `blocked` in coverage.md. A gate should treat this as **waived**, not failing.
  - **Test:** none today, by environment limitation rather than by choice.

- **GAP-2 — The validation recovery arms assert acceptance, not recomputation.**
  - **What's missing:** legacy slices S13/S14/S15 each carry a recovery scenario whose tail asserts the
    totals update once a valid value is entered. The bound outlines here assert the value is **accepted**
    (no inline error) but not that figures update — because in this app they cannot until Save.
  - **Why:** this is DIV-1's consequence, not an independent hole. The recomputation itself is fully covered
    by `happy-path.feature`, which asserts every derived figure against real arithmetic.
  - **Status:** OPEN — QA addresses this coverage gap once **DIV-1** ([#291](https://github.com/bcgov/nr-ilcr/issues/291))
    is resolved and closed, since what the recovery arms should assert depends on that outcome.
  - **Test:** `validation.feature` bound outlines cover the acceptance half (`@p2 @S13/@S14/@S15`).

- **GAP-3 — Two page-level fallback messages are untested at any level.**
  - **What's missing:** `Unable to load Schedule 2.` (`index.tsx:56`) and `Unable to delete Schedule 2.`
    (`index.tsx:171`) are the strings the page falls back to when a request fails *and* the response
    carries no `ProblemDetail.detail` — a gateway/proxy error, a dropped connection, an empty-bodied 500.
  - **Checked for existing unit coverage 2026-08-14: there is NONE.**
    `components/schedule2/__tests__/Schedule2.test.tsx` has 11 tests and not one exercises a failed load or
    a failed delete (no match for `Unable to load` / `Unable to delete` / `mapLoadError` / `errorDetail`).
    So these two user-facing strings are unverified at every level today.
  - **Is a test warranted? Yes — one small Vitest case each, NOT an E2E scenario.** They are pure
    client-side branches: no request shape, no persistence, no cross-schedule arithmetic. A Vitest test can
    reject the mocked axios call with a detail-less error and assert the fallback renders, deterministically
    and without the stack. E2E would need a second route-interception fixture, take ~10s per scenario, and
    prove strictly less.
  - **And unlike the backend ITs, Vitest DOES gate:** CI runs `npm run test:cov` (`analysis.yml`), so a
    regression in these fallbacks would fail the build. (This bullet used to add "which an E2E test could
    not claim, since the data-backed suite is a manual gate" — no longer true since upstream #327 runs the
    full suite on every PR. The placement argument above is unaffected: it rests on cost and on where a
    route-interception fallback belongs, not on which suite gates.)
  - **Where it belongs — NOT here.** A component test on `components/schedule2` is Schedule 2 story
    territory (3.3), not this verification story (3.4); and this suite changes no files outside
    `frontend/e2e/`. Two cases, ~10 lines, beside the existing 11.
  - **Ticket:** [bcgov/nr-ilcr#298](https://github.com/bcgov/nr-ilcr/issues/298) — *"[BUGFIX]: Schedule 2:
    add unit tests for the two load/delete error fallback messages."* (#297 is a closed duplicate of it —
    see the Correction under the headline.)
  - **Status:** **CLOSED 2026-08-26** — both cases landed in `Schedule2.test.tsx` on nr-ilcr
    `fix/bugfix-298-schedule2-error-fallback-tests`, and coverage.md's `deferred` row flipped with them.
    **Eleven Vitest cases, not two** (counted at PR #364 review, paulushcgcj — this entry said five,
    which was the implementation-time figure and stale by two rounds of review). The ticket's single
    "detail-less error" is **four** distinct shapes on the wire — a dropped connection (no `response`
    property at all), an empty-bodied 500 (`response.data` is `''`), a gateway problem+json (every
    field but `detail`), and a **blank** `detail` (present but falsy, the only shape that pins `||`
    against a swap to `??`). Load runs all four and delete runs all four, plus a separate
    detail-BEARING DELETE case: **9 in `Schedule2.test.tsx`**, and **2** in the new
    `fallback-strings.test.ts` tripwire. The targeted suite went 43 → **54**. Two line numbers in this entry were **stale**: the sites are
    `index.tsx:60` and `:162`, not `:56`/`:171` — the delete fallback moved when #292 restructured
    `handleDelete` around the delete→reload lock. The "11 tests" count was 25 by the time the fix was
    written (#291 and #292 added cases in between); the claim it supported — no match for `Unable to
    load` / `Unable to delete` anywhere in the suite — was re-verified and held.
  - **One correction to this entry's own prescription:** "reject the mocked axios call" describes a
    pattern this file does not use. `Schedule2.test.tsx` mocks at the **HTTP boundary** with MSW; its only
    `vi.mock` is TanStack Router. Stubbing axios would have bypassed the `apiService` interceptors that
    shape the very error object `extractDetail` reads. The cases use MSW, matching this suite's sibling
    unit files and `Schedule6.test.tsx`, which #332 names as the pattern to copy.
  - **Test:** `Schedule2.test.tsx` — *"a load failure carrying no detail falls back to the generic load
    message (AC7, defect #298)"* ×3 and *"a DELETE failure carrying no detail falls back to the generic
    delete message and leaves the record intact (AC5, defect #298)"* ×2. Both **mutation-proved**: they
    fail when the fallback is emptied, and the load case fails even when only its full stop is dropped.
    **Two claims first written here were corrected by the #298 code review.** (1) An emptied fallback does
    not render "an empty subtitle" — `errorDetail` becomes falsy, so the error branch is skipped and the
    page returns `null`: a blank screen, header and all. (2) `findByText(/unable to load/i)` does not
    "pass over a deleted branch" — it fails both ways (nothing renders, or title and subtitle both match
    and it throws). The trap needs a *reworded* non-empty fallback. The assertions now compare the whole
    notification set — count, `kind`, title, subtitle — which also pins the WCAG severity contract that
    flipping both banners to `kind="success"` used to leave green, and a fourth shape (a blank `detail`)
    pins `||` against a swap to `??`. The sibling *save*
    fallback (ERR-003) is covered end-to-end by `save-error.feature` `@p1 @S12`; Schedule 2's remaining
    uncovered fallback, `Unable to check status.` (`index.tsx:216`), belongs to
    [#332](https://github.com/bcgov/nr-ilcr/issues/332), not here.

- **GAP-4 — The validation-error state is not swept by axe here, deliberately.**
  - **What's missing:** Schedule 2's accessibility sweep covers four renders (editable-and-populated,
    read-only, the Check-Status result, a guard state) but omits the validation-error state.
  - **Why:** sweeping it would re-find a single already-triaged, **app-wide** defect — Carbon's `TextInput`
    invalid state wires `aria-errormessage` to an element it never announces (axe rule
    `aria-valid-attr-value`, impact critical), so a field error never reaches assistive technology. It
    affects every schedule page, is recorded in `deferred-work.md`, and is already carried as the standing
    red in `features/sch11/uc-sch11-001-report-costs/accessibility.feature` (that UC's BUG-1).
  - **Why a Coverage gap here, when Schedule 11 files it as a Bug.** The defect is owned by
    `deferred-work.md` → *"Deferred (cross-cutting): validation errors are never announced to assistive
    technology (app-wide WCAG 4.1.2)"*. Its original 2026-07-30 note specifically asked for a
    deliberately-RED check **on Schedule 11**, so sch11 carries one and it stays. That is specific to
    Schedule 11 — not a claim that its red covers this page. Every other page, and every page from here on,
    records the item as a Coverage gap pointing at that section instead.
  - **What is genuinely NOT covered:** Schedule 2's validation-error state is **unswept**. A
    Schedule-2-specific accessibility problem in that state would not be caught today. This entry is the
    record of that, not a waiver.
  - **Does it block the AC?** No. Story 3.4 AC2 is "zero violations **or** triaged exceptions" — the
    `deferred-work.md` entry is that disposition, and Schedule 2's four swept renders are clean.
  - **When it is done:** remove `aria-valid-attr-value` from `KNOWN_A11Y_RULES` in `pages/common/axe.ts`.
    QA then authors and runs the `@a11y` sweep of the validation-error state on every page that skipped it,
    Schedule 2 included, and closes this gap — the fix is not proven on this page until it is swept here.
  - **Status:** DEFERRED 2026-08-14 — owned by the cross-cutting `deferred-work.md` entry; nothing owed by
    this story.
  - **Test:** four clean sweeps in `accessibility.feature`; the fifth state intentionally not swept.

- **GAP-5 — CLOSED 2026-08-14, not pursued: a stale domain list in a CI workflow comment.**
  - `.github/workflows/reusable-tests.yml` described the manual gate by naming domains, so the list
    went stale each time a suite lands. Cosmetic only — the job greps `@smoke` and was unaffected.
  - **Moot since 2026-08-28:** upstream #327 rewrote that comment block wholesale (the job now runs the
    full suite, no domain enumeration), so the stale list is gone without anyone editing it for its own sake.
  - **Closed deliberately.** Editing a shared CI file pulls in reviewers for a comment that changes no
    behaviour. Not worth the churn; whoever is next in that file can drop the enumeration if they care.
  - **Status:** CLOSED — won't pursue. No action owed by anyone.

---

## Spec gap

> The requirements/Gherkin do not describe behaviour the app genuinely has. These feed back to the BA,
> not to the dev team.

**None — nothing is owed here.** Every one of the 18 slices in the catalogue has a feature file (S17/S18
were added upstream 2026-08-27 by ilcr-bmad PR #92 and are correct as written — they are `deferred` for
coverage reasons, not a spec problem), and the 21 scenarios projected from S01–S16 match the slice
descriptions (including the recovery arms for S09, S10, S12, S13, S14 and
S15). The reconciliation in `coverage.md` found no scenario the source documents list but the Gherkin
omits.

---

## Verified — not a defect

> Checked because something looked wrong or unknown, and confirmed correct. Recorded so nobody re-opens
> them.

- **VER-1 — Blank fields produce *absent* figures, not zeros.**
  - **Why it looked wrong:** slice S04 says the Net Purchased and Total Average figures "compute using zero
    for the log-sales offset". The app instead leaves the volume and $/m³ cells **empty** (an em dash) and
    carries the cost through unchanged, which reads at first like a lost calculation.
  - **What we found:** it is correct. The service's documented null-propagation mirrors the legacy
    `CoreUtil` — subtraction returns the minuend when the subtrahend is null, and addition returns the
    non-null operand — so with no log-sales values the net is the subtotal *unreduced*. That is exactly the
    "zero offset" outcome S04 describes, expressed as "no value" rather than a literal 0.
  - **Evidence:** probe 2026-08-13 — `PUT` cost 8000 with both log-sales fields null →
    `netPurchased {cost: 8000}`, no volume, no perUnit.
  - **Verdict:** Not a defect — deliberate legacy fidelity. **Now asserted as-is** by
    `blank-fields.feature` `@p1 @S04`.
  - **Status:** CLOSED as verified 2026-08-13.

- **VER-2 — Delete on a schedule with no summary returns success rather than 404.**
  - **Why it looked wrong:** calling DELETE for a mill/year that has never had a Schedule 2 answers 200
    with "Data deleted successfully", which looks like the app confirming work it did not do.
  - **What we found:** it is deliberate and documented — `Schedule2Service.deleteSchedule2` returns early
    when no summary exists, so DELETE is idempotent by contract. Schedule 2 never 404s on its own summary
    (unlike Schedule 1, whose read does).
  - **Verdict:** Not a defect. **It is not BUG-1 either** — BUG-1 is that the *button* is offered in the
    first place, which is a frontend gating fault. The idempotent endpoint is the correct backend
    behaviour, and it is exactly what makes cleanup safe: the suite restores every mutating anchor by
    calling DELETE unconditionally.
  - **Status:** CLOSED as verified 2026-08-13. **Amended 2026-08-24 (nr-ilcr #292):** the verdict on the
    *status* stands — the 200 is deliberate and the suite's unconditional-DELETE cleanup still depends on
    it — but the *message* was changed. #292's code review argued that a UI-only gate leaves the misleading
    confirmation live for every other caller (a second tab, a replayed request, any non-UI client), which
    the project's own rule that the backend enforces and the frontend advises makes hard to defend. The
    no-op now answers 200 with `noDataToDeleteInfoMsg` — *"No saved data was found, so nothing was
    deleted"* — instead of borrowing `dataDeletedSuccesfullyInfoMsg`. Cleanup is unaffected (still 200,
    still idempotent); only the text differs. If a scenario ever asserts the cleanup DELETE's message,
    that is the one to expect.
