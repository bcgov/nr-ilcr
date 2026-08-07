# Defects — UC-SCH1-001 Report Average Cost of Logging (Schedule 1)
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

**Last re-verified: 2026-08-07** (branch `e2e/schedule1-recheck-and-defect-restale`). Every entry below was
re-checked against the app as it stands today, not carried forward on trust. The app moved underneath this
log — backend commit `0b58057` "restore legacy parity for derived costs, audit columns" and the bcgov
`EditableSubPageLayout` sync both changed behaviour this file described — so one Divergence was retired as
obsolete, one follow-up was confirmed done, one Coverage gap was closed, and three new findings were added.

**Bug / Regression:**

- **#1 — Adding a Subtotal Other Costs line item fails with a 500 on the delivery database.**
  - **What's wrong:** On the delivery Oracle DB, clicking **Add** on the Other Costs sub-page (or `POST /api/v1/schedule1/other-costs`) fails — the page shows "Other cost could not be saved." and no row is added. The same defect blocks any *first-time* Schedule 1 detail insert; the main Save (S01) escapes it only because it *updates* rows that already exist in the seed.
  - **Expected vs actual:** Expected the row to be added and the API to return "Data saved successfully" with the row persisted. Actual — HTTP 500 `"Schedule could not be saved."`, nothing persisted.
  - **How we caught it (verified on real data 2026-07-30):** `POST /api/v1/schedule1/other-costs?millId=25050&year=2017` and `…millId=9050&year=2017` both → HTTP 500. Reproduced at the DB: the app's `INSERT` into `THE.ILCR_COST_REPORT_DETAIL` raises `ORA-01400: cannot insert NULL into (…"REVISION_COUNT")` (then `UPDATE_USERID`, `UPDATE_TIMESTAMP`).
  - **Why (technical):** `THE.ILCR_COST_REPORT_DETAIL` has `REVISION_COUNT`, `UPDATE_USERID`, `UPDATE_TIMESTAMP` as **NOT NULL with no column default**, but `Schedule1Repository.insertOtherCost` / `insertFixedDetail` / `insertFixedDetailVolume` did **not** supply them. The app was built against a schema where these are defaulted/nullable; the delivery schema is stricter, so every insert was rejected.
  - **Is it a defect?** Yes — a genuine 500 that broke Other Costs add (and any first detail insert) on the delivery DB.
  - **Fix (applied by dev, verified 2026-07-30):** `Schedule1Repository.insertOtherCost` now sets `REVISION_COUNT = 0` and the audit columns in its INSERT (an app-side fix, not a schema change).
  - **Follow-up — NOW CONFIRMED DONE (2026-08-07):** the sibling first-insert paths carry the same fix. `Schedule1Repository.insertFixedDetail` (line ~245) and `insertFixedDetailVolume` (line ~318) both now list `REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP` in their INSERT column lists. The open follow-up on this entry is closed.
  - **Priority / env:** p1 · local seeded delivery DB `THE/…@localhost:1525/DBDOCK_01`.
  - **Status:** RESOLVED — found and fixed 2026-07-30; follow-up verified 2026-08-07. (BA/QA to confirm the real delivery Oracle shares the strict schema.)
  - **Test:** `other-costs.feature` `@S09 @p1` — GREEN (was the `@discovered-bug` red that tracked this).
  - **Note (2026-08 bcgov sync):** the per-row `POST` add path described above is superseded — the sub-page now persists the whole row set via `PUT …?intent=save`. Retained as the historical record of the bug and its fix.

- **#2 — A cleared volume is silently discarded: five Schedule 1 volume fields cannot be blanked.** _(NEW 2026-08-07)_
  - **What's wrong:** If you clear one of five volume boxes on Schedule 1 and press Save, the app says "Data saved successfully" — but the old number is still there. Reload the page and it comes back. There is no way to remove a volume you entered by mistake in these five boxes. Every *other* amount box on the screen clears normally, which is what makes this so easy to miss.
  - **Which fields:** Forest Management Administration Costs (Sch 3) volume, Subtotal Company Logging Cost (no Silviculture) volume, Less Silviculture Admin Costs volume, Total Silviculture (As per Financial Statements) volume, and Subtotal Other Costs volume.
  - **Expected vs actual:** Expected a cleared box to save as empty, exactly as clearing "Standing Tree to Loaded Truck volume" does. Actual — the save reports success and the previous value is retained.
  - **How we caught it (verified on real data 2026-08-07):** The S01 happy-path cleanup stopped working after these fields became editable. Reproduced directly against the API on 13050/2017: a `PUT /api/v1/schedule1` sending `forestMgmtAdminVolume: null` returned HTTP 200 but read back `143 → 400` unchanged; the same PUT sending `0` wrote `0`. So null is ignored and only a real number lands.
  - **Why (technical):** `Schedule1Service.writeWritableDetails` / `writeSilviculture` guard these five scalars with `if (request.<field>() != null)`. The comment says the guard exists "so a request that omits the field leaves the stored volume untouched" — but the React client (`buildRequest` in `components/schedule1/index.tsx`) *always sends* all five, using `null` to mean "the user cleared this box". Omitted and cleared are indistinguishable at the server, so cleared loses. Note the inconsistency: the nested `lineItems[]` and `silviculture.actualSpent/accruedLessActual` objects are guarded at the OBJECT level and pass their inner `volume: null` straight through — which is why those fields clear correctly.
  - **Is it a defect?** Yes — user-visible data the user cannot correct, with a success message that misreports what happened.
  - **Priority / env:** p1 · branch `e2e/schedule1-recheck-and-defect-restale` · local seeded delivery DB.
  - **Status:** OPEN. Found 2026-08-07.
  - **Test:** `clear-amounts.feature` — the `@discovered-bug @p1` scenario is a genuine RED tracking this and will go green when it is fixed. Its mirror arm (`@p1`, clearing an ordinary line-item volume) is GREEN, which is the proof that clearing is supposed to work.

- **#3 — Schedule 1 returns a 500 when the shared Other-Costs row has no volume (latent).** _(NEW 2026-08-07)_
  - **What's wrong:** If a Schedule 1 holds a "shared Subtotal Other Costs" row whose volume is empty, opening that Schedule 1 fails outright — the page shows "Unable to load Schedule 1 / An unexpected error occurred" and the API returns HTTP 500. The schedule becomes unopenable rather than showing a blank box.
  - **Expected vs actual:** Expected the shared Other-Costs volume to render blank. Actual — HTTP 500 and no page.
  - **How we caught it (verified on real data 2026-08-07):** Building the S02 first-entry precondition. Nulling every detail volume on 22051/2017 made `GET /api/v1/schedule1?millId=22051&year=2017` return 500; backend log: `java.lang.NullPointerException at Schedule1Service.toOtherCosts(Schedule1Service.java:773)`.
  - **Why (technical):** `toOtherCosts` reads the shared row's volume with `…filter(descriptionIsEmpty).map(DetailRow::volume).findFirst().orElse(null)`. `Stream.findFirst()` throws NPE when the first element is `null` — the `.orElse(null)` never gets a chance to run. It needs the null-tolerant form (map to `Optional.ofNullable` / use `reduce`), not `findFirst`.
  - **Is it a defect?** Yes, but **latent** — please read the reachability note before prioritising. **No seeded schedule is in this state** (a query over all 17 shared item-19 rows in the delivery DB found zero with a null volume), and the app cannot create the state itself: the write path only ever inserts that row *with* a value, and Bug/Regression #2 above means a user cannot clear it either. So #2 is currently masking #3 — **fixing #2 without also fixing #3 would make this reachable from the UI**, because clearing the Subtotal Other Costs volume would then persist as null and the schedule would 500 on next open. Legacy-migrated data could also arrive in this state.
  - **Priority / env:** p2 today, but **must ship with the fix for #2** · local seeded delivery DB.
  - **Status:** OPEN. Found 2026-08-07.
  - **Test:** none — deliberately not automated. Producing the state needs direct DB manipulation, so an E2E red here would assert a state no user can reach; it is covered instead by the note above and by `scripts/sch1_db_restore.py first-entry`, which documents and works around it. `not-applicable (E2E)` in coverage.md.

**Divergences:**

- **#1 — Invalid amounts are flagged inline and Save is blocked, rather than rejected at the keystroke.**
  - **What's wrong:** The legacy-derived Gherkin (S03–S07) says an out-of-range or non-numeric cost/volume is rejected *at entry* — "the invalid amount is not accepted into the field." In the new app the field **accepts** the typed value, an inline error appears immediately, and the **Save is blocked** (nothing is sent). The protection those slices exist for — invalid data can never be saved — still holds; only the moment/mechanism of the block differs.
  - **Expected vs actual:** Expected the field to refuse the value on entry (legacy FLD-001/002/003/004/005). Actual — the value stays in the field with a red inline message (e.g. "Entered cost must be between -99,999,999 and 99,999,999."), and clicking Save shows "Please correct the highlighted fields before saving." and sends nothing to the server.
  - **How we caught it (verified 2026-07-29, re-verified 2026-08-07):** Re-grounding S03–S07 against 24050/2017. For each field we typed an invalid value, saw the inline error, clicked Save, and a `page.route` spy confirmed **zero** `PUT /api/v1/schedule1` calls. Still accurate today: `Schedule1.handleSave` (index.tsx:165) aborts on `validateSchedule1(form)` before the PUT.
  - **Is it a defect? NO — confirmed DELIBERATE by BA/QA (2026-08-07).** The inline-error + blocked-Save design is the intended behaviour; the legacy reject-at-keystroke mechanism is not required, because the guarantee those slices exist for (invalid data can never be saved) is fully preserved.
  - **Action:** none. Adjudicated and closed. The re-grounded tests stay **GREEN**, asserting the preserved guarantee (inline error + proven zero-write) — never a `@discovered-divergence` red.
  - **Priority / env:** p1 · local seeded DB (security off, datasource on).
  - **Status:** CLOSED — accepted as designed (BA/QA, 2026-08-07). Found 2026-07-29.
  - **Test:** `validation.feature` (S03–S07) — GREEN.

- **#2 — RETIRED (obsolete): the 8-digit volume fields are editable again.**
  - **What this used to say:** that legacy FLD-003's three editable 8-digit volumes were reduced to one here — Forest Mgmt Admin (143) and Subtotal Company Logging (144) rendering read-only/derived and impossible to type into — so S06 was re-grounded onto Subtotal Other Costs volume as the only editable 8-digit field.
  - **Why it is retired (verified 2026-08-07):** backend commit `0b58057` "restore legacy parity for derived costs" reversed this. `components/schedule1/index.tsx` now renders `#vol-143` and `#vol-144` as editable TextInputs (`numberCell(…, true, …)`), `validation.ts` `fieldKind()` routes both to the 8-digit rule, and `Schedule1Request` carries `forestMgmtAdminVolume` / `subtotalCompanyLoggingVolume`. Only their COSTS remain read-only, which matches legacy BR-04 ("their cost comes from Sch 3 / is derived"), not a divergence. The same commit also made silviculture 139/140 volume-editable. `UC-SCH1-001-slices.md` confirms the legacy inventory this now matches: 3 editable 8-digit volumes (`forestManagementAdminCostsVol`, `subTotalOtherCostsVol`, `subtotalCompanyLoggingCostsVol`) and 11 editable 7-digit volumes (including `lessSilvAdminCostsVol`, `totalSilvVol`).
  - **Action:** none — parity is restored, so there is nothing for BA/QA to adjudicate. Coverage was widened to match: `validation.feature` `@S06` now exercises all three 8-digit fields and `@S05` all four 7-digit groups; `happy-path.feature` writes and reads back all four restored volume-only fields.
  - **Status:** CLOSED 2026-08-07 (superseded by the app; no defect).
  - **Test:** `validation.feature` `@S05`/`@S06`, `happy-path.feature` `@S01` — GREEN.

- **#3 — The per-row Other Costs delete-confirmation modal was removed (bcgov EditableSubPage rewrite).**
  - **What's wrong:** Legacy-derived S12 removes an itemized Other Cost "after confirming the prompt" — the fork's app popped a Carbon danger Modal ("Delete other cost") to confirm before deleting. After the 2026-08 sync to bcgov's shared `EditableSubPageLayout` / `useEditableCostRows` rewrite, the per-row delete is an icon-only **"Remove"** button that deletes the row **immediately** (optimistic) and persists the whole set via one `PUT …?intent=delete` — there is no confirmation step.
  - **Expected vs actual:** Expected a confirm-before-delete prompt (legacy S12). Actual — Remove deletes immediately; SUC-002 "Data deleted successfully" is echoed from the API after the whole-set PUT.
  - **How we caught it (verified 2026-08, re-verified 2026-08-07):** Re-grounding S12. Still accurate: `components/schedule1OtherCosts/index.tsx` renders a `hasIconOnly iconDescription="Remove"` button whose `onClick` → `useEditableCostRows.removeRow` → immediate `persist(next, 'delete')`; no dialog is rendered.
  - **Is it a defect?** A behaviour/parity change owned upstream (bcgov), not this suite. BA/QA to confirm whether the confirmation should exist (does legacy ILCR confirm a per-row Other Costs delete?). If it should, it is a parity regression to raise upstream; if not, dropping it is fine.
  - **Action:** BA/QA to confirm parity. S12 re-grounded to the no-confirm behaviour and kept **GREEN**. (Note: the whole-schedule delete S13 still has its "Delete schedule" confirm Modal — only the per-row Other Costs confirm was removed.)
  - **Priority / env:** p1 · local seeded DB.
  - **Status:** OPEN. Found 2026-08 (bcgov sync); re-verified 2026-08-07.
  - **Test:** `other-costs.feature` `@S12 @p1` — GREEN (re-grounded).

- **#4 — RETRACTED (author error): inline edits DO get client-side validation, and match legacy.**
  - **What it claimed:** that editing a row already in the list skipped browser-side validation, so an
    invalid inline edit was only caught by the server after a round-trip.
  - **Why it was wrong (corrected 2026-08-07):** the claim came from reading
    `useEditableCostRows.handleSave`, which looks bare — but it delegates to `persist`, and **`persist`
    validates every row**, populates `rowErrors`, and **returns before sending** if any row is invalid.
    An invalid inline edit therefore behaves exactly like an invalid Add: inline error, no request.
  - **Checked against LEGACY, not just against our own internal consistency (the correct test for a
    divergence):** legacy `schedule1OtherCosts.xhtml` put the per-row inputs in form `otherCostsForm`
    with `required="true"` on the row description and `validator="costValidator"` on the row cost
    (technical.md:96,99). JSF runs those in Process Validations on **form submit** — i.e. on clicking
    Save — displaying the errors and persisting nothing. That is the same point in the interaction as
    ours. **No mechanism divergence on this sub-page.** (Note this differs from the MAIN page, where
    legacy validated at keystroke — that is Divergence #1, and it is confined to `schedule1.xhtml`.)
  - **Open question answered, also from legacy:** should the sub-page show BOTH an Add and a Save
    button? **Yes — legacy had both**, as two independent forms: `addCostForm` (Description / disabled
    shared Volume / Cost / `$/m³` + **Add** → `addOtherCost()`) and `otherCostsForm` (the row table +
    **Save** → `save()` + Back) — technical.md:43,90-94,104-105. Our sub-page mirrors that, including
    the disabled shared volume and `$/m³` in the add form (`#add-description` / `#add-volume` /
    `#add-cost` / `#add-perunit`). So having both buttons is parity, not an accident.
  - **Status:** RETRACTED 2026-08-07. Nothing to adjudicate — but see Divergence #5, which the legacy
    re-check surfaced.

- **#5 — The per-field "original value" indicators from legacy do not exist anywhere in the new app.**
  _(NEW 2026-08-07 — found by re-checking #4 against legacy instead of against our own implementation.)_
  - **What's missing:** in legacy, once a report had been **submitted** (left Draft), every editable
    Cost/Volume field that differed from its previously-saved value displayed a small icon button beside
    it, with a tooltip showing the earlier value — so a reviewer could see at a glance what had been
    changed since the last save, and what it used to be. The new app has nothing equivalent.
  - **Legacy evidence:** technical.md:44 — "every editable Cost/Volume field on `schedule1.xhtml` is
    paired with an 'original value' indicator — a `type="button"` icon button `{id}OB` (rendered only
    when `{lineItem}.is{Volume|Cost}OriginalVal(schedule1MB.isSubmit())` is true) plus a `p:tooltip`
    `{id}O` bound to it, showing the previously-saved value via `{lineItem}.{volume|cost}Original`.
    **This indicator only appears once the report has left Draft** and the entered value differs from
    the original." Named instances: `commentsOB`/`commentsTT` on the main page (technical.md:84) and
    `descriptionOB` / `costOB` per row on the Other Costs sub-page (technical.md:97,100).
  - **How we caught it (2026-08-07):** re-checking Divergence #4 against the legacy sidecars rather than
    assuming our implementation was right. Grepped the new app for any equivalent: none in
    `components/schedule1/index.tsx`, `components/schedule1OtherCosts/index.tsx`,
    `hooks/useEditableCostRows.ts` or `core/EditableSubPageLayout`.
  - **Why (technical):** it is missing **end to end, not just in the UI** — the API exposes no prior
    value at all (`schedule1/dto/*.java` has no `original`/`previous` field), so the frontend could not
    render the indicator today even if someone added the markup. Restoring it needs a backend change.
  - **Is it a defect?** BA/QA to decide. It is a genuine legacy capability that has not been carried
    over, in the **post-submission review** path — which is why no Draft-focused scenario would ever
    have caught it. If reviewers/auditors relied on it to spot what a licensee changed after submitting,
    this is a real functional gap; if the audit tables now serve that need, dropping it is fine.
  - **Priority / env:** p2 pending triage · local seeded delivery DB.
  - **Status:** OPEN. Found 2026-08-07.
  - **Test:** none — out of reach for this UC's scenarios, which all run against Draft schedules (the
    indicator only renders once a report has left Draft). S22 covers the non-Draft render but asserts
    only that inputs are absent and actions disabled. `not-applicable (E2E, current scope)` in
    coverage.md; revisit with the submission/review UC.

**Coverage gaps (not tested yet — no app problem):**

- **#1 — There is no role-dependent Schedule 1 behaviour to cover yet.** _(reworded 2026-08-07 — the
  earlier wording said role branches were "blocked by mock auth", which implied we were failing to cover
  behaviour that exists. Re-checked against the code: that behaviour does not exist yet.)_
  - **Why not:** `ILCR_ADMIN` and `ILCR_SUBMITTER` are granted **exactly the same actions**. From
    `security/SchedulePermissions.java:28-29`:
    `ROLE_ACTIONS.put(Role.ADMIN, EnumSet.of(VIEW_SCHEDULE, EDIT_SCHEDULE));`
    `ROLE_ACTIONS.put(Role.SUBMITTER, EnumSet.of(VIEW_SCHEDULE, EDIT_SCHEDULE));`
    — with the comment "Both FAM production roles may view and edit schedules; the two tracks /
    Draft-gate are enforced separately in the domain services (AD-9)". Every Schedule 1 endpoint is
    guarded by `VIEW_SCHEDULE` or `EDIT_SCHEDULE` only, so **no admin-only branch and no role-driven 403
    exists on this UC**. There is nothing to assert, not merely something we cannot reach.
  - **On the header's mock-user selector (ILCR_ADMIN / ILCR_SUBMITTER / both):** it is a **frontend-only
    display affordance** and does NOT grant roles. `context/auth/mockUsers.ts` persists the choice to
    `localStorage` under `nr-ilcr.mock-user`; no header or interceptor carries it to the API. The backend
    stamps ONE authority on every request from the startup property
    `ilcr.security.mock-role` (default `ILCR_SUBMITTER`, `SecurityConfiguration.java:38` →
    `MockPrincipalFilter`). The only consumer of the selected user anywhere in the app is
    `Dashboard.tsx`, which renders `user.displayName` / `user.email` / role chips — nothing branches on
    it. So switching it changes the name on the Home card, not what you may do.
  - **Future action:** revisit when FAM auth lands **and the two `ROLE_ACTIONS` sets actually diverge**.
    At that point the lever is a CI matrix — a second suite run against a backend started with
    `ilcr.security.mock-role=ILCR_ADMIN` — not a per-test switch, because the authority is fixed per
    process. Until the maps differ, that second job would assert nothing new.
  - **Status:** OPEN (informational). Re-verified 2026-08-07.
  - **Test:** none needed today — `not-applicable (no role-dependent behaviour)` in coverage.md.

- **#2 — CLOSED: S02 (crown pre-fill) is now automated.**
  - **What this used to say:** that S02 needed a mill/year whose Schedule 3 carries a Crown Timber volume while the Schedule 1 volumes are all still empty, that no such anchor existed, and that manufacturing one meant seeding Schedule 3 — out of scope.
  - **Why it is closed (2026-08-07):** Schedule 3 has since shipped with crown data. A DB sweep of all 30 Schedule-1/Schedule-3 pairs found **28** carrying an item-119 Crown Timber volume, so the Schedule 3 half of the precondition is now real seeded data, not something to manufacture. The remaining half (every Schedule 1 volume null) is produced by snapshotting the dedicated target 22051/2017, nulling its volumes at the DB, and restoring it verbatim on teardown — the same snapshot/restore machinery S13 and S24 already use.
  - **Status:** CLOSED 2026-08-07.
  - **Test:** `crown-prefill.feature` `@S02 @p1 @WRN-001` — GREEN. Asserts the WRN-001 advisory, all 13 pre-filled volume fields, that the shared Other-Costs volume is excluded from the pre-filled set, and that nothing is persisted until the user saves.

- **#3 — S08 (open Other Costs before first save) is unreachable in the current backend model.**
  - **Why not:** The legacy guard blocked opening Other Costs before Schedule 1 was saved. In the new app an openable schedule is always already saved (the GET 404s when no summary exists), so `Schedule1.handleOtherCosts`'s `!data` branch (the ALT-001 "save first" Modal) cannot be produced through the UI against real data.
  - **It is unreachable by construction, not for want of data (proved 2026-08-07).** No seed patch or
    probe can produce it, because the requirement is self-contradictory within one render:
    `index.tsx:341` is `if (!data) { return null }`, and the "Subtotal Other Costs(N):" button that calls
    `handleOtherCosts` is rendered *below* that guard. So triggering the `if (!data)` branch at
    `index.tsx:261` needs `data` to be null, while clicking the button that reaches it needs `data` to be
    non-null. Dead code — the component's own comment already says "effectively unreachable".
  - **Not related to Bug/Regression #3**, despite the shared number (different registers). When the GET
    500s, `data` is null, so the component renders the error state and the button never exists — Bug #3
    stops the page rendering rather than exposing this branch.
  - **No unit test covers it either (searched 2026-08-07).** `ALT_SAVE_BEFORE_OTHER_COSTS` appears
    exactly twice in the whole repo — its definition (`index.tsx:42`) and its render (`index.tsx:698`) —
    and never in a test. `Schedule1.test.tsx`'s `describe('Schedule1 Other Costs navigation (Story 2.5)')`
    block has three tests (confirm-then-navigate, cancel-does-not-navigate, read-only-opens-without-
    confirm) and none forces a null-data state. The BR-06 hits in
    `Schedule1OtherCostsServiceTest`/`Schedule1OtherCostsIT` are about the shared-volume **inheritance**
    rule, not the save-before-open gate. So this branch is currently covered by nothing, at any level.
  - **Future action:** raise with the dev — either delete the dead branch (a guard that cannot fire is a
    maintenance trap) or, if it is being kept for a future backend model with create-on-open, add the
    component test that mounts `Schedule1` with a forced null-data state. Either way it is not an E2E
    concern.
  - **Status:** OPEN. Re-verified 2026-08-07.
  - **Test:** none, at any level — `not-applicable (E2E; unreachable by construction)` in coverage.md.

**Spec gaps (the Gherkin is missing scenarios its own docs list):**

- **#1 — Per-row inline edit of an existing Other Cost row has no derived `.feature` scenario.** _(NEW 2026-08-07)_
  - **What's missing:** `UC-SCH1-001-slices.md` documents inline editing of an existing Other Cost row in two places — the Description rule reads "Description (new line item; **same rule applies to per-row inline edit of an existing item's description**)", and the invalid-Cost trigger reads "…when adding a new Other Cost line item **or editing an existing row's Cost inline**". The derived `UC-SCH1-001-S09..S12.feature` files cover only the **Add** form and the per-row **Remove** — there is no slice for editing a row that is already in the list, even though the app implements it (`#row-description-<key>` / `#row-cost-<key>` plus a batch Save).
  - **How we caught it (2026-08-07):** reconciling the `.feature` set against the slice matrix rather than treating it as the complete inventory — the classic lossy-projection case.
  - **Action / RESOLVED 2026-08-07:** the missing slice has now been derived upstream in `ilcr-bmad` as
    **UC-SCH1-001-S25 "Edit an Existing Other Cost Line Item Inline"** (branch
    `spec/uc-sch1-001-inline-edit-slice`) — feature file, gherkin README inventory (24 -> 25 slices), and
    a full detail section in `UC-SCH1-001-slices.md`. The projection and the matrix now agree, so this
    gap is closed at the source rather than only compensated for here.
  - **Status:** RESOLVED — slice derived upstream 2026-08-07; E2E coverage already in place.
  - **Test:** `other-costs-inline-edit.feature` `@S25 @p1` — GREEN. (An earlier Divergence #4 claiming inline edits skip client-side validation was RETRACTED — it was a misreading; validation is uniform with Add.)

**Verified — not a defect:**

- **Accessibility (AC4 / NFR1): zero WCAG 2.1 AA violations.** `@axe-core/playwright` (tags `wcag2a` + `wcag2aa` + `wcag21a` + `wcag21aa`) ran against the Schedule 1 page (24050/2017) and the Other Costs sub-page (17052/2016) → **zero violations** on both, so no triage/dispositions are required. (`accessibility.feature`, verified 2026-07-30; still green 2026-08-07.) If a future change introduces a violation, the axe helper prints each rule + node + help URL for a recorded disposition.

- **The legacy `ILCR_LICENSEE` role was re-grounded to the new two-group model.** The Gherkin authenticates as `ILCR_LICENSEE`, but the new app has no such role — the ratified model is `ILCR_ADMIN` + `ILCR_SUBMITTER` (PRD DL-23). Schedule 1 saves are authorized for `ILCR_SUBMITTER` (live: `PUT /api/v1/schedule1?millId=13050&year=2017` with security off → HTTP 200, `message.text = "Data saved successfully"`, persisted on read-back). Scenarios use the real role; deliberate rename, not a defect. (Verified 2026-07-29.)

- **The crown pre-fill is served, never stored.** BR-03 pre-fills 13 volume fields on the GET and raises WRN-001 "Please check and save schedule", but writes nothing — confirmed 2026-08-07 by counting non-null stored volumes at the DB immediately after the pre-filled page rendered (0 rows). The advisory is therefore accurate, not a stale message. (`crown-prefill.feature`.)
