package ca.bc.gov.nrs.ilcr.millinformation.dto;

/**
 * One mill's Mill Information section, at the service boundary: raw values with nulls preserved.
 *
 * <p>Null means "the delivery data has nothing here", and that stays true all the way to the
 * mapper, which is the single place the legacy {@code "-"} substitution happens. Substituting
 * earlier would make an absent postal code indistinguishable from one that genuinely reads "-".
 *
 * <p>The four milestone dates arrive here with the legacy sort prefix already stripped, so a
 * milestone the mill has not reached is null rather than the raw {@code "D: "}.
 *
 * @param millId the mill id
 * @param millNumber the mill number
 * @param millName the mill name
 * @param active whether the mill's status xref reads {@code ACT} (legacy prints Yes/No)
 * @param region the selling-price zone description; nullable
 * @param clientName the ownership client name; nullable
 * @param address1 address line 1; nullable
 * @param address2 address line 2; nullable
 * @param city city; nullable
 * @param postalCode postal code; nullable
 * @param headOfficeContactIndicator the head-office contact flag; nullable
 * @param headOfficeContactName head-office contact name; nullable
 * @param headOfficePhone head-office business phone; nullable
 * @param divisionContactName division contact name; nullable
 * @param divisionPhone division business phone; nullable
 * @param openDate the open milestone; nullable
 * @param draftDate the draft milestone; nullable
 * @param submitDate the submit milestone; nullable
 * @param verifyDate the verify milestone; nullable
 */
public record MillInformationSection(
    long millId,
    String millNumber,
    String millName,
    boolean active,
    String region,
    String clientName,
    String address1,
    String address2,
    String city,
    String postalCode,
    String headOfficeContactIndicator,
    String headOfficeContactName,
    String headOfficePhone,
    String divisionContactName,
    String divisionPhone,
    String openDate,
    String draftDate,
    String submitDate,
    String verifyDate) {}
