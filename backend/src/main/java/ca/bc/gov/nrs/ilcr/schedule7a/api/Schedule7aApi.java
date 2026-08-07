package ca.bc.gov.nrs.ilcr.schedule7a.api;

import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aResponse;
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
 * Schedule 7A (Bridge Costs) API contract (controller + api-interface split, the established idiom).
 * The interface owns the request mapping and parameter contract; {@code Schedule7aController}
 * implements it and adds authorization.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings so a missing/blank/non-numeric value
 * resolves to the ONE verbatim legacy ERR-001 message ({@code MillContextService.validateMillYearActive},
 * AD-4) — a typed required {@code @RequestParam} cannot produce it.
 */
@RequestMapping("/api/v1/schedule7a")
public interface Schedule7aApi {

  /**
   * Get the Schedule 7A bridge document for a mill and reporting year. Guards: missing/malformed
   * params → 400 ERR-001; no {@code ILCR_MILL_REPORT_STATUS} row → 404 ERR-003 (an empty bridge list
   * is a valid 200); mill not active → 409 ERR-002; no {@code VIEW_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the bridge document
   */
  @GetMapping
  ResponseEntity<Schedule7aResponse> getSchedule7a(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Add one bridge (S01/S02). The bridge persists immediately and the recomputed document (totals
   * refreshed) is echoed with a success {@code message}. Validation → 400; unknown code → 400;
   * malformed date → 400; non-Draft 1–10 track → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered bridge fields (validated, default group)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PostMapping("/bridges")
  ResponseEntity<Schedule7aResponse> addBridge(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody BridgeRequest request,
      Authentication authentication);

  /**
   * Correct one existing bridge (S03). Same validation/gates as add; the body must carry the row's
   * {@code revisionCount} ({@link OnUpdate} group — omit = clean 400). A stale token → 409; an
   * unknown id → 404.
   *
   * @param id the bridge id ({@code BRIDGE_REPORT_ID}) to correct
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields + required {@code revisionCount} (default + OnUpdate groups)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/bridges/{id}")
  ResponseEntity<Schedule7aResponse> updateBridge(
      @PathVariable long id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody BridgeRequest request,
      Authentication authentication);

  /**
   * Save EVERY bridge of the schedule in ONE transaction — the page-level Save (legacy
   * {@code Schedule7aMB.save()}, which persisted the whole schedule from a single button). Same
   * validation and gates as the per-row PUT, applied to each entry; any entry failing rolls the
   * whole batch back, so the reporter never has to work out which rows landed. An empty list → 400.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request every bridge to save, each with its id and {@code revisionCount}
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/bridges")
  ResponseEntity<Schedule7aResponse> saveAllBridges(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody BridgeSaveAllRequest request,
      Authentication authentication);

  /**
   * Delete one bridge and its cost children (S04/S05). Draft-gated. Unknown id → 404. The success
   * {@code message} is SUC-002 when bridges remain, or SUC-003 (empty schedule) when it was the last.
   *
   * @param id the bridge id to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller (EDIT_SCHEDULE + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @DeleteMapping("/bridges/{id}")
  ResponseEntity<Schedule7aResponse> deleteBridge(
      @PathVariable long id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Check Status for Schedule 7A (BR-08, S29) — read-only validation, mutates nothing, NOT
   * Draft-gated ({@code VIEW_SCHEDULE}). Flags each missing required value per bridge; returns the
   * per-bridge and schedule-wide all-met messages when complete.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the check-status result
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule7aCheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
