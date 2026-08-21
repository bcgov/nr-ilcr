package ca.bc.gov.nrs.ilcr.codetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSaveResponse;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;

/**
 * Unit test for the Table Maintenance controller (Story 24.3 / T3) — delegation + response shape.
 */
@ExtendWith(MockitoExtension.class)
class CodeTableControllerTest {

  @Mock private CodeTableService service;

  @Mock private MessageSource messageSource;

  @Mock private Authentication authentication;

  @InjectMocks private CodeTableController controller;

  @Test
  void listTables_returnsTheServiceList() {
    when(service.listTables())
        .thenReturn(List.of(new CodeTableSummary("UNIT_CODE", "Unit Codes", 10, 120)));
    var body = controller.listTables(authentication).getBody();
    assertEquals(1, body.size());
    assertEquals("UNIT_CODE", body.get(0).key());
  }

  @Test
  void getEntries_returnsTheTablesEntries() {
    CodeTableEntry entry = new CodeTableEntry("M3", "Cubic Metres", LocalDate.of(2000, 1, 1), null);
    when(service.entries("UNIT_CODE")).thenReturn(List.of(entry));
    assertEquals(List.of(entry), controller.getEntries("UNIT_CODE", authentication).getBody());
  }

  @Test
  void saveEntry_persistsAndReturnsOutcomeVerbatimMessageAndReloadedGrid() {
    CodeTableEntry entry = new CodeTableEntry("M3", "Cubic Metres", LocalDate.of(2020, 1, 1), null);
    when(authentication.getName()).thenReturn("alex.admin");
    when(service.save("UNIT_CODE", entry, "alex.admin")).thenReturn(UpsertResult.INSERTED);
    when(messageSource.getMessage(
            eq("dataSavedSuccesfullyInfoMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Data saved successfully");
    when(service.entries("UNIT_CODE")).thenReturn(List.of(entry));

    CodeTableSaveResponse body = controller.saveEntry("UNIT_CODE", entry, authentication).getBody();
    assertEquals("INSERTED", body.outcome());
    assertEquals("dataSavedSuccesfullyInfoMsg", body.messageKey());
    assertEquals("Data saved successfully", body.message());
    assertEquals(1, body.entries().size());
  }
}
