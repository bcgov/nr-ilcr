package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The Schedule 10 dropdown lists, served in-document.
 *
 * <p>There is no standalone {@code /codes} endpoint in this application — Schedule 7A set the
 * in-document precedent and Schedule 10 follows it.
 *
 * <p>Every list except {@code becClassifications} is year-filtered on
 * {@code EFFECTIVE_DATE <= 1-Jan-{year} <= EXPIRY_DATE}, reproducing legacy
 * {@code LookupCache.getCacheList} (:77-98). The BEC catalogue is deliberately NOT year-filtered
 * because {@code BiogeoclimaticCatalogue} does not extend {@code AbstractILCRCode} and so falls
 * through legacy's date filter entirely.
 *
 * <p><strong>There are no ASM Code or Soil Moisture Code lists.</strong> LD-1/LD-2 remove both
 * fields, which also eliminates BR-06's runtime filtering of those two lists.
 *
 * @param forestRegions the forest region codes
 * @param tsaNumbers the TSA numbers offered by the location control
 * @param supplyBlocks the supply block codes; the control narrows these to the chosen TSA
 * @param roadLifetimes the Road Type codes
 * @param ballastMethods the ballast method codes
 * @param ballastMaterials the ballast material codes
 * @param rsmrClasses the RSMR class codes; the one list legacy renders as {@code "{code} - {desc}"}
 * @param becClassifications the BEC classifications offerable through the surviving BR-06 xref gate
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule10CodeLists(
    List<CodeDescriptionDto> forestRegions,
    List<CodeDescriptionDto> tsaNumbers,
    List<CodeDescriptionDto> supplyBlocks,
    List<CodeDescriptionDto> roadLifetimes,
    List<CodeDescriptionDto> ballastMethods,
    List<CodeDescriptionDto> ballastMaterials,
    List<CodeDescriptionDto> rsmrClasses,
    List<BecClassification> becClassifications) {
}
