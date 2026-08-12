package ca.bc.gov.nrs.ilcr.codetable;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.api.CodeTableApi;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSaveResponse;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSummary;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Table Maintenance endpoints (Story 24.3 / UC-CODE-001). Every method is gated on the ADMIN-only
 * {@code MAINTAIN_CODE_TABLES} action (AD-7, S13) so a non-admin is denied 403 server-side — the
 * hidden Administration menu is UX only, not the boundary. Delegates all work to
 * {@link CodeTableService}; never touches the repository directly (AD-1 layering).
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class CodeTableController implements CodeTableApi {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";

  private final CodeTableService service;
  private final MessageSource messageSource;

  public CodeTableController(CodeTableService service, MessageSource messageSource) {
    this.service = service;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'MAINTAIN_CODE_TABLES')")
  public ResponseEntity<List<CodeTableSummary>> listTables(Authentication authentication) {
    return ResponseEntity.ok(service.listTables());
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'MAINTAIN_CODE_TABLES')")
  public ResponseEntity<List<CodeTableEntry>> getEntries(
      String tableKey, Authentication authentication) {
    return ResponseEntity.ok(service.entries(tableKey));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'MAINTAIN_CODE_TABLES')")
  public ResponseEntity<CodeTableSaveResponse> saveEntry(
      String tableKey, CodeTableEntry entry, Authentication authentication) {
    UpsertResult result = service.save(tableKey, entry, authentication.getName());
    String message =
        messageSource.getMessage(MSG_SAVED, null, MSG_SAVED, LocaleContextHolder.getLocale());
    return ResponseEntity.ok(new CodeTableSaveResponse(
        result.name(), MSG_SAVED, message, service.entries(tableKey)));
  }
}
