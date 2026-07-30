package ca.bc.gov.nrs.ilcr.schedule7a.dto;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import java.util.List;

/**
 * The five Schedule 7A code option lists carried on the served document (AD-12), so the frontend
 * renders the New/Used, superstructure, decking, abutment, and load-rating dropdowns without a
 * second call. Each list is the {@code (code, description)} rows of the corresponding
 * {@code THE.*_CODE} table (legacy {@code ILCRBridge*Code}/{@code ILCRDeckCode}).
 *
 * @param constructionTypes {@code ILCR_BRIDGE_CNSTRCTN_TYPE_CODE} options
 * @param superstructureTypes {@code ILCR_BRIDGE_SUPERSTRUCTR_CODE} options
 * @param deckTypes {@code ILCR_DECK_CODE} options
 * @param abutmentTypes {@code ILCR_BRIDGE_ABUTMENT_TYPE_CODE} options
 * @param loadRatings {@code ILCR_BRIDGE_LOAD_RATING_CODE} options
 */
public record BridgeCodeLists(
    List<CodeDescriptionDto> constructionTypes,
    List<CodeDescriptionDto> superstructureTypes,
    List<CodeDescriptionDto> deckTypes,
    List<CodeDescriptionDto> abutmentTypes,
    List<CodeDescriptionDto> loadRatings) {
}
