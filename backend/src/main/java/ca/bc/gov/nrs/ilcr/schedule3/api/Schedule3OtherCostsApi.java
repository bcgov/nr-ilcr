package ca.bc.gov.nrs.ilcr.schedule3.api;

import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRequest;
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
 * Schedule 3 Other Acceptable Costs sub-resource contract (Story 4.4, AD-12; controller + api-interface
 * split). The sole writer of the item-124 TOT+PO&amp;P groups. {@code millId} and {@code year} are
 * required query params on every operation (AD-4), binding the sub-resource to its Schedule 3 context.
 */
@RequestMapping("/api/v1/schedule3/other-acceptable-costs")
public interface Schedule3OtherCostsApi {

  /**
   * List the itemized Other Acceptable groups + server-computed subtotal.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the Other Acceptable document
   */
  @GetMapping
  ResponseEntity<OtherAcceptableDocument> getOtherAcceptable(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Add one group (a fresh TOT + PO&amp;P pair). Validation → 400; non-Draft or no summary → 409;
   * missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the group to add (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PostMapping
  ResponseEntity<OtherAcceptableDocument> addOtherAcceptable(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody OtherAcceptableRequest request,
      Authentication authentication);

  /**
   * Update one group by its TOT detail {@code id}. Same validation/gates as add; unknown id → 404.
   *
   * @param id the group's TOT detail id
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the new description/total/pop (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/{id}")
  ResponseEntity<OtherAcceptableDocument> updateOtherAcceptable(
      @PathVariable int id,
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody OtherAcceptableRequest request,
      Authentication authentication);

  /**
   * Delete one group (TOT + PO&amp;P) by its TOT detail {@code id}. Non-Draft or no summary → 409;
   * unknown id → 404; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param id the group's TOT detail id
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the recomputed document (success {@code message})
   */
  @DeleteMapping("/{id}")
  ResponseEntity<OtherAcceptableDocument> deleteOtherAcceptable(
      @PathVariable int id,
      @RequestParam long millId,
      @RequestParam int year,
      Authentication authentication);
}
