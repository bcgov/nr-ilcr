package ca.bc.gov.nrs.ilcr.schedule11.dto;

/**
 * Bean-Validation group marking constraints that apply only to a Schedule 11 location UPDATE (PUT),
 * not a create (POST). The single member is {@code revisionCount}, which is required on an edit
 * (optimistic-lock token, AC7) but meaningless on a create. The PUT handler validates with
 * {@code @Validated({Default.class, OnUpdate.class})}; the POST handler validates the default group
 * only ({@code @Valid}), so omitting {@code revisionCount} on a create is fine while omitting it on
 * an edit is a clean 400 (never a coerced-{@code -1} 409 — Story 2.1 review lesson).
 */
public interface OnUpdate {
}
