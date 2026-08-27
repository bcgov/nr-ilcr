package ca.bc.gov.nrs.ilcr.homecontent;

import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads and updates the role-keyed Home messages in the legacy {@code THE.ILCR_ROLE} table (Story
 * 24.2 / UC-CNT-001): PK {@code ILCR_ROLE_NAME} ({@code LICENSEE}/{@code AUDITOR}/{@code ADMIN}),
 * the rich-text {@code MESSAGE_TEXT VARCHAR2(4000)}, and the NOT NULL audit quartet. Every value is
 * a bound named parameter; the acting admin + {@code SYSTIMESTAMP} are stamped on each update
 * (AD-11).
 */
@Repository
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class HomeContentRepository {

  private static final String UPDATE_SQL =
      "UPDATE THE.ILCR_ROLE SET MESSAGE_TEXT = :text, UPDATE_USERID = :user, "
          + "UPDATE_TIMESTAMP = SYSTIMESTAMP, REVISION_COUNT = REVISION_COUNT + 1 "
          + "WHERE ILCR_ROLE_NAME = :role";

  private static final RowMapper<HomeContentEntry> MAPPER =
      (rs, rowNum) ->
          new HomeContentEntry(rs.getString("ILCR_ROLE_NAME"), rs.getString("MESSAGE_TEXT"));

  private final NamedParameterJdbcTemplate jdbc;

  public HomeContentRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** All role messages, role-ordered (the Content Editing page loads all three). */
  public List<HomeContentEntry> findAll() {
    return jdbc.query(
        "SELECT ILCR_ROLE_NAME, MESSAGE_TEXT FROM THE.ILCR_ROLE ORDER BY ILCR_ROLE_NAME", MAPPER);
  }

  /** One role's message (the Home render of the viewer's role), or empty when the row is absent. */
  public Optional<HomeContentEntry> findByRole(String role) {
    return jdbc
        .query(
            "SELECT ILCR_ROLE_NAME, MESSAGE_TEXT FROM THE.ILCR_ROLE WHERE ILCR_ROLE_NAME = :role",
            new MapSqlParameterSource("role", role),
            MAPPER)
        .stream()
        .findFirst();
  }

  /** Insert or update one role's message, preserving all NOT NULL audit columns. */
  public int upsertMessage(String role, String messageText, String user) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("text", messageText)
            .addValue("user", user)
            .addValue("role", role);
    try {
      return requireOne(
          jdbc.update(
              "MERGE INTO THE.ILCR_ROLE target "
                  + "USING (SELECT :role AS ILCR_ROLE_NAME, :text AS MESSAGE_TEXT, :user AS USERID "
                  + "FROM DUAL) source "
                  + "ON (target.ILCR_ROLE_NAME = source.ILCR_ROLE_NAME) "
                  + "WHEN MATCHED THEN UPDATE SET target.MESSAGE_TEXT = source.MESSAGE_TEXT, "
                  + "target.UPDATE_USERID = source.USERID, target.UPDATE_TIMESTAMP = SYSTIMESTAMP, "
                  + "target.REVISION_COUNT = target.REVISION_COUNT + 1 "
                  + "WHEN NOT MATCHED THEN INSERT (ILCR_ROLE_NAME, MESSAGE_TEXT, REVISION_COUNT, "
                  + "ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP) "
                  + "VALUES (source.ILCR_ROLE_NAME, source.MESSAGE_TEXT, 0, source.USERID, "
                  + "SYSTIMESTAMP, source.USERID, SYSTIMESTAMP)",
              params));
    } catch (DataIntegrityViolationException duplicateInsert) {
      // Two admins can both observe a missing role. If the MERGE loses that race on the PK,
      // update the row created by the winning transaction; rethrow if no row is available so a
      // genuine constraint failure is not reported as a successful save.
      int updated = jdbc.update(UPDATE_SQL, params);
      if (updated != 1) {
        throw duplicateInsert;
      }
      return updated;
    }
  }

  private static int requireOne(int affectedRows) {
    if (affectedRows != 1) {
      throw new IllegalStateException(
          "Expected exactly one Home content row to be inserted or updated, but affected "
              + affectedRows);
    }
    return affectedRows;
  }
}
