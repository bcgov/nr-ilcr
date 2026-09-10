package ca.bc.gov.nrs.ilcr.schedule4.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * One Schedule 4 dump location (AD-12) — a family of legacy {@code TRANSPORTATION_REPORT} rows that
 * share a {@code LOCATION_DESCRIPTION}. The pinned wire shape, frozen across the later Schedule 4
 * stories.
 *
 * <p>{@code id} is the primary report's {@code TRANSPORTATION_REPORT_ID} (the family's
 * distance-null report, else its lowest report id) — a stable, rename-safe handle the Story 4.2
 * write targets (§Decision 2). {@code revisionCount} is that primary report's {@code
 * REVISION_COUNT} — the optimistic-lock token the edit write echoes back (without it an edit of a
 * previously-saved location could never supply the current revision and would always 409). {@code
 * name} ({@code LOCATION_DESCRIPTION}) is the location's natural key within a mill/year. {@code
 * categories} holds this location's in-scope category amounts — the 9 fixed categories (from the
 * primary report, no distance) plus the 3 distance-based categories (47/48/52), each of which lives
 * on its own {@code TRANSPORTATION_REPORT} row and carries its OWN distance (see {@link
 * CategoryAmount#distance()}). Delivery-DB confirmed: distance is per-category, NOT a single
 * per-location value. A name-only location has an empty list.
 *
 * <p>{@code subPageRows} holds this location's list-based sub-page rows (Story 4.3): Towing Total
 * (43), Truck Rehaul (46, with cycle), and Other Transportation (55) — each its own {@code
 * TRANSPORTATION_REPORT} sharing the location name (see {@link SubPageRow}). Kept separate from
 * {@code categories} because they are free-text list rows, not the fixed category grid. Empty when
 * the location has no sub-page rows.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Location(
    Integer id,
    Integer revisionCount,
    String name,
    String comments,
    List<CategoryAmount> categories,
    List<SubPageRow> subPageRows,
    Map<String, OriginalValue> originalValues) {

  /**
   * Without original values — the shape every caller that is not serving a stored, beyond-Draft
   * document uses (a print mapper, a check-status projection, a test fixture). The canonical
   * constructor is the one the read path uses (Story 16.2).
   */
  public Location(
      Integer id,
      Integer revisionCount,
      String name,
      String comments,
      List<CategoryAmount> categories,
      List<SubPageRow> subPageRows) {
    this(id, revisionCount, name, comments, categories, subPageRows, null);
  }
}
