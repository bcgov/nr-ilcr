# Schedule 5 — Inline Validation

**Date:** 2026-08-19
**Branch:** `fix/frontend-styling-sched-5`
**Scope:** Frontend only. No backend, DTO, or message-bundle change.
**Nature:** Straight change, not a new story record.

## Problem

Schedule 5's camp panel has a complete advisory rule set in
`frontend/src/components/schedule5/validation.ts`, but `index.tsx` runs it only when Save is
pressed. A licensee filling in a camp learns nothing about a bad entry until they submit, and then
receives every error at once.

Camp-name uniqueness (BR-02) is worse: it is checked only server-side. `Schedule5Service` pre-checks
`countCampsNamed(millId, year, name)` case-insensitively and raises `CampNameConflictException` →
409 `Camp name already exists.`, which renders in the page-level error banner rather than on the
field that caused it. The client already holds every camp for the current mill/year in
`data.camps`, so the same rule is checkable before the request is sent.

## Goals

1. Camp-panel field errors surface as the licensee leaves a field, not only at Save.
2. A duplicate camp name is reported inline under Camp Name, on blur and at Save, before any
   request is issued.
3. `validateCamp` remains the single source of the rules. No rule is expressed twice.

## Non-goals

- The expense sub-pages (`schedule5SubPage`) are untouched. Their per-input change/Save timing
  encodes the legacy S21/S22 distinction deliberately.
- The server-side 409 path is untouched (see Decisions).
- No form library is introduced.

## Deviations from legacy

Both are departures from legacy JSF and must be recorded as such rather than presented as
legacy-faithful:

- **Blur-time validation.** Legacy validated the camp panel only at submit. Field errors now
  appear on blur.
- **Client-side BR-02 pre-check.** Legacy left camp-name uniqueness entirely to the server.
  The client now pre-checks it against the served camp list. The server check remains and remains
  authoritative.

## Design

### 1. `validation.ts`

Add to `CAMP_MESSAGES`, verbatim from `backend/src/main/resources/messages.properties:222`:

```ts
campNameDuplicate: 'Camp name already exists.',
```

`validateCamp` gains a second parameter:

```ts
validateCamp(values: CampFormValues, otherCampNames: readonly string[] = []): CampErrors
```

The duplicate check compares the trimmed name case-insensitively against `otherCampNames`, and runs
**only when the name is otherwise valid** — stacking "required" or "30 characters or fewer" with
"already exists" tells the licensee two things about one field when only the first is actionable.

The module takes a plain `readonly string[]`, not the document or `Camp[]`: it stays free of
transport types, and the exclusion decision (below) lives with the caller that knows the panel's
identity.

The existing comment at `validation.ts:305-306` ("There is no duplicate-name check: BR-02 is the
server's") is replaced with the reason there now is one, and with why the server's 409 is still
required: the served list is a snapshot, so a camp added by another user after load is invisible
here and only the server can catch it.

### 2. `index.tsx` state

`otherCampNames` — memoized over `[data, panelCampId]`:

```
data.camps, dropping the camp whose campId === panelCampId, dropping null names, mapped to campName
```

**Excluding by id, never by name.** This is what makes the three panel modes come out right with no
mode-specific branching:

| Mode | `panelCampId` | Effect |
|---|---|---|
| new | `null` | every stored name collides — correct |
| edit | the camp's id | its own name is excluded, so saving without renaming does not collide with itself |
| copy | `null` | the source camp's name collides — which is exactly what WRN-001 asks the licensee to fix, now enforced |

After `applySaved` re-seats the panel in edit mode, `panelCampId` is set, so a second Save of the
same camp does not collide with the row the first Save created.

New state `blurred: Set<string>`, keyed identically to `CampErrors` (so `cateringAndFood.volume`,
not a separate scheme). It replaces the `errors` state and is cleared at every site that currently
calls `setErrors({})`: `openNew`, `openEditOrView`, `openCopy`, `closePanel`, `resetTransient`, and
`applySaved`.

### 3. Display gate

Errors are **derived at render**, following `schedule3/index.tsx:352`:

```
const allErrors = editable ? validateCamp(form, otherCampNames) : {}
const shown = keys of allErrors that are present in blurred
```

The three transitions:

- **blur a field** → add its key to `blurred`; its error, if any, appears.
- **change a field** → remove its key from `blurred`; its error disappears while the licensee
  corrects it, and returns on the next blur if still wrong.
- **rejected Save** → add every currently-erroring key to `blurred`, revealing all of them.

Adding keys on a rejected Save rather than setting a separate "show everything" flag keeps one
mechanism: clear-on-type continues to work per-field after a failed Save, which a global flag would
have suppressed.

`errors` state is replaced by `blurred`; the render sites keep reading a `CampErrors`-shaped map, so
`DescriptorFields` and `CategoryGrid` signatures do not change.

### 4. Field wiring

`onBlur` is added to: Camp Name, Road Distance to Operating Area, Size of Camp, Associated Camp
Volume, Comments, and both the volume and cost input of every category row.

`isolatedCamp` is a `Select`, where a change **is** the commit — it validates on change, not on
blur. There is no partial state to protect from flicker.

Read-only and view mode validate nothing (`editable` gate above), so a stored value that today's
rules would reject is never flagged at a licensee who cannot edit it.

**BR-03 propagation un-blurs the eleven category volumes it overwrites.** `handleCampVolumeChange`
assigns the Associated Camp Volume into all eleven volume-bearing categories; those values were not
typed into those fields, and an out-of-range camp volume already reports itself at its own input
rather than as eleven duplicates of the same message. This is the reasoning
`schedule5SubPage/validation.ts:53-55` already uses for not flagging untouched rows.

### 5. Save and CFM-004 gates

`handleSave` and `confirmSubPageSave` both call `validateCamp(form, otherCampNames)` and both gate
on it, so a duplicate name blocks the request in either path. Gating rather than merely displaying
follows the file's stated policy of avoiding a doomed round-trip.

### 6. Server error path — unchanged

The 409 continues to render verbatim in the page-level error banner via `runMutation`. It is not
routed to the Camp Name field. AD-6/AD-8 stand: every server message reaches the user verbatim
through one uniform path, and no client rule rewrites one.

## Testing

**`validation.test.ts`**

- duplicate detected case-insensitively and after trimming
- empty `otherCampNames`, and the default-argument call, detect nothing
- a blank or over-length name reports its own message, not the duplicate message
- omitting the parameter leaves every existing assertion in the file passing

**`Schedule5.test.tsx`**

- blur of an invalid field shows its error; untouched fields stay silent
- typing in an erroring field clears it; blurring again restores it
- a rejected Save reveals every error at once, and issues no request
- copy mode flags the source camp's name as a duplicate
- edit mode saving an unchanged name does **not** flag a duplicate, and does issue the request
- a duplicate name blocks the CFM-004 save-and-navigate path
- changing the Associated Camp Volume leaves the eleven propagated volumes unflagged
- view mode renders no field errors
- the server 409 still lands in the error banner, verbatim

## Files

- `frontend/src/components/schedule5/validation.ts`
- `frontend/src/components/schedule5/index.tsx`
- `frontend/src/components/schedule5/__tests__/validation.test.ts`
- `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`
