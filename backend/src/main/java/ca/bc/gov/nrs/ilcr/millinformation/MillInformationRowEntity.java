package ca.bc.gov.nrs.ilcr.millinformation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one mill's Mill Information section (AD-3), projected by explicit
 * {@code @Query} from {@code THE.ILCR_MILL_REPORT_STATUS_RPT_VW} joined out to the mill, its status
 * xref, its selling-price zone, its client location and its two client contacts.
 *
 * <p>The driving table is the report-status view, which is what makes the report year the only
 * input: a mill appears in the report exactly when it has a row there for the selected year.
 *
 * <p>Everything outboard of {@code MILL} — the status xref included — is joined LEFT, and every
 * such column is nullable. That is not defensive coding: on the delivery image only 4 of 17 mills
 * carry contacts, one carries no postal code, and {@code ISP_SELL_PRICE_ZONE_CODE} is empty
 * outright, so the absent case is the common one. The mapper substitutes {@code "-"} for the
 * address, region and contact fields, and leaves the milestone dates blank as legacy did.
 *
 * <p>The four milestone columns arrive with their legacy three-character prefix still attached;
 * {@link MillInformationService} strips it. Mapped by {@code THE} column name — this never crosses
 * the service boundary.
 *
 * @param millId the mill id ({@code ILCR_MILL_ID}), also the status-xref primary key
 * @param millNumber the mill number
 * @param millName the mill name
 * @param millStatusCode {@code ACT}/{@code CLS} for the REPORTING YEAR, from the view — not the
 *     mill's status today, which is what the xref carries
 * @param regionCode the selling-price zone code; nullable. Its DESCRIPTION is resolved separately
 *     by {@code MillInformationRepository.findZoneDescriptions}
 * @param clientLocationName the ownership client name; nullable
 * @param address1 client location address line 1; nullable
 * @param address2 client location address line 2; nullable
 * @param city client location city; nullable
 * @param postalCode client location postal code; nullable
 * @param headOfficeContactIndicator the {@code HEAD_OFFICE_CONTACT_IND} flag; nullable
 * @param headOfficeContactName head-office contact name; nullable
 * @param headOfficePhone head-office business phone; nullable
 * @param divisionContactName division contact name; nullable
 * @param divisionPhone division business phone; nullable
 * @param openDate prefixed open milestone; nullable
 * @param draftDate prefixed draft milestone; nullable
 * @param submitDate prefixed submit milestone; nullable
 * @param verifyDate prefixed verify milestone; nullable
 */
@Table(name = "ILCR_MILL_REPORT_STATUS_RPT_VW", schema = "THE")
public record MillInformationRowEntity(
    @Id @Column("ILCR_MILL_ID") long millId,
    @Column("MILL_NUMBER") String millNumber,
    @Column("MILL_NAME") String millName,
    @Column("ILCR_MILL_STATUS_CODE") String millStatusCode,
    @Column("REGION_CODE") String regionCode,
    @Column("CLIENT_LOCN_NAME") String clientLocationName,
    @Column("ADDRESS_1") String address1,
    @Column("ADDRESS_2") String address2,
    @Column("CITY") String city,
    @Column("POSTAL_CODE") String postalCode,
    @Column("HEAD_OFFICE_CONTACT_IND") String headOfficeContactIndicator,
    @Column("HEAD_OFFICE_CONTACT_NAME") String headOfficeContactName,
    @Column("HEAD_OFFICE_PHONE") String headOfficePhone,
    @Column("DIVISION_CONTACT_NAME") String divisionContactName,
    @Column("DIVISION_PHONE") String divisionPhone,
    @Column("MILL_STATUS_OPEN_DATE") String openDate,
    @Column("MILL_STATUS_DRAFT_DATE") String draftDate,
    @Column("MILL_STATUS_SUBMIT_DATE") String submitDate,
    @Column("MILL_STATUS_VERIFY_DATE") String verifyDate) {}
