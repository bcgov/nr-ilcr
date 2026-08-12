package ca.bc.gov.nrs.ilcr.schedule7b.dto;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import java.util.List;

/**
 * The Schedule 7B code option list carried on the served document (AD-12), so the frontend renders
 * the Type dropdown without a second call. Schedule 7B has exactly ONE code list (unlike 7A's
 * five): the {@code (code, description)} rows of {@code THE.ILCR_CULVERT_TYPE_CODE} (legacy {@code
 * ILCRCulvertTypeCode}), filtered to the codes effective for the reporting year.
 *
 * <p>Reading the maintainable table — rather than a hardcoded enum — is what lets a Table
 * Maintenance addition reach the form with no code change. That is not hypothetical: the business
 * added {@code Round Plastic} ({@code RP}) on 2026-08-11, which is code-table DATA, not a legacy
 * departure (PRD § Business-Directed Legacy Departures; Epic 13 note; UC-CODE-001).
 *
 * <p>Kept as a wrapper record rather than a bare list so the document's shape matches its 7A twin
 * and can absorb a second list later without a breaking wire change.
 *
 * @param culvertTypes {@code ILCR_CULVERT_TYPE_CODE} options effective for the reporting year
 */
public record CulvertCodeLists(List<CodeDescriptionDto> culvertTypes) {
}
