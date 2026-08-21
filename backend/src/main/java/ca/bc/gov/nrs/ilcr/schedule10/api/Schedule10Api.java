package ca.bc.gov.nrs.ilcr.schedule10.api;

import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPageRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.OnUpdate;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetailRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
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
 * Schedule 10 (Report New Road Construction Costs) API contract. The interface owns the request
 * mapping and parameter contract; {@code Schedule10Controller} implements it and adds
 * authorization.
 *
 * <p>This story is the read only. Stories 11.2 (writes + check-status) and 11.3 (frontend) build on
 * the document shape pinned here and must not re-pin it.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings, not typed required params, because
 * the contract pins the verbatim legacy ERR-001 message for missing, blank AND non-numeric values —
 * which a typed required {@code @RequestParam} cannot produce (it yields the generic
 * missing-parameter and type-mismatch 400s instead). Parsing and the guard chain live in {@code
 * MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule10")
public interface Schedule10Api {

  /**
   * Get the Schedule 10 road-construction document for a mill and reporting year.
   *
   * <p>Each page carries a system-derived read-only Road Group (BR-04); an unmapped
   * TSA/supply-block or TFL combination serves a blank Road Group without error (S12). Each page
   * also carries the road-detail count backing the legacy {@code Enter Road Data ({count})} link
   * text (CNT-001).
   *
   * <p>Guards: missing/blank/non-numeric params → 400 ERR-001; mill not active for the year → 409
   * ERR-002; no {@code ILCR_MILL_REPORT_STATUS} row → 404 ERR-003; no {@code VIEW_SCHEDULE} → 403.
   * A valid, active mill/year with zero construction pages is a 200 with an empty page list, not a
   * 404.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent or malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent or malformed)
   * @param authentication the caller, which drives the read-only {@code editable} flag
   * @return 200 with the Schedule 10 document
   */
  @GetMapping
  ResponseEntity<Schedule10Response> getSchedule10(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Create a construction page.
   *
   * <p>Requires {@code EDIT_SCHEDULE} and a Draft 1–10 track. The derived Road Group, page number,
   * page label and road-detail count are absent from the request by construction and appear only in
   * the echoed document.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered page fields
   * @param authentication the caller, whose name is stamped into the audit columns
   * @return 200 with the refreshed document and a saved message
   */
  @PostMapping("/pages")
  ResponseEntity<Schedule10Response> addPage(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody ConstructionPageRequest request,
      Authentication authentication);

  /**
   * Edit a construction page under its optimistic lock.
   *
   * <p>{@code revisionCount} is required here and validated through the {@link OnUpdate} group, so
   * omitting it is a clean 400 rather than a coerced conflict. Changing the location re-derives the
   * Road Group; nothing about it is stored.
   *
   * @param pageId the page to edit
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields, carrying the last-read revision
   * @param authentication the caller
   * @return 200 with the refreshed document and a saved message
   */
  @PutMapping("/pages/{pageId}")
  ResponseEntity<Schedule10Response> updatePage(
      @PathVariable int pageId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody ConstructionPageRequest request,
      Authentication authentication);

  /**
   * Copy a construction page and save the copy immediately.
   *
   * <p>The copy carries the page header only — no road details — reproducing legacy, whose copy
   * constructor nulls both detail collections before saving. There is no request body.
   *
   * @param pageId the page to copy
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller
   * @return 200 with the refreshed document and a saved message
   */
  @PostMapping("/pages/{pageId}/copy")
  ResponseEntity<Schedule10Response> copyPage(
      @PathVariable int pageId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Delete a construction page and every road detail and cost line beneath it.
   *
   * <p>Carries no optimistic-lock token, consistent with every other schedule's delete.
   *
   * @param pageId the page to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller
   * @return 200 with the refreshed document and a deleted message
   */
  @DeleteMapping("/pages/{pageId}")
  ResponseEntity<Schedule10Response> deletePage(
      @PathVariable int pageId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Create a road detail under a page.
   *
   * <p>The two moisture codes the business removed from the screen are derived server-side from the
   * BEC classification and RSMR class; they are absent from the request and from the response.
   *
   * @param pageId the owning page
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered road-detail fields
   * @param authentication the caller
   * @return 200 with the refreshed document and a saved message
   */
  @PostMapping("/pages/{pageId}/road-details")
  ResponseEntity<Schedule10Response> addRoadDetail(
      @PathVariable int pageId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Valid @RequestBody RoadDetailRequest request,
      Authentication authentication);

  /**
   * Edit a road detail under its OWN optimistic lock — the detail's revision, not its page's.
   *
   * <p>The parent page id is part of the identity check, so a road detail belonging to another page
   * cannot be edited through this path.
   *
   * @param pageId the owning page
   * @param roadDetailId the road detail to edit
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param request the entered fields, carrying the last-read revision
   * @param authentication the caller
   * @return 200 with the refreshed document and a saved message
   */
  @PutMapping("/pages/{pageId}/road-details/{roadDetailId}")
  ResponseEntity<Schedule10Response> updateRoadDetail(
      @PathVariable int pageId,
      @PathVariable int roadDetailId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      @Validated({Default.class, OnUpdate.class}) @RequestBody RoadDetailRequest request,
      Authentication authentication);

  /**
   * Delete one road detail and its cost lines, leaving the page and its other details untouched.
   *
   * @param pageId the owning page
   * @param roadDetailId the road detail to delete
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller
   * @return 200 with the refreshed document and a deleted message
   */
  @DeleteMapping("/pages/{pageId}/road-details/{roadDetailId}")
  ResponseEntity<Schedule10Response> deleteRoadDetail(
      @PathVariable int pageId,
      @PathVariable int roadDetailId,
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Run the Schedule 10 readiness rules.
   *
   * <p>Requires only {@code VIEW_SCHEDULE} and is deliberately NOT Draft-gated, so a submitted or
   * verified schedule can still be checked. Mutates nothing. There is no request body and no scope
   * parameter: the check always covers the whole schedule for the mill and year, as legacy does.
   *
   * @param millId the raw mill id param
   * @param year the raw reporting year param
   * @param authentication the caller
   * @return 200 with the outcome — a single banner when everything passes, otherwise the
   *     outstanding requirements per page and road detail
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule10CheckStatusResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
