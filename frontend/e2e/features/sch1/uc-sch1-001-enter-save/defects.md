# Defects — UC-SCH1-001 Report Average Cost of Logging (Schedule 1)
> How this log works (registers, tags, per-register templates): [defects-guide.md](../../../defects-guide.md)

> **Entry ids:** each register numbers independently, so ids carry their register as a prefix —
> `BUG-n` (Bug / Regression), `DIV-n` (Divergence), `GAP-n` (Coverage gap), `SPEC-n` (Spec gap).
> Cite the prefixed id when raising a ticket; a bare "#3" is ambiguous across three registers.

**Last re-verified: 2026-08-11** (branch `fix/schedule-1-e2e-fix`). **BUG-2 and BUG-3 are both now FIXED
and verified against the running app** — backend commit `3ee9ff2` "write cleared volumes as null and guard
null shared volume in toOtherCosts", raised as issues
[#260](https://github.com/bcgov/nr-ilcr/issues/260) (BUG-2) and
[#261](https://github.com/bcgov/nr-ilcr/issues/261) (BUG-3). The suite's one intentional
`@discovered-bug` RED has flipped GREEN and the tag is removed; the whole suite is green with **no
`@discovered-*` reds remaining**. See each entry for the evidence.

**Previously re-verified: 2026-08-07** (branch `e2e/schedule1-recheck-and-defect-restale`). Every entry below was
re-checked against the app as it stands today, not carried forward on trust. The app moved underneath this
log — backend commit `0b58057` "restore legacy parity for derived costs, audit columns" and the shared
`EditableSubPageLayout` rewrite both changed behaviour this file described — so one Divergence was retired as
obsolete, one follow-up was confirmed done, one Coverage gap was closed, and three new findings were added.

**Bug / Regression:**

- **BUG-1 — Adding a Subtotal Other Costs line item fails with a 500 on the delivery database.**
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
  - **Note (2026-08 shared-subpage rewrite):** the per-row `POST` add path described above is superseded — the sub-page now persists the whole row set via `PUT …?intent=save`. Retained as the historical record of the bug and its fix.

- **BUG-2 — A cleared volume is silently discarded: five Schedule 1 volume fields cannot be blanked.**
  _(found 2026-08-07 · ticket [#260](https://github.com/bcgov/nr-ilcr/issues/260) · **FIXED & VERIFIED 2026-08-11**)_
  - **What's wrong:** If you clear one of five volume boxes on Schedule 1 and press Save, the app says "Data saved successfully" — but the old number is still there. Reload the page and it comes back. There is no way to remove a volume you entered by mistake in these five boxes. Every *other* amount box on the screen clears normally, which is what makes this so easy to miss.
  - **Which fields:** Forest Management Administration Costs (Sch 3) volume, Subtotal Company Logging Cost (no Silviculture) volume, Less Silviculture Admin Costs volume, Total Silviculture (As per Financial Statements) volume, and Subtotal Other Costs volume.
  - **Expected vs actual:** Expected a cleared box to save as empty, exactly as clearing "Standing Tree to Loaded Truck volume" does. Actual — the save reports success and the previous value is retained.
  - **How we caught it (verified on real data 2026-08-07):** The S01 happy-path cleanup stopped working after these fields became editable. Reproduced directly against the API on 13050/2017: a `PUT /api/v1/schedule1` sending `forestMgmtAdminVolume: null` returned HTTP 200 but read back `143 → 400` unchanged; the same PUT sending `0` wrote `0`. So null is ignored and only a real number lands.
  - **Why (technical):** `Schedule1Service.writeWritableDetails` / `writeSilviculture` guard these five scalars with `if (request.<field>() != null)`. The comment says the guard exists "so a request that omits the field leaves the stored volume untouched" — but the React client (`buildRequest` in `components/schedule1/index.tsx`) *always sends* all five, using `null` to mean "the user cleared this box". Omitted and cleared are indistinguishable at the server, so cleared loses. Note the inconsistency: the nested `lineItems[]` and `silviculture.actualSpent/accruedLessActual` objects are guarded at the OBJECT level and pass their inner `volume: null` straight through — which is why those fields clear correctly.
  - **Is it a defect? YES — and now CONFIRMED AGAINST LEGACY (2026-08-07), not just against our own app:**
    - **Legacy allowed these fields to be blank.** In the legacy source only the *Description* inputs
      carry `required="true"` (technical.md:90,96) — none of the five volume fields do. Blank was
      accepted at Save; our own `components/schedule1/validation.ts` header records the same rule
      ("legacy accepts blank amounts at Save; Check Status catches missing required fields").
    - **Legacy had dedicated rules whose whole purpose is to detect these fields being null**, which only
      makes sense if null was a reachable, persisted state: **FLD-007** reports "`{Field Label}: Value
      Required`" at Check Status for a missing mandatory Volume/Cost, explicitly "applies to every fixed
      line item and to **Subtotal Company Logging volume** and all Silviculture fields"
      (technical.md:131); **FLD-010** fires when "**Subtotal Other Costs volume field itself is null**"
      (technical.md:134).
    - **So this is a functional regression, and it breaks a legacy workflow**: a user can no longer
      produce the very state FLD-007 / FLD-010 exist to catch. Once one of these five fields has a value,
      it can never be returned to "not yet entered".
  - **The seeded data proves the state is real, and that our own tests depend on it:** 9 seeded detail
    rows carry a NULL volume on the four guarded line-item/silviculture fields, and the S15 Check Status
    anchor (13050/2016) has a null shared Other-Costs volume. **Our `@S15` scenario passes only because
    the seed contains a null the application itself can no longer create.** Nothing in the new app could
    set up that scenario from scratch.
  - **Fix (dev, commit `3ee9ff2`, verified by QA 2026-08-11):** the five volume-only scalars are now
    written **unconditionally** in `Schedule1Service.writeWritableDetails` / `writeSilviculture`, so a
    null reaches `upsertFixedDetail` and clears the stored volume. The `!= null` guards that made
    "cleared" indistinguishable from "omitted" are gone, and `Schedule1Request`'s javadoc now states the
    contract explicitly — *a null scalar CLEARS the stored value; only an absent enclosing BLOCK
    (`lineItems`, `silviculture`, or one of its `EntryAmount` entries) means "not submitted"*. The
    block-level null checks were deliberately kept, which is what preserves that distinction. Two new
    unit tests pin it (`Schedule1WriteServiceTest.save_clearedVolumeFields_overwriteStoredValuesWithNull`
    and `save_nonWritableCode_isSkipped_butNullVolumesClear`).
  - **How the fix was verified (2026-08-11, running app + local seeded delivery DB):**
    1. **API round-trip** on the dedicated anchor 25052/2015 (snapshotted first, restored after): a
       `PUT /api/v1/schedule1` writing 4321 into all five, then a second PUT sending `null` for all five
       → HTTP 200, and the read-back GET returned **all five null** (143, 144, 139, 140, and the shared
       item-19 volume). Pre-fix the same PUT returned 200 and read back the old number unchanged.
    2. **At the column level**, not just through the API: the four detail rows (139/140/143/144) are
       still **present** with `VOLUME = NULL` — the clear blanks the volume, it does not delete the row.
    3. **The 4 itemized Other-Costs rows were untouched** (`count: 4`, `costSubtotal` unchanged), so the
       unconditional write does not over-reach into the itemized rows (AC2 still holds).
    4. **Through the browser:** the `clear-amounts.feature` scenario that was the `@discovered-bug` RED
       now passes end-to-end, and the full suite is **57 passed / 0 failed** on two consecutive runs.
       Non-flaky: 5 serialized repeats of both `@clear-amounts` scenarios, 16/16 green.
  - **Priority / env:** p1 · fixed on branch `fix/schedule-1-e2e-fix` · local seeded delivery DB.
  - **Status:** RESOLVED — found 2026-08-07, fixed and verified 2026-08-11 (ticket #260 still OPEN on
    GitHub; BA/QA to close it, and to confirm no *other* client is relying on the old
    omit-means-leave-alone behaviour, since the contract change is now PUT-of-the-whole-set).
  - **Test:** `clear-amounts.feature` `@p1` "Clearing the five volume-only fields blanks them and the
    schedule still reopens" — **GREEN**; this was the `@discovered-bug` red and the tag is now removed,
    so it stands as the regression guard. Its mirror arm (`@p1`, clearing an ordinary line-item volume)
    stays GREEN.

- **BUG-3 — Schedule 1 returns a 500 when the shared Other-Costs row has no volume.**
  _(found 2026-08-07 · ticket [#261](https://github.com/bcgov/nr-ilcr/issues/261) · **FIXED & VERIFIED 2026-08-11**)_
  - **What's wrong:** If a Schedule 1 holds a "shared Subtotal Other Costs" row whose volume is empty, opening that Schedule 1 fails outright — the page shows "Unable to load Schedule 1 / An unexpected error occurred" and the API returns HTTP 500. The schedule becomes unopenable rather than showing a blank box.
  - **Expected vs actual:** Expected the shared Other-Costs volume to render blank. Actual — HTTP 500 and no page.
  - **How we caught it (verified on real data 2026-08-07):** Building the S02 first-entry precondition. Nulling every detail volume on 22051/2017 made `GET /api/v1/schedule1?millId=22051&year=2017` return 500; backend log: `java.lang.NullPointerException at Schedule1Service.toOtherCosts(Schedule1Service.java:773)`.
  - **Why (technical):** `toOtherCosts` reads the shared row's volume with `…filter(descriptionIsEmpty).map(DetailRow::volume).findFirst().orElse(null)`. `Stream.findFirst()` throws NPE when the first element is `null` — the `.orElse(null)` never gets a chance to run. It needs the null-tolerant form (map to `Optional.ofNullable` / use `reduce`), not `findFirst`.
  - **Checked against LEGACY (2026-08-07):** legacy **rendered** a schedule whose Subtotal Other Costs
    volume was null without failing — it reported the condition at Check Status via **FLD-010**
    ("Subtotal Other Costs volume field itself is null", technical.md:134) rather than refusing to open
    the page. So a null there was an ordinary, supported state in legacy; the new app returns a 500 for
    it. That makes this a genuine regression, not merely a hardening gap.
  - **Precision for whoever fixes it:** the NPE needs an item-19 shared row that EXISTS with a null
    VOLUME. A schedule with *no* item-19 row at all is fine — `findFirst()` on an empty stream returns
    an empty Optional and `.orElse(null)` works; it is a null *element* that throws. (13050/2016 has no
    shared row, which is why it opens normally despite reporting a null shared volume.)
  - **Is it a defect? (assessment as of 2026-08-07, when it was found — superseded by the fix below.)**
    Yes, but **latent** at that time. **No seeded schedule is in this state** (a query over all 17 shared item-19 rows in the delivery DB found zero with a null volume), and the app cannot create the state itself: the write path only ever inserts that row *with* a value, and BUG-2 above means a user cannot clear it either. So #2 is currently masking #3 — **fixing #2 without also fixing #3 would make this reachable from the UI**, because clearing the Subtotal Other Costs volume would then persist as null and the schedule would 500 on next open. Legacy-migrated data could also arrive in this state — and that is not hypothetical:
    the seed already carries 9 detail rows with a NULL volume on the sibling guarded fields, so nulls of
    this class are demonstrably present in real extracted data.
  - **Fix (dev, commit `3ee9ff2` — the SAME commit as BUG-2, which is what the entry above asked for;
    verified by QA 2026-08-11):** `toOtherCosts` now selects the **row** first and maps to its nullable
    volume afterwards — `.filter(descriptionIsEmpty).findFirst().map(DetailRow::volume).orElse(null)`
    instead of `.map(DetailRow::volume).findFirst().orElse(null)`. `findFirst()` now only ever sees a
    non-null row, so the NPE cannot arise and `.orElse(null)` finally gets to run. A unit test pins it
    (`Schedule1ServiceTest.otherCosts_sharedRowWithNullVolume_readsAsNullVolume_notAnNpe`), and it also
    asserts the itemized rows still aggregate with `perUnit` null — there is no volume to divide by.
  - **The reachability inversion this entry predicted actually happened — and it is why the fix landing
    together mattered.** The 2026-08-07 note said "#2 is currently masking #3 — fixing #2 without also
    fixing #3 would make this reachable from the UI." That is exactly right: with BUG-2 fixed, clearing
    the Subtotal Other Costs box now persists a null on the shared item-19 row, so **an ordinary user
    action reaches the old 500 state**. Both were fixed in one commit, so the window never opened.
  - **How the fix was verified (2026-08-11, running app + local seeded delivery DB):** on 25052/2015 the
    BUG-3 precondition was produced **through the app's own write path** (not by hand-editing the DB) —
    clearing the shared volume — and confirmed at the column level to be the real trap and not the benign
    empty-stream case: **exactly one shared row (null `ITEM_DESCRIPTION`) EXISTS with `VOLUME = NULL`**,
    alongside 4 itemized rows. `GET /api/v1/schedule1?millId=25052&year=2015` then returned **HTTP 200**
    with `otherCosts.volume` null and the itemized subtotal intact, and the backend log carried **no
    `NullPointerException`**. Pre-fix this precise state produced
    `java.lang.NullPointerException at Schedule1Service.toOtherCosts` and HTTP 500.
  - **Priority / env:** was p2-latent; shipped with BUG-2 as required · branch `fix/schedule-1-e2e-fix`
    · local seeded delivery DB.
  - **Status:** RESOLVED — found 2026-08-07, fixed and verified 2026-08-11 (ticket #261 still OPEN on
    GitHub; BA/QA to close). Worth noting for the migration/extract owners: legacy-migrated data can
    arrive with nulls of this class (the seed already carries 9 such detail rows), and those schedules
    would have been unopenable before this fix.
  - **Test:** **now automated, and no longer `not-applicable`.** The state stopped needing direct DB
    manipulation the moment BUG-2 was fixed, so the E2E guard no longer asserts an unreachable state:
    `clear-amounts.feature` `@p1` ends by reopening the schedule from Home after clearing the shared
    volume, and `I open Schedule 1` only succeeds once the Company Logging Costs table renders — a 500
    fails the scenario instead of passing silently. The guard is non-vacuous: the shared row's continued
    **existence** with a null volume was confirmed at the column level for this exact write path (a
    deleted row would also read back null, which is why that was checked separately). `covered` in
    coverage.md.

**Divergences:**

- **DIV-1 — Invalid amounts are flagged inline and Save is blocked, rather than rejected at the keystroke.**
  - **What's wrong:** The legacy-derived Gherkin (S03–S07) says an out-of-range or non-numeric cost/volume is rejected *at entry* — "the invalid amount is not accepted into the field." In the new app the field **accepts** the typed value, an inline error appears immediately, and the **Save is blocked** (nothing is sent). The protection those slices exist for — invalid data can never be saved — still holds; only the moment/mechanism of the block differs.
  - **Expected vs actual:** Expected the field to refuse the value on entry (legacy FLD-001/002/003/004/005). Actual — the value stays in the field with a red inline message (e.g. "Entered cost must be between -99,999,999 and 99,999,999."), and clicking Save shows "Please correct the highlighted fields before saving." and sends nothing to the server.
  - **How we caught it (verified 2026-07-29, re-verified 2026-08-07):** Re-grounding S03–S07 against 24050/2017. For each field we typed an invalid value, saw the inline error, clicked Save, and a `page.route` spy confirmed **zero** `PUT /api/v1/schedule1` calls. Still accurate today: `Schedule1.handleSave` (index.tsx:165) aborts on `validateSchedule1(form)` before the PUT.
  - **Is it a defect? NO — confirmed DELIBERATE by BA/QA (2026-08-07).** The inline-error + blocked-Save design is the intended behaviour; the legacy reject-at-keystroke mechanism is not required, because the guarantee those slices exist for (invalid data can never be saved) is fully preserved.
  - **Action:** none. Adjudicated and closed. The re-grounded tests stay **GREEN**, asserting the preserved guarantee (inline error + proven zero-write) — never a `@discovered-divergence` red.
  - **Priority / env:** p1 · local seeded DB (security off, datasource on).
  - **Status:** CLOSED — accepted as designed (BA/QA, 2026-08-07). Found 2026-07-29.
  - **Test:** `validation.feature` (S03–S07) — GREEN.

- **DIV-2 — RETIRED (obsolete): the 8-digit volume fields are editable again.**
  - **What this used to say:** that legacy FLD-003's three editable 8-digit volumes were reduced to one here — Forest Mgmt Admin (143) and Subtotal Company Logging (144) rendering read-only/derived and impossible to type into — so S06 was re-grounded onto Subtotal Other Costs volume as the only editable 8-digit field.
  - **Why it is retired (verified 2026-08-07):** backend commit `0b58057` "restore legacy parity for derived costs" reversed this. `components/schedule1/index.tsx` now renders `#vol-143` and `#vol-144` as editable TextInputs (`numberCell(…, true, …)`), `validation.ts` `fieldKind()` routes both to the 8-digit rule, and `Schedule1Request` carries `forestMgmtAdminVolume` / `subtotalCompanyLoggingVolume`. Only their COSTS remain read-only, which matches legacy BR-04 ("their cost comes from Sch 3 / is derived"), not a divergence. The same commit also made silviculture 139/140 volume-editable. `UC-SCH1-001-slices.md` confirms the legacy inventory this now matches: 3 editable 8-digit volumes (`forestManagementAdminCostsVol`, `subTotalOtherCostsVol`, `subtotalCompanyLoggingCostsVol`) and 11 editable 7-digit volumes (including `lessSilvAdminCostsVol`, `totalSilvVol`).
  - **Re-verified against LEGACY (2026-08-07), not just against our current source.** The technical
    sidecar confirms our implementation now matches legacy field-for-field — editable volume at the
    stated range, read-only pulled/derived cost:
    | Legacy control | Legacy behaviour | Ours today |
    |---|---|---|
    | `forestManagementAdminCostsVol` (143) | `p:inputText`, 8-digit range (technical.md:60) | `#vol-143` editable, 8-digit ✓ |
    | `forestManagementAdminCostsCos` | `p:inputText` **disabled** — pulled from Sch 3 per BR-04 (:61) | read-only, pulled ✓ |
    | `subtotalCompanyLoggingCostsVol` (144) | `p:inputText`, 8-digit range (:70) | `#vol-144` editable, 8-digit ✓ |
    | `lessSilvAdminCostsVol` (139) | `p:inputText`, 7-digit (:74) | `#vol-139` editable, 7-digit ✓ |
    | `lessSilvAdminCostsCos` | **disabled** — pulled from Sch 3 per BR-04 (:75) | read-only, pulled ✓ |
    | `totalSilvVol` (140) | `p:inputText`, 7-digit (:77) | `#vol-140` editable, 7-digit ✓ |
    | `totalSilvCosCal` | **disabled** — derived (:78) | read-only, derived ✓ |
    The retirement stands: this is genuine parity, confirmed at the source rather than inferred from the
    commit message.
  - **Action:** none — parity is restored and legacy-verified, so there is nothing for BA/QA to adjudicate. Coverage was widened to match: `validation.feature` `@S06` now exercises all three 8-digit fields and `@S05` all four 7-digit groups; `happy-path.feature` writes and reads back all four restored volume-only fields.
  - **Status:** CLOSED 2026-08-07 (superseded by the app; no defect).
  - **Test:** `validation.feature` `@S05`/`@S06`, `happy-path.feature` `@S01` — GREEN.

- **DIV-3 — The per-row Other Costs delete-confirmation modal was removed (EditableSubPage rewrite).**
  - **What's wrong:** Legacy-derived S12 removes an itemized Other Cost "after confirming the prompt" — the app used to pop a Carbon danger Modal ("Delete other cost") to confirm before deleting. Since the 2026-08 move to the shared `EditableSubPageLayout` / `useEditableCostRows` rewrite, the per-row delete is an icon-only **"Remove"** button that deletes the row **immediately** (optimistic) and persists the whole set via one `PUT …?intent=delete` — there is no confirmation step.
  - **Expected vs actual:** Expected a confirm-before-delete prompt (legacy S12). Actual — Remove deletes immediately; SUC-002 "Data deleted successfully" is echoed from the API after the whole-set PUT.
  - **How we caught it (verified 2026-08, re-verified 2026-08-07):** Re-grounding S12. Still accurate: `components/schedule1OtherCosts/index.tsx` renders a `hasIconOnly iconDescription="Remove"` button whose `onClick` → `useEditableCostRows.removeRow` → immediate `persist(next, 'delete')`; no dialog is rendered.
  - **Is it a defect? LEGACY SAYS YES — this is a CONFIRMED PARITY REGRESSION (checked 2026-08-07).**
    The earlier version of this entry left "does legacy ILCR confirm a per-row Other Costs delete?" as an
    open question for BA/QA. Our own sidecars already answered it, in three places:
    - technical.md:102 — the per-row Delete is a `p:commandButton` with **`p:confirm` bound to
      `confirmDeleteMsg`**.
    - technical.md:154 — "Delete confirm dialog … PrimeFaces styled modal (NOT a native browser dialog)
      — shows `confirmDeleteMsg` for Delete actions", listed for **both** `schedule1.xhtml` **and
      `schedule1OtherCosts.xhtml`**.
    - detailed.md:66 (AF2 step 1) — clicking Delete on a row "Prompts a PrimeFaces confirm dialog
      (message key `confirmDeleteMsg`)"; and slices.md:538 carries it in S12's own message table.
    So legacy DID require a confirmation before removing a row, and the new app removes it immediately
    with no prompt. A user can now destroy an itemized cost with one mis-click and no undo.
  - **It is also now internally inconsistent:** the whole-schedule delete (S13) KEPT its "Delete
    schedule" confirm Modal, so the same app confirms the large destructive action and not the small one.
  - **The open question is CLOSED against the legacy SOURCE (2026-08-26), not the sidecars.** This entry
    previously deferred to the Schedule 1 dev to check "whether legacy actually prompts", because the
    evidence was captured sidecars (`technical.md:102,154`, `detailed.md:66`) rather than legacy code.
    Checked directly while triaging the same defect on Schedule 3: `webapp/schedule1OtherCosts.xhtml:94-96`
    carries `<p:confirm header="Confirmation" message="#{msg.confirmDeleteMsg}" icon="ui-icon-alert"/>` on
    the per-row Delete `p:commandButton`, and `messages.properties:31` resolves that key to *"This will
    delete the current record. Do you want to continue?"*. The sidecars were right; nothing needs
    correcting there.
  - **SAME DEFECT AS SCHEDULE 3 DIV-5 — one ticket, one fix.** The behaviour is in the shared
    `useEditableCostRows.removeRow` -> `persist(next, 'delete')` (`hooks/useEditableCostRows.ts:270-283`),
    so all three pages built on it are affected: Schedule 1 Other Costs and both Schedule 3 cost
    sub-pages. Legacy prompted on all three (`schedule3SubtotalOtherCosts.xhtml:94-96`,
    `schedule3IncludedUnacceptableCosts.xhtml:80-82`). Eight other row-level deletes in the app still
    confirm (Schedules 4, 5, 7A, 7B, 8, 9, 10, 11), which is what makes this a defect rather than a
    house style.
  - **The re-grounding was the WRONG CALL — ruled by the repo owner 2026-08-26.** From 2026-08-07 this
    scenario asserted the app's actual no-confirm behaviour and passed. Re-grounding a scenario onto a
    divergence makes the suite *ratify* the defect instead of tracking it: the green here is why the
    regression sat unticketed for three weeks, and it is also why Schedule 3's suite had to rediscover
    it independently. Corrected — S12 now asserts the legacy guarantee and is a tracked red. The rule
    this entry now carries: re-ground a scenario onto changed *design*, never onto a suspected defect;
    where the legacy guarantee is in doubt, the honest state is a tagged red, not a green.
  - **Ticket:** [bcgov/nr-ilcr#362](https://github.com/bcgov/nr-ilcr/issues/362) — *"Deleting an itemized
    cost row on the Schedule 1 and 3 cost sub-pages destroys it with no confirmation, unlike legacy and
    every other schedule"*, labelled `bug`, filed by the repo owner 2026-08-26. Repro verified on the
    extract anchor **727 Updated Mill E2E / 2017** (millId 17052) with no test-data patch applied: a row
    added and saved, then removed, produced **0 dialogs** and was already gone after a reload — on both
    this page and Schedule 3's. The filed issue deliberately omits two things this register keeps, as
    the register is their home: why the suites missed it (the re-grounding above), and the
    related-ticket comparison (#292 CLOSED — Schedule 2's Delete *button* hidden when no schedule
    exists; #296 — Schedule 1 and 3 empty data set. Neither concerns confirming a destructive action).
  - **Priority / env:** p1 · local seeded DB · Chrome. Real data loss, but bounded: the click is
    deliberate and the row can be retyped, so it is not p0.
  - **Status:** OPEN — confirmed and triaged by raising a ticket. Dev to gate
    `useEditableCostRows.removeRow` behind a confirmation in the shared `EditableSubPageLayout` (the
    `components/core/ConfirmDeleteModal` primitive already exists), without disturbing the
    whole-schedule Delete or the "Leave Schedule 1" prompt; QA re-verifies and closes this entry and
    Schedule 3's DIV-5 together when the fix lands. Found 2026-08 (EditableSubPage rewrite);
    legacy-source-confirmed 2026-08-07 (sidecars) and 2026-08-26 (legacy code); ticketed 2026-08-26.
  - **Test:** `other-costs.feature` `@S12 @p1 @discovered-divergence` — **RED on purpose** since
    2026-08-26. Asserts that Remove asks first and that the row survives until the prompt is answered;
    it does not pin any modal chrome, so it goes green on its own when the confirmation is restored, with
    no test change needed. The seeded row is cleaned by the marker registry whichever way the assertion
    goes, so the red leaves no residue (verified: anchor 9050/2017 clean after the run).

- **DIV-4 — RETRACTED (author error): inline edits DO get client-side validation, and match legacy.**
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
    legacy validated at keystroke — that is DIV-1, and it is confined to `schedule1.xhtml`.)
  - **Open question answered, also from legacy:** should the sub-page show BOTH an Add and a Save
    button? **Yes — legacy had both**, as two independent forms: `addCostForm` (Description / disabled
    shared Volume / Cost / `$/m³` + **Add** → `addOtherCost()`) and `otherCostsForm` (the row table +
    **Save** → `save()` + Back) — technical.md:43,90-94,104-105. Our sub-page mirrors that, including
    the disabled shared volume and `$/m³` in the add form (`#add-description` / `#add-volume` /
    `#add-cost` / `#add-perunit`). So having both buttons is parity, not an accident.
  - **Status:** RETRACTED 2026-08-07. Nothing to adjudicate — but see DIV-5, which the legacy
    re-check surfaced.

- **DIV-5 — The per-field "original value" indicators from legacy do not exist anywhere in the new app.**
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
  - **Corroborated while re-checking DIV-2:** the indicator is named individually on each of the
    four volume-only fields whose editability was just restored — `forestManagementAdminCostsVolOB`,
    `subtotalCompanyLoggingCostsVolOB`, `lessSilvAdminCostsVolOB`, `totalSilvVolOB` (technical.md:60,70,
    74,77). So restoring the volumes to parity (DIV-2) restored only half of what legacy showed
    for those fields; the change-tracking half is still missing.
  - **How we caught it (2026-08-07):** re-checking DIV-4 against the legacy sidecars rather than
    assuming our implementation was right. Grepped the new app for any equivalent: none in
    `components/schedule1/index.tsx`, `components/schedule1OtherCosts/index.tsx`,
    `hooks/useEditableCostRows.ts` or `core/EditableSubPageLayout`.
  - **Why (technical):** it is missing **end to end, not just in the UI** — the API exposes no prior
    value at all (`schedule1/dto/*.java` has no `original`/`previous` field), so the frontend could not
    render the indicator today even if someone added the markup. Restoring it needs a backend change.
  - **Is it a defect?** A genuine legacy capability that has not been carried
    over, in the **post-submission review** path — which is why no Draft-focused scenario would ever
    have caught it. If reviewers/auditors relied on it to spot what a licensee changed after submitting,
    this is a real functional gap; if the audit tables now serve that need, dropping it is fine.
  - **Action — with the Schedule 1 developer (2026-08-07).** Raised with him alongside DIV-3 in the QA
    review; when he gets a chance he'll look into it and raise a ticket if it is confirmed. Scope is his
    call: this cannot be fixed in the frontend alone, because the API exposes no previous value to render —
    so restoring it means a backend change, not just markup.
  - **Priority / env:** p2 pending triage · local seeded delivery DB.
  - **Status:** OPEN — with the Schedule 1 dev, who'll look into it when he gets a chance. Found 2026-08-07.
  - **Test:** none — out of reach for this UC's scenarios, which all run against Draft schedules (the
    indicator only renders once a report has left Draft). S22 covers the non-Draft render but asserts
    only that inputs are absent and actions disabled. `not-applicable (E2E, current scope)` in
    coverage.md; revisit with the submission/review UC.

- **DIV-6 — Check Status judges the SAVED schedule and ignores unsaved on-screen edits (APP-WIDE, 11 of 12
  schedules).**
  - **This entry is a POINTER, on purpose.** The full analysis — what legacy did, why the rewrite cannot,
    the app-wide sweep and the fix direction — lives in **ONE** place:
    **`sch3/defects.md` DIV-6** (`features/sch3/uc-sch3-001-report-admin-costs/defects.md`). Do not restate it here. Two copies
    of the same reasoning diverged inside a single session on ilcr-bmad PR #92, and this register carries
    only the facts that are genuinely local to Schedule 1.
  - **What's wrong, in one line:** Check Status reports on the last saved Schedule 1 and silently ignores
    anything typed since, so a reporter can be told the schedule is complete while a mandatory value is
    empty on screen — or told to fix something they have just fixed.
  - **Ticket:** [bcgov/nr-ilcr#359](https://github.com/bcgov/nr-ilcr/issues/359) — the same ticket for every
    affected schedule. One fix turns all of these green.
  - **Local facts (this is what belongs here):**
    - **Scenarios:** `check-status-unsaved.feature` `@discovered-divergence @p1 @S27` (the false-GREEN arm —
      clear a mandatory volume) and `@S28` (the false-RED arm — supply a flagged one). Both arms are needed:
      they fail in OPPOSITE directions.
    - **Anchors:** the existing READ-ONLY Check Status fixtures, shared as this suite already shares them —
      `requirements-met` (24050/2017, `requirementsMet: true` at rest) for S27, and
      `missing-line-item-volume` (24051/2016, 22 errors at rest) for S28. Typing without saving writes
      nothing, which each scenario proves with the unchanged revision token.
    - **Re-grounding note:** S28 asserts only that ITS OWN field's error stops being reported, not that the
      schedule becomes met — the anchor's other 21 values are genuinely still missing.
  - **Priority / env:** p1 · local seeded DB · Chrome.
  - **Status:** OPEN — confirmed and triaged against the shared ticket. Dev to send the on-screen values with
    the check-status request and evaluate those, following Schedule 6's `Schedule6CheckRequest`; QA
    re-verifies and closes this entry when the fix lands. The scenarios assert the CORRECT behaviour, so they
    go green on their own, at which point their tags and `[DISCOVERED …]` title markers come off together.
    No test change is needed. Added 2026-08-27.
  - **Test:** `check-status-unsaved.feature` ×2 — both RED by design.

**Coverage gaps (not tested yet — no app problem):**

- **GAP-1 — There is no role-dependent Schedule 1 behaviour to cover yet.** _(reworded 2026-08-07 — the
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

- **GAP-2 — CLOSED: S02 (crown pre-fill) is now automated.**
  - **What this used to say:** that S02 needed a mill/year whose Schedule 3 carries a Crown Timber volume while the Schedule 1 volumes are all still empty, that no such anchor existed, and that manufacturing one meant seeding Schedule 3 — out of scope.
  - **Why it is closed (2026-08-07):** Schedule 3 has since shipped with crown data. A DB sweep of all 30 Schedule-1/Schedule-3 pairs found **28** carrying an item-119 Crown Timber volume, so the Schedule 3 half of the precondition is now real seeded data, not something to manufacture. The remaining half (every Schedule 1 volume null) is produced by snapshotting the dedicated target 22051/2017, nulling its volumes at the DB, and restoring it verbatim on teardown — the same snapshot/restore machinery S13 and S24 already use.
  - **Status:** CLOSED 2026-08-07.
  - **Test:** `crown-prefill.feature` `@S02 @p1 @WRN-001` — GREEN. Asserts the WRN-001 advisory, all 13 pre-filled volume fields, that the shared Other-Costs volume is excluded from the pre-filled set, and that nothing is persisted until the user saves.

- **GAP-3 — S08 (open Other Costs before first save) was unreachable dead code. Defect #296 REWIRED the
  branch and made it live; the gap is now CLOSED by a test.**
  - **CLOSED 2026-08-27 — and the reasoning below expired rather than being wrong.** #296 makes an unsaved
    Schedule 1 serve a 200 empty editable document, so `data` is truthy on a never-saved schedule and the
    old `!data` condition could never fire again. Rylan re-gated it on saved-ness instead —
    `if (!data || !isScheduleSaved(data))` (`components/schedule1/index.tsx:288`) — and his commit comment
    at `:280-287` cites this slice by name, explaining that the sub-page controllers still require a
    summary (`validateScheduleViewable`, deliberately kept, #296 D1) so without the gate the click would
    land on a 404 dead-end. So the branch is now reachable by an ordinary user action, and the legacy
    guarantee is testable. Covered by `save-first-gate.feature` `@p1 @S08`, GREEN — the verbatim message
    plus the refusal to navigate. The `not-applicable (E2E)` row in coverage.md moved to `covered`.
  - **The lesson worth keeping:** "unreachable by construction" is a claim about *today's* construction. It
    was true and proved when written, and a fix elsewhere silently falsified it. Schedule 3's S18/S19 and
    its DIV-3 entry expired the same way, on the same day, from the same fix.
  - **Why it was unreachable, as proved 2026-08-07** *(historical — superseded above)*: the legacy guard
    blocked opening Other Costs before Schedule 1 was saved, and in the new app an openable schedule was
    always already saved (the GET 404'd when no summary existed). The requirement was self-contradictory
    within one render: `if (!data) { return null }` sat ABOVE the "Subtotal Other Costs(N):" button that
    calls `handleOtherCosts`, so triggering the `!data` branch needed `data` to be null while clicking the
    button that reaches it needed `data` to be non-null. Dead code — the component's own comment said
    "effectively unreachable". (Those line numbers have since moved: the `return null` guard is
    `index.tsx:360` today.)
  - **Not related to BUG-3** (a different register — see the id legend at the top). While that 500 still
    existed, it did not expose this branch either: `data` was null, so the component rendered the error
    state and the button never existed — BUG-3 stopped the page rendering rather than reaching this guard.
    Moot since BUG-3 was fixed 2026-08-11; the branch stays unreachable for the reason above.
  - **No unit test covers it either (searched 2026-08-07).** `ALT_SAVE_BEFORE_OTHER_COSTS` appears
    exactly twice in the whole repo — its definition (`index.tsx:42`) and its render (`index.tsx:698`) —
    and never in a test. `Schedule1.test.tsx`'s `describe('Schedule1 Other Costs navigation (Story 2.5)')`
    block has three tests (confirm-then-navigate, cancel-does-not-navigate, read-only-opens-without-
    confirm) and none forces a null-data state. The BR-06 hits in
    `Schedule1OtherCostsServiceTest`/`Schedule1OtherCostsIT` are about the shared-volume **inheritance**
    rule, not the save-before-open gate. So this branch is currently covered by nothing, at any level.
  - **What happened to the "future action":** it asked the Schedule 1 dev to either delete the dead branch
    or unit-test it with a forced null-data state. He did neither, and the third option was the right one —
    #296 gave the branch a real trigger, so it needed re-gating rather than deleting. Nothing is outstanding.
  - **Status:** CLOSED (covered) 2026-08-27. Raised 2026-08-07 and re-verified then; made reachable by #296
    (2026-08-26); closed by writing the E2E scenario 2026-08-27.
  - **Test:** `save-first-gate.feature` `@p1 @S08` — GREEN. Mirrors `sch3`'s `save-first-gate.feature`,
    which covers the same behaviour on the other schedule #296 touched (and where the second sub-page's
    wording is still wrong — sch3 DIV-7).

**Spec gaps (the Gherkin is missing scenarios its own docs list):**

- **SPEC-1 — Per-row inline edit of an existing Other Cost row has no derived `.feature` scenario.** _(NEW 2026-08-07)_
  - **What's missing:** `UC-SCH1-001-slices.md` documents inline editing of an existing Other Cost row in two places — the Description rule reads "Description (new line item; **same rule applies to per-row inline edit of an existing item's description**)", and the invalid-Cost trigger reads "…when adding a new Other Cost line item **or editing an existing row's Cost inline**". The derived `UC-SCH1-001-S09..S12.feature` files cover only the **Add** form and the per-row **Remove** — there is no slice for editing a row that is already in the list, even though the app implements it (`#row-description-<key>` / `#row-cost-<key>` plus a batch Save).
  - **How we caught it (2026-08-07):** reconciling the `.feature` set against the slice matrix rather than treating it as the complete inventory — the classic lossy-projection case.
  - **Correction on review (2026-08-07):** "never derived" overstated it. The slice matrix HAD analysed
    this behaviour — it folded the per-row Description/Cost into S09/S10/S11 and dispositioned the
    Other-Costs Save button as "S09 (implicit auto-save on Add)". What was missing is a slice of its
    own, so no scenario ever exercised the inline-edit path. A real gap, but a weaker one than claimed.
  - **Action / RESOLVED 2026-08-07:** the missing slices are now derived upstream in `ilcr-bmad` as
    **UC-SCH1-001-S25** (valid inline edit, Alternative — split out of S09) and **UC-SCH1-001-S26**
    (the rejection paths, Exception — split out of S10/S11), matching the catalog's own
    S09-Alternative / S10-S11-Exception shape (branch `spec/uc-sch1-001-inline-edit-slice`) — feature file, gherkin README inventory (24 -> 25 slices), and
    a full detail section in `UC-SCH1-001-slices.md`. The projection and the matrix now agree, so this
    gap is closed at the source rather than only compensated for here.
  - **Status:** RESOLVED — slice derived upstream 2026-08-07; E2E coverage already in place.
  - **Test:** `other-costs-inline-edit.feature` — `@S25 @p1` (valid edit + the BR-06 shared-volume
    assertion) and `@S26` (blank description `@FLD-006`; invalid cost Outline `@FLD-001`/`@FLD-004`) —
    all GREEN. The rejects run on the validate anchor and each proves a zero-write with the spy. (An earlier DIV-4 claiming inline edits skip client-side validation was RETRACTED — it was a misreading; validation is uniform with Add.)

**Verified — not a defect:**

_(Entries in this register are unnumbered unless something cross-references them — VER-1 below is cited
from a step comment, so it carries an id.)_

- **VER-1 — The delete read-back asserted `lineItems.length === 0`, which passes in CI and fails locally.
  Found 2026-08-28; the app is correct and the assertion was wrong.** The `S13` delete scenario went red
  on merging `main`, on `And the Schedule 1 should no longer be saved`: `revisionCount=undefined,
  lineItems=9`.
  - **What's wrong, in plain terms:** nothing, for any user. The delete works. The test was reading the
    wrong thing to prove it, and only one of our two databases exposed that.
  - **The delete genuinely worked.** Watched at the DB through the scenario: summary 3564 and all 13 of
    its detail rows present, then **gone**, then restored by the teardown. `revisionCount` absent is the
    correct "not saved" signal, exactly what `utils/schedule.ts isScheduleSaved` reads.
  - **Why nine line items still came back:** `lineItems` is the SERVED projection, not a store readout.
    When Schedule 1 holds no volumes and its Schedule 3 carries a Crown Timber volume, the server
    pre-fills all nine codes from it — `Schedule1Service:686` `prefill = sch3CrownVolume != null &&
    allVolumesEmpty(details)` (BR-09 / WRN-001, and this suite's own `crown-prefill.feature` covers it).
    The delete target 25052/2016 has precisely that Schedule 3: summary 3563, item 119 volume 1111. So
    the response carries nine pre-filled rows **before and after** the delete, and the clause could never
    be satisfied there. (Its stored rows all carried volume 1111 too — someone had saved after a
    pre-fill — so the shape is identical either side of the delete.)
  - **Why it looked correct to whoever wrote it — and the part worth remembering:** the CI Flyway seed
    (`db-e2e/R__80_e2e_anchor_seed.sql`) gives 25052/2016 **no category-3 summary at all**, so there is no
    crown volume to pre-fill from and `lineItems` really is empty in CI. The assertion therefore **passes
    in CI and fails locally against the real extract**. That is the environment-split failure
    `preflight/ci-seed-parity.setup.ts` was written to prevent, arriving in the one direction that gate
    cannot see: it compares openability — a mill, a status row, a reporting year — not whether a
    NEIGHBOURING schedule on the same mill-year holds data that changes this one's served document. The
    seed's header claims the two databases hold "identical states" so the unmodified suite passes against
    either; this is a counter-example to that claim, and it is now noted in both files.
  - **Fix:** the clause is gone, with the measurement recorded at `steps/sch1/schedule1.steps.ts`.
    `revisionCount == null` is the whole assertion, which is also what the step is named for. Proving the
    detail rows went too would need a DB read, not this projection; the rows are deleted in one
    repository call with the summary (`Schedule1Repository.deleteSchedule` → `deleteDetailsBySummary` then
    `deleteSummary`), so the summary's absence is sufficient evidence through the API.
  - **Status:** CLOSED 2026-08-28 — assertion corrected, no app change. `delete.feature` `@S13 @p1` GREEN;
    the sch1 domain re-run afterwards was **204 passed, 6 tracked `@discovered-*` reds, none untagged**
    (that grep spans sch11 too, since `@sch1` prefixes `@sch11`).

- **The #296 fix left TWO stale assertions in THIS suite, red on `main` before this branch
  touched them. Re-grounded 2026-08-26 against legacy; no app defect.**
  - **What was stale:** defect #296 ("open a blank, usable form when nothing is saved yet", `main`
    `60c24dd`) deliberately removed the 404 for an unsaved or just-deleted Schedule 1 — the GET now serves
    a 200 empty EDITABLE document so the reporter can start over. Two places still asserted the old
    behaviour: `Then the Schedule 1 should no longer exist` polled for a GET **404**
    (`steps/sch1/schedule1.steps.ts`), and `delete.feature` asserted the post-delete form was
    **read-only** with **all actions disabled**. Measured on the merge commit before any edit: this suite
    ran **163 passed / 1 failed**, the failure being `@delete @S13`.
  - **Re-grounded against LEGACY, not against the fix's description** — the discipline the S12 episode
    taught. `Schedule1MB`'s delete mirrors Schedule 3's (`Schedule3MB.delete():125-136`): delete, re-read
    the schedule, stay on the page. Editability is gated on the track status / role
    (`disableReportEdits()` → `userSessionMB.disableUserInput()`), never on summary existence, and Delete
    renders only while the summary exists (`schedule3.xhtml:426` is the Schedule 3 twin). So a blank
    EDITABLE form with Delete withdrawn IS legacy behaviour; the pre-#296 read-only strand was the
    divergence.
  - **What it asserts now:** the steps #296's own suite work added but never wired into `delete.feature` —
    `the Schedule 1 input form is displayed`, `every Schedule 1 amount is blank`, `the Schedule 1 Delete
    action is not offered` — and "no longer exists" now means UNSAVED (`revisionCount` absent), the
    predicate the app itself uses (`utils/schedule.ts isScheduleSaved`).
  - **Worth passing to whoever owns #296:** their PR merged with this suite red on `main`.
  - **Status:** CLOSED (re-grounded) 2026-08-26. Suite state after: **164 passed, 1 deliberate
    `@discovered-divergence` red** (DIV-3 / #362), no untagged failures. (`delete.feature` `@S13 @p1` —
    GREEN.)

- **Accessibility (AC4 / NFR1): zero WCAG 2.1 AA violations.** `@axe-core/playwright` (tags `wcag2a` + `wcag2aa` + `wcag21a` + `wcag21aa`) ran against the Schedule 1 page (24050/2017) and the Other Costs sub-page (17052/2016) → **zero violations** on both, so no triage/dispositions are required. (`accessibility.feature`, verified 2026-07-30; still green 2026-08-07.) If a future change introduces a violation, the axe helper prints each rule + node + help URL for a recorded disposition.

- **The legacy `ILCR_LICENSEE` role was re-grounded to the new two-group model.** The Gherkin authenticates as `ILCR_LICENSEE`, but the new app has no such role — the ratified model is `ILCR_ADMIN` + `ILCR_SUBMITTER` (PRD DL-23). Schedule 1 saves are authorized for `ILCR_SUBMITTER` (live: `PUT /api/v1/schedule1?millId=13050&year=2017` with security off → HTTP 200, `message.text = "Data saved successfully"`, persisted on read-back). Scenarios use the real role; deliberate rename, not a defect. (Verified 2026-07-29.)

- **The crown pre-fill is served, never stored.** BR-03 pre-fills 13 volume fields on the GET and raises WRN-001 "Please check and save schedule", but writes nothing — confirmed 2026-08-07 by counting non-null stored volumes at the DB immediately after the pre-filled page rendered (0 rows). The advisory is therefore accurate, not a stale message. (`crown-prefill.feature`.)
