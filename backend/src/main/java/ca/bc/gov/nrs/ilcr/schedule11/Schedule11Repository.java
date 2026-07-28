package ca.bc.gov.nrs.ilcr.schedule11;

import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JDBC reads for the Schedule 11 locations document (AD-3: repository interface +
 * {@code @Table} record entities + explicit {@code @Query} named-parameter SQL, {@code THE}-
 * qualified; no derived queries, no {@code CrudRepository.save}, no {@code JdbcClient}). SQL only —
 * all derivations live in {@link Schedule11Service}.
 *
 * <p>Both reads return LISTS (duplicate-row safety: no {@code .optional()} single-row expectations
 * — a query that can legally return multiples must never 500 on duplicates). The category literal
 * {@code '11'} is legacy {@code Constant.CATEGORIES.Schedule11}; items {@code 23}/{@code 24} are
 * {@code REPORT_COST_ITEMS.Schedule11_1_Planned}/{@code _Actual}.
 */
public interface Schedule11Repository extends Repository<SilvicultureLocationEntity, Long> {

  /**
   * The Schedule 11 locations for a mill/year, ordered by {@code BASIC_SILVICULTURE_REPORT_ID}
   * ascending (legacy {@code Schedule11DAO.getBasicSilvicultureReports} HQL order). LEFT join to
   * the BEC catalogue so a dangling catalogue id (no FK in delivery — AC9) degrades to null label
   * parts instead of dropping the row.
   *
   * @param year the reporting year
   * @param millId the mill id
   * @return the location rows in serving order; empty when the mill/year has none (a valid state)
   */
  @Query("""
      SELECT b.BASIC_SILVICULTURE_REPORT_ID, b.LOCATION, b.ENHANCED_IND,
             b.BECBIOGEOCLIMATIC_CATALOGUE_ID, c.BEC_ZONE_CODE, c.SUBZONE, c.VARIANT, c.PHASE,
             b.REFORESTED_NET_AREA, b.COMMENTS, b.REVISION_COUNT
        FROM THE.BASIC_SILVICULTURE_REPORT b
        LEFT JOIN THE.BIOGEOCLIMATIC_CATALOGUE c
          ON c.BIOGEOCLIMATIC_CATALOGUE_ID = b.BECBIOGEOCLIMATIC_CATALOGUE_ID
       WHERE b.REPORT_YEAR = :year
         AND b.ILCR_MILL_ID = :millId
         AND b.ILCR_CATEGORY_ID = '11'
       ORDER BY b.BASIC_SILVICULTURE_REPORT_ID
      """)
  List<SilvicultureLocationEntity> findLocations(
      @Param("year") int year, @Param("millId") long millId);

  /**
   * The Actual/Planned cost rows (items 24/23) for every location of a mill/year, in one read.
   * Filtered to 23/24 here AND re-checked in the service's unpacking loop (legacy
   * {@code getSilvicultureReport} assigns only those two) — out-of-scope items attached to a
   * location must reach no figure.
   *
   * @param year the reporting year
   * @param millId the mill id
   * @return the cost rows; a location may have 0, 1, or both items (real data: 0 is dominant)
   */
  @Query("""
      SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.BASIC_SILVICULTURE_REPORT_ID,
             d.ILCR_REPORT_COST_ITEM_ID, d.COST
        FROM THE.ILCR_COST_REPORT_DETAIL d
        JOIN THE.BASIC_SILVICULTURE_REPORT b
          ON b.BASIC_SILVICULTURE_REPORT_ID = d.BASIC_SILVICULTURE_REPORT_ID
       WHERE b.REPORT_YEAR = :year
         AND b.ILCR_MILL_ID = :millId
         AND b.ILCR_CATEGORY_ID = '11'
         AND d.ILCR_REPORT_COST_ITEM_ID IN (23, 24)
       ORDER BY d.ILCR_COST_REPORT_DETAIL_ID
      """)
  List<SilvicultureCostEntity> findCostDetails(
      @Param("year") int year, @Param("millId") long millId);
}
