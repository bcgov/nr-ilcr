# Schedule 5 Inline Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Schedule 5's camp-panel errors appear as the licensee leaves each field, and report a duplicate camp name inline under Camp Name before any request is sent.

**Architecture:** `validateCamp` stays the single source of every rule and gains one optional parameter carrying the other camps' names. `index.tsx` derives the full error map at render (the `schedule3/index.tsx:352` pattern) and gates *which* of those errors are on screen through a new `blurred: Set<string>` state — blur adds a key, editing removes it, a rejected Save adds every erroring key. The server's 409 path is untouched.

**Tech Stack:** React 19 + TypeScript, IBM Carbon (`TextInput`/`Select`/`TextArea` `invalid`/`invalidText`), Vitest + Testing Library + MSW.

## Global Constraints

- Repo: `nr-ilcr` (nested at `~/ilcr/ilcr-bmad/nr-ilcr`). Branch: **`fix/frontend-styling-sched-5`**. Do not create a new branch.
- Frontend only. No file under `backend/` is modified — including `messages.properties`.
- All commands run from `nr-ilcr/frontend`.
- Test command: `npx vitest run --mode test <path>`. Full suite: `npm run test:unit -- --run`. Lint: `npm run lint`.
- Client message literals are **byte-identical** to the backend bundle. `campAlreadyExists=Camp name already exists.` at `backend/src/main/resources/messages.properties:222` is the only new one.
- Duplicate detection is **advisory**. The server's `CampNameConflictException` 409 remains and remains authoritative; it keeps rendering verbatim in the page-level error banner and is **never** routed to a field (AD-6/AD-8).
- Comments explain **why**, not what, and cite the legacy or backend source they encode — matching the existing density in these two files.
- This is a straight change: no story record, no deviation letters. Deviations from legacy (blur-time timing; client-side BR-02 pre-check) are captured in the code comments.
- Do not restructure, rename, or reformat anything the task does not require.

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `frontend/src/components/schedule5/validation.ts` | Every camp rule, as pure functions over form strings. Knows nothing of the document or transport types. | Add the duplicate message + rule; `validateCamp` gains a second parameter. |
| `frontend/src/components/schedule5/index.tsx` | The page: state, the `blurred` gate, the derived error map, field wiring. | New state and handlers; `onBlur` threaded to every editable control. |
| `frontend/src/components/schedule5/__tests__/validation.test.ts` | Unit coverage of the rules + the bundle drift guard. | New `describe` block; one new `BUNDLE_KEYS` entry. |
| `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx` | Component coverage of the page's behaviour. | Two new `describe` blocks. |

---

### Task 1: The duplicate-name rule

Pure, unit-testable, and self-contained: `validateCamp`'s new parameter defaults to `[]`, so every existing caller and every existing test keeps compiling and passing before Task 2 touches the page.

**Files:**
- Modify: `frontend/src/components/schedule5/validation.ts` (`CAMP_MESSAGES` at :18-33; `validateCamp` at :301-383)
- Test: `frontend/src/components/schedule5/__tests__/validation.test.ts` (new block at end; `BUNDLE_KEYS` at :355-368)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `validateCamp(values: CampFormValues, otherCampNames?: readonly string[]): CampErrors` and `CAMP_MESSAGES.campNameDuplicate`. Task 2 calls the two-argument form.

- [ ] **Step 1: Write the failing tests**

Append to `frontend/src/components/schedule5/__tests__/validation.test.ts`:

```ts
describe('camp name uniqueness (BR-02, client pre-check)', () => {
  it('reports a duplicate case-insensitively and ignoring surrounding whitespace', () => {
    // The server upper-cases both sides (Schedule5Repository:419) and trims the submitted name
    // (Schedule5Service.trimmedCampName():863), so this entry WOULD be rejected server-side.
    const errors = validateCamp(baseForm({ campName: '  cedar flats camp  ' }), [
      'Cedar Flats Camp',
    ])
    expect(errors.campName).toBe(CAMP_MESSAGES.campNameDuplicate)
  })

  it('accepts a name no other camp holds', () => {
    expect(validateCamp(baseForm({ campName: 'Birch Ridge Camp' }), ['Cedar Flats Camp'])).toEqual(
      {},
    )
  })

  it('checks nothing when the caller supplies no names, including by omission', () => {
    expect(validateCamp(baseForm())).toEqual({})
    expect(validateCamp(baseForm(), [])).toEqual({})
  })

  it('reports the name’s OWN error ahead of the duplicate when it is also blank or over-length', () => {
    // Two statements about one field where only the first is actionable.
    expect(validateCamp(baseForm({ campName: '   ' }), ['   ']).campName).toBe(
      CAMP_MESSAGES.campNameRequired,
    )
    const overLong = 'C'.repeat(31)
    expect(validateCamp(baseForm({ campName: overLong }), [overLong]).campName).toBe(
      CAMP_MESSAGES.campNameMaxLength,
    )
  })

  it('leaves every other field’s rules untouched', () => {
    // A duplicate name must not mask, or be masked by, an unrelated error.
    const errors = validateCamp(baseForm({ campName: 'Cedar Flats Camp', sizeOfCamp: '0' }), [
      'Cedar Flats Camp',
    ])
    expect(errors.campName).toBe(CAMP_MESSAGES.campNameDuplicate)
    expect(errors.sizeOfCamp).toBe(CAMP_MESSAGES.sizeRange)
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/validation.test.ts
```

Expected: the five new tests FAIL — `CAMP_MESSAGES.campNameDuplicate` is `undefined`, so the first, fourth and fifth compare against `undefined`, and the second/third pass vacuously. TypeScript also reports `campNameDuplicate` does not exist on `CAMP_MESSAGES`.

- [ ] **Step 3: Add the message constant**

In `validation.ts`, inside `CAMP_MESSAGES`, immediately after the `campNameMaxLength` line:

```ts
  // BR-02/ERR-001. Mirrored so the client's pre-check and the server's 409 say the same sentence.
  campNameDuplicate: 'Camp name already exists.',
```

- [ ] **Step 4: Add the comparison helper**

In `validation.ts`, immediately above `validateCampName` (:274):

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

- [ ] **Step 5: Extend `validateCamp`**

Replace the doc comment and signature at `validation.ts:301-307` — currently:

```ts
/**
 * Validate the whole camp panel. Returns a field-keyed error map, empty when the form may be sent.
 *
 * There is no duplicate-name check: BR-02 is the server's (a name collision is its 409, rendered
 * verbatim), and the client cannot see other mills' camps anyway.
 */
export const validateCamp = (values: CampFormValues): CampErrors => {
```

with:

```ts
/**
 * Validate the whole camp panel. Returns a field-keyed error map, empty when the form may be sent.
 *
 * `otherCampNames` carries the names of every OTHER camp in the served mill/year, letting BR-02 be
 * reported inline instead of only as the server's 409 on a doomed round-trip. It is a PRE-check, not
 * a replacement: the served list is a snapshot, so a camp another licensee adds after this page
 * loaded is invisible here and only `Schedule5Service`'s own `countCampsNamed` can catch it. That
 * 409 still renders verbatim on the page banner (AD-6/AD-8).
 *
 * Legacy left this check entirely to the server, so the client half is a deliberate deviation.
 *
 * The caller supplies the list already filtered — by campId, see `otherCampNames` in index.tsx — so
 * this module stays free of transport types and of any notion of which camp the panel is showing.
 */
export const validateCamp = (
  values: CampFormValues,
  otherCampNames: readonly string[] = [],
): CampErrors => {
```

Then replace the name block at :309-312:

```ts
  const nameError = validateCampName(values.campName)
  if (nameError) {
    errors.campName = nameError
  }
```

with:

```ts
  const nameError = validateCampName(values.campName)
  if (nameError) {
    errors.campName = nameError
  } else if (isDuplicateName(values.campName, otherCampNames)) {
    // Only once the name is otherwise legal: "required"/"30 characters or fewer" and "already
    // exists" are two statements about one field where only the first is actionable.
    errors.campName = CAMP_MESSAGES.campNameDuplicate
  }
```

- [ ] **Step 6: Add the bundle drift-guard entry**

The `BUNDLE_KEYS` map at `validation.test.ts:355` is typed `Record<keyof typeof CAMP_MESSAGES, string>`, so Step 3 breaks its compile until this is added. Insert after the `campNameMaxLength` line:

```ts
    campNameDuplicate: 'campAlreadyExists',
```

Also update the count in the comment at `validation.test.ts:330` from `The twelve mirrored strings` to `The thirteen mirrored strings`.

- [ ] **Step 7: Run the tests to verify they pass**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/validation.test.ts
```

Expected: PASS, 40 tests (35 existing + 5 new), including the `campNameDuplicate is byte-identical to its bundle entry` case that the `it.each` now generates.

- [ ] **Step 8: Commit**

```bash
git add src/components/schedule5/validation.ts src/components/schedule5/__tests__/validation.test.ts
git commit -m "feat(schedule5): client-side camp-name uniqueness rule

BR-02 as an advisory pre-check in validateCamp, matching the server's
trim-and-fold comparison. The 409 remains authoritative."
```

---

### Task 2: Blur-gated inline errors

One task because it is one compile unit: replacing the `errors` state breaks every render site until all of them are updated.

**Files:**
- Modify: `frontend/src/components/schedule5/index.tsx`
  - imports :13
  - `AmountCell` :181-204
  - `CategoryGridRow` :209-272
  - `CategoryGrid` :291-298 and its `CategoryGridRow` call :334-352
  - `DescriptorFields` :361-421
  - state :437
  - `resetTransient` :462, `openEditOrView` :528, `openNew` :538, `openCopy` :547, `closePanel` :568, `applySaved` :636
  - `setField` :577-579, `handleCategoryChange` :607-612
  - `handleSave` :642-664, `confirmSubPageSave` :751-784
  - derived-error insertion point after :882
  - panel render :970-999
- Test: `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`

**Interfaces:**
- Consumes from Task 1: `validateCamp(values, otherCampNames)`, `CAMP_MESSAGES.campNameDuplicate`.
- Produces: `markBlurred(key: string): void`, `clearBlurred(key: string): void`, `otherCampNames: string[]`, and the render-local `errors: CampErrors` that `DescriptorFields`/`CategoryGrid`/the Comments `TextArea` keep consuming under their existing prop names. Task 3 calls `setBlurred` directly.

- [ ] **Step 1: Write the failing tests**

Append to `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`:

```ts
describe('Schedule 5 inline validation timing', () => {
  test('an invalid entry reports itself on blur, and untouched fields stay silent', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const size = screen.getByLabelText('Size of Camp (number of persons)')
    await user.clear(size)
    await user.type(size, '0')
    // Nothing yet: the licensee has not left the field.
    expect(screen.queryByText('Entered number of persons must be between 1 and 999.')).toBeNull()

    await user.tab()
    expect(
      await screen.findByText('Entered number of persons must be between 1 and 999.'),
    ).toBeInTheDocument()
    // A second bad field the licensee never visited is NOT reported.
    expect(screen.queryByText('Entered distance must be between 0 and 999,999.')).toBeNull()
  })

  test('editing a reported field clears its message, and the next blur restores it', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const size = screen.getByLabelText('Size of Camp (number of persons)')
    await user.clear(size)
    await user.type(size, '0')
    await user.tab()
    expect(
      await screen.findByText('Entered number of persons must be between 1 and 999.'),
    ).toBeInTheDocument()

    // Clear-on-type: still invalid ('00'), but the message goes while it is being corrected.
    await user.type(size, '0')
    await waitFor(() =>
      expect(screen.queryByText('Entered number of persons must be between 1 and 999.')).toBeNull(),
    )

    await user.tab()
    expect(
      await screen.findByText('Entered number of persons must be between 1 and 999.'),
    ).toBeInTheDocument()
  })

  test('a corrected value leaves nothing behind on blur', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const size = screen.getByLabelText('Size of Camp (number of persons)')
    await user.clear(size)
    await user.type(size, '0')
    await user.tab()
    expect(
      await screen.findByText('Entered number of persons must be between 1 and 999.'),
    ).toBeInTheDocument()

    await user.clear(size)
    await user.type(size, '60')
    await user.tab()
    await waitFor(() =>
      expect(screen.queryByText('Entered number of persons must be between 1 and 999.')).toBeNull(),
    )
  })

  test('a rejected Save reveals EVERY offending field at once and issues no request', async () => {
    let put = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, () => {
        put = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const size = screen.getByLabelText('Size of Camp (number of persons)')
    await user.clear(size)
    await user.type(size, '0')
    const distance = screen.getByLabelText('Road Distance to Operating Area (km)')
    await user.clear(distance)
    await user.type(distance, '1000000')
    const recoveries = screen.getByLabelText('Recoveries cost')
    await user.clear(recoveries)
    await user.type(recoveries, '-1')

    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(
      await screen.findByText('Entered number of persons must be between 1 and 999.'),
    ).toBeInTheDocument()
    expect(screen.getByText('Entered distance must be between 0 and 999,999.')).toBeInTheDocument()
    expect(screen.getByText('Entered cost must be between 0 and 9,999,999.')).toBeInTheDocument()
    await flushAsync()
    expect(put).toBe(false)
  })

  test('a revealed error still clears as its own field is edited', async () => {
    // The reveal must not freeze on screen — Save adds keys to the same gate blur uses.
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const size = screen.getByLabelText('Size of Camp (number of persons)')
    await user.clear(size)
    await user.type(size, '0')
    await user.click(screen.getByRole('button', { name: /^save$/i }))
    expect(
      await screen.findByText('Entered number of persons must be between 1 and 999.'),
    ).toBeInTheDocument()

    await user.type(size, '0')
    await waitFor(() =>
      expect(screen.queryByText('Entered number of persons must be between 1 and 999.')).toBeNull(),
    )
  })

  test('choosing the blank Isolated Camp option reports it immediately — a Select change IS its commit', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    await user.selectOptions(screen.getByLabelText('Isolated Camp'), '')
    expect(await screen.findByText('Isolated Camp is required.')).toBeInTheDocument()
  })

  test('a category cell reports on blur, keyed to that half of that row alone', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const cost = screen.getByLabelText('Catering and Food cost')
    await user.clear(cost)
    await user.type(cost, '99999999')
    expect(screen.queryByText('Entered cost must be between -9,999,999 and 9,999,999.')).toBeNull()

    await user.tab()
    expect(
      await screen.findByText('Entered cost must be between -9,999,999 and 9,999,999.'),
    ).toBeInTheDocument()
  })

  test('a view-only panel reports nothing, however the stored values look', async () => {
    server.use(
      http.get(URL, () =>
        HttpResponse.json(
          doc({ editable: false, camps: [{ ...cedarFlats, isolatedCamp: null, sizeOfCamp: 0 }] }),
        ),
      ),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /^view$/i }))

    await screen.findByText('Cedar Flats Camp')
    expect(screen.queryByText('Isolated Camp is required.')).toBeNull()
    expect(screen.queryByText('Entered number of persons must be between 1 and 999.')).toBeNull()
  })

  test('reopening the panel starts clean', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const size = screen.getByLabelText('Size of Camp (number of persons)')
    await user.clear(size)
    await user.type(size, '0')
    await user.tab()
    expect(
      await screen.findByText('Entered number of persons must be between 1 and 999.'),
    ).toBeInTheDocument()

    await user.click(panelButton(/^close$/i))
    const dialog = confirmDialog(
      'Any unsaved changes to the current camp report will be lost. Are you sure you would like to continue?',
    )
    await user.click(within(dialog).getByRole('button', { name: /^yes$/i }))
    await openEditor(user)

    expect(screen.queryByText('Entered number of persons must be between 1 and 999.')).toBeNull()
  })
})

describe('Schedule 5 duplicate camp name (BR-02) inline', () => {
  test('a new camp taking a stored name is reported on blur and blocks the POST', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CAMPS_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /add new camp/i }))

    const name = await screen.findByLabelText('Camp Name')
    await user.type(name, 'cedar flats camp')
    await user.tab()
    expect(await screen.findByText('Camp name already exists.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^save$/i }))
    await flushAsync()
    expect(posted).toBe(false)
  })

  test('editing a camp WITHOUT renaming it does not collide with itself — the PUT goes out', async () => {
    let put = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.put(CAMP_URL, () => {
        put = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    // Blur the untouched, unchanged name: exclusion is by campId, so it must not flag.
    await user.click(screen.getByLabelText('Camp Name'))
    await user.tab()
    expect(screen.queryByText('Camp name already exists.')).toBeNull()

    await user.click(screen.getByRole('button', { name: /^save$/i }))
    await waitFor(() => expect(put).toBe(true))
  })

  test('a COPY collides with its source camp, enforcing WRN-001’s rename', async () => {
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.get(MESSAGES_URL, () => HttpResponse.json({ key: 'x', text: 'Provide a new Camp Name.' })),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /^copy$/i }))

    const name = await screen.findByLabelText('Camp Name')
    await user.click(name)
    await user.tab()
    expect(await screen.findByText('Camp name already exists.')).toBeInTheDocument()

    // Renaming clears it.
    await user.clear(name)
    await user.type(name, 'Birch Ridge Camp')
    await user.tab()
    await waitFor(() => expect(screen.queryByText('Camp name already exists.')).toBeNull())
  })

  test('a duplicate name blocks the CFM-004 save-and-navigate path', async () => {
    let posted = false
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CAMPS_URL, () => {
        posted = true
        return HttpResponse.json(doc())
      }),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /add new camp/i }))

    const name = await screen.findByLabelText('Camp Name')
    await user.type(name, 'Cedar Flats Camp')
    await user.selectOptions(screen.getByLabelText('Isolated Camp'), 'true')
    await user.click(screen.getByRole('button', { name: /other camp expenses/i }))

    // CFM-004, verbatim from index.tsx:74.
    const dialog = confirmDialog(
      'The information for the New Camp must be saved before you can add other expenses. Would you like to save the information now?',
    )
    await user.click(within(dialog).getByRole('button', { name: /^yes$/i }))

    expect(await screen.findByText('Camp name already exists.')).toBeInTheDocument()
    await flushAsync()
    expect(posted).toBe(false)
    expect(navigateSpy).not.toHaveBeenCalledWith(
      expect.objectContaining({ search: expect.objectContaining({ sub: 'CAMP' }) }),
    )
  })

  test('the server’s own 409 still lands in the page banner, verbatim, not on the field', async () => {
    // A race: the name is free in the served snapshot, so the client passes and the server rejects.
    server.use(
      http.get(URL, () => HttpResponse.json(doc())),
      http.post(CAMPS_URL, () => problemBody(409, 'Camp name already exists.')),
    )
    render(<Schedule5 />)
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: /add new camp/i }))

    await user.type(await screen.findByLabelText('Camp Name'), 'Birch Ridge Camp')
    await user.selectOptions(screen.getByLabelText('Isolated Camp'), 'true')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByText('Action failed')).toBeInTheDocument()
    expect(screen.getByText('Camp name already exists.')).toBeInTheDocument()
    // The entered name is retained for correction.
    expect(screen.getByLabelText('Camp Name')).toHaveValue('Birch Ridge Camp')
  })
})
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/Schedule5.test.tsx
```

Expected: the new tests FAIL. The timing ones fail because errors currently appear only after Save (so the `queryByText(...)).toBeNull()` pre-blur assertions pass but the post-`tab()` `findByText` times out); the duplicate ones fail because no client check exists.

- [ ] **Step 3: Import `useMemo`**

`index.tsx:13`, replace:

```ts
import { useCallback, useState } from 'react'
```

with:

```ts
import { useCallback, useMemo, useState } from 'react'
```

- [ ] **Step 4: Replace the `errors` state with the `blurred` gate**

`index.tsx:437`, replace:

```ts
  const [errors, setErrors] = useState<CampErrors>({})
```

with:

```ts
  /**
   * Which fields are worth reporting on. Errors themselves are DERIVED at render from
   * `validateCamp` (the single rule source, the `schedule3/index.tsx:352` pattern); this set decides
   * only which of them are on screen.
   *
   * Keyed exactly as `CampErrors` is, category halves included (`cateringAndFood.volume`), so the
   * gate and the error map can never drift into two naming schemes.
   *
   * Legacy validated the camp panel only at submit, so reporting on blur is a deliberate deviation.
   */
  const [blurred, setBlurred] = useState<ReadonlySet<string>>(new Set())
```

- [ ] **Step 5: Replace every `setErrors({})` call**

There are six, at :462 (`resetTransient`), :528 (`openEditOrView`), :538 (`openNew`), :547 (`openCopy`), :568 (`closePanel`) and :636 (`applySaved`). Each becomes:

```ts
    setBlurred(new Set())
```

- [ ] **Step 6: Add the mark/clear handlers**

In `index.tsx`, immediately above `setField` (:577):

```ts
  /** Blur is the commit point: a field's error appears only once the licensee has left it. */
  const markBlurred = (key: string) => {
    setBlurred((prev) => (prev.has(key) ? prev : new Set(prev).add(key)))
  }

  /**
   * Clear-on-type. Editing a reported field un-reports it, so the message goes while the licensee is
   * correcting it and returns on the next blur if the value is still wrong.
   *
   * This is also what keeps a rejected Save from freezing its errors on screen: Save reports every
   * offending field through the SAME set, so each message is then cleared by the very edit that
   * starts fixing it. A separate "show everything" flag would have suppressed that.
   */
  const clearBlurred = (key: string) => {
    setBlurred((prev) => {
      if (!prev.has(key)) {
        return prev
      }
      const next = new Set(prev)
      next.delete(key)
      return next
    })
  }
```

- [ ] **Step 7: Make the change handlers clear, and add the blur handlers**

`index.tsx:577-579`, replace:

```ts
  const setField = (field: keyof CampFormValues, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
  }
```

with:

```ts
  const setField = (field: keyof CampFormValues, value: string) => {
    setForm((prev) => ({ ...prev, [field]: value }))
    clearBlurred(field)
  }

  /**
   * The Select's change IS its commit — there is no half-entered state to protect from flicker — so
   * choosing a value reports the field at once. Choosing the blank option therefore surfaces
   * "Isolated Camp is required." immediately, which is the point of a tri-state control whose empty
   * state is invalid.
   */
  const handleIsolatedCampChange = (value: string) => {
    setForm((prev) => ({ ...prev, isolatedCamp: value as CampFormValues['isolatedCamp'] }))
    markBlurred('isolatedCamp')
  }
```

`index.tsx:607-612`, replace `handleCategoryChange` and follow it with the blur twin:

```ts
  const handleCategoryChange = (key: CategoryKey, half: 'volume' | 'cost', value: string) => {
    setForm((prev) => ({
      ...prev,
      categories: { ...prev.categories, [key]: { ...prev.categories[key], [half]: value } },
    }))
    clearBlurred(`${key}.${half}`)
  }

  const handleCategoryBlur = (key: CategoryKey, half: 'volume' | 'cost') => {
    markBlurred(`${key}.${half}`)
  }
```

- [ ] **Step 8: Add `otherCampNames`**

In `index.tsx`, immediately below the `query` const (:486):

```ts
  /**
   * Every OTHER camp's name in the served mill/year — the client's half of BR-02.
   *
   * Excluded by campId, never by name, which is what makes the three panel modes come out right with
   * no mode-specific branching. An EDIT excludes the camp it is editing, so re-saving an unrenamed
   * camp cannot collide with itself — and a rename that duplicates a THIRD camp is still caught. A
   * NEW and a COPY both carry a null panelCampId, so every stored name collides; for a copy that is
   * exactly the rename WRN-001 asks for, now enforced before the request instead of by the 409
   * afterwards. And once `applySaved` re-seats the panel in edit mode, panelCampId is set, so the
   * second Save of a camp is not blocked by the row the first Save created.
   */
  const otherCampNames = useMemo(
    () =>
      (data?.camps ?? [])
        .filter((camp) => camp.campId !== panelCampId)
        .map((camp) => camp.campName)
        .filter((name): name is string => name !== null),
    [data, panelCampId],
  )
```

- [ ] **Step 9: Feed the two save gates**

`handleSave` (:647-652), replace:

```ts
    const found = validateCamp(form)
    setErrors(found)
    if (!isCampFormValid(found)) {
      // A client rejection issues NO request; the entered values stay exactly as typed.
      return
    }
```

with:

```ts
    const found = validateCamp(form, otherCampNames)
    if (!isCampFormValid(found)) {
      // A client rejection issues NO request; the entered values stay exactly as typed. Reporting
      // every offending field at once is the one place blur is bypassed — at Save the licensee has
      // asked about the whole form, not one field.
      setBlurred(new Set(Object.keys(found)))
      return
    }
```

`confirmSubPageSave` (:758-762), replace:

```ts
    const found = validateCamp(form)
    setErrors(found)
    if (!isCampFormValid(found)) {
      return
    }
```

with:

```ts
    const found = validateCamp(form, otherCampNames)
    if (!isCampFormValid(found)) {
      setBlurred(new Set(Object.keys(found)))
      return
    }
```

- [ ] **Step 10: Derive the displayed error map**

In `index.tsx`, immediately after `readOnlyPanel` (:882):

```ts
  // Derived, never stored: `validateCamp` stays the single rule source and `blurred` filters it
  // (the `schedule3/index.tsx:352` pattern). A read-only or non-editable panel validates nothing, so
  // a stored value today's rules would reject is never flagged at a licensee who cannot fix it.
  const allErrors: CampErrors =
    editable && !readOnlyPanel ? validateCamp(form, otherCampNames) : {}
  const errors: CampErrors = Object.fromEntries(
    Object.entries(allErrors).filter(([key]) => blurred.has(key)),
  )
```

- [ ] **Step 11: Thread `onBlur` through the category grid**

`AmountCell` (:181-204) — add the prop and pass it:

```ts
const AmountCell: FC<{
  readonly inputId: string
  readonly label: string
  readonly value: string
  readonly readOnly: boolean
  readonly invalidText?: string
  readonly onChange?: (value: string) => void
  readonly onBlur?: () => void
}> = ({ inputId, label, value, readOnly, invalidText, onChange, onBlur }) =>
```

and inside the `TextInput`, after the `onChange` line:

```tsx
        onBlur={onBlur}
```

`CategoryGridRow` (:209-219) — add to its props, beside `onChange`:

```ts
  readonly onBlur: (key: CategoryKey, half: 'volume' | 'cost') => void
```

destructure `onBlur`, and add to each of the two `AmountCell`s (:241-248 volume, :256-263 cost) after their `onChange` line:

```tsx
          onBlur={() => onBlur(row.key, 'volume')}
```

```tsx
          onBlur={() => onBlur(row.key, 'cost')}
```

`CategoryGrid` (:291-298) — add the same prop signature beside `onChange`, destructure it, and pass it to `CategoryGridRow` in the call at :334-342:

```tsx
              onBlur={onBlur}
```

- [ ] **Step 12: Thread `onBlur` through the descriptors**

`DescriptorFields` (:361-367) — replace its props block:

```ts
const DescriptorFields: FC<{
  readonly values: CampFormValues
  readonly readOnly: boolean
  readonly errors: CampErrors
  readonly onFieldChange: (field: keyof CampFormValues, value: string) => void
  readonly onFieldBlur: (field: keyof CampFormValues) => void
  readonly onIsolatedCampChange: (value: string) => void
  readonly onCampVolumeChange: (value: string) => void
}> = ({
  values,
  readOnly,
  errors,
  onFieldChange,
  onFieldBlur,
  onIsolatedCampChange,
  onCampVolumeChange,
}) => (
```

Add an `onBlur` to each of the four `TextInput`s, after their `onChange` line:

- `camp-name` → `onBlur={() => onFieldBlur('campName')}`
- `road-distance` → `onBlur={() => onFieldBlur('roadDistanceToOperatingArea')}`
- `size-of-camp` → `onBlur={() => onFieldBlur('sizeOfCamp')}`
- `associated-camp-volume` → `onBlur={() => onFieldBlur('associatedCampVolume')}`

And change the `Select`'s handler (:411) from `onFieldChange('isolatedCamp', event.target.value)` to:

```tsx
      onChange={(event) => onIsolatedCampChange(event.target.value)}
```

- [ ] **Step 13: Wire the panel render**

`index.tsx:970-976`, the `DescriptorFields` call gains two props:

```tsx
      <DescriptorFields
        values={form}
        readOnly={readOnlyPanel}
        errors={errors}
        onFieldChange={setField}
        onFieldBlur={markBlurred}
        onIsolatedCampChange={handleIsolatedCampChange}
        onCampVolumeChange={handleCampVolumeChange}
      />
```

`markBlurred` takes a `string` and `onFieldBlur` supplies a `keyof CampFormValues`, which is assignable — no cast needed.

The `CategoryGrid` call (:978-985) gains:

```tsx
        onBlur={handleCategoryBlur}
```

The Comments `TextArea` (:989-999) gains, after its `onChange` line:

```tsx
          onBlur={() => {
            markBlurred('comments')
          }}
```

- [ ] **Step 14: Run the schedule 5 tests**

```bash
npx vitest run --mode test src/components/schedule5
```

Expected: PASS. All pre-existing tests in both files still pass — the ones that type an invalid value and click Save keep working because Save reports every offending key.

If a pre-existing test fails, do not weaken it. Diagnose whether the new gate genuinely changed a behaviour that test was pinning, and report it rather than editing the assertion.

- [ ] **Step 15: Lint and typecheck**

```bash
npm run lint
```

Expected: no new errors. `errors` is now a render-local `const` shadowing nothing; if the linter objects to the name, keep the name and resolve the objection — the render sites reading `errors` are what keeps this diff small.

- [ ] **Step 16: Commit**

```bash
git add src/components/schedule5/index.tsx src/components/schedule5/__tests__/Schedule5.test.tsx
git commit -m "feat(schedule5): report camp-panel errors on blur

Errors derive from validateCamp at render; a blurred-key set decides which
are shown. Blur reports, editing un-reports, a rejected Save reports all.
Duplicate camp names now surface inline before the request."
```

---

### Task 3: BR-03 propagation un-reports the volumes it overwrites

Independently rejectable: it changes only what the licensee sees after a camp-volume edit, and a reviewer could accept Task 2 while disagreeing here.

**Files:**
- Modify: `frontend/src/components/schedule5/index.tsx` (`handleCampVolumeChange` :594-605)
- Test: `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`

**Interfaces:**
- Consumes from Task 2: `setBlurred`, and `VOLUME_CATEGORY_KEYS` (already imported at :16-32).
- Produces: nothing new.

- [ ] **Step 1: Write the failing test**

Append to `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`:

```ts
describe('Schedule 5 BR-03 propagation and the blur gate', () => {
  test('propagating a camp volume does not flag the eleven category volumes it overwrote', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    // Report a category volume first, so there is something for propagation to un-report.
    const catering = screen.getByLabelText('Catering and Food volume')
    await user.clear(catering)
    await user.type(catering, '99999999')
    await user.tab()
    expect(
      await screen.findByText('Entered volume must be between 0 and 9,999,999.'),
    ).toBeInTheDocument()

    // BR-03 assigns the camp volume into all eleven. The values were not typed there, and an
    // out-of-range camp volume reports itself at its own input instead of eleven times over.
    const campVolume = screen.getByLabelText('Associated Camp Volume (m³)')
    await user.clear(campVolume)
    await user.type(campVolume, '99999999')
    await waitFor(() =>
      expect(screen.queryByText('Entered volume must be between 0 and 9,999,999.')).toBeNull(),
    )

    // The camp volume itself still reports, once, on its own blur.
    await user.tab()
    expect(
      await screen.findByText('Entered volume must be between 0 and 9,999,999.'),
    ).toBeInTheDocument()
  })

  test('an UNPARSEABLE camp volume does not propagate, so it un-reports only itself', async () => {
    server.use(http.get(URL, () => HttpResponse.json(doc())))
    render(<Schedule5 />)
    const user = userEvent.setup()
    await openEditor(user)

    const catering = screen.getByLabelText('Catering and Food volume')
    await user.clear(catering)
    await user.type(catering, '99999999')
    await user.tab()
    expect(
      await screen.findByText('Entered volume must be between 0 and 9,999,999.'),
    ).toBeInTheDocument()

    // Legacy's listener ran only after BigDecimal conversion succeeded, so 'abc' reaches no
    // category — and the category's own reported error must therefore survive.
    const campVolume = screen.getByLabelText('Associated Camp Volume (m³)')
    await user.clear(campVolume)
    await user.type(campVolume, 'abc')
    await flushAsync()
    expect(
      screen.getByText('Entered volume must be between 0 and 9,999,999.'),
    ).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
npx vitest run --mode test src/components/schedule5/__tests__/Schedule5.test.tsx
```

Expected: the first test FAILS — after Task 2, `handleCampVolumeChange` does not go through `setField`, so it neither clears its own key nor the eleven propagated ones, and the `Catering and Food volume` error stays on screen. The second test passes already; keep it, it pins the branch the fix must not flatten.

- [ ] **Step 3: Un-report on propagation**

`index.tsx:594`, replace `handleCampVolumeChange`'s body — currently:

```ts
  const handleCampVolumeChange = (value: string) => {
    setForm((prev) => {
      if (value.trim() !== '' && parseDecimalInput(value) === null) {
        return { ...prev, associatedCampVolume: value }
      }
      const categories = { ...prev.categories }
      for (const key of VOLUME_CATEGORY_KEYS) {
        categories[key] = { ...categories[key], volume: value }
      }
      return { ...prev, associatedCampVolume: value, categories }
    })
  }
```

with:

```ts
  const handleCampVolumeChange = (value: string) => {
    // Computed OUT here, not inside the updater: the updater must stay pure, and the same condition
    // decides both whether the eleven volumes change and whether they should be un-reported.
    const propagates = value.trim() === '' || parseDecimalInput(value) !== null
    setForm((prev) => {
      if (!propagates) {
        return { ...prev, associatedCampVolume: value }
      }
      const categories = { ...prev.categories }
      for (const key of VOLUME_CATEGORY_KEYS) {
        categories[key] = { ...categories[key], volume: value }
      }
      return { ...prev, associatedCampVolume: value, categories }
    })
    // Clear-on-type for this input, plus the eleven volumes BR-03 just overwrote. Those values were
    // not typed into those fields, and an out-of-range camp volume already reports itself at its own
    // input rather than as eleven duplicates of one message — the reasoning
    // `schedule5SubPage/validation.ts:53-55` uses for not flagging untouched rows. An unparseable
    // entry propagates nothing, so it must un-report nothing but itself.
    setBlurred((prev) => {
      const next = new Set(prev)
      next.delete('associatedCampVolume')
      if (propagates) {
        for (const key of VOLUME_CATEGORY_KEYS) {
          next.delete(`${key}.volume`)
        }
      }
      return next
    })
  }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
npx vitest run --mode test src/components/schedule5
```

Expected: PASS, both new tests and everything before them.

- [ ] **Step 5: Run the full frontend suite and lint**

```bash
npm run test:unit -- --run
npm run lint
```

Expected: the whole suite green (the pre-change baseline was 625 frontend tests; this plan adds 21), no new lint errors. Report the actual counts rather than asserting a number.

- [ ] **Step 6: Commit**

```bash
git add src/components/schedule5/index.tsx src/components/schedule5/__tests__/Schedule5.test.tsx
git commit -m "feat(schedule5): BR-03 propagation un-reports the volumes it overwrites

An out-of-range camp volume reports itself at its own input rather than as
eleven copies in the categories it was assigned into. An unparseable entry
propagates nothing and un-reports nothing but itself."
```

---

## Verification

Before claiming the work complete, run and paste the output of:

```bash
npm run test:unit -- --run
npm run lint
git log --oneline fix/frontend-styling-sched-5 -4
```

Manual check worth doing once in the browser (dev backend via `docker compose up --force-recreate`, per the project's standing note — not `docker restart`): open Schedule 5, Copy a camp, tab out of Camp Name, and confirm the inline `Camp name already exists.` appears under the field with no page banner.
