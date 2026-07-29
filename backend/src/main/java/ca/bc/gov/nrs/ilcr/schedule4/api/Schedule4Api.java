package ca.bc.gov.nrs.ilcr.schedule4.api;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4LocationRequest;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4SubPageRowRequest;
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
 * Schedule 4 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule4Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4). Adds the location
 * write path (Story 4.2): PUT (save create-or-edit) and DELETE. The sub-page lists (Towing/Truck
 * Rehaul/Other) and Check Status are later stories.
 */
@RequestMapping("/api/v1/schedule4")
public interface Schedule4Api {

  /**
   * Get the Schedule 4 (Special Log Transportation Costs) read document for a mill and reporting
   * year: the list of dump locations, each with its in-scope transportation-category amounts (9 fixed
   * + 3 distance-based). Context guards (400/404/409/403) are enforced by {@code MillContextService} +
   * method security. A valid, active mill/year with no {@code TRANSPORTATION_REPORT} rows for category
   * {@code "4"} returns 200 with {@code locations: []} — never a 404.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the Schedule 4 read document
   */
  @GetMapping
  ResponseEntity<Schedule4Response> getSchedule4(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Save (create-or-edit) one Schedule 4 location for a mill/year and return the recomputed document
   * (Story 4.2, S01/S02/S07). {@code request.id()} null creates; present edits (rename-safe). A blank
   * name → 400 ({@code locationEmptyOrNull}); a case-insensitive duplicate → 409
   * ({@code locationAlreadyExists}); out-of-range amounts / BR-04 violations → 400; a non-Draft track
   * → 409; a stale {@code revisionCount} → 409; missing {@code EDIT_SCHEDULE} → 403. Nothing persists
   * on any failure.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the location name, optimistic-lock token, and entered categories (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document carrying the success {@code message} (AD-8)
   */
  @PutMapping("/locations")
  ResponseEntity<Schedule4Response> saveLocation(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody Schedule4LocationRequest request,
      Authentication authentication);

  /**
   * Delete one Schedule 4 location family (primary + distance children + cascaded details) for a
   * mill/year, targeted by the primary report {@code id} (Story 4.2, S10 / BR-08). Idempotent: an
   * unknown id returns 200 (never 404). Non-Draft track → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param id the primary report id of the location to delete (required)
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the success {@code message} (SUC-004, AD-8)
   */
  @DeleteMapping("/locations")
  ResponseEntity<MessageResponse> deleteLocation(
      @RequestParam long millId,
      @RequestParam int year,
      @RequestParam int id,
      Authentication authentication);

  /**
   * Add one sub-page list row (Towing/Truck Rehaul/Other) to a location and return the recomputed
   * document (Story 4.3, S03–S06). {@code request.type} selects the code (43/46/55); {@code cycle}
   * applies to Truck Rehaul only. Row range/description violations → 400; a non-Draft track → 409;
   * an unknown {@code locationId} → 404; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param locationId the parent location's id (its {@code Location.id}, primary report id)
   * @param request the row type, description, and amounts (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document carrying the success {@code message} (AD-8)
   */
  @PostMapping("/locations/{locationId}/rows")
  ResponseEntity<Schedule4Response> addSubPageRow(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int locationId,
      @Valid @RequestBody Schedule4SubPageRowRequest request,
      Authentication authentication);

  /**
   * Delete one sub-page list row (its whole report + cascaded detail) and return the recomputed
   * document (Story 4.3, S11 / BR-08). Idempotent: an unknown/non-sub-page {@code rowId} is a no-op
   * 200 (never deletes a location). Non-Draft track → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param locationId the parent location's id (path symmetry; the delete targets {@code rowId})
   * @param rowId the sub-page row's own report id
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the recomputed document carrying the deleted {@code message} (AD-8)
   */
  @DeleteMapping("/locations/{locationId}/rows/{rowId}")
  ResponseEntity<Schedule4Response> deleteSubPageRow(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int locationId,
      @PathVariable int rowId,
      Authentication authentication);

  /**
   * Evaluate the Schedule 4 completion requirement (BR-07, Check Status) for a mill/year — read-only
   * (AD-5), mutates nothing, no request body (Story 4.4, S28–S31). Returns 200 with a per-location
   * breakdown: {@code outcome = "MET"} only when every location's in-scope Costs are present, else
   * {@code "ISSUES"} with per-field {@code Value Required} findings. Same no-summary-required context
   * guards as the read: 400 / 404 / 409 / 403 ({@code VIEW_SCHEDULE}).
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (authorized for VIEW_SCHEDULE)
   * @return 200 with the {@link Schedule4CheckStatusResponse} (outcome + per-location results)
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule4CheckStatusResponse> checkStatus(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);
}
