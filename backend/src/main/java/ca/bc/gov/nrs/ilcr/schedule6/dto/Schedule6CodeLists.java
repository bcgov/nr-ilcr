package ca.bc.gov.nrs.ilcr.schedule6.dto;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The Schedule 6 dropdown lists, served in-document.
 *
 * <p>There is no standalone {@code /codes} endpoint in this application — Schedule 7A set the
 * in-document precedent and Schedules 9, 10 and now 6 follow it.
 *
 * <p>Both lists are year-filtered on {@code EFFECTIVE_DATE <= 1-Jan-{year} <= EXPIRY_DATE},
 * reproducing legacy {@code LookupCache.getCacheList}. There is no TFL list: legacy's TFL leg is a
 * free-text {@code p:inputText} with {@code TflNumberValidator} ({@code schedule6.xhtml:102,290}),
 * validated in the service against {@code RoadGroupLookup} rather than picked from a menu.
 *
 * <p>The synthetic {@code "TFL"} entry legacy prepends to the TSA cache ({@code
 * LookUpCacheDAO.java:230}) is NOT included here. It is not a code-table row; the control adds it,
 * which keeps this DTO a faithful projection of the two code tables.
 *
 * @param tsaNumbers the TSA numbers offered by the area-type control
 * @param supplyBlocks the supply block codes; the control narrows these to the chosen TSA
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule6CodeLists(
    List<CodeDescriptionDto> tsaNumbers, List<CodeDescriptionDto> supplyBlocks) {}
