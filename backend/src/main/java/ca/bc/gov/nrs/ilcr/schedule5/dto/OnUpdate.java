package ca.bc.gov.nrs.ilcr.schedule5.dto;

/**
 * Bean-Validation group marking constraints that apply only to a Schedule 5 camp UPDATE (PUT), not
 * a create (POST). The single member is {@code revisionCount}, which is required on an edit
 * (per-camp optimistic-lock token, AR11 keying delta — deviation (b)) but meaningless on a create.
 * The PUT handler validates with {@code @Validated({Default.class, OnUpdate.class})}; the POST
 * handler validates the default group only ({@code @Valid}), so omitting {@code revisionCount} on a
 * create is fine while omitting it on an edit is a clean 400 — never a coerced 409 (the Story 2.1
 * review lesson: a 409 tells the user to reload when the real fix is to send the token).
 *
 * <p>Deliberately schedule5-local rather than imported from {@code schedule6.dto} or {@code
 * schedule11.dto}, which declare their own identical markers. Extracting one shared group is queued
 * for the client-blessed cross-schedule consistency PR ({@code deferred-work.md:14, 185}) —
 * importing a sibling schedule's marker here would couple two domains for the sake of an empty
 * interface.
 */
public interface OnUpdate {
}
