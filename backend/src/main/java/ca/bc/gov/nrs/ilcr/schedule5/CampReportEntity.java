package ca.bc.gov.nrs.ilcr.schedule5;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC row shape for the legacy {@code THE.CAMP_REPORT} table (AD-3), the
 * Schedule 5 master record. Each row is one logging camp keyed by {@code ILCR_MILL_ID} +
 * {@code REPORT_YEAR} + {@code ILCR_CATEGORY_ID = '5'}; its twelve category amounts hang off it as
 * keyed rows in {@code ILCR_COST_REPORT_DETAIL} (see {@link CostReportDetailEntity}).
 *
 * <p>Types are the delivery column types, not legacy's Hibernate mappings (Story 7.1 Task 1 gate
 * (i)): {@code DISTANCE_TO_OPERATING_AREA} is {@code NUMBER(8,2)} and is read as
 * {@code BigDecimal} — legacy mapped it to a {@code Double} and then did
 * {@code new BigDecimal(double)}, which leaks the binary expansion of a value the column had
 * already fixed at scale 2 ({@code 0.1} becomes {@code 0.1000000000000000055…}); a JSF
 * {@code DecimalFormat} hid that, a JSON API would not. {@code ISOLATED_CAMP_IND} stays the raw
 * {@code Y}/{@code N} string here and becomes a Boolean in the service.
 *
 * <p>The aggregate type for {@link Schedule5Repository}; SQL stays explicit ({@code @Query})
 * because the model is a legacy-projection, not a {@code save()}-driven aggregate.
 */
@Table(name = "CAMP_REPORT", schema = "THE")
public record CampReportEntity(
    @Id @Column("CAMP_REPORT_ID") Integer campReportId,
    @Column("CAMP_NAME") String campName,
    @Column("DISTANCE_TO_OPERATING_AREA") BigDecimal distanceToOperatingArea,
    @Column("CAMP_SIZE_CAPACITY") Integer campSizeCapacity,
    @Column("ASSOCIATED_CAMP_VOLUME") BigDecimal associatedCampVolume,
    @Column("ISOLATED_CAMP_IND") String isolatedCampInd,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
