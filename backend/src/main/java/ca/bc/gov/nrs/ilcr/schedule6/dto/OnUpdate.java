package ca.bc.gov.nrs.ilcr.schedule6.dto;

/**
 * Bean-Validation group marking constraints that apply only to a Schedule 6 road-record UPDATE
 * (PUT), not a create (POST). The single member is {@code revisionCount}, which is required on an
 * edit (per-record optimistic-lock token, AR11 keying delta) but meaningless on a create. The PUT
 * handler validates with {@code @Validated({Default.class, OnUpdate.class})}; the POST handler
 * validates the default group only ({@code @Valid}), so omitting {@code revisionCount} on a create
 * is fine while omitting it on an edit is a clean 400 (never a coerced 409 — Story 2.1 review
 * lesson). Deliberately schedule6-local, not imported from {@code schedule11.dto} — cross-schedule
 * coupling is a deferred-work extraction, not a story import.
 */
public interface OnUpdate {
}
