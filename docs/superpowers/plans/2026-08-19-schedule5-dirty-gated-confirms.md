# Schedule 5 Dirty-Gated Confirms Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Schedule 5's three "unsaved data will be lost" confirms fire only when the camp panel holds something not saved since the last save — and close out the stored-name trim defect the previous plan's final review found.

**Architecture:** Two derived values in `index.tsx`: `panelBaseline` (what the panel would be discarding nothing against, computed from the served document rather than stored as state) and `panelDirty` (a stringify comparison of the entered text against that baseline, following `schedule8/index.tsx:489-493`). Four call sites then consult `panelDirty`, with one deliberate exception — CFM-004 stays unconditional because it is a route, not a warning.

**Tech Stack:** React 19 + TypeScript, IBM Carbon (`Modal`), Vitest + Testing Library + MSW.

## Global Constraints

- Repo: `nr-ilcr` (nested at `~/ilcr/ilcr-bmad/nr-ilcr`). Branch: **`fix/frontend-styling-sched-5`**. Do not create a branch.
- **Never `git push`.** Commit locally only; the user pushes.
- Frontend only. Nothing under `backend/` is modified, including `messages.properties`.
- All commands run from `nr-ilcr/frontend`.
- **Windows-side `npx vitest` and `npm` FAIL on this UNC-mounted checkout** (Rolldown native config resolution + cmd.exe UNC restrictions). Run tests through WSL's own Node against the Linux-native path `/home/sofiascholefield/ilcr/ilcr-bmad/nr-ilcr/frontend`. The exact invocation is recorded in `.superpowers/sdd/2026-08-19-schedule5-inline-validation/task-2-report.md` — reuse it rather than rediscovering it.
- Baseline at the start of this plan: **844 frontend tests passing, lint clean.** Report the counts you actually observe.
- No confirm's wording changes. `CONFIRM_DELETE` is not touched.
- Comments explain **why**, not what, and cite the legacy or backend source they encode — `index.tsx` and `validation.ts` are citation-heavy, and that is the house standard.
- Straight change: no story record, no deviation letters. The deviation from legacy (legacy warned on every one of these transitions) is captured in the code comments.
- Do not restructure, rename, or reformat anything the task does not require.

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `frontend/src/components/schedule5/validation.ts` | Every camp rule, as pure functions over form strings. | Task 1: stop trimming the stored side of the BR-02 comparison. |
| `frontend/src/components/schedule5/index.tsx` | The page: state, derived values, confirm wiring. | Tasks 2-3: add `panelBaseline`/`panelDirty`; gate four call sites. |
| `frontend/src/components/schedule5/__tests__/validation.test.ts` | Unit coverage of the rules. | Task 1: one new test. |
| `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx` | Component coverage. | Tasks 2-3: two new `describe` blocks. |

---

### Task 1: Stop trimming the stored camp name

Carried over from the previous plan's final whole-branch review, which rated it the one thing blocking merge. Independent of the confirm work and committed separately.

The client currently trims **both** sides of the BR-02 comparison. The server deliberately trims only the submitted side, and `Schedule5Repository.java:400-410` spells out why:

> The STORED side is deliberately not `TRIM`med: legacy persisted names untrimmed (`Schedule5DAO.java:373`) and compared the raw stored value, so a legacy-persisted `" Cedar "` does not collide with a new `"Cedar"` there either. Matching that is legacy parity, not an oversight (deviation (I) note) — adding `TRIM(CAMP_NAME)` would retroactively 409 edits next to padded incumbents legacy accepted.

Reads are raw — the camps `SELECT` does not trim — so a legacy-persisted `" Cedar Flats Camp "` reaches the client padded. Because Save is *gated* on `validateCamp`, the licensee is hard-blocked from a save the server would have accepted, with no way out: `buildRequest` trims, so they cannot type padding to escape, and the padded incumbent renders as identical text in the table.

**Files:**
- Modify: `frontend/src/components/schedule5/validation.ts:276-288`
- Test: `frontend/src/components/schedule5/__tests__/validation.test.ts` (the `camp name uniqueness (BR-02, client pre-check)` describe block)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: no signature change. `isDuplicateName` stays module-private; `validateCamp(values, otherCampNames?)` is unchanged.

- [ ] **Step 1: Write the failing test**

Add inside the existing `camp name uniqueness (BR-02, client pre-check)` describe block in `validation.test.ts`:

```ts
  it('does NOT collide with a padded STORED name — the server does not trim that side either', () => {
    // `countCampsNamed` compares `UPPER(CAMP_NAME)` with no TRIM on the stored side
    // (Schedule5Repository.java:400-410): legacy persisted names untrimmed, so the server ACCEPTS
    // this save. Trimming here would hard-block it, and buildRequest trims the entry so the
    // licensee could not type padding to escape.
    expect(validateCamp(baseForm({ campName: 'Cedar Flats Camp' }), ['  Cedar Flats Camp  '])).toEqual(
      {},
    )
  })
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/validation.test.ts
```

(Through the WSL-native invocation — see Global Constraints.)

Expected: FAIL. The stored side is trimmed today, so `'  Cedar Flats Camp  '` folds to `'CEDAR FLATS CAMP'`, matches, and the returned map carries `campName: 'Camp name already exists.'` instead of being empty.

- [ ] **Step 3: Drop the trim on the stored side**

In `validation.ts`, replace lines 276-288 — currently:

```ts
/**
 * BR-02's comparison, matching the server's exactly: `countCampsNamed` upper-cases both sides
 * (`Schedule5Repository.java:419`) and the service trims the submitted name before counting
 * (`Schedule5Service.trimmedCampName():863`). Trimming and folding case here is what stops
 * `  cedar flats camp ` from passing an advisory check and then drawing a 409.
 *
 * `toUpperCase`, not `toLocaleUpperCase`: Oracle's `UPPER` is not locale-aware, and a locale-aware
 * fold would disagree with it on a Turkish dotless i.
 */
const isDuplicateName = (raw: string, otherCampNames: readonly string[]): boolean => {
  const candidate = raw.trim().toUpperCase()
  return otherCampNames.some((name) => name.trim().toUpperCase() === candidate)
}
```

with:

```ts
/**
 * BR-02's comparison, matching the server's predicate exactly — which is ASYMMETRIC:
 *
 *     UPPER(CAMP_NAME) = UPPER(:name)      -- Schedule5Repository.java:412-418
 *
 * Case is folded on both sides, but only the SUBMITTED side is trimmed, by
 * `Schedule5Service.trimmedCampName()` (`:862-864`) before the value is bound. The STORED side is
 * deliberately left untrimmed (`Schedule5Repository.java:400-410`): legacy persisted names untrimmed
 * (`Schedule5DAO.java:373`), so a stored `" Cedar "` does not collide with a new `"Cedar"` there
 * either, and adding `TRIM(CAMP_NAME)` "would retroactively 409 edits next to padded incumbents
 * legacy accepted."
 *
 * So this must NOT trim `name`. Doing so made the advisory check STRICTER than the authority it
 * mirrors, and because Save is gated on `validateCamp`, a padded legacy incumbent hard-blocked a save
 * the server accepts — with no way out, since `buildRequest` trims the entry.
 *
 * `toUpperCase`, not `toLocaleUpperCase`: Oracle's `UPPER` is not locale-aware, and a locale-aware
 * fold would disagree with it on a Turkish dotless i.
 */
const isDuplicateName = (raw: string, otherCampNames: readonly string[]): boolean => {
  const candidate = raw.trim().toUpperCase()
  return otherCampNames.some((name) => name.toUpperCase() === candidate)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/validation.test.ts
```

Expected: PASS, including the pre-existing `reports a duplicate case-insensitively and ignoring surrounding whitespace` test — that one pads the **submitted** name (`'  cedar flats camp  '` against a clean stored `'Cedar Flats Camp'`), which still trims and still collides. If that test now fails, the trim was removed from the wrong side.

- [ ] **Step 5: Run the full suite and lint**

```bash
npm run test:unit -- --run
npm run lint
```

Expected: green, one test more than the 844 baseline.

- [ ] **Step 6: Commit**

```bash
git add src/components/schedule5/validation.ts src/components/schedule5/__tests__/validation.test.ts
git commit -m "fix(schedule5): do not trim the stored name in the BR-02 pre-check

The server's predicate is asymmetric — UPPER on both sides, TRIM on the
submitted side only (Schedule5Repository:400-410). Trimming the stored side
made the advisory check stricter than its authority, hard-blocking a save
the server accepts next to a padded legacy incumbent."
```

---

### Task 2: `panelDirty`, and the Close and switch confirms

**Files:**
- Modify: `frontend/src/components/schedule5/index.tsx`
  - insertion point after `otherCampNames` (`:530-537`)
  - row Edit button `:1025-1034`
  - Close button `:1135-1142`
  - Add New Camp button `:1196-1203`
- Test: `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`

**Interfaces:**
- Consumes: `emptyForm(): CampFormValues` (`index.tsx:100`), `seedForm(camp: Camp, keepName: boolean): CampFormValues` (`:115`), and the existing state `panelMode: PanelMode`, `panelCampId: number | null`, `form: CampFormValues`, `data: Schedule5Response | undefined`.
- Produces: `panelDirty: boolean` and `panelBaseline: CampFormValues | null`. Task 3 reads `panelDirty`.

- [ ] **Step 1: Write the failing tests**

Append to `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`:

```ts
describe('Schedule 5 unsaved-data confirms fire only when the panel is dirty', () => {
  const CLOSE_CONFIRM = 'Any unsaved data will be lost. Are you sure you would like to continue?'
  const SWITCH_CONFIRM =
    'Any unsaved changes to the current camp report will be lost. Are you sure you would like to continue?'

  /** A second camp, so the switch paths have somewhere to go. */
  const birchRidge: Camp = { ...cedarFlats, campId: 8402, campName: 'Birch Ridge Camp' }

  test('a clean edit panel closes immediately, with no confirm', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    await user.click(panelButton(/^close$/i))

    // The panel is gone and no confirm was shown.
    await waitFor(() => expect(screen.queryByLabelText('Camp Name')).toBeNull())
    expect(screen.queryByText(CLOSE_CONFIRM)).toBeNull()
  })

  test('a dirty edit panel confirms on Close, and No keeps the edit', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    await user.type(screen.getByLabelText('Comments'), ' extra')
    await user.click(panelButton(/^close$/i))

    const dialog = confirmDialog(CLOSE_CONFIRM)
    await user.click(within(dialog).getByRole('button', { name: /^no$/i }))
    expect(screen.getByLabelText('Camp Name')).toHaveValue('Cedar Flats Camp')
    expect(screen.getByLabelText('Comments')).toHaveValue('Seasonal camp, spring only. extra')
  })

  test('after a successful save the panel is clean again, so Close is silent', async () => {
    // The "since the last save" case: applySaved re-seeds the form from the saved camp, which IS
    // the new baseline.
    const saved = { ...cedarFlats, comments: 'Seasonal camp, spring only. extra' }
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, () =>
        HttpResponse.json(
          doc({
            camps: [saved],
            message: { key: 'dataSavedSuccesfullyInfoMsg', text: 'Data saved successfully' },
          }),
        ),
      ),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    await user.type(screen.getByLabelText('Comments'), ' extra')
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    expect(await screen.findByText('Data saved successfully')).toBeInTheDocument()

    await user.click(panelButton(/^close$/i))
    await waitFor(() => expect(screen.queryByLabelText('Camp Name')).toBeNull())
    expect(screen.queryByText(CLOSE_CONFIRM)).toBeNull()
  })

  test('a freshly-opened Copy panel confirms on Close with no edit at all', async () => {
    // A copy is unsaved data in itself: the camp does not exist server-side, so Close discards a
    // camp the licensee asked to create.
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.get(MESSAGES_URL, () =>
        HttpResponse.json({ key: 'x', text: 'Provide a new Camp Name.' }),
      ),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /^copy$/i }))
    await screen.findByLabelText('Camp Name')

    await user.click(panelButton(/^close$/i))
    expect(await screen.findByText(CLOSE_CONFIRM)).toBeInTheDocument()
  })

  test('an empty New panel closes silently; one typed character makes it confirm', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /add new camp/i }))
    await screen.findByLabelText('Camp Name')

    await user.click(panelButton(/^close$/i))
    await waitFor(() => expect(screen.queryByLabelText('Camp Name')).toBeNull())
    expect(screen.queryByText(CLOSE_CONFIRM)).toBeNull()

    await user.click(screen.getByRole('button', { name: /add new camp/i }))
    await user.type(await screen.findByLabelText('Camp Name'), 'B')
    await user.click(panelButton(/^close$/i))
    expect(await screen.findByText(CLOSE_CONFIRM)).toBeInTheDocument()
  })

  test('a View panel closes with no confirm', async () => {
    // Only the Close path is testable from a view panel: `view` is reachable only on a NON-editable
    // document, where Add New Camp is disabled and the rows render `View` alone with no panelOpen
    // gate — so no switch confirm can fire there in the first place.
    server.use(http.get(URL, () => HttpResponse.json(doc({ editable: false }))))
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: /^view$/i }))
    await screen.findByLabelText('Camp Name')

    await user.click(panelButton(/^close$/i))
    await waitFor(() => expect(screen.queryByLabelText('Camp Name')).toBeNull())
    expect(screen.queryByText(CLOSE_CONFIRM)).toBeNull()
  })

  test('switching camps from a CLEAN panel proceeds with no confirm', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ camps: [cedarFlats, birchRidge] }))))
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click((await screen.findAllByRole('button', { name: /^edit$/i }))[0])
    await screen.findByLabelText('Camp Name')

    await user.click(screen.getAllByRole('button', { name: /^edit$/i })[1])
    expect(screen.queryByText(SWITCH_CONFIRM)).toBeNull()
    await waitFor(() => expect(screen.getByLabelText('Camp Name')).toHaveValue('Birch Ridge Camp'))
  })

  test('switching camps from a DIRTY panel still confirms, and Yes discards the draft', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc({ camps: [cedarFlats, birchRidge] }))))
    render(<Schedule5 />)
    const user = userEvent.setup()

    await user.click((await screen.findAllByRole('button', { name: /^edit$/i }))[0])
    await user.type(await screen.findByLabelText('Comments'), ' extra')

    await user.click(screen.getAllByRole('button', { name: /^edit$/i })[1])
    const dialog = confirmDialog(SWITCH_CONFIRM)
    await user.click(within(dialog).getByRole('button', { name: /^yes$/i }))
    await waitFor(() => expect(screen.getByLabelText('Camp Name')).toHaveValue('Birch Ridge Camp'))
  })

  test('Add New Camp from a CLEAN panel opens the new panel with no confirm', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    await user.click(screen.getByRole('button', { name: /add new camp/i }))
    expect(screen.queryByText(SWITCH_CONFIRM)).toBeNull()
    expect(await screen.findByText('New Camp Details')).toBeInTheDocument()
  })

  test('BR-03 propagation makes the panel dirty', async () => {
    // Changing the camp volume rewrites eleven category volumes — unmistakably unsaved data.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const campVolume = screen.getByLabelText('Associated Camp Volume (m³)')
    await user.clear(campVolume)
    await user.type(campVolume, '130000')

    await user.click(panelButton(/^close$/i))
    expect(await screen.findByText(CLOSE_CONFIRM)).toBeInTheDocument()
  })

  test('a camp missing from the served document is treated as dirty', async () => {
    // Nothing to compare against, so confirm rather than discard silently.
    //
    // The delete echo is the lever: deleting the OTHER camp refreshes `data` while leaving the panel
    // open (handleDelete only closes it when the deleted camp IS the panel's), and the echo here
    // drops camp 8401 as well — as if another session had removed it meanwhile.
    server.use(
      http.get(URL, () => HttpResponse.json(doc({ camps: [cedarFlats, birchRidge] }))),
      http.delete(CAMP_URL, () => HttpResponse.json(doc({ camps: [] }))),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()

    // Edit 8401, then delete 8402 so the panel survives the refresh.
    await user.click((await screen.findAllByRole('button', { name: /^edit$/i }))[0])
    await screen.findByLabelText('Camp Name')
    await user.click(screen.getAllByRole('button', { name: /^delete$/i })[1])
    const del = confirmDialog('This will delete the current record. Do you want to continue?')
    await user.click(within(del).getByRole('button', { name: /^yes$/i }))

    // Panel still seated on a camp the document no longer carries → baseline unprovable → confirm.
    await waitFor(() => expect(screen.getByText('No records found.')).toBeInTheDocument())
    expect(screen.getByLabelText('Camp Name')).toBeInTheDocument()
    await user.click(panelButton(/^close$/i))
    expect(await screen.findByText(CLOSE_CONFIRM)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/Schedule5.test.tsx
```

Expected: the "clean" tests FAIL (every confirm is unconditional today, so a confirm appears where the test asserts none). The "dirty still confirms" tests PASS already — they pin behaviour that must survive. Report which did which.

- [ ] **Step 3: Add `panelBaseline` and `panelDirty`**

In `index.tsx`, immediately after the `otherCampNames` `useMemo` (which ends at `:537`):

```ts
  /**
   * What the panel would be discarding nothing against. DERIVED from the served document rather than
   * snapshotted into state, so there is nothing to keep in sync — and so "since the last save" falls
   * out for free: `applySaved` re-seeds the form from the saved camp, which means a successful save
   * lands the panel exactly on its own new baseline.
   *
   * `emptyForm()` for a NEW or COPIED camp, because neither exists server-side and there is no saved
   * state to compare with. A copy is therefore dirty from the moment it opens — it carries the source
   * camp's values against an empty baseline — which is exactly right: closing it discards a whole
   * camp the licensee asked to create. An empty new panel matches its baseline and closes silently.
   *
   * `null` means "cannot compare": either no panel, or an edited camp the served document no longer
   * carries (deleted in another session).
   */
  const panelBaseline = useMemo<CampFormValues | null>(() => {
    if (panelMode === 'closed') {
      return null
    }
    if (panelCampId === null) {
      return emptyForm()
    }
    const served = (data?.camps ?? []).find((camp) => camp.campId === panelCampId)
    return served === undefined ? null : seedForm(served, true)
  }, [data, panelMode, panelCampId])

  /**
   * Whether the panel holds anything not saved since the last save — the gate on all three
   * "unsaved data will be lost" confirms. Legacy warned on every one of those transitions regardless
   * of state, so gating them is a deliberate deviation.
   *
   * Compared as the ENTERED TEXT (`schedule8/index.tsx:489-493` does the same for its page editor).
   * Safe here because both sides are built by `seedForm`/`emptyForm` and every update spreads rather
   * than rebuilds, so key order is stable. Text rather than parsed values means retyping `120,000` as
   * `120000` counts as dirty though the number is unchanged: it over-warns only there and NEVER
   * under-warns, and legacy warned every time, so the over-warn is the legacy-faithful direction.
   * Schedule 5 has no blur-time re-grouping (its masks format only read-only served values), so
   * merely focusing and leaving a field cannot fake a change.
   *
   * A `view` panel is excluded explicitly rather than relying on its form matching its baseline, so
   * that a view panel whose camp went missing does not start prompting. An unprovable baseline is
   * otherwise treated as dirty: a spurious confirm costs a click, a missing one costs the licensee's
   * work.
   */
  const panelDirty =
    panelMode !== 'closed' &&
    panelMode !== 'view' &&
    (panelBaseline === null || JSON.stringify(form) !== JSON.stringify(panelBaseline))
```

- [ ] **Step 4: Gate the Close button**

`index.tsx:1135-1142`, replace:

```tsx
        <Button
          kind="secondary"
          disabled={saving}
          onClick={() => (readOnlyPanel ? closePanel() : setConfirmClose(true))}
        >
          Close
        </Button>
```

with:

```tsx
        <Button
          kind="secondary"
          disabled={saving}
          // The `readOnlyPanel` test this replaces is subsumed: a view panel is never dirty.
          onClick={() => (panelDirty ? setConfirmClose(true) : closePanel())}
        >
          Close
        </Button>
```

- [ ] **Step 5: Gate the row Edit button**

`index.tsx:1029-1031`, replace:

```tsx
          onClick={() =>
            panelOpen ? setPendingSwitch({ kind: 'edit', camp }) : openEditOrView(camp, 'edit')
          }
```

with:

```tsx
          onClick={() =>
            panelOpen && panelDirty
              ? setPendingSwitch({ kind: 'edit', camp })
              : openEditOrView(camp, 'edit')
          }
```

- [ ] **Step 6: Gate Add New Camp**

`index.tsx:1200`, replace:

```tsx
            onClick={() => (panelOpen ? setPendingSwitch({ kind: 'new' }) : openNew())}
```

with:

```tsx
            onClick={() => (panelOpen && panelDirty ? setPendingSwitch({ kind: 'new' }) : openNew())}
```

- [ ] **Step 7: Run the schedule 5 tests**

```bash
npx vitest run --mode test src/components/schedule5
```

Expected: PASS, new and pre-existing alike. Several pre-existing tests drive these confirms deliberately — e.g. `editable WITH a panel open: Edit fires CFM-003 before switching` and `Add New Camp fires CFM-003 when a panel is already open`. Those tests type into Camp Name first, so their panels are genuinely dirty and the confirms still fire.

**If a pre-existing test fails because its panel is clean, do NOT weaken it and do NOT add a stray edit just to make the confirm reappear.** Report it: a test that pinned an unconditional confirm is now pinning behaviour this task deliberately changed, and the controller decides whether the test's intent survives.

- [ ] **Step 8: Lint**

```bash
npm run lint
```

Expected: no new errors.

- [ ] **Step 9: Commit**

```bash
git add src/components/schedule5/index.tsx src/components/schedule5/__tests__/Schedule5.test.tsx
git commit -m "feat(schedule5): confirm on Close and camp switch only when dirty

panelBaseline derives from the served document, so a successful save lands
the panel on its own new baseline and the next Close is silent. A copy is
dirty from the moment it opens; an empty new panel is not."
```

---

### Task 3: The sub-page link — CFM-002 gated, CFM-004 not

Separated because it is the subtle one: `pendingSubPage` drives two different modals and only one of them is a warning about losing edits. A reviewer could accept Task 2 and reject this.

**Files:**
- Modify: `frontend/src/components/schedule5/index.tsx:838-851` (`requestSubPage`)
- Test: `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`

**Interfaces:**
- Consumes from Task 2: `panelDirty: boolean`. Also the existing `panelIsUnsavedCamp: boolean` (`index.tsx:829`) and `openSubPage(kind: SubPageKind, campId: number | null): void` (`:831`).
- Produces: nothing new.

- [ ] **Step 1: Write the failing tests**

Append to `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`:

```ts
describe('Schedule 5 sub-page links: CFM-002 is dirty-gated, CFM-004 is not', () => {
  const CFM_002 = 'Any unsaved data will be lost. Are you sure you would like to continue?'
  const CFM_004 =
    'The information for the New Camp must be saved before you can add other expenses. Would you like to save the information now?'

  test('a CLEAN existing camp reaches its sub-page with no confirm', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    await user.click(screen.getByRole('button', { name: /other camp expenses/i }))

    expect(screen.queryByText(CFM_002)).toBeNull()
    await waitFor(() =>
      expect(navigateSpy).toHaveBeenCalledWith(
        expect.objectContaining({ search: { camp: 8401, sub: 'CAMP' } }),
      ),
    )
  })

  test('a DIRTY existing camp confirms before navigating', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    await user.type(screen.getByLabelText('Comments'), ' extra')
    await user.click(screen.getByRole('button', { name: /other camp expenses/i }))

    expect(await screen.findByText(CFM_002)).toBeInTheDocument()
  })

  test('a PRISTINE new camp still fires CFM-004 — it is a route, not a warning', async () => {
    // The camp does not exist server-side, so there is nothing to navigate to until it is saved
    // (Schedule5MB.java:212-217). Dirtiness is irrelevant here.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /add new camp/i }))
    await screen.findByLabelText('Camp Name')

    await user.click(screen.getByRole('button', { name: /other camp expenses/i }))

    expect(await screen.findByText(CFM_004)).toBeInTheDocument()
    expect(screen.queryByText(CFM_002)).toBeNull()
  })

  test('a COPY panel fires CFM-004, not CFM-002', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.get(MESSAGES_URL, () =>
        HttpResponse.json({ key: 'x', text: 'Provide a new Camp Name.' }),
      ),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /^copy$/i }))
    await screen.findByLabelText('Camp Name')

    await user.click(screen.getByRole('button', { name: /other camp expenses/i }))

    expect(await screen.findByText(CFM_004)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/Schedule5.test.tsx
```

Expected: only the first test FAILS — a clean existing camp currently gets CFM-002. The other three PASS already; they pin behaviour this task must not break.

- [ ] **Step 3: Gate CFM-002 only**

`index.tsx:838-851`, replace `requestSubPage` — currently:

```ts
  const requestSubPage = (kind: SubPageKind) => {
    if (saving) {
      return
    }
    clearBanners()
    // Read-only navigates straight through: legacy renders a bare link there and there is no
    // unsaved data to warn about. `data.editable` is read directly rather than through the
    // `editable` const, which is not in scope until after the loading guards below.
    if (panelMode === 'view' || data?.editable !== true) {
      openSubPage(kind, panelCampId)
      return
    }
    setPendingSubPage(kind)
  }
```

with:

```ts
  const requestSubPage = (kind: SubPageKind) => {
    if (saving) {
      return
    }
    clearBanners()
    // Read-only navigates straight through: legacy renders a bare link there and there is no
    // unsaved data to warn about. `data.editable` is read directly rather than through the
    // `editable` const, which is not in scope until after the loading guards below.
    if (panelMode === 'view' || data?.editable !== true) {
      openSubPage(kind, panelCampId)
      return
    }
    // `pendingSubPage` drives TWO modals and only one of them warns about losing edits, so only one
    // is dirty-gated:
    //
    //   CFM-004 (unsaved new-or-copied camp) is not a warning at all — it is the ONLY route to a
    //   sub-page for a camp that does not exist server-side yet, which is why legacy saves first
    //   (`Schedule5MB.java:212-217`). It must fire even for a pristine, empty new panel.
    //
    //   CFM-002 (existing camp) is a genuine warning: legacy saves nothing here and discards the
    //   panel's edits outright (`:195-203`). With nothing entered there is nothing to discard.
    if (!panelIsUnsavedCamp && !panelDirty) {
      openSubPage(kind, panelCampId)
      return
    }
    setPendingSubPage(kind)
  }
```

`panelIsUnsavedCamp` is declared at `:829`, above `requestSubPage`, and `panelDirty` comes from Task 2's insertion after `:537` — both are in scope here.

- [ ] **Step 4: Run the schedule 5 tests**

```bash
npx vitest run --mode test src/components/schedule5
```

Expected: PASS. Pre-existing sub-page tests that drive CFM-002 type into the panel first, so they stay dirty and their confirms still fire. Apply Task 2 Step 7's rule if one does not: report it, do not weaken it.

- [ ] **Step 5: Run the full suite and lint**

```bash
npm run test:unit -- --run
npm run lint
```

Expected: green. Report the observed counts against the 844 starting baseline.

- [ ] **Step 6: Commit**

```bash
git add src/components/schedule5/index.tsx src/components/schedule5/__tests__/Schedule5.test.tsx
git commit -m "feat(schedule5): dirty-gate CFM-002, leave CFM-004 unconditional

A clean existing camp reaches its sub-page without a warning about edits it
does not have. An unsaved new or copied camp still always confirms: CFM-004
is the only route to a sub-page for a camp with no server-side row."
```

---

## Verification

Before claiming the work complete, run and paste the output of:

```bash
npm run test:unit -- --run
npm run lint
git log --oneline fix/frontend-styling-sched-5 -6
```

Do not push. Report the branch and commit range and stop there.

Manual check worth doing once in the browser (dev backend via `docker compose up --force-recreate`, not `docker restart`): open a camp, press Close with nothing typed — it should close with no prompt. Reopen, type one character in Comments, press Close — the prompt should appear.
