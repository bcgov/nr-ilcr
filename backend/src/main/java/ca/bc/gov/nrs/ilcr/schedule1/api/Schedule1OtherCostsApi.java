package ca.bc.gov.nrs.ilcr.schedule1.api;

import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
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
 * Subtotal Other Costs sub-resource contract (Story 2.4, AD-12; controller + api-interface split).
 * The sole writer of the itemized item-19 rows. {@code millId} and {@code year} are required query
 * params on every operation (AD-4), binding the sub-resource to its Schedule 1 context.
 */
@RequestMapping("/api/v1/schedule1/other-costs")
public interface Schedule1OtherCostsApi {

  /**
   * List the itemized Other-Costs rows + shared volume + server-computed totals (S09 read).
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the Other-Costs document
   */
  @GetMapping
  ResponseEntity<OtherCostsDocument> getOtherCosts(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Add one itemized row inheriting the shared volume (BR-06, S09). Range/required validation → 400;
   * non-Draft or no summary → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the row to add (validated: description required &le; 30, cost optional)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PostMapping
  ResponseEntity<OtherCostsDocument> addOtherCost(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody OtherCostRequest request,
      Authentication authentication);

  /**
   * Update one itemized row's description/cost (S11). Same validation/gates as add; unknown
   * {@code id} → 404.
   *
   * @param id the itemized row's detail id
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the new description/cost (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed document (success {@code message})
   */
  @PutMapping("/{id}")
  ResponseEntity<OtherCostsDocument> updateOtherCost(
      @PathVariable int id,
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody OtherCostRequest request,
      Authentication authentication);

  /**
   * Delete one itemized row by id (S12). Non-Draft or no summary → 409; unknown {@code id} → 404;
   * missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param id the itemized row's detail id
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the recomputed document (success {@code message})
   */
  @DeleteMapping("/{id}")
  ResponseEntity<OtherCostsDocument> deleteOtherCost(
      @PathVariable int id,
      @RequestParam long millId,
      @RequestParam int year,
      Authentication authentication);
}
