/**
 * Legacy rendered every date on the administration screens through
 * `f:convertDateTime pattern="dd/MM/yyyy"` — six sites on mills.xhtml, six on users.xhtml, none
 * with a time, all in the server timezone (WEB-INF/web.xml:92-93). The wire carries an ISO
 * `LocalDate`, so this is a re-spelling of the parts and NOT a `Date` parse: constructing a Date
 * from "2026-03-04" reads it as UTC midnight and can render the previous day west of it.
 */
export const legacyDate = (iso: string | null | undefined): string | null => {
  if (iso == null || iso === '') return null
  const [year, month, day] = iso.split('-')
  return year && month && day ? `${day}/${month}/${year}` : iso
}
