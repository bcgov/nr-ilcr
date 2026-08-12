package ca.bc.gov.nrs.ilcr.schedule7b.api;

import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bResponse;
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
 * Schedule 7B (Culvert Costs) API contract (controller + api-interface split, the established
 * idiom). The interface owns the request mapping and parameter contract; {@code
 * Schedule7bController} implements it and adds authorization.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings so a missing/blank/non-numeric
 * value resolves to the ONE verbatim legacy ERR-003 message ({@code
 * MillContextService.validateMillYearActive}, AD-4) — a typed required {@code
 * @RequestParam} cannot produce it.
 */
@RequestMapping("/api/v1/schedule7b")
public interface Schedule7bApi {

  /**
   * Get the Schedule 7B culvert document for a mill and reporting year. Guards: missing/malformed
   * params → 400 ERR-003; no {@code ILCR_MILL_REPORT_STATUS} row → 404 ERR-002 (an empty culvert
   * list is a valid 200); mill not active → 409 ERR-004; no {@code VIEW_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the culvert document
   */
  @GetMapping
  ResponseEntity<Schedule7bResponse> getSchedule7b(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Record one culvert (S01/S02). The culvert persists immediately and the recomputed document
   * (total refreshed) is echoed with a success {@code message}. Only Type and No of Pieces are
   * required — span, rise, length, both costs and comments are optional at Save (BR-07 defers those
   * to Check Status). Validation → 400; a type outside the year's effective codes → 400; non-Draft
   * 1–10 track → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered culvert fields (validated, default group)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PostMapping("/culverts")
  ResponseEntity<Schedule7bResponse> addCulvert(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody CulvertRequest request,
      Authentication authentication);

  /**
   * Correct one existing culvert (S03). Same validation/gates as add; the body must carry the row's
   * {@code revisionCount} ({@link OnUpdate} group — omit = clean 400). A stale token → 409; an
   * unknown id → 404.
   *
   * @param id the culvert id ({@code CULVERT_REPORT_ID}) to correct
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields + required {@code revisionCount} (default + OnUpdate groups)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/culverts/{id}")
  ResponseEntity<Schedule7bResponse> updateCulvert(
      @PathVariable long id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody CulvertRequest request,
      Authentication authentication);

  /**
   * Save EVERY culvert of the schedule in ONE transaction — the page-level Save (legacy {@code
   * Schedule7bMB.save()}, which persisted the whole schedule from a single button). Same validation
   * and gates as the per-row PUT, applied to each entry; any entry failing rolls the whole batch
   * back, so the reporter never has to work out which rows landed. An empty list → 400; the same
   * culvert twice → 400.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request every culvert to save, each with its id and {@code revisionCount}
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/culverts")
  ResponseEntity<Schedule7bResponse> saveAllCulverts(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody CulvertSaveAllRequest request,
      Authentication authentication);

  /**
   * Delete one culvert and its two cost children (S04). Draft-gated. Unknown id → 404. The success
   * {@code message} is always SUC-002 {@code dataDeletedSuccesfullyInfoMsg}, including when that
   * was the last culvert (legacy 7B has no empty-list message branch — see {@code
   * Schedule7bController.deleteCulvert}). The Yes/No confirmation (ALT-001) is an in-page dialog
   * with no backend contract — a cancelled delete (S05) sends no request.
   *
   * @param id the culvert id to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller (EDIT_SCHEDULE + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @DeleteMapping("/culverts/{id}")
  ResponseEntity<Schedule7bResponse> deleteCulvert(
      @PathVariable long id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Check Status for Schedule 7B (BR-07) — read-only validation, mutates nothing, NOT Draft-gated
   * ({@code VIEW_SCHEDULE}). Applies the type-conditional matrix: span required only for {@code R}
   * (Round), comments only for {@code O} (Others), rise never checked, and
   * length/pieces/material/install required for every culvert. Returns the schedule-wide all-met
   * message when every culvert passes — there is no per-culvert all-met message (unlike Schedule
   * 7A).
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the check-status result
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule7bCheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
