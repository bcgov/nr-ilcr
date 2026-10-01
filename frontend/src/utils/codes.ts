// Shared code-table helpers, hoisted out of schedule10/validation.ts so Schedule 6 can use the same
// location semantics as Schedule 10 (2026-08-21 corrections): both schedules serve TSA numbers and
// supply blocks over the identical {code, description} shape, and both treat TFL as a synthetic
// sentinel rather than a served code-table row.

/** One code-table option: the stored value plus the label shown in the menu. */
export interface CodeDescription {
  readonly code: string
  readonly description: string
}

/** The sentinel a TSA-or-TFL field carries when the record/page is TFL-located, on both schedules. */
export const TFL_SENTINEL = 'TFL'

/**
 * Resolve a stored code to the description an offered list carries for it. Falls back to the bare
 * code when the list does not carry it — a code the served list never carried must still show
 * SOMETHING: a stored value that predates the code table would otherwise render blank over data that
 * is really there.
 */
export const describe = (options: readonly CodeDescription[], code: string): string =>
  options.find((option) => option.code === code)?.description ?? code

/**
 * Legacy narrowed the supply-block list to blocks whose code begins with the chosen TSA, which is
 * what makes the pair coherent — block `01A` belongs to TSA `01`. With no TSA chosen the list is
 * empty rather than the full catalogue, matching the legacy control's cleared state.
 *
 * A stored block is always kept, even when it does not belong to the stored TSA. Delivery holds such
 * pairs — a page on TSA `02` carrying block `01D` — because the TSA leg was never validated, and
 * narrowing them away would blank a field that does hold a value and silently drop it on the next
 * save. The narrowing governs what can be CHOSEN; it must not hide what is already there.
 *
 * That holds for a block absent from the CATALOGUE too, not just one off the chosen branch. Filtering
 * the served list for the stored code yields nothing when the code was never served — delivery page
 * 8904 stores TSB `16Z`, which no longer appears in the code table — and the field then renders blank
 * over a value that is really there. The stored code is synthesised as its own option instead, showing
 * the bare code because that is all the document carries about it.
 */
export const supplyBlocksFor = (
  blocks: readonly CodeDescription[],
  tsaOrTfl: string,
  selectedCode = '',
): CodeDescription[] => {
  const tsa = tsaOrTfl.trim()
  const selected = selectedCode.trim()
  const stored: CodeDescription[] =
    selected === ''
      ? []
      : [
          blocks.find((block) => block.code === selected) ?? {
            code: selected,
            description: selected,
          },
        ]

  if (tsa === '' || tsa.toUpperCase() === TFL_SENTINEL) {
    return stored
  }

  const offered = blocks.filter((block) => block.code.startsWith(tsa))
  const missing = stored.filter((block) => !offered.some((o) => o.code === block.code))
  return [...offered, ...missing]
}
