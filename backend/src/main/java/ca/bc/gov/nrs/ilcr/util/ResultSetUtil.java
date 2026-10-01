package ca.bc.gov.nrs.ilcr.util;

import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Null-preserving {@link ResultSet} reads for the hand-written {@code RowMapper}s.
 *
 * <p>JDBC's primitive getters cannot express SQL {@code NULL}: {@link ResultSet#getInt} answers
 * {@code 0} for it, and only a {@link ResultSet#wasNull()} check immediately afterwards tells the
 * two apart. Every mapper reading a nullable numeric column needs that same two-line dance, and
 * three had grown their own private copy of it under two different names — {@code
 * Schedule8Repository}, {@code Schedule10Repository} and the shared cost-detail snapshot mapper.
 *
 * <p>Reading {@code 0} where the column was {@code NULL} is silent and plausible — a blank cost
 * rendering as {@code $0} rather than as blank — so the check is not optional, and one shared,
 * tested implementation is worth more than three identical private ones.
 *
 * <p>Not to be confused with the {@code nullableInt} in {@code Schedule1Repository} and {@code
 * Schedule3Repository}: those read the column as a {@link java.math.BigDecimal} and narrow it,
 * which is a different strategy rather than a fourth copy of this one, so they stay where they are.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ResultSetUtil {

  /** The column as an {@link Integer}, or null when the column held SQL {@code NULL}. */
  public static Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  /** The column as a {@link Long}, or null when the column held SQL {@code NULL}. */
  public static Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }
}
