package ca.bc.gov.nrs.ilcr.codetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Unit test for the whitelisted code-table registry (Story 24.3 / UC-CODE-001, BR-01/BR-06/BR-08). */
class CodeTableRegistryTest {

  @Test
  void holds_the_nineteen_maintainable_tables() {
    assertEquals(19, CodeTableRegistry.values().length);
  }

  @Test
  void every_non_contractual_entry_has_a_the_table_and_matching_code_column() {
    for (CodeTableRegistry t : CodeTableRegistry.values()) {
      if (t.contractual()) {
        continue;
      }
      assertTrue(t.table() != null && !t.table().isBlank(), t + " must name a table");
      // ILCR convention: the code column is named the same as the table.
      assertEquals(t.table(), t.codeColumn(), t + " code column should equal its table name");
      assertTrue(t.codeMaxLength() > 0 && t.descriptionMaxLength() > 0);
    }
  }

  @Test
  void contractual_item_codes_is_the_sole_special_case_with_no_backing_code_table() {
    long contractualCount = Arrays.stream(CodeTableRegistry.values())
        .filter(CodeTableRegistry::contractual)
        .count();
    assertEquals(1, contractualCount);
    CodeTableRegistry contractual = CodeTableRegistry.CONTRACTUAL_ITEM_CODE;
    assertTrue(contractual.contractual());
    assertNull(contractual.table()); // backed by the Schedule 9 cost-item list, not a *_CODE table
    assertEquals(500, contractual.descriptionMaxLength());
  }

  @Test
  void pins_the_two_irregular_delivery_table_names() {
    // British spelling in the DB, and no ILCR_ prefix on the ASM table — both easy to get wrong.
    assertEquals("ILCR_SUPPORT_CENTRE_CODE", CodeTableRegistry.SUPPORT_CENTER_CODE.table());
    assertEquals("RELATIVE_SOIL_MOISTUR_RGM_CODE",
        CodeTableRegistry.RELATIVE_SOIL_MOISTUR_RGM_CODE.table());
  }

  @Test
  void byKey_resolves_a_known_table_and_rejects_anything_else() {
    assertEquals(CodeTableRegistry.SKID_TYPE_CODE, CodeTableRegistry.byKey("SKID_TYPE_CODE").get());
    assertFalse(CodeTableRegistry.byKey("NOT_A_TABLE").isPresent());
    assertFalse(CodeTableRegistry.byKey(null).isPresent());
    // A raw delivery table name is NOT a valid key (keys are the enum constant names).
    assertFalse(CodeTableRegistry.byKey("ILCR_SKID_TYPE_CODE").isPresent());
  }
}
