# Schedule 5 — Dirty-Gated Unsaved-Data Confirms

**Date:** 2026-08-19
**Branch:** `fix/frontend-styling-sched-5`
**Scope:** Frontend only. No backend, DTO, or message-bundle change.
**Nature:** Straight change, not a new story record.

## Problem

Schedule 5's camp panel fires three near-identical "you will lose unsaved data" confirms, all of
them unconditionally:

| Confirm | Text | Fires from |
|---|---|---|
| Close | `CONFIRM_NAVIGATION` — "Any unsaved data will be lost. Are you sure you would like to continue?" | the panel's Close button (`index.tsx:1139`) |
| Switch | `CONFIRM_CAMP_SWITCH` — "Any unsaved changes to the current camp report will be lost. …" | a row's Edit (`:1030`) and Add New Camp (`:1200`), whenever a panel is open |
| CFM-002 | `CONFIRM_NAVIGATION` again | a sub-page link from an existing camp (`:1302`) |

None of them asks whether there is anything to lose. Open a camp, read it, press Close — the licensee
is warned about discarding data they never touched.

## Goals

Each of the three confirms fires only when the panel holds something not saved since the last save.
When it holds nothing, the action proceeds immediately.

## Non-goals

- `CONFIRM_DELETE` is untouched — deleting a stored camp always warrants a confirm.
- **CFM-004 stays unconditional.** See Decisions.
- No change to any confirm's wording.

## Deviation from legacy

Legacy warned on every one of these transitions regardless of state. Gating them is a deliberate
deviation, recorded here rather than presented as legacy-faithful.

## Decisions

### The baseline is derived, not stored

`panelBaseline` is computed from the served document and the panel's identity, not held in state:

- **edit / view** → `seedForm(servedCamp, true)`, where `servedCamp` is the camp in `data.camps`
  whose `campId` is `panelCampId`.
- **new / copy** → `emptyForm()`.

Nothing to keep in sync, and it yields "since the last save" for free: `applySaved` re-seeds the form
from the saved camp (`index.tsx:733`, `applySaved`), so a successful save lands the panel exactly on its own
new baseline and the next Close is silent.

A **copy** therefore comes out dirty the moment it opens — its form carries the source camp's values
against an empty baseline. That is the intended behaviour, reached by the rule rather than by a
special case: the copied camp does not exist server-side, so Close discards a whole camp the licensee
asked to create. An empty **new** panel, by contrast, matches its empty baseline and closes silently,
because there is genuinely nothing to lose.

### Dirty compares entered text, not parsed values

```ts
JSON.stringify(form) !== JSON.stringify(panelBaseline)
```

This is the house pattern, already used at `schedule8/index.tsx:489-493` for the same purpose. It is
safe here because both sides are built by the same two constructors (`seedForm` / `emptyForm`) and
every update spreads rather than rebuilds, so key order is stable.

Comparing text means retyping `120,000` as `120000` counts as dirty even though the number is
unchanged. That over-warns only in that case and **never** under-warns — and since legacy warned on
every transition, over-warning is the legacy-faithful direction. Schedule 5 has no blur-time
re-grouping (its masks apply only to read-only served values), so merely focusing and leaving a field
cannot fake a change.

### A camp missing from the served document is dirty

If the panel is in edit mode and `data.camps` no longer carries `panelCampId` — deleted in another
session — there is nothing to compare against. Treat that as dirty and confirm. A spurious confirm
costs a click; a missing one costs the licensee's work.

### CFM-004 is not a dirty warning and stays unconditional

`pendingSubPage` drives two different modals, and only one of them is about losing edits:

- **CFM-004** (`CONFIRM_SAVE_NEW_CAMP`) fires for an unsaved new-or-copied camp. It is not a warning
  — it is the only route to a sub-page for a camp that does not exist server-side yet, and legacy
  saves first for exactly that reason (`Schedule5MB.java:212-217`). It must keep firing even for a
  pristine empty new panel.
- **CFM-002** (`CONFIRM_NAVIGATION`) fires for an existing camp, where legacy saves nothing and
  genuinely discards panel edits (`Schedule5MB.java:195-203`). Only this one is dirty-gated.

So the guard in `requestSubPage` is `!panelIsUnsavedCamp && !panelDirty` → navigate directly.

## Design

### `index.tsx` — two derived values

Both are computed early, next to `otherCampNames` (`index.tsx:530`), because `requestSubPage`
(`:838`) is defined well before the later `servedCamp` const exists:

```
panelBaseline: CampFormValues | null
  panelMode === 'closed'  → null
  panelCampId === null    → emptyForm()          // new and copy
  camp found in data      → seedForm(camp, true) // edit and view
  camp NOT found          → null                 // cannot compare

panelDirty: boolean
  false when panelMode is 'closed' or 'view'
  otherwise: panelBaseline === null || stringify(form) !== stringify(panelBaseline)
```

`view` is excluded explicitly rather than relying on its form matching its baseline, so that a view
panel whose camp went missing does not start prompting.

### The four call sites

| Site | Now | Becomes |
|---|---|---|
| Close button `:1139` | `readOnlyPanel ? closePanel() : setConfirmClose(true)` | `panelDirty ? setConfirmClose(true) : closePanel()` |
| Row Edit `:1030` | `panelOpen ? setPendingSwitch({kind:'edit',camp}) : openEditOrView(camp,'edit')` | `panelOpen && panelDirty ? setPendingSwitch(…) : openEditOrView(…)` |
| Add New Camp `:1200` | `panelOpen ? setPendingSwitch({kind:'new'}) : openNew()` | `panelOpen && panelDirty ? setPendingSwitch(…) : openNew()` |
| `requestSubPage` `:838-850` | read-only navigates through, else `setPendingSubPage(kind)` | additionally: `!panelIsUnsavedCamp && !panelDirty` navigates through (`panelIsUnsavedCamp` is at `:829`) |

The Close site no longer needs its own `readOnlyPanel` test — a view panel is never dirty — but the
existing read-only short-circuit in `requestSubPage` stays, because it also covers a non-editable
document.

**The `view` exclusion only ever matters to the Close button.** A view panel can exist only on a
non-editable document (`openEditOrView(camp, 'view')` is reached solely from the `View` button in
`rowActions`' `!editable` branch), and on such a document `Add New Camp` is disabled and the rows
render `View` alone with no `panelOpen` gate. So no switch path is reachable from a view panel, and
excluding `view` from `panelDirty` is defensive rather than a behaviour fix. It is kept so that a view
panel whose camp went missing cannot start prompting.

## Testing

`Schedule5.test.tsx`:

- a clean edit panel closes immediately, with no confirm
- a dirty edit panel confirms on Close, and No leaves the panel open with the edit intact
- **after a successful save, Close is silent** — the "since the last save" case
- a freshly-opened Copy panel confirms on Close without any edit
- an empty New panel closes silently; one typed character makes it confirm
- a View panel closes with no confirm (its switch paths are unreachable — see above)
- switching camps, and Add New Camp, from a clean panel proceed with no confirm
- switching from a dirty panel still confirms, and Yes still discards the draft
- a sub-page link from a clean existing camp navigates with no CFM-002
- a sub-page link from a dirty existing camp confirms
- a sub-page link from a New or Copy panel still fires CFM-004, including when the panel is pristine
- BR-03 propagation (changing the Associated Camp Volume) makes the panel dirty
- a camp absent from the served document is treated as dirty

## Files

- `frontend/src/components/schedule5/index.tsx`
- `frontend/src/components/schedule5/__tests__/Schedule5.test.tsx`
