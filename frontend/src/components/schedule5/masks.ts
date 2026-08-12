// The Schedule 5 display masks, transcribed from the JSF converters. Every value passed through
// these is server-computed and this only formats it (AD-5 — no recompute). Those converters return
// "" for a null value, so NULL RENDERS BLANK, never "0"/"0.00": a camp that never had a Recoveries
// cost must look different from one whose Recoveries cost is genuinely 0.
//
// These live in their OWN module rather than in index.tsx, and that is deliberate. Story 7.4 renders
// its expense sub-pages as an early return FROM index.tsx, so a sub-page importing the masks back
// out of index.tsx would form an import cycle — and these are `const` arrow functions, which a cycle
// resolves through the temporal dead zone rather than through hoisting, i.e. it would fail at
// runtime rather than at build time and only on whichever module the bundler happened to evaluate
// first. index.tsx re-exports them, so the public surface the story asked for is unchanged.
//
// Deliberately NOT `fmtNumber`/`fmtCurrency` from @/utils/number: those render an em dash for null
// where Schedule 5 requires blank.

export const mask = (value: number | null | undefined, minFrac: number, maxFrac: number): string =>
  value === null || value === undefined
    ? ''
    : value.toLocaleString('en-CA', {
        minimumFractionDigits: minFrac,
        maximumFractionDigits: maxFrac,
      })

/** ILCRVolumeConverter `#,###,###` — grouped, no decimals. */
export const fmtVolume = (value: number | null | undefined): string => mask(value, 0, 0)

/** ILCRCostConverter `##,###,###` — grouped, no decimals. */
export const fmtCost = (value: number | null | undefined): string => mask(value, 0, 0)

/** ILCRCostVolumeConverter `###,##0.00` — grouped, always two decimals. */
export const fmtCostPerVolume = (value: number | null | undefined): string => mask(value, 2, 2)
