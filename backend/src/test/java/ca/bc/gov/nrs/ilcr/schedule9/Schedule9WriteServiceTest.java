package ca.bc.gov.nrs.ilcr.schedule9;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecordRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for the Schedule 9 WRITE path (Story 9.2) with a mocked repository — the FLD-001 required
 * check and its screen order, the BR-04 conditional descriptions, the FLD-005 code check, the
 * conditional-null storage rules, the Draft gate, and the 404-vs-409 optimistic-lock disambiguation,
 * all without a database. The SQL and the verbatim message composition are exercised against real
 * Oracle by the {@code Schedule9Write*IT} suites.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule9Service — write validation, conditional fields, and locking")
class Schedule9WriteServiceTest {

  private static final long MILL = 700L;
  private static final int YEAR = 2017;
  private static final String USER = "tester";

  @Mock
  private Schedule9Repository repository;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private Schedule9Service service;

  /** A fully valid create body: item 108, unit M3, source A, BEC BZ1 — no conditional fields active. */
  private static ContractualWorkRecordRequest valid() {
    return new ContractualWorkRecordRequest(
        "CTR-1", 108, null, "M3", null, new BigDecimal("10.0"), "BZ1", 5000, null, "A", null,
        "ok", null);
  }

  private void draft() {
    when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
  }

  /** Stub every FLD-005 code lookup as valid, and the buildDocument reads as empty. */
  private void allCodesValidAndEmptyDocument() {
    lenient().when(repository.countUnitCode(anyString())).thenReturn(1);
    lenient().when(repository.countBecZoneCode(anyString())).thenReturn(1);
    lenient().when(repository.countSourceCode(anyString())).thenReturn(1);
    lenient().when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findRecords(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findCostLines(MILL, YEAR)).thenReturn(List.of());
  }

  @Nested
  @DisplayName("FLD-001 required fields (BR-03)")
  class Required {

    @Test
    @DisplayName("a blank Company ID reports Company ID (S17)")
    void companyIdRequired() {
      draft();
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "  ", 108, null, "M3", null, new BigDecimal("10.0"), "BZ1", 5000, null, "A", null, null,
          null);

      FieldValuesRequiredException ex = assertThrows(FieldValuesRequiredException.class,
          () -> service.addRecord(MILL, YEAR, request, true, USER));
      assertEquals(List.of("Company ID"), ex.getFieldLabels());
    }

    @Test
    @DisplayName("all five core omissions report together in screen order (S17–S21)")
    void allCoreRequiredInScreenOrder() {
      draft();
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          null, null, null, null, null, null, null, null, null, null, null, null, null);

      FieldValuesRequiredException ex = assertThrows(FieldValuesRequiredException.class,
          () -> service.addRecord(MILL, YEAR, request, true, USER));
      assertEquals(
          List.of("Company ID", "Contractual Item", "Unit Type", "Biogeoclimatic Zone", "Source"),
          ex.getFieldLabels());
    }

    @Test
    @DisplayName("the three 'Other' descriptions are NOT required at Save (legacy parity)")
    void otherDescriptionsNotRequiredAtSave() {
      draft();
      allCodesValidAndEmptyDocument();
      when(repository.nextContractualWorkReportId()).thenReturn(9210);
      when(repository.nextCostDetailId()).thenReturn(8610);
      // Every "Other" driver selected (item 114, unit O, source S) but every description blank —
      // legacy's item desc has no required attr, unit desc is required="false", and source desc's
      // require= is a typo JSF ignores. So this saves rather than throwing FLD-001.
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 114, null, "O", null, new BigDecimal("1.0"), "BZ1", 1, null, "S", null, null,
          null);

      assertDoesNotThrow(() -> service.addRecord(MILL, YEAR, request, true, USER));
      verify(repository).insertCostLine(anyInt(), anyInt(), eq(114), any(), isNull(), eq(USER));
    }
  }

  @Nested
  @DisplayName("FLD-005 force selection")
  class CodeLists {

    @Test
    @DisplayName("a Contractual Item outside 108–114 is rejected")
    void itemOutOfRange() {
      draft();
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 999, null, "M3", null, new BigDecimal("1.0"), "BZ1", 1, null, "A", null, null,
          null);

      assertThrows(InvalidContractualCodeException.class,
          () -> service.addRecord(MILL, YEAR, request, true, USER));
    }

    @Test
    @DisplayName("a Unit Type not in ILCR_UNIT_CODE is rejected")
    void unitNotInList() {
      draft();
      when(repository.countUnitCode("ZZ")).thenReturn(0);
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 108, null, "ZZ", null, new BigDecimal("1.0"), "BZ1", 1, null, "A", null, null,
          null);

      assertThrows(InvalidContractualCodeException.class,
          () -> service.addRecord(MILL, YEAR, request, true, USER));
    }
  }

  @Nested
  @DisplayName("conditional-null storage (BR-04)")
  class ConditionalNull {

    @Test
    @DisplayName("item 108 stores NULL side slope and NULL item description regardless of the body")
    void nonRoadNonOtherClearsDependents() {
      draft();
      allCodesValidAndEmptyDocument();
      when(repository.nextContractualWorkReportId()).thenReturn(9200);
      when(repository.nextCostDetailId()).thenReturn(8600);
      // A body that TRIES to set side slope and item description on a non-road, non-Other item.
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 108, "ignored", "M3", "ignored", new BigDecimal("10.0"), "BZ1", 5000, 55, "A",
          "ignored", "ok", null);

      service.addRecord(MILL, YEAR, request, true, USER);

      verify(repository).insertRecord(eq(9200), eq(MILL), eq(YEAR), eq("CTR-1"),
          isNull(), eq(new BigDecimal("10.0")), eq("M3"), isNull(), eq("A"), isNull(), eq("BZ1"),
          eq("ok"), eq(USER));
      verify(repository).insertCostLine(eq(8600), eq(9200), eq(108), eq(5000), isNull(), eq(USER));
    }

    @Test
    @DisplayName("the 'Other' descriptions are KEPT when their driver enables them")
    void otherDriversKeepDescriptions() {
      draft();
      allCodesValidAndEmptyDocument();
      when(repository.nextContractualWorkReportId()).thenReturn(9202);
      when(repository.nextCostDetailId()).thenReturn(8602);
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 114, "gate", "O", "linear metre", new BigDecimal("1.0"), "BZ1", 1, null, "S",
          "quote", null, null);

      service.addRecord(MILL, YEAR, request, true, USER);

      // unit O keeps unit desc, source S keeps source desc on the master; item 114 keeps item desc
      // on the cost line.
      verify(repository).insertRecord(anyInt(), eq(MILL), eq(YEAR), eq("CTR-1"),
          isNull(), any(), eq("O"), eq("linear metre"), eq("S"), eq("quote"), eq("BZ1"), isNull(),
          eq(USER));
      verify(repository).insertCostLine(anyInt(), anyInt(), eq(114), any(), eq("gate"), eq(USER));
    }

    @Test
    @DisplayName("item 111 (road deactivation) KEEPS the side slope")
    void roadDeactivationKeepsSideSlope() {
      draft();
      allCodesValidAndEmptyDocument();
      when(repository.nextContractualWorkReportId()).thenReturn(9201);
      when(repository.nextCostDetailId()).thenReturn(8601);
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 111, null, "M3", null, new BigDecimal("10.0"), "BZ1", 5000, 55, "A", null, null,
          null);

      service.addRecord(MILL, YEAR, request, true, USER);

      verify(repository).insertRecord(anyInt(), eq(MILL), eq(YEAR), eq("CTR-1"),
          eq(55), any(), eq("M3"), isNull(), eq("A"), isNull(), eq("BZ1"), isNull(), eq(USER));
    }
  }

  @Nested
  @DisplayName("Draft gate and optimistic lock")
  class Gate {

    @Test
    @DisplayName("a non-Draft track rejects the write with 409 before any validation")
    void nonDraftRejected() {
      when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("S"));

      assertThrows(ScheduleNotEditableException.class,
          () -> service.addRecord(MILL, YEAR, valid(), true, USER));
    }

    @Test
    @DisplayName("a zero-row update with the record present is a stale-token 409")
    void staleTokenIsConflict() {
      draft();
      allCodesValidAndEmptyDocument();
      when(repository.updateRecord(eq(42), eq(MILL), eq(YEAR), eq(7), any(), any(), any(), any(),
          any(), any(), any(), any(), any(), any())).thenReturn(0);
      when(repository.countRecord(42, MILL, YEAR)).thenReturn(1);
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 108, null, "M3", null, new BigDecimal("10.0"), "BZ1", 5000, null, "A", null, null,
          7);

      assertThrows(StaleRevisionException.class,
          () -> service.updateRecord(MILL, YEAR, 42, request, true, USER));
    }

    @Test
    @DisplayName("a zero-row update with the record absent is a 404")
    void absentRecordIsNotFound() {
      draft();
      allCodesValidAndEmptyDocument();
      when(repository.updateRecord(eq(42), eq(MILL), eq(YEAR), eq(7), any(), any(), any(), any(),
          any(), any(), any(), any(), any(), any())).thenReturn(0);
      when(repository.countRecord(42, MILL, YEAR)).thenReturn(0);
      ContractualWorkRecordRequest request = new ContractualWorkRecordRequest(
          "CTR-1", 108, null, "M3", null, new BigDecimal("10.0"), "BZ1", 5000, null, "A", null, null,
          7);

      assertThrows(ContractualWorkRecordNotFoundException.class,
          () -> service.updateRecord(MILL, YEAR, 42, request, true, USER));
    }

    @Test
    @DisplayName("an update without a revision token is a 400, never a coerced 409")
    void missingTokenIsBadRequest() {
      draft();
      assertThrows(RevisionCountRequiredException.class,
          () -> service.updateRecord(MILL, YEAR, 42, valid(), true, USER));
    }
  }
}
