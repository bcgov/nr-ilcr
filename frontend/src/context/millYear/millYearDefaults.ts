// A known-good mill + reporting-year context against the delivery Oracle DB (fortmp1.nrs.bcgov):
// mill 13050 (ISP Test) / 2017. Kept as a shared TEST fixture — the suites that need a valid working
// context seed it explicitly via `<MillYearProvider initial={...}>`.
//
// These are NO LONGER the app's default context. MillYearProvider used to fall back to them when
// nothing was stored, which meant a context always existed and Home could never show its
// "Select Mill" / "Select Reporting Year" placeholders. The app now starts with no context until the
// user picks one on Home (see the note on getDefaultContext in MillYearProvider.tsx).
export const DEFAULT_MILL_ID = 13050
export const DEFAULT_YEAR = 2017
