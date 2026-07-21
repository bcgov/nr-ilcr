// Dev/UAT default mill + reporting-year context. These live here — not hardcoded in the page
// component — because the scaffold has no runtime/env-config layer yet (same status as the mock-auth
// scaffold). A real runtime-config injection (and the Home mill/year selector) is deferred.
// Set to mill 514 (ANGEL MILLING) / 2021 against the delivery Oracle DB (fortmp1.nrs.bcgov).
export const DEFAULT_MILL_ID = 514
export const DEFAULT_YEAR = 2021
