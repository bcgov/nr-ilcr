package ca.bc.gov.nrs.ilcr.schedule10;

import java.util.Set;

/**
 * The legacy cost-item ordinals Schedule 10 routes, and the column scales its dimensions are stored
 * at.
 *
 * <p>Held here because BOTH halves of the schedule need them: the read path routes stored cost rows
 * into the document's substructures by ordinal, and the write path writes the same twelve rows
 * back. They lived on {@code Schedule10Service} until the document assembly was extracted (code
 * review follow-up 2026-08-19) — at which point keeping them there would have meant either a
 * back-reference from the assembler to the service, or the same twelve magic numbers declared
 * twice.
 */
final class Schedule10CostItems {

  // Legacy cost-item ordinals (Constant.REPORT_COST_ITEMS :371-376), all verified against the
  // delivery ILCR_REPORT_COST_ITEM rows. The six "Less" lines span THREE subcategories, so routing
  // must be by item id — scanning a single subcategory would silently under-count the deductions.
  static final int SUB_GRADE_TRANSFER = 3;        // cat 10 / sub 1
  static final int LESS_OTHER_ENGINEERING = 4;    // cat 10 / sub 3
  static final int OTHER_TT_TRANSFER = 5;         // cat 10 / sub 3
  static final int LESS_CULVERT = 6;              // cat 10 / sub 1
  static final int LESS_BRIDGE = 7;               // cat 10 / sub 1
  static final int LESS_LANDING = 8;              // cat 10 / sub 1
  static final int STABILIZING_OTHER_TRANSFER = 9; // cat 10 / sub 4
  static final int STABILIZING_TRANSFER = 10;     // cat 10 / sub 2
  static final int LESS_OVERLAND = 11;            // cat 10 / sub 1
  static final int SUB_GRADE_ACTUAL = 20;         // cat 10 / sub 1
  static final int LESS_END_HAUL = 21;            // cat 10 / sub 1
  static final int STABILIZING_ACTUAL = 22;       // cat 10 / sub 2

  /**
   * Every cost-item ordinal Schedule 10 routes. A cost row outside this set contributes to no
   * substructure and would silently vanish from the totals, so it is logged instead.
   */
  static final Set<Integer> ROUTED = Set.of(
      SUB_GRADE_TRANSFER, LESS_OTHER_ENGINEERING, OTHER_TT_TRANSFER, LESS_CULVERT, LESS_BRIDGE,
      LESS_LANDING, STABILIZING_OTHER_TRANSFER, STABILIZING_TRANSFER, LESS_OVERLAND,
      SUB_GRADE_ACTUAL, LESS_END_HAUL, STABILIZING_ACTUAL);

  // G8 — Oracle does not preserve trailing zeros, so a NUMBER(6,3) holding 3.000 comes back as 3
  // and serialises as the integer 3 while its 12.500 neighbour serialises as 12.5. Stored
  // dimensions are normalised to their column's declared scale so the served document matches the
  // pinned contract regardless of the value (code review 2026-08-17 — caught by a new assertion).
  static final int LENGTH_SCALE = 3;   // SUB_GRADE_LENGTH / STABILIZING_LENGTH  NUMBER(6,3)
  static final int MEASURE_SCALE = 1;  // widths, depth, distances               NUMBER(x,1)
  static final int VOLUME_SCALE = 0;   // END_HAUL_VOLUME / OVERLAND_VOLUME      NUMBER(7,0)

  private Schedule10CostItems() {
  }
}
