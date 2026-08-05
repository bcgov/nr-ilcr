package ca.bc.gov.nrs.ilcr.schedule6.api;

import ca.bc.gov.nrs.ilcr.schedule6.dto.GeneralCommentsRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 6 (Road Management Costs) API contract (controller + api-interface split, the
 * established idiom). The interface owns the request mapping and parameter contract;
 * {@code Schedule6Controller} implements it and adds authorization. There is deliberately NO
 * DELETE: the row-Delete control and the BR-09 delete-side re-insert are un-sliced by the UC
 * (exclusion #1) — fresh requirements are needed before one may exist.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like Schedule 11, unlike
 * Schedule 1's typed params — because AC4 pins the verbatim legacy ERR-001 message for missing,
 * blank, AND
 * non-numeric values, which a typed required {@code @RequestParam} cannot produce (it yields the
 * generic missing-parameter / type-mismatch 400s). Parsing + the guard chain live in
 * {@code MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule6")
public interface Schedule6Api {

  /**
   * Get the Schedule 6 road-management-costs document for a mill and reporting year. Guards:
   * missing/malformed params → 400 ERR-001; mill not active → 409 ERR-002; no
   * {@code ILCR_MILL_REPORT_STATUS} row → 404 ERR-003 (zero road records is a valid 200); no
   * {@code VIEW_SCHEDULE} → 403.
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
   * Edit one Schedule 6 road record (S19: switching the area type stores the new side and NULLs
   * the other — BR-02). Same validation/gates as add; the body must carry the record's
   * {@code revisionCount} ({@link OnUpdate} group — omit = clean 400). A stale token → 409; an
   * unknown/foreign/placeholder id → 404.
   *
   * @param recordId the road record id ({@code ROAD_MAINTENANCE_REPORT_ID}) to edit
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields + required {@code revisionCount} (default + OnUpdate groups)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/records/{recordId}")
  ResponseEntity<Schedule6Response> updateRoadRecord(
      @PathVariable int recordId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody RoadRecordRequest request,
      Authentication authentication);

  /**
   * Save the schedule-level General Comment independently of any road record (S04, BR-09: rows
   * exist → replicated onto every row; none → placeholder row inserted; placeholder-only + blank →
   * placeholder deleted). Carries NO revision token (recorded deviation (c2)). Draft-gated; blank
   * clears.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the comment text (null/blank = clear)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/general-comments")
  ResponseEntity<Schedule6Response> saveGeneralComments(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody GeneralCommentsRequest request,
      Authentication authentication);

  /**
   * Check Status for Schedule 6 (S09–S11, S20, S21) — read-only readiness validation, mutates
   * nothing, NOT Draft-gated ({@code VIEW_SCHEDULE}; the 2.6 precedent). Returns the composed
   * per-record {@code Value Required} lines byte-for-byte, the per-record met banner on mixed
   * results, and the single schedule-level MET banner (with no per-record results) when everything
   * passes.
   *
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the check-status result
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule6CheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
