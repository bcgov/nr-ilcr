package ca.bc.gov.nrs.ilcr.millinformation;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Reads every mill's Mill Information content for one reporting year (AD-3: repository interface +
 * {@code @Table} record entity + explicit {@code @Query} named-parameter SQL). SQL only — decisions
 * live in {@link MillInformationService}.
 *
 * <p>ONE query for the whole year, not one per mill. Legacy re-queried the mill, its status xref,
 * its client location and both contacts inside a per-row loop ({@code
 * MillReportStatusDAO.getReport}); at 17 mills that is 85 round trips to build one PDF. The join
 * below is the same data in a single pass, and it is what keeps the report's cost flat as mills are
 * added.
 */
@org.springframework.stereotype.Repository
public interface MillInformationRepository extends Repository<MillInformationRowEntity, Long> {

  /**
   * Every mill with a report-status row for the year, with its information, milestones, ownership
   * and contacts. Unscoped by design — the Administrator report covers all mills (BR-01), so there
   * is deliberately no user or mill predicate here.
   *
   * <p>The Active flag comes from the VIEW's {@code ILCR_MILL_STATUS_CODE}, not the xref's. They
   * are different facts: the view's is the mill's status FOR THAT REPORTING YEAR, the xref's is its
   * status today. Legacy reads the view ({@code MillReportStatusDAO.getReport} takes it off the
   * {@code ILCRMillReportStatusRptOv} row), and taking the xref's would make a reprint of a 2021
   * report change once the mill closed.
   *
   * <p>{@code ILCR_MILL_STATUS_XREF} joins on {@code ILCR_MILL_STATUS_XREF_ID = MILL.MILL_ID} — the
   * xref shares its primary key with the mill, which is also why the view's {@code ILCR_MILL_ID}
   * addresses both. It is LEFT joined, like everything beyond it: a mill with a report-status row
   * but no xref row must still appear, because the report's contract is every mill (BR-01), and an
   * inner join would delete it silently.
   *
   * <p>Ordered by mill id, matching legacy's {@code findILCRMillReportStatusRptOv} ({@code Order By
   * rep.ilcr_mill_id}), so the section sequence reproduces the legacy document.
   *
   * @param year the reporting year
   * @return one row per mill, ordered by mill id (legacy's order — note mill id is NOT mill number)
   */
  @Query(
      """
      SELECT v.ILCR_MILL_ID,
             m.MILL_NUMBER,
             m.MILL_NAME,
             v.ILCR_MILL_STATUS_CODE,
             m.ISP_SELL_PRICE_ZONE_CODE AS REGION_CODE,
             cl.CLIENT_LOCN_NAME,
             cl.ADDRESS_1,
             cl.ADDRESS_2,
             cl.CITY,
             cl.POSTAL_CODE,
             x.HEAD_OFFICE_CONTACT_IND,
             ho.CONTACT_NAME    AS HEAD_OFFICE_CONTACT_NAME,
             ho.BUSINESS_PHONE  AS HEAD_OFFICE_PHONE,
             dv.CONTACT_NAME    AS DIVISION_CONTACT_NAME,
             dv.BUSINESS_PHONE  AS DIVISION_PHONE,
             v.MILL_STATUS_OPEN_DATE,
             v.MILL_STATUS_DRAFT_DATE,
             v.MILL_STATUS_SUBMIT_DATE,
             v.MILL_STATUS_VERIFY_DATE
        FROM THE.ILCR_MILL_REPORT_STATUS_RPT_VW v
        JOIN THE.MILL m
          ON m.MILL_ID = v.ILCR_MILL_ID
        LEFT JOIN THE.ILCR_MILL_STATUS_XREF x
          ON x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID
        LEFT JOIN THE.CLIENT_LOCATION cl
          ON cl.CLIENT_NUMBER = m.CLIENT_NUMBER
         AND cl.CLIENT_LOCN_CODE = m.CLIENT_LOCN_CODE
        LEFT JOIN THE.CLIENT_CONTACT ho
          ON ho.CLIENT_CONTACT_ID = x.HEAD_OFFICE_CONTACT_ID
        LEFT JOIN THE.CLIENT_CONTACT dv
          ON dv.CLIENT_CONTACT_ID = x.DIVISION_CONTACT_ID
       WHERE v.REPORT_YEAR = :year
       ORDER BY v.ILCR_MILL_ID
      """)
  List<MillInformationRowEntity> findSectionRows(@Param("year") int year);

  /**
   * The selling-price zone code to description lookup, read SEPARATELY from the section rows.
   *
   * <p>It is not joined into {@link #findSectionRows} on purpose. {@code ISP_SELL_PRICE_ZONE_CODE}
   * is a shared ministry table reached through a PUBLIC synonym, and on the FTA development
   * database that synonym is dangling: the underlying table is absent, so ANY reference to it fails
   * the whole statement at parse time with ORA-00942, join type notwithstanding. Joined in, one
   * missing code table took down the entire report; read apart, a mill simply shows "-" for its
   * region, which is the fallback that field already has. Region is a display description, not
   * report data.
   *
   * @return one row per zone code
   */
  @Query(
      """
      SELECT ISP_SELL_PRICE_ZONE_CODE, DESCRIPTION
        FROM THE.ISP_SELL_PRICE_ZONE_CODE
      """)
  List<ZoneDescriptionEntity> findZoneDescriptions();
}
