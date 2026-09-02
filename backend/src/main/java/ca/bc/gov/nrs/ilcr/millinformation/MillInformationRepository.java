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
   * Every mill with a report-status row for the year — or ONE named mill's row — with its
   * information, milestones, ownership and contacts. Unscoped by USER either way: the Administrator
   * report covers all mills (BR-01), so there is deliberately no user or associated-mill predicate
   * here.
   *
   * <p><b>One query serves both reports, and that is the requirement, not a convenience.</b> The
   * all-mills Mill Information PDF (Story 19.1, {@code millId} null) and the per-mill drill-down
   * PDF (Story 19.3, {@code millId} set) render the SAME template from the SAME mapper, and their
   * sameness is the parity contract — legacy ran one renderer for both, calling {@code
   * addMillReportStatus} once where the all-mills path loops it ({@code
   * ILCRPrintService.java:213-220,230-249}). A second copy of the 19-column join below is the one
   * failure mode nothing here would catch: each report would keep passing its own tests while
   * quietly disagreeing about a mill, because a column added to one copy and not the other is
   * invisible until someone compares two PDFs by eye. So the drill-down is a PREDICATE, never a
   * second query — and {@code AND (:millId IS NULL OR ...)} is the standard Oracle optional-bind
   * idiom, which over a year's ~118 rows costs nothing worth measuring.
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
   * <p><b>The ORDER BY continues past mill id on purpose, and removing the tail reintroduces a
   * non-reproducible read.</b> {@code ILCR_MILL_REPORT_STATUS_RPT_VW} is a VIEW with no PK or
   * uniqueness guarantee — {@code MillContextRepository.findStatusDates} documents the same fact
   * and takes first-row semantics for it, as legacy's {@code
   * MillReportStatusDAO.getMillReportStatusList} did with {@code get(0)}. So one (year, mill) pair
   * can yield several rows, and {@link MillInformationService#findSection} takes the FIRST of them.
   * With {@code ORDER BY v.ILCR_MILL_ID} alone that first row is whatever Oracle's plan happens to
   * emit, which is free to differ between two executions of the same request: two consecutive
   * drill-downs of one mill could hand the administrator different addresses or contacts.
   *
   * <p>The tail is the view's OWN projected columns, and that is what makes it sufficient. Every
   * other table here joins 1:1 on a primary key ({@code MILL} by {@code MILL_ID}, the xref by the
   * same id, {@code CLIENT_LOCATION} by its composite PK, both contacts by {@code
   * CLIENT_CONTACT_ID}), so no joined column can differ between two rows of the same mill — only
   * the view's status code and its four milestone strings can. Ordering by exactly those totally
   * orders the DISTINCT PROJECTIONS: two view rows agreeing on all five produce byte-identical
   * sections, so which one wins is unobservable. A surrogate like {@code
   * x.ILCR_MILL_STATUS_XREF_ID} would NOT work — it equals {@code m.MILL_ID}, which equals {@code
   * v.ILCR_MILL_ID}, so it is constant across exactly the rows that need separating. Oracle's
   * default {@code NULLS LAST} on ASC settles the unreached milestones deterministically.
   *
   * @param year the reporting year
   * @param millId ONE mill to restrict to, or {@code null} for every mill in the year
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
         AND (:millId IS NULL OR v.ILCR_MILL_ID = :millId)
       ORDER BY v.ILCR_MILL_ID,
                v.ILCR_MILL_STATUS_CODE,
                v.MILL_STATUS_OPEN_DATE,
                v.MILL_STATUS_DRAFT_DATE,
                v.MILL_STATUS_SUBMIT_DATE,
                v.MILL_STATUS_VERIFY_DATE
      """)
  List<MillInformationRowEntity> findSectionRows(
      @Param("year") int year, @Param("millId") Long millId);

  /**
   * The selling-price zone code to description lookup, read SEPARATELY from the section rows.
   *
   * <p><b>The table is {@code APPRAISAL_SELL_PRICE_ZONE_CODE}, not {@code
   * ISP_SELL_PRICE_ZONE_CODE}.</b> The join key on {@code MILL} is a column named {@code
   * ISP_SELL_PRICE_ZONE_CODE}, but the descriptions it points at live in the APPRAISAL table — see
   * {@link ZoneDescriptionEntity} for the legacy mapping that settles it. This query originally
   * named the ISP table, which does not exist on the FTA database, so the degrade below fired on
   * every request and Region rendered "-" for all 140 mills while legacy showed real values
   * (verified 2026-09-02 as {@code ILCR$WEB1} on {@code fortmp1}: {@code
   * THE.APPRAISAL_SELL_PRICE_ZONE_CODE} holds 19 rows and resolves every mill's code).
   *
   * <p>It is still not joined into {@link #findSectionRows}, and the degrade below stays. This is a
   * shared ministry code table reached through a PUBLIC synonym; a synonym whose target is missing
   * fails the whole statement at parse time with ORA-00942, join type notwithstanding. Joined in,
   * one unreadable code table takes down the entire report; read apart, a mill simply shows "-" for
   * its region, which is the fallback that field already has. Region is a display description, not
   * report data — but a "-" is now a real signal about the environment rather than the normal case.
   *
   * <p><b>Two consumers.</b> Besides {@link MillInformationService}, {@code
   * ca.bc.gov.nrs.ilcr.millreportstatus.MillReportStatusService} borrows THIS method for the Mill
   * Status Report table's Region column (Story 19.2) — deliberately, so there is one definition of
   * the read rather than two copies that can drift, and so a table-name fix reaches both surfaces
   * at once. Renaming or narrowing it while working on 19.1 breaks 19.2 as well.
   *
   * <p>Deliberately unfiltered by {@code EFFECTIVE_DATE}/{@code EXPIRY_DATE}: legacy reached the
   * description by FK navigation with no date predicate either, so a retired zone still names
   * itself on a historical report. Whether Region should be as-of-year is an open question recorded
   * in {@code deferred-work.md}, not something to change here unasked.
   *
   * @return one row per zone code
   */
  @Query(
      """
      SELECT APPRAISAL_SELL_PRICE_ZONE_CODE, DESCRIPTION
        FROM THE.APPRAISAL_SELL_PRICE_ZONE_CODE
      """)
  List<ZoneDescriptionEntity> findZoneDescriptions();
}
