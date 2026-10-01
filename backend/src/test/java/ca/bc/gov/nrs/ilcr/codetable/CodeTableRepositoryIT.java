package ca.bc.gov.nrs.ilcr.codetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Repository IT for the generic code-table read/upsert (Story 24.3 / T2). Exercises the real SQL
 * against {@code THE.ILCR_UNIT_CODE} in the Testcontainer (seeded by V20260812). Write tests use
 * their own unique codes so they never mutate the seed rows and are order-independent.
 */
@DisplayName("CodeTableRepository — generic read + upsert over THE.*_CODE (Story 24.3)")
class CodeTableRepositoryIT extends AbstractOracleIT {

  private static final CodeTableRegistry UNIT = CodeTableRegistry.UNIT_CODE;

  @Autowired private CodeTableRepository repository;

  private CodeTableEntry entry(String code) {
    return repository.findEntries(UNIT).stream()
        .filter(e -> code.equals(e.code()))
        .findFirst()
        .orElse(null);
  }

  @Test
  @DisplayName(
      "findEntries returns the seeded rows with description + effective/expiry, code-ordered")
  void findEntries_returnsSeededRows() {
    List<CodeTableEntry> all = repository.findEntries(UNIT);
    // Ordered by code: 'M3' sorts before 'TON'.
    assertTrue(all.indexOf(entry("M3")) < all.indexOf(entry("TON")));

    CodeTableEntry m3 = entry("M3");
    assertEquals("Cubic Metres", m3.description());
    assertEquals(LocalDate.of(2000, 1, 1), m3.effectiveDate());
    assertNull(m3.expiryDate()); // never expires

    assertEquals(LocalDate.of(2020, 12, 31), entry("TON").expiryDate());
  }

  @Test
  @DisplayName("exists distinguishes a present code from an absent one")
  void exists_reflectsRowPresence() {
    assertTrue(repository.exists(UNIT, "M3"));
    assertFalse(repository.exists(UNIT, "NOPE"));
  }

  @Test
  @DisplayName("upsert inserts a new code, then updates it in place (no duplicate) — BR-03")
  void upsert_insertsThenUpdatesInPlace() {
    String code = "BDF"; // unique to this test — never touches the seed rows
    CodeTableEntry inserted =
        new CodeTableEntry(code, "Board Feet", LocalDate.of(2010, 1, 1), null);
    assertEquals(UpsertResult.INSERTED, repository.upsert(UNIT, inserted));
    assertEquals("Board Feet", entry(code).description());

    CodeTableEntry updated =
        new CodeTableEntry(
            code, "Board Feet (revised)", LocalDate.of(2011, 6, 1), LocalDate.of(2030, 12, 31));
    assertEquals(UpsertResult.UPDATED, repository.upsert(UNIT, updated));

    // Same code updated in place — one row, new description + dates.
    long rowsForCode =
        repository.findEntries(UNIT).stream().filter(e -> code.equals(e.code())).count();
    assertEquals(1, rowsForCode);
    assertEquals("Board Feet (revised)", entry(code).description());
    assertEquals(LocalDate.of(2011, 6, 1), entry(code).effectiveDate());
    assertEquals(LocalDate.of(2030, 12, 31), entry(code).expiryDate());
  }

  @Test
  @DisplayName("Contractual Item Codes (no backing *_CODE table) is rejected by the generic repo")
  void contractual_isRejected() {
    CodeTableRegistry contractual = CodeTableRegistry.CONTRACTUAL_ITEM_CODE;
    assertThrows(IllegalArgumentException.class, () -> repository.findEntries(contractual));
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.upsert(contractual, new CodeTableEntry("X", "x", null, null)));
  }
}
