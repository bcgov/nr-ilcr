package ca.bc.gov.nrs.ilcr.homecontent;

import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentEntry;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads and updates the role-keyed Home messages in the legacy {@code THE.ILCR_ROLE} table (Story 24.2
 * / UC-CNT-001): PK {@code ILCR_ROLE_NAME} ({@code LICENSEE}/{@code AUDITOR}/{@code ADMIN}), the
 * rich-text {@code MESSAGE_TEXT VARCHAR2(4000)}, and the NOT NULL audit quartet. Every value is a bound
 * named parameter; the acting admin + {@code SYSTIMESTAMP} are stamped on each update (AD-11).
 */
@Repository
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class HomeContentRepository {

  private static final RowMapper<HomeContentEntry> MAPPER =
      (rs, rowNum) -> new HomeContentEntry(rs.getString("ILCR_ROLE_NAME"), rs.getString("MESSAGE_TEXT"));

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
    return jdbc.query(
        "SELECT ILCR_ROLE_NAME, MESSAGE_TEXT FROM THE.ILCR_ROLE WHERE ILCR_ROLE_NAME = :role",
        new MapSqlParameterSource("role", role), MAPPER).stream().findFirst();
  }

  /** Update one role's message + audit columns; returns rows affected (0 when the role is absent). */
  public int updateMessage(String role, String messageText, String user) {
    return jdbc.update(
        "UPDATE THE.ILCR_ROLE SET MESSAGE_TEXT = :text, UPDATE_USERID = :user, "
            + "UPDATE_TIMESTAMP = SYSTIMESTAMP, REVISION_COUNT = REVISION_COUNT + 1 "
            + "WHERE ILCR_ROLE_NAME = :role",
        new MapSqlParameterSource()
            .addValue("text", messageText)
            .addValue("user", user)
            .addValue("role", role));
  }
}
