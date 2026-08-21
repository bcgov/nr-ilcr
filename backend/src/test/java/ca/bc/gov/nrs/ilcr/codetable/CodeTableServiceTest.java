package ca.bc.gov.nrs.ilcr.codetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Unit test for the code-table maintenance service (Story 24.3 / T2) — validation + upsert routing.
 */
@ExtendWith(MockitoExtension.class)
class CodeTableServiceTest {

  private static final LocalDate JAN_2020 = LocalDate.of(2020, 1, 1);
  private static final LocalDate DEC_2030 = LocalDate.of(2030, 12, 31);

  @Mock private CodeTableRepository repository;

  @InjectMocks private CodeTableService service;

  private CodeTableException saveExpectingReject(CodeTableEntry entry) {
    return assertThrows(
        CodeTableException.class, () -> service.save("UNIT_CODE", entry, "alex.admin"));
  }

  @Test
  void listTables_excludesContractual_soOnlyTheGenericTablesAreOffered() {
    var tables = service.listTables();
    assertEquals(18, tables.size()); // 19 registry entries minus Contractual Item Codes
    assertFalse(tables.stream().anyMatch(t -> "CONTRACTUAL_ITEM_CODE".equals(t.key())));
  }

  @Test
  void save_validEntry_upsertsAndReturnsTheResult() {
    CodeTableEntry entry = new CodeTableEntry("M3", "Cubic Metres", JAN_2020, DEC_2030);
    when(repository.upsert(eq(CodeTableRegistry.UNIT_CODE), eq(entry)))
        .thenReturn(UpsertResult.INSERTED);
    assertEquals(UpsertResult.INSERTED, service.save("UNIT_CODE", entry, "alex.admin"));
    verify(repository).upsert(CodeTableRegistry.UNIT_CODE, entry);
  }

  @Test
  void save_unknownTable_is404_andNeverWrites() {
    CodeTableEntry entry = new CodeTableEntry("M3", "Cubic Metres", JAN_2020, DEC_2030);
    CodeTableException ex =
        assertThrows(CodeTableException.class, () -> service.save("NOPE", entry, "alex.admin"));
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    verify(repository, never()).upsert(any(), any());
  }

  @Test
  void save_blankCode_is400_andNeverWrites() {
    CodeTableException ex = saveExpectingReject(new CodeTableEntry("  ", "d", JAN_2020, DEC_2030));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    assertEquals("codeRequiredErrorMsg", ex.getMessageKey());
    verify(repository, never()).upsert(any(), any());
  }

  @Test
  void save_blankDescription_is400() {
    assertEquals(
        "descriptionRequiredErrorMsg",
        saveExpectingReject(new CodeTableEntry("M3", "", JAN_2020, DEC_2030)).getMessageKey());
  }

  @Test
  void save_missingEffectiveDate_is400() {
    assertEquals(
        "effectiveDateRequiredErrorMsg",
        saveExpectingReject(new CodeTableEntry("M3", "d", null, DEC_2030)).getMessageKey());
  }

  @Test
  void save_nullExpiry_isAllowed_neverExpires() {
    // Expiry is optional (a null = "never expires"), so an entry without one must save, not reject.
    CodeTableEntry entry = new CodeTableEntry("M3", "Cubic Metres", JAN_2020, null);
    when(repository.upsert(eq(CodeTableRegistry.UNIT_CODE), eq(entry)))
        .thenReturn(UpsertResult.UPDATED);
    assertEquals(UpsertResult.UPDATED, service.save("UNIT_CODE", entry, "alex.admin"));
  }

  @Test
  void save_expiryBeforeEffective_is400() {
    assertEquals(
        "expiryBeforeEffectiveErrorMsg",
        saveExpectingReject(new CodeTableEntry("M3", "d", DEC_2030, JAN_2020)).getMessageKey());
  }

  @Test
  void save_codeExceedingTableCap_is400() {
    // UNIT_CODE codeMaxLength = 10.
    assertEquals(
        "codeTableCodeLengthErrorMsg",
        saveExpectingReject(new CodeTableEntry("ABCDEFGHIJK", "d", JAN_2020, DEC_2030))
            .getMessageKey());
  }

  @Test
  void save_descriptionExceedingTableCap_is400() {
    // UNIT_CODE descriptionMaxLength = 120.
    String tooLong = "x".repeat(121);
    assertEquals(
        "codeTableDescriptionLengthErrorMsg",
        saveExpectingReject(new CodeTableEntry("M3", tooLong, JAN_2020, DEC_2030)).getMessageKey());
  }
}
