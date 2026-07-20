package ca.bc.gov.nrs.ilcr.schedule4.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One Schedule 4 dump location (AD-12) — one legacy {@code TRANSPORTATION_REPORT} row. The pinned
 * wire shape, frozen across the later Schedule 4 stories.
 *
 * <p>{@code name} is {@code LOCATION_DESCRIPTION}. {@code distance} is the single per-location
 * {@code TRANSPORTATION_REPORT.DISTANCE} — meaningful only for the distance-based categories (47/48/52,
 * which share it), null (omitted) when the location stored no distance. {@code categories} holds this
 * location's in-scope category amounts (the 9 fixed + 3 distance-based codes actually stored),
 * ordered by cost-item code; a name-only location has an empty list.
 *
 * <p>With the app-wide Jackson {@code non_null} inclusion, {@code distance} is omitted when null.
 */
public record Location(
    String name,
    BigDecimal distance,
    List<CategoryAmount> categories) {
}
