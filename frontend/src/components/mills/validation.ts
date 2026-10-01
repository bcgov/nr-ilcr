import type { SaveMillContactsRequest } from '@/interfaces/MillMaintenance'

/**
 * The one advisory pre-request check on the Mills page (AD-6), kept in sync with {@link
 * SaveMillContactsRequest}. Advisory means exactly that: the backend re-validates everything, a
 * check here never becomes the authority, and it must never suppress a server message.
 *
 * <p>There is only one, and it exists because of a legacy defect rather than a legacy rule. Legacy
 * offered no blank option on the Head Office selector and gave it no default (mills.xhtml:57-64,
 * MillsMB.java:62), so a mill whose column was NULL rendered as "No" and the first Save silently
 * persisted `N` — changing that mill's Mill Information output without anyone choosing to. The
 * backend now requires `Y` or `N` (@NotNull @Pattern), so "leave it null" is not expressible on a
 * save either. D5 rules that the administrator must therefore make the choice explicitly.
 *
 * <p>Note what is NOT checked here: the contact selections. BR-09 is enforced server-side (22.1
 * deviation (B)) against a deliberately UNFILTERED option list (22.1 D4a), so pre-validating a
 * contact would only hide the 409 the administrator needs to see.
 */

/** The head-office indicator's only two legal values, matching the delivery CHECK constraint. */
export const HEAD_OFFICE_VALUES = ['Y', 'N'] as const

/** The editable panel as the screen holds it, before it becomes a request. */
export type ContactForm = {
  readonly headOfficeContactInd: string | null
  readonly headOfficeContactId: number | null
  readonly divisionContactId: number | null
}

/**
 * Whether the panel can be saved at all. False only while the head-office indicator has never been
 * chosen — every other shape the form can hold is a legal request.
 */
export const canSaveContacts = (form: ContactForm): boolean =>
  form.headOfficeContactInd !== null &&
  (HEAD_OFFICE_VALUES as readonly string[]).includes(form.headOfficeContactInd)

/**
 * The form as the wire wants it, or null when it is not yet saveable. Returning null rather than a
 * partially-filled body keeps the impossible request unbuildable instead of merely un-sent.
 *
 * @param form the editable panel's current state
 * @param revisionCount the revision last READ for this mill — never an incremented one
 */
export const toSaveRequest = (
  form: ContactForm,
  revisionCount: number,
): SaveMillContactsRequest | null =>
  canSaveContacts(form)
    ? {
        // Non-null by canSaveContacts, which the compiler cannot see through.
        headOfficeContactInd: form.headOfficeContactInd as string,
        // Null CLEARS the column; there is no "unchanged" to express, by design.
        headOfficeContactId: form.headOfficeContactId,
        divisionContactId: form.divisionContactId,
        revisionCount,
      }
    : null
