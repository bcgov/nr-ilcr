import type { Dispatch, SetStateAction } from 'react'
import { useCallback, useRef, useState } from 'react'
import type { BannerEntry } from '@/utils/legacyValidationBanner'
import { setBannerEntry } from '@/utils/legacyValidationBanner'
import type { BannerScheme } from './validation'
import { bannerEntries, bannerEntry } from './validation'

type Errors = Record<string, string>

interface JudgedFormOptions<F extends Record<keyof F, string>> {
  readonly initial: () => F
  /** The editor's full validator (what Save runs); its keys may include non-form keys. */
  readonly validate: (form: F) => Errors
  readonly scheme: BannerScheme
  /** The banner this editor writes its lines into (it may share it with another editor). */
  readonly setBanner: Dispatch<SetStateAction<readonly BannerEntry[]>>
  /**
   * The keys a change of `field` can also affect (a conditional requirement, a cleared counterpart):
   * re-judged with it when they already show an error, so no line outlives what it described.
   */
  readonly dependents?: (field: keyof F) => readonly string[]
}

/** Recompute `fields` in an error map from a fresh validation, leaving every other field as it was. */
const mergeErrors = (prev: Errors, fresh: Errors, fields: readonly string[]): Errors => {
  const next = { ...prev }
  for (const field of fields) {
    const message = fresh[field]
    if (message === undefined) {
      delete next[field]
    } else {
      next[field] = message
    }
  }
  return next
}

/**
 * One Schedule 8 editor's form, judged the way Schedule 10's are (#359 group C change log): Save
 * judges the whole editor and REPLACES the banner with the full list; leaving a field whose value
 * changed since it took focus (JSF `onchange`) judges THAT field only, adding or removing its own
 * line — the accumulating banner the business area chose for Schedules 4, 7A, 9 and 10. A dropdown
 * is judged on selection, and again when focus leaves it after a typed change. Nothing is judged
 * while typing, and a focus-and-leave judges nothing.
 *
 * It also remembers each field's LAST VALID value — the value it was opened with, then whatever it
 * held when it was last left (or set by a selection or by the editor itself) passing Save's own check
 * for that field — because legacy's Check Status processed no field (`process="@this"`) and so
 * evaluated the model. Each legacy field ran its converter, `required` and validators in its own
 * change listener, and a value failing any of them never reached the model.
 */
export function useJudgedForm<F extends Record<keyof F, string>>(options: JudgedFormOptions<F>) {
  const { initial, validate, scheme, setBanner, dependents } = options
  const [form, setForm] = useState<F>(initial)
  const [errors, setErrors] = useState<Errors>({})
  // The form as last WRITTEN, read synchronously by a judgement: a combo box can commit its selection
  // and lose focus in the same event, and the judgement on leave must see the value just written.
  const formRef = useRef<F>(form)
  const lastValidRef = useRef<F>(form)
  // Each text field's value when it took focus, compared when it is left.
  const focusedRef = useRef(new Map<keyof F, string>())

  const validateRef = useRef(validate)
  validateRef.current = validate

  /** Make every field of `fields` that passes Save's check for it its last valid value. */
  const recordValid = useCallback((current: F, fields: Iterable<keyof F>) => {
    const fresh = validateRef.current(current)
    let next = lastValidRef.current
    for (const field of fields) {
      if (fresh[field as string] === undefined && next[field] !== current[field]) {
        next = { ...next, [field]: current[field] }
      }
    }
    lastValidRef.current = next
  }, [])

  /**
   * Write the form. A field the editor changed WITHOUT it having focus — a selection, or a value this
   * editor rewrites itself (the TSA-or-TFL switch clearing its counterpart) — is a completed change,
   * so a valid new value becomes its last valid one at once; a field being typed in waits for leave.
   */
  const write = useCallback(
    (next: F) => {
      const prev = formRef.current
      formRef.current = next
      setForm(next)
      const changed = (Object.keys(next) as (keyof F)[]).filter(
        (field) => next[field] !== prev[field] && !focusedRef.current.has(field),
      )
      if (changed.length > 0) {
        recordValid(next, changed)
      }
    },
    [recordValid],
  )

  /** Load a record into the editor (open, new, copy, save echo): its values are the baseline. */
  const seed = useCallback(
    (next: F) => {
      write(next)
      lastValidRef.current = next
      focusedRef.current.clear()
      setErrors({})
    },
    [write],
  )

  /** Drop the red fields (the caller clears the banner it owns). */
  const clearErrors = useCallback(() => {
    focusedRef.current.clear()
    setErrors({})
  }, [])

  /**
   * Judge `fields` against the current values: each failing one turns red and puts its line in the
   * banner, each passing one loses both. Every other field is left as it was.
   */
  const judge = (fields: readonly string[]) => {
    const fresh = validate(formRef.current)
    setErrors((prev) => mergeErrors(prev, fresh, fields))
    setBanner((prev) =>
      fields.reduce(
        (entries, field) =>
          setBannerEntry(
            entries,
            `${scheme.scope}:${field}`,
            bannerEntry(scheme, field, fresh[field]),
          ),
        prev,
      ),
    )
  }

  const judgeWithDependents = (field: keyof F) => {
    const extra = (dependents?.(field) ?? []).filter((key) => errors[key] !== undefined)
    judge([field as string, ...extra])
  }

  /** A text field took focus: remember its value, to compare when it is left. */
  const enter = (field: keyof F) => {
    focusedRef.current.set(field, formRef.current[field])
  }

  /**
   * A field was left. Its value differs from the one it had on focus → judge THAT field (with its
   * dependents); `changed` is a combo box's own report of the same. Leaving it valid also makes its
   * value the last valid one.
   */
  const leave = (field: keyof F, changed = false) => {
    const before = focusedRef.current.get(field)
    focusedRef.current.delete(field)
    const current = formRef.current
    recordValid(current, [field])
    if (changed || (before !== undefined && before !== current[field])) {
      judgeWithDependents(field)
    }
  }

  /** A dropdown or select picked a value: write it, then judge it at once (the selection IS the change). */
  const select = (field: keyof F, next: F) => {
    write(next)
    judgeWithDependents(field)
  }

  /**
   * Save: judge the whole editor, REPLACE the banner with the full list (legacy order) and return
   * the errors. `ownLinesOnly` replaces just this editor's lines, for a banner shared with another
   * editor whose red fields stay as they are.
   */
  const validateAll = (ownLinesOnly = false): Errors => {
    const fresh = validate(formRef.current)
    const entries = bannerEntries(scheme, fresh)
    setErrors(fresh)
    setBanner((prev) =>
      ownLinesOnly
        ? [...prev.filter((entry) => !entry.key.startsWith(`${scheme.scope}:`)), ...entries].sort(
            (a, b) => a.rank - b.rank,
          )
        : entries,
    )
    return fresh
  }

  /**
   * The form as legacy's model would hold it: every field that fails Save's check for it (format,
   * range, `required`, a conditional requirement, NA) carries its last valid value instead of what
   * is on screen. A cross-field rule (the skidding Total) is not a field and replaces nothing.
   */
  const modelSnapshot = (): F => {
    const current = formRef.current
    const fresh = validate(current)
    const snapshot = { ...current }
    for (const field of Object.keys(current) as (keyof F)[]) {
      if (fresh[field as string] !== undefined) {
        snapshot[field] = lastValidRef.current[field]
      }
    }
    return snapshot
  }

  return {
    form,
    formRef,
    errors,
    setErrors,
    write,
    seed,
    clearErrors,
    enter,
    leave,
    select,
    validateAll,
    modelSnapshot,
  }
}
