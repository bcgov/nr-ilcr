package ca.bc.gov.nrs.ilcr.schedule4.dto;

import java.util.List;

/**
 * One Schedule 4 dump location (AD-12) — a family of legacy {@code TRANSPORTATION_REPORT} rows that
 * share a {@code LOCATION_DESCRIPTION}. The pinned wire shape, frozen across the later Schedule 4
 * stories.
 *
 * <p>{@code name} ({@code LOCATION_DESCRIPTION}) is the location's natural key within a mill/year
 * (used to target edit/delete). {@code categories} holds this location's in-scope category amounts —
 * the 9 fixed categories (from the primary report, no distance) plus the 3 distance-based categories
 * (47/48/52), each of which lives on its own {@code TRANSPORTATION_REPORT} row and carries its OWN
 * distance (see {@link CategoryAmount#distance()}). Delivery-DB confirmed: distance is per-category,
 * NOT a single per-location value. A name-only location has an empty list.
 */
public record Location(
    String name,
    List<CategoryAmount> categories) {
}
