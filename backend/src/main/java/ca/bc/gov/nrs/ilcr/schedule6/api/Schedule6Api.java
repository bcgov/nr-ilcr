package ca.bc.gov.nrs.ilcr.schedule6.api;

import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6SaveRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 6 (Road Management Costs) API contract (controller + api-interface split, the
 * established idiom). The interface owns the request mapping and parameter contract; {@code
 * Schedule6Controller} implements it and adds authorization.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like Schedule 11, unlike Schedule
 * 1's typed params — because AC4 pins the verbatim legacy ERR-001 message for missing, blank, AND
 * non-numeric values, which a typed required {@code @RequestParam} cannot produce (it yields the
 * generic missing-parameter / type-mismatch 400s). Parsing + the guard chain live in {@code
 * MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule6")
public interface Schedule6Api {

  /**
   * Get the Schedule 6 road-management-costs document for a mill and reporting year. Guards:
   * missing/malformed params → 400 ERR-001; mill not active → 409 ERR-002; no {@code
   * ILCR_MILL_REPORT_STATUS} row → 404 ERR-003 (zero road records is a valid 200); no {@code
   * VIEW_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the road-records document
   */
  @GetMapping
  ResponseEntity<Schedule6Response> getSchedule6(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Save the whole Schedule 6 document — every road record plus the general comment — in one
   * transaction, as legacy's single Save did ({@code Schedule6DAO.saveSchedule} :236-346). Retires
   * deviation (C). {@code records} must carry EVERY served row (a placeholder never counts as
   * served); an omitted row → 400. Per-row field validation → 400 (verbatim FLD texts); a stale
   * {@code revisionCount} on any row → 409 and nothing is written (rows are written before the
   * comment, in the same transaction); an unknown/foreign/placeholder id → 404; non-Draft → 409;
   * missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param request every served record plus the general comment
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping
  ResponseEntity<Schedule6Response> saveSchedule6Document(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody Schedule6SaveRequest request,
      Authentication authentication);

  /**
   * Add one Schedule 6 road record (S01/S03). The record persists immediately and the recomputed
   * document (RMG/$/m&sup3; derived, totals refreshed) is echoed with a success {@code message}.
   * The BR-02 counterpart is cleared server-side; an invalid/missing TFL number on a TFL record →
   * 400 FLD-002. Field validation → 400 (verbatim FLD texts); non-Draft 1–10 track → 409; missing
   * {@code EDIT_SCHEDULE} → 403; bad mill/year context → 400/404/409 (ERR-001/003/002).
   *
   * @param millId the raw mill id param (validated by millcontext; verbatim ERR-001 on absence)
   * @param year the raw reporting year param (validated by millcontext)
   * @param request the entered record fields (validated, default group)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PostMapping("/records")
  ResponseEntity<Schedule6Response> addRoadRecord(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody RoadRecordRequest request,
      Authentication authentication);

  /**
   * Check Status for Schedule 6 (S09–S11, S20, S21) — read-only readiness validation, mutates
   * nothing, NOT Draft-gated ({@code VIEW_SCHEDULE}; the 2.6 precedent). Returns the composed
   * per-record {@code Value Required} lines byte-for-byte, the per-record met banner on mixed
   * results, and the single schedule-level MET banner (with no per-record results) when everything
   * passes.
   *
   * <p>{@code request} carries the on-screen values (Task 6): legacy's {@code ajax="false"}
   * postback applied the screen to the model before evaluating ({@code Schedule6MB.checkStatus}
   * :139-140), so the verdict must describe the screen, not the database.
   *
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param request the on-screen values
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the check-status result
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule6CheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody Schedule6CheckRequest request,
      Authentication authentication);

  /**
   * Delete one Schedule 6 road record. Carries NO revision token — legacy's row Delete had none
   * ({@code Schedule6MB.remove} :208-218), matching the general-comments precedent (deviation
   * (c2)). When the deleted record was the mill/year's only road record AND carried a non-blank
   * general comment, a bare placeholder row is re-inserted to preserve it (BR-09 delete side,
   * {@code Schedule6DAO.java:297-309}). Draft-gated; an unknown, foreign, or placeholder id → 404;
   * missing {@code EDIT_SCHEDULE} → 403; bad mill/year context → 400/404/409 (ERR-001/003/002).
   *
   * @param recordId the road record id ({@code ROAD_MAINTENANCE_REPORT_ID}) to delete
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @DeleteMapping("/records/{recordId}")
  ResponseEntity<Schedule6Response> deleteRoadRecord(
      @PathVariable int recordId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
