package ca.bc.gov.nrs.ilcr.dataextract.api;

import ca.bc.gov.nrs.ilcr.dataextract.dto.DataExtractRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Data Extract API contract (controller + api-interface split). The interface owns the request
 * mapping and the wire documentation; {@code DataExtractController} implements it and adds the
 * authorization gate.
 *
 * <p>ONE endpoint validates and generates, because that is the shape legacy had: {@code
 * ExtractDataMB.extract()} ran both in a single action and there was no validate-only path
 * anywhere. The CSV body is not built yet — a selection that passes the gate currently answers
 * {@code 501 Not Implemented} with the verbatim "not yet available" text, the same honest refusal
 * the Print Schedules page gives for its still-deferred Mill Information section. Replacing that
 * branch with the stream is all that remains.
 */
@RequestMapping("/api/v1/reports")
public interface DataExtractApi {

  /**
   * Validate an extract selection and generate its CSV.
   *
   * <p>The 400 reports EVERY failing check together, never the first one: blank start year, blank
   * end year, no mill selected, no schedule selected, then end-year-before-start-year, each as
   * verbatim legacy text in an {@code application/problem+json} {@code messages} array of {@code
   * {key, text}}. A start year equal to the end year is a valid single-year extract — legacy
   * refuses only a start LATER than the end. No file is produced on any failure.
   *
   * @param request the selection; deliberately unannotated, see {@link DataExtractRequest}
   * @param authentication the authenticated caller, for the authorization gate
   * @return 501 until the generator lands; 400 problem+json with the accumulated messages when the
   *     selection is incomplete; 403 when the caller does not hold the extract capability
   */
  @PostMapping("/data-extract")
  ResponseEntity<Void> generate(
      @RequestBody DataExtractRequest request, Authentication authentication);
}
