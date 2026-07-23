package ca.bc.gov.nrs.ilcr.schedule3.api;

import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRequest;
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
 * Schedule 3 Included Unacceptable Costs sub-resource contract (Story 4.4, AD-12). The sole writer of
 * the item-38 rows. {@code millId} and {@code year} are required query params on every operation (AD-4).
 */
@RequestMapping("/api/v1/schedule3/included-unacceptable-costs")
public interface Schedule3UnacceptableCostsApi {

  /**
   * List the itemized Included Unacceptable rows + subtotal + the read-only Annual Rents (S111) figure.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the Included Unacceptable document
   */
  @GetMapping
  ResponseEntity<UnacceptableDocument> getUnacceptable(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Add one Included Unacceptable row. Validation → 400; non-Draft or no summary → 409; missing
   * {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the row to add (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PostMapping
  ResponseEntity<UnacceptableDocument> addUnacceptable(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody UnacceptableRequest request,
      Authentication authentication);

  /**
   * Update one row by detail {@code id}. Same validation/gates as add; unknown id → 404.
   *
   * @param id the item-38 detail id
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the new description/total (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/{id}")
  ResponseEntity<UnacceptableDocument> updateUnacceptable(
      @PathVariable int id,
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody UnacceptableRequest request,
      Authentication authentication);

  /**
   * Delete one row by detail {@code id}. Non-Draft or no summary → 409; unknown id → 404; missing
   * {@code EDIT_SCHEDULE} → 403.
   *
   * @param id the item-38 detail id
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the recomputed document (success {@code message})
   */
  @DeleteMapping("/{id}")
  ResponseEntity<UnacceptableDocument> deleteUnacceptable(
      @PathVariable int id,
      @RequestParam long millId,
      @RequestParam int year,
      Authentication authentication);
}
