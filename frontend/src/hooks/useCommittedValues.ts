import type { FieldValues } from '@/hooks/useScheduleDocument'
import { useCallback, useLayoutEffect, useRef, useState } from 'react'
import { isUnusableEntry } from '@/utils/derivedMath'

type UseCommittedValuesResult = {
  /** The last COMMITTED value of every form field — what the derived-figure mirror reads. */
  committed: FieldValues
  /**
   * Commit one field (call from its `onBlur`). Pass `value` when the blur handler has already
   * transformed the field (Schedules 1 and 3 re-group the display on blur), so `committed` and `form`
   * hold the identical string. Pass `invalid` to hold the previous value instead of advancing.
   */
  commit: (key: string, options?: { value?: string; invalid?: boolean }) => void
}

/**
 * The blur-committed snapshot of a page's form values, for the display-only derived mirrors
 * (defect #291).
 *
 * Legacy recalculated dependent read-only figures when focus LEFT an entry field — each field carried
 * its own AJAX `change` handler — not on every keystroke. Mirroring per keystroke instead would make
 * the derived cells churn while a multi-digit number is typed (`1` → `12` → `123`, each briefly
 * showing a wrong total), so `form` keeps tracking every keystroke because it drives the inputs, and
 * this hook keeps a second copy that only advances when a field is committed.
 *
 * **Invalid entries do not advance the baseline** (ruled 2026-08-21 after code review). Legacy's
 * round-trip failed validation and left the totals at their last valid figures; committing an invalid
 * or unparseable value instead let it drive the whole cascade — an out-of-range volume could move nine
 * cells to a state the server can never produce, and text like `-` silently dropped its line out of
 * every total. A field whose value is unusable, or which the page reports as invalid, holds its
 * previous committed value.
 *
 * `committed` re-seeds itself whenever `data` is replaced — load, Save echo, Delete reload — which is
 * exactly when the server document, and with it the freshly seeded form, changes. Pass the same `data`
 * the page renders from; its IDENTITY is the signal, not its contents.
 */
export function useCommittedValues(form: FieldValues, data: unknown): UseCommittedValuesResult {
  const [committed, setCommitted] = useState<FieldValues>(form)

  // Latest-value ref so committing a field reads the CURRENT form without making `commit`'s identity
  // change on every keystroke, and so the re-seed effect depends only on `data`.
  const formRef = useRef(form)
  formRef.current = form

  // `useLayoutEffect`, not `useEffect`: a passive effect runs after paint, so the first frame carrying
  // a freshly loaded document would compute the mirror from the previous (or empty) entry set and show
  // wrong totals for one frame (code review 2026-08-21).
  useLayoutEffect(() => {
    // Intentional re-seed: a replaced document means the page reseeded `form` from the server, so the
    // mirror's baseline moves with it.
    setCommitted(formRef.current)
  }, [data])

  const commit = useCallback((key: string, options?: { value?: string; invalid?: boolean }) => {
    setCommitted((prev) => {
      const value = options?.value ?? formRef.current[key] ?? ''
      // Hold the last valid figure rather than recomputing from something unsavable.
      if (options?.invalid || isUnusableEntry(value)) {
        return prev
      }
      // Skip the state write when the field is unchanged, so tabbing through a form the user did not
      // edit does not re-render the page once per field.
      return prev[key] === value ? prev : { ...prev, [key]: value }
    })
  }, [])

  return { committed, commit }
}
