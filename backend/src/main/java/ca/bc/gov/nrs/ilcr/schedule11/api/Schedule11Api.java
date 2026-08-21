package ca.bc.gov.nrs.ilcr.schedule11.api;

import ca.bc.gov.nrs.ilcr.schedule11.dto.BiogeoclimaticOption;
import ca.bc.gov.nrs.ilcr.schedule11.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocationRequest;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.util.List;
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
 * Schedule 11 API contract (controller + api-interface split, the established idiom). The interface
 * owns the request mapping and parameter contract; {@code Schedule11Controller} implements it and
 * adds authorization. Future actions are POST sub-resources ({@code /schedule11/check-status},
 * Story 25.2).
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — unlike Schedule 1's typed params
 * — because AC3 pins the verbatim legacy ERR-001 message for missing, blank, AND non-numeric
 * values, which a typed required {@code @RequestParam} cannot produce (it yields the generic
 * missing-parameter/type-mismatch 400s). Parsing + the guard chain live in {@code
 * MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule11")
public interface Schedule11Api {

  /**
   * Get the Schedule 11 (Basic Silviculture) locations document for a mill and reporting year.
   * Guards: missing/malformed params → 400 ERR-001; mill not active → 409 ERR-002; no {@code
   * ILCR_MILL_REPORT_STATUS} row → 404 ERR-003 (zero locations is a valid 200); no {@code
   * VIEW_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the locations document
   */
  @GetMapping
  ResponseEntity<Schedule11Response> getSchedule11(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Add one Schedule 11 location (S01/S02/S09). The location persists immediately and the
   * recomputed document (footer totals refreshed, CNT-001) is echoed with a success {@code
   * message}. Required fields Location/Enhanced/Biogeo/NAR; costs optional. Validation → 400;
   * unresolvable biogeo → 400; duplicate biogeo/location key → 409; non-Draft silviculture track →
   * 409; missing {@code EDIT_SCHEDULE} → 403; bad mill/year context → 400/404/409
   * (ERR-001/003/002).
   *
   * @param millId the raw mill id param (validated by millcontext; verbatim ERR-001 on absence)
   * @param year the raw reporting year param (validated by millcontext)
   * @param request the entered location fields (validated, default group)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PostMapping("/locations")
  ResponseEntity<Schedule11Response> addLocation(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody SilvicultureLocationRequest request,
      Authentication authentication);

  /**
   * Edit one Schedule 11 location (S03). Same validation/gates as add; the body must carry the
   * row's {@code revisionCount} ({@link OnUpdate} group — omit = clean 400). A stale token → 409;
   * an unknown id → 404.
   *
   * @param id the location id ({@code BASIC_SILVICULTURE_REPORT_ID}) to edit
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields + required {@code revisionCount} (default + OnUpdate groups)
   * @param authentication the caller (EDIT_SCHEDULE + audit user + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/locations/{id}")
  ResponseEntity<Schedule11Response> updateLocation(
      @PathVariable long id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody SilvicultureLocationRequest request,
      Authentication authentication);

  /**
   * Delete one Schedule 11 location and its item-23/24 cost children (S07). Draft-gated; carries no
   * revision token (systemic AR11 DELETE deviation). Unknown id → 404.
   *
   * @param id the location id to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller (EDIT_SCHEDULE + echoed editability)
   * @return 200 with the recomputed document (success {@code message})
   */
  @DeleteMapping("/locations/{id}")
  ResponseEntity<Schedule11Response> deleteLocation(
      @PathVariable long id,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Check Status for Schedule 11 (BR-07, S04/S05/S06) — read-only validation, mutates nothing, NOT
   * Draft-gated ({@code VIEW_SCHEDULE}). A location passes iff both costs are non-null. Returns
   * SUC-004 always and SUC-003 when all met, else per-missing-cost FLD-004 flags.
   *
   * @param millId the raw mill id param (validated by millcontext)
   * @param year the raw reporting year param
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the check-status result
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule11CheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Type-ahead search of the global BEC catalogue for the forced-selection field (BR-09, S16). A
   * read-only lookup that mutates nothing and takes NO mill/year context (the catalogue is global,
   * not Draft-gated). {@code q} is matched case-insensitively as a prefix of the concatenated
   * zone+subzone+variant+phase label; a blank/whitespace {@code q} yields an empty list. Requires
   * {@code VIEW_SCHEDULE} → 403 otherwise.
   *
   * @param q the raw search term (may be absent/blank — blank yields an empty list)
   * @param authentication the caller (VIEW_SCHEDULE)
   * @return 200 with the label-ordered, capped list of catalogue options
   */
  @GetMapping("/biogeoclimatic-catalogue")
  ResponseEntity<List<BiogeoclimaticOption>> searchBiogeoCatalogue(
      @RequestParam(name = "q", required = false) String q, Authentication authentication);
}
