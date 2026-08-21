package ca.bc.gov.nrs.ilcr.schedule9.dto;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import java.util.List;

/**
 * The Schedule 9 code option lists carried on the served document (AD-12, Story 9.3), so the
 * frontend renders the four dropdowns — Contractual Item, Unit Type, Biogeoclimatic Zone, Source —
 * without a second call. Each option is a {@code (code, description)} pair, the same shape the
 * record fields already serve, so the page can match a record's stored code to its option.
 *
 * <p>Added by Story 9.3: Story 9.1's read contract omitted these (unlike Schedule 7B/7A, whose
 * documents carry their code lists). This is a small, read-only, additive extension of the GET — it
 * does not re-pin the write contract.
 *
 * <p><strong>Not year-scoped.</strong> Unlike Schedule 7A/7B (which filter to codes effective for
 * the reporting year via a {@code LookupCache} port), these lists serve every reference row,
 * matching the 9.1 read joins, which resolve descriptions unscoped. The Contractual Item catalogue
 * is the fixed category-{@code '9'} set (108–114, BR-09); the unit/BEC/source reference tables are
 * small and stable. Year-scoping the unit list (the only reference table here with effective/expiry
 * columns) is a possible later refinement, recorded rather than done, to keep this addition
 * minimal.
 *
 * @param contractualItems the category-'9' cost items (108–114) — code + catalogue name
 * @param unitTypes the {@code ILCR_UNIT_CODE} options
 * @param biogeoclimaticZones the {@code BEC_ZONE_CODE} options
 * @param sources the {@code ILCR_CONTRACTUAL_SOURCE_CODE} options
 */
public record Schedule9CodeLists(
    List<CodeDescriptionDto> contractualItems,
    List<CodeDescriptionDto> unitTypes,
    List<CodeDescriptionDto> biogeoclimaticZones,
    List<CodeDescriptionDto> sources) {}
