package ca.bc.gov.nrs.ilcr.schedule1.dto;

/**
 * The Schedule 1 silviculture block (AD-12): the four silviculture line items (legacy cost-item
 * codes 1, 2, 139, 140). Any member is null when its detail row is absent (Jackson omits nulls).
 */
public record SilvicultureBlock(
    LineItem actualSpent,
    LineItem accruedLessActual,
    LineItem lessAdmin,
    LineItem total) {
}
