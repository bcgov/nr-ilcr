package ca.bc.gov.nrs.ilcr.codetable;

import java.util.Optional;

/**
 * The fixed, whitelisted set of maintainable lookup code tables (Story 24.3 / UC-CODE-001, BR-01),
 * ported from legacy {@code Constants.codeTables} + {@code Constants.CodeTableColLengths}.
 *
 * <p>Each entry names the delivery {@code THE.<table>} plus its code column, the per-table code /
 * description length caps (BR-06), and whether it is the Contractual Item Codes special case (BR-08).
 * The enum is the ONLY source of table/column identifiers the generic repository interpolates into
 * SQL — the client selects a table by {@link #name() key}, which is validated against this enum, so
 * no caller-supplied string ever reaches a query (no injection surface).
 *
 * <p>Naming is the ILCR convention: the code column is named the same as the table (confirmed for the
 * bridge tables read by {@code Schedule7aRepository}). Two irregular names are pinned verbatim:
 * {@code SUPPORT_CENTER} maps to {@code ILCR_SUPPORT_CENTRE_CODE} (British spelling in the DB), and
 * {@code ASM Codes} maps to {@code RELATIVE_SOIL_MOISTUR_RGM_CODE} with NO {@code ILCR_} prefix.
 * Per-table presence of the audit columns remains a Task-1 delivery-schema confirmation.
 */
public enum CodeTableRegistry {
  BRIDGE_ABUTMENT_TYPE_CODE("Bridge Abutment Type Codes", "ILCR_BRIDGE_ABUTMENT_TYPE_CODE", 2, 120),
  BRIDGE_CNSTRCTN_TYPE_CODE("Bridge Construction Type Codes", "ILCR_BRIDGE_CNSTRCTN_TYPE_CODE", 10,
      120),
  BRIDGE_LOAD_RATING_CODE("Bridge Load Rating Codes", "ILCR_BRIDGE_LOAD_RATING_CODE", 10, 120),
  BRIDGE_SUPERSTRUCTR_CODE("Bridge Super Structure Codes", "ILCR_BRIDGE_SUPERSTRUCTR_CODE", 1, 120),
  /** Special case (BR-08): maintained by description only, backed by the Schedule 9 cost-item list. */
  CONTRACTUAL_ITEM_CODE("Contractual Item Codes", null, 10, 500, true),
  CONTRACTUAL_SOURCE_CODE("Contractual Source Codes", "ILCR_CONTRACTUAL_SOURCE_CODE", 20, 120),
  CULVERT_TYPE_CODE("Culvert Type Codes", "ILCR_CULVERT_TYPE_CODE", 20, 120),
  DECK_CODE("Deck Codes", "ILCR_DECK_CODE", 10, 120),
  FOREST_REGION_CODE("Forest Region Codes", "ILCR_FOREST_REGION_CODE", 10, 120),
  RATE_COST_TYPE_CODE("Rate Cost Type Codes", "ILCR_RATE_COST_TYPE_CODE", 1, 120),
  ROAD_BALLAST_MATERL_CODE("Road Ballast Material Codes", "ILCR_ROAD_BALLAST_MATERL_CODE", 10, 120),
  ROAD_BALLAST_METHOD_CODE("Road Ballast Method Codes", "ILCR_ROAD_BALLAST_METHOD_CODE", 10, 120),
  ROAD_LIFETIME_CODE("Road Lifetime Codes", "ILCR_ROAD_LIFETIME_CODE", 10, 120),
  SKID_TYPE_CODE("Skid Type Codes", "ILCR_SKID_TYPE_CODE", 3, 120),
  SOIL_MOISTURE_CODE("Soil Moisture Codes", "ILCR_SOIL_MOISTURE_CODE", 10, 120),
  SUPPORT_CENTER_CODE("Support Center Codes", "ILCR_SUPPORT_CENTRE_CODE", 10, 120),
  UNIT_CODE("Unit Codes", "ILCR_UNIT_CODE", 10, 120),
  RELATIVE_SOIL_MOISTUR_RGM_CODE("ASM Codes", "RELATIVE_SOIL_MOISTUR_RGM_CODE", 1, 120),
  ILCR_RL_SOIL_MOIS_RGM_CLS_CODE("RSMRC Codes", "ILCR_RL_SOIL_MOIS_RGM_CLS_CODE", 2, 120);

  private final String label;
  private final String table;
  private final int codeMaxLength;
  private final int descriptionMaxLength;
  private final boolean contractual;

  CodeTableRegistry(String label, String table, int codeMaxLength, int descriptionMaxLength) {
    this(label, table, codeMaxLength, descriptionMaxLength, false);
  }

  CodeTableRegistry(String label, String table, int codeMaxLength, int descriptionMaxLength,
      boolean contractual) {
    this.label = label;
    this.table = table;
    this.codeMaxLength = codeMaxLength;
    this.descriptionMaxLength = descriptionMaxLength;
    this.contractual = contractual;
  }

  /** The stable API key for this table (the enum constant name). */
  public String key() {
    return name();
  }

  /** The human-readable dropdown label (legacy verbatim). */
  public String label() {
    return label;
  }

  /** The bare delivery table name under the {@code THE} schema, or {@code null} for Contractual. */
  public String table() {
    return table;
  }

  /** The code column — same as the table name (ILCR convention), or {@code null} for Contractual. */
  public String codeColumn() {
    return table;
  }

  public int codeMaxLength() {
    return codeMaxLength;
  }

  public int descriptionMaxLength() {
    return descriptionMaxLength;
  }

  /** True for Contractual Item Codes: description-only, backed by the Schedule 9 cost-item list. */
  public boolean contractual() {
    return contractual;
  }

  /** Resolve a client-supplied table key against the whitelist; empty when it is not a known table. */
  public static Optional<CodeTableRegistry> byKey(String key) {
    if (key == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(key));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }
}
