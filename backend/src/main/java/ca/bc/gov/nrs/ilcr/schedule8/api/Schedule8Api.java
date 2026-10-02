package ca.bc.gov.nrs.ilcr.schedule8.api;

import ca.bc.gov.nrs.ilcr.dto.base.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Options;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8RateRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRequest;
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
 * Schedule 8 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule8Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4). Story 14.1 is
 * the read; Story 14.2 adds the report-page write path (PUT save, DELETE cascade). Samples, rate
 * details, and Check Status are later stories (14.3/14.4/14.6).
 */
@RequestMapping("/api/v1/schedule8")
public interface Schedule8Api {

  /**
   * Get the Schedule 8 (Tree to Truck / Special Skidding Costs) read document for a mill and
   * reporting year: the three-level hierarchy of report pages, each with its samples, each with its
   * additions/deductions and server-computed rates and counts. Context guards (400/404/409/403) are
   * enforced by {@code MillContextService} + method security. A valid, active mill/year with no
   * category-{@code '8'} pages returns 200 with {@code pages: []} — never a 404.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the Schedule 8 read document
   */
  @GetMapping
  ResponseEntity<Schedule8Response> getSchedule8(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Get the reference-data option lists for the page-editor dropdowns (Support Centre / Region /
   * BEC Zone / TSA / TFL / Supply Block) — each a code + its {@code DESCRIPTION} label. Global
   * reference data (no mill/year scope); {@code VIEW_SCHEDULE} authorizes the read. Lets the editor
   * render descriptions instead of raw codes.
   *
   * @param authentication the caller (authorized for VIEW_SCHEDULE)
   * @return 200 with the six ordered option lists
   */
  @GetMapping("/options")
  ResponseEntity<Schedule8Options> getOptions(Authentication authentication);

  /**
   * Save (create-or-edit) one Schedule 8 report page for a mill/year and return the recomputed
   * document (Story 14.2, S01/S02/S04). {@code request.id()} null creates; present edits. A missing
   * required field (License / Support Centre / Region / BEC Zone / TSA-or-TFL) → 400 ({@code Value
   * Required}); a non-Draft track → 409; a stale {@code revisionCount} → 409; missing {@code
   * EDIT_SCHEDULE} → 403. Nothing persists on any failure. Copy (S02) arrives here as an ordinary
   * create.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the page fields + optimistic-lock token (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document carrying the success {@code message} (AD-8)
   */
  @PutMapping("/pages")
  ResponseEntity<Schedule8Response> savePage(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody Schedule8PageRequest request,
      Authentication authentication);

  /**
   * Delete one Schedule 8 report page (cascading its samples + all their rate details) for a
   * mill/year, targeted by the page {@code id} (Story 14.2, S07 / BR-05). Idempotent: an unknown id
   * returns 200 (never 404). Non-Draft track → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param id the page id to delete
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the success {@code message} (SUC-002, AD-8)
   */
  @DeleteMapping("/pages/{id}")
  ResponseEntity<MessageResponse> deletePage(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int id,
      Authentication authentication);

  /**
   * Save (create-or-edit) one sample under a page and return the recomputed document (Story 14.3,
   * S01/S03/S05). {@code request.id()} null creates; present edits. Contract ID missing → 400; an
   * individual % out of 0–100 or a skidding sum > 100 → 400 (a sum &lt; 100 saves); Helicopter-/
   * Other-conditional fields missing → 400; volume/rate out of range → 400; unknown page/sample →
   * 404; non-Draft → 409; stale {@code revisionCount} → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param pageId the parent page id
   * @param request the sample fields + optimistic-lock token (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document carrying the success {@code message} (AD-8)
   */
  @PutMapping("/pages/{pageId}/samples")
  ResponseEntity<Schedule8Response> saveSample(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int pageId,
      @Valid @RequestBody Schedule8SampleRequest request,
      Authentication authentication);

  /**
   * Delete one sample (cascading its rate details) under a page and return the recomputed document
   * (Story 14.3, S08 / BR-05). Idempotent: an unknown page/sample id returns 200 (never 404).
   * Non-Draft → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param pageId the parent page id
   * @param id the sample id to delete
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the recomputed document carrying the deleted {@code message} (AD-8)
   */
  @DeleteMapping("/pages/{pageId}/samples/{id}")
  ResponseEntity<Schedule8Response> deleteSample(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int pageId,
      @PathVariable int id,
      Authentication authentication);

  /**
   * Add one rate-detail row (an addition or a deduction — determined by the chosen cost item's
   * subcategory) to a sample and return the recomputed document (Story 14.4, S01). Cost Item /
   * costing rate / Cost Type required → else 400; costing rate out of 0..9,999,999.99 → 400;
   * unknown sample → 404; non-Draft → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param sampleId the parent sample id
   * @param request the rate-row fields (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document carrying the success {@code message} (AD-8)
   */
  @PostMapping("/samples/{sampleId}/rates")
  ResponseEntity<Schedule8Response> addRate(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int sampleId,
      @Valid @RequestBody Schedule8RateRequest request,
      Authentication authentication);

  /**
   * Edit one rate-detail row inline (Story 14.4, S06). Same validation/guards as add; unknown row →
   * 404; stale {@code revisionCount} → 409. The sample's totals + finalRate recompute in the
   * response.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param sampleId the parent sample id
   * @param rowId the rate-row id to edit
   * @param request the rate-row fields + optimistic-lock token (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document carrying the success {@code message} (AD-8)
   */
  @PutMapping("/samples/{sampleId}/rates/{rowId}")
  ResponseEntity<Schedule8Response> updateRate(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int sampleId,
      @PathVariable int rowId,
      @Valid @RequestBody Schedule8RateRequest request,
      Authentication authentication);

  /**
   * Delete one rate-detail row under a sample and return the recomputed document (Story 14.4, S09 /
   * BR-05). Idempotent: an unknown sample/row id returns 200 (never 404, never touches another
   * sample's rows). Non-Draft → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param sampleId the parent sample id
   * @param rowId the rate-row id to delete
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the recomputed document carrying the deleted {@code message} (AD-8)
   */
  @DeleteMapping("/samples/{sampleId}/rates/{rowId}")
  ResponseEntity<Schedule8Response> deleteRate(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int sampleId,
      @PathVariable int rowId,
      Authentication authentication);

  /**
   * Check Status — all-pages sweep (Story 14.6, BR-07). Read-only (AD-5), mutates nothing, needs no
   * revision token. Returns 200 with a per-page → per-sample → per-field breakdown: {@code outcome
   * = "MET"} only when every page (and its samples) passes, else {@code "ISSUES"}. Same no-summary
   * context guards as the read (400/404/409/403, {@code VIEW_SCHEDULE}).
   *
   * <p>{@code request} carries the page panel currently ON SCREEN, if one is open (#359). It is
   * overlaid onto the stored page with the same id, so an unsaved edit moves the verdict, as it did
   * in legacy; samples are always the stored ones. A new page (null id) and an id matching nothing
   * stored are ignored. With {@code page} null the stored schedule is evaluated alone. The body is
   * REQUIRED — an absent one is a clean 400 — but its members are unvalidated, because reporting
   * missing values is the check's whole job.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the open page panel, if any
   * @param authentication the caller (authorized for VIEW_SCHEDULE)
   * @return 200 with the {@link Schedule8CheckStatusResponse}
   */
  @PostMapping("/check-status")
  ResponseEntity<Schedule8CheckStatusResponse> checkStatus(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody Schedule8CheckRequest request,
      Authentication authentication);

  /**
   * Check Status — single page scope (Story 14.6, S14/BR-09). Read-only; validates only {@code
   * pageId}'s samples. Same result shape as the sweep, scoped to the one page.
   *
   * <p>{@code request} carries the sample panel currently ON SCREEN, if one is open (#359). It is
   * overlaid onto the page's stored sample with the same id; a new sample (null id), or an id
   * matching no stored sample under the page, is evaluated as the page's next sample, as legacy's
   * Add put it into the checked list. The page header is always the stored one. With {@code sample}
   * null the stored page is evaluated alone. The body is REQUIRED — an absent one is a clean 400 —
   * but its members are unvalidated.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param pageId the page to check
   * @param request the open sample panel, if any
   * @param authentication the caller (authorized for VIEW_SCHEDULE)
   * @return 200 with the {@link Schedule8CheckStatusResponse} scoped to the page
   */
  @PostMapping("/pages/{pageId}/check-status")
  ResponseEntity<Schedule8CheckStatusResponse> checkStatusPage(
      @RequestParam long millId,
      @RequestParam int year,
      @PathVariable int pageId,
      @Valid @RequestBody Schedule8PageCheckRequest request,
      Authentication authentication);
}
