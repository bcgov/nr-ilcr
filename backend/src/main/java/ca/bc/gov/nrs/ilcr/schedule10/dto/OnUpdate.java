package ca.bc.gov.nrs.ilcr.schedule10.dto;

/**
 * Bean-Validation group marking constraints that apply only to a Schedule 10 UPDATE (PUT), not a
 * create (POST). Its members are the {@code revisionCount} tokens on {@link
 * ConstructionPageRequest} and {@link RoadDetailRequest} — required on an edit (optimistic lock)
 * but meaningless on a create.
 *
 * <p>PUT handlers validate with {@code @Validated({Default.class, OnUpdate.class})}; POST handlers
 * validate the default group only ({@code @Valid}). Omitting {@code revisionCount} on a create is
 * therefore fine, while omitting it on an edit is a clean 400 — never a coerced-{@code -1} 409,
 * which would report a phantom concurrent edit for what is simply a malformed request.
 *
 * <p>Deliberately schedule-local rather than imported from another schedule's dto package: the
 * cross-schedule extraction of these markers is tracked as separate consolidation work, and
 * reaching across domain packages for it would couple two schedules that are otherwise independent.
 */
public interface OnUpdate {
}
