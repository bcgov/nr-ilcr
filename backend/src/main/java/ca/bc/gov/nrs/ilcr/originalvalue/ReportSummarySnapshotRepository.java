package ca.bc.gov.nrs.ilcr.originalvalue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.RowMapper;

/**
 * Reads the licensee's submitted summary-level figures from {@code THE.ILCR_REPORT_SUMMARY_S_VW}
 * (AD-3). SQL only, read-only.
 *
 * <p>Keyed by the summary id rather than by mill/year/category so it matches the id a schedule
 * service already holds from its own {@code findSummary} read, and so a snapshot can never be
 * fetched for a different summary than the one being served.
 */
public interface ReportSummarySnapshotRepository extends Repository<ReportSummarySnapshot, Long> {

  /** Submitted summary figures for one report summary, or empty when none is on file. */
  @Query(
      value =
          """
      SELECT CROWN_VOLUME, LOCATION, COMMENTS
        FROM THE.ILCR_REPORT_SUMMARY_S_VW
       WHERE ILCR_REPORT_SUMMARY_ID = :summaryId
      """,
      rowMapperClass = SnapshotRowMapper.class)
  Optional<Snapshot> findBySummaryId(@Param("summaryId") int summaryId);

  /**
   * The submitted summary figures. {@code overrideTotalPop} carries the {@code LOCATION} column,
   * which is where Schedule 3 stores that flag (see {@link ReportSummarySnapshot}).
   */
  record Snapshot(Integer crownVolume, String overrideTotalPop, String comments) {}

  /** Maps an {@code ILCR_REPORT_SUMMARY_S_VW} row. */
  class SnapshotRowMapper implements RowMapper<Snapshot> {
    @Override
    public Snapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
      int crownVolume = rs.getInt("CROWN_VOLUME");
      return new Snapshot(
          rs.wasNull() ? null : crownVolume, rs.getString("LOCATION"), rs.getString("COMMENTS"));
    }
  }
}
