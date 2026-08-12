package ca.bc.gov.nrs.ilcr.schedule9.api;

import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule9.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 9 (Miscellaneous and Unique Logging Costs) API contract (controller + api-interface
 * split, the established idiom). The interface owns the request mapping; {@code Schedule9Controller}
 * implements it and adds authorization.
 *
 * <p>Story 9.2 adds the write half (add/edit/delete + {@code POST /check-status}); it realizes the
 * document pinned by {@link Schedule9Response} in Story 9.1 rather than re-pinning it. Each write
 * body is a {@link ContractualWorkRecordRequest} of entered fields only — no derived {@code
 * costPerUnit}.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like Schedules 5/6/11 — because the
 * guards pin the verbatim ERR message for missing, blank, AND non-numeric values, which a typed
 * required {@code @RequestParam} cannot produce. Parsing + the guard chain live in
 * {@code MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule9")
public interface Schedule9Api {

  /**
   * Get the Schedule 9 contractual-work document for a mill and reporting year.
   *
   * <p>Guards (verbatim legacy text from the message bundle): missing/blank/non-numeric params →
   * 400 (EF1); mill not active for the year → 409 (EF2); no report-status context → 404 (EF3); no
   * {@code VIEW_SCHEDULE} → 403. A mill/year with zero records is a valid 200 with
   * {@code records: []}, never a 404.
   *
   * @param millId the mill id (optional raw String)
   * @param year the reporting year (optional raw String)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the aggregate document
   */
  @GetMapping
  ResponseEntity<Schedule9Response> getSchedule9(
      @RequestParam(required = false) String millId,
      @RequestParam(required = false) String year,
      Authentication authentication);

  /**
   * Add one contractual-work record (S01). The record persists immediately and the recomputed
   * document — every {@code costPerUnit} re-derived — is echoed with a {@code
   * dataSavedSuccesfullyInfoMsg} (SUC-001) message.
   *
   * <p>Guards: required-field omissions → 400 with one FLD-001 line per field; a range failure → 400
   * with the verbatim FLD-002/003/004 text; a code outside its list → 400 FLD-005; a non-Draft 1–10
   * track → 409; no {@code EDIT_SCHEDULE} → 403; bad mill/year context → 400/409/404.
   *
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param request the entered fields (default group — {@code revisionCount} is not required on a
   *     create)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document and the success message
   */
  @PostMapping("/records")
  ResponseEntity<Schedule9Response> addRecord(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody ContractualWorkRecordRequest request,
      Authentication authentication);

  /**
   * Edit one record in place (S02/S07). Same validation and gates as add; the body must additionally
   * carry the record's own {@code revisionCount} (the {@link OnUpdate} group — omitting it is a clean
   * 400, never a coerced 409). A stale token → 409; an unknown or foreign record id → 404.
   *
   * @param id the record id ({@code CONTRACTUAL_WORK_REPORT_ID}) to edit
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields plus the required {@code revisionCount} (default + OnUpdate)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document and the success message
   */
  @PutMapping("/records/{id}")
  ResponseEntity<Schedule9Response> updateRecord(
      @PathVariable int id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody ContractualWorkRecordRequest request,
      Authentication authentication);

  /**
   * Delete one record and its cost line (S10). Echoes the recomputed document with a {@code
   * dataDeletedSuccesfullyInfoMsg} (DEL-001) message.
   *
   * <p>CFM-001 ({@code This will delete the current record. Do you want to continue?}) is the
   * CLIENT's confirmation (Story 9.3), not a server round-trip — reaching this endpoint IS the
   * confirmation. Carries NO body and NO revision token, so a delete is never rejected as stale. An
   * unknown or foreign record id → 404 (mill/year/category-scoped, unlike legacy's PK-only delete); a
   * non-Draft track → 409.
   *
   * @param id the record id to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller (EDIT_SCHEDULE + echoed editability)
   * @return 200 with the recomputed document and the delete message
   */
  @DeleteMapping("/records/{id}")
  ResponseEntity<Schedule9Response> deleteRecord(
      @PathVariable int id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Check Status for Schedule 9 (S09) — read-only readiness validation. Mutates nothing, takes NO
   * request body, and is NOT Draft-gated: {@code VIEW_SCHEDULE} only (the Schedule 5/7A precedent),
   * so a Submitted mill can still be checked.
   *
   * <p>Returns either {@code requirementsMet: true} with the single SUC-002 banner and no errors, or
   * {@code requirementsMet: false} with each record's composed {@code Value Required} / range lines
   * byte-for-byte. Zero records is vacuously met.
   *
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the check-status result
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule9CheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
