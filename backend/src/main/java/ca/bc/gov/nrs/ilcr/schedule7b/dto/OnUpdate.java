package ca.bc.gov.nrs.ilcr.schedule7b.dto;

/**
 * Bean-Validation group marking constraints that apply only to a Schedule 7B culvert UPDATE (PUT),
 * not a create (POST). The single member is {@code revisionCount}, required on an edit
 * (optimistic-lock token) but meaningless on a create. The PUT handler validates with {@code
 * @Validated({Default.class, OnUpdate.class})}; the POST handler validates the default group only
 *     ({@code @Valid}), so omitting {@code revisionCount} on a create is fine while omitting it on
 *     an edit is a clean 400 — never a coerced-{@code -1} 409, which would report a phantom
 *     concurrent edit for what is simply a malformed request (the established schedule idiom).
 */
public interface OnUpdate {
}
