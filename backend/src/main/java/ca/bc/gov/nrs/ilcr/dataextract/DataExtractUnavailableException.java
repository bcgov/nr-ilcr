package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The selection passed every check but the CSV writer does not exist yet.
 *
 * <p>Legacy validated and generated in one action with no validate-only path, so the rebuild keeps
 * one endpoint and fills in its generator rather than shipping a throwaway {@code /validate} route.
 * This is the seam that gets replaced by the stream.
 *
 * <p>{@code 501 Not Implemented} rather than the 404 its nearest sibling ({@code
 * MillInformationReportUnavailableException}) uses: a 404 would read as "the selected mills and
 * years hold no data", which is a claim about the Ministry's records that nothing here has
 * established. The message text follows that sibling's ratified idiom — an honest "not yet
 * available" in place of a misleading refusal — and has no legacy analogue, because legacy always
 * had its extract to give.
 */
public class DataExtractUnavailableException extends BusinessException {

  public DataExtractUnavailableException() {
    super(HttpStatus.NOT_IMPLEMENTED, "dataExtractUnavailableMsg");
  }
}
