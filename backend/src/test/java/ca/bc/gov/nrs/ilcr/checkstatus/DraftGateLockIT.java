package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aRepository;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bRepository;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance test — Story 15.3 Task 10.3 (D8, ruled a′): every write-path editability gate that
 * read the track status WITHOUT a row lock at {@code d06161c1} now reads it {@code FOR UPDATE}
 * inside its write transaction, so a save and a status transition on one mill/year serialize
 * instead of racing.
 *
 * <p>One case per converted gate, Schedule 11 included. Each case holds the mill/year status row
 * under {@code SELECT … FOR UPDATE} from a second connection (standing in for an in-flight submit),
 * fires the schedule's DELETE for an id that does not exist, and asserts three things in order: the
 * request BLOCKS while the lock is held; once the holder flips the row to Submitted and commits,
 * the request reads {@code S} and answers the gate's own 409 ({@code scheduleNotEditableErrorMsg});
 * and nothing was written. The inverse direction is proved too: a repository spy pauses each gate
 * immediately after its real {@code FOR UPDATE} returns, and a second connection's {@code NOWAIT}
 * read must fail with Oracle error 54 until the surrounding service transaction is released. A
 * DELETE is the right probe because it carries no body to validate, and an unknown id makes the
 * gate the first thing the service does — the unblocked control proves that: on the same Draft mill
 * with no lock held, the same request reaches the gate, reads {@code D}, passes, and answers the
 * schedule's own verdict for the missing id (404, or 200 where the delete is an idempotent no-op) —
 * never the gate's 409.
 *
 * <p>Schedule 1's {@code applyCrownTimberVolume} gate is converted too but reachable only through
 * the Schedule 3 save, which already held the lock at {@code d06161c1}; its unit test pins the
 * locked repository method directly. The gates already locked before this story — Schedule 2,
 * Schedule 9, Schedule 5 (camps and sub-pages both go through its locked {@code requireEditable}),
 * and the Schedule 1/3 main save and delete — are not repeated here.
 *
 * <p>Owns mill 763 ({@code R__55}: status row {@code D}/{@code D}, category rows, and two EMPTY
 * category 1 and 3 summaries — the Schedule 1/3 sub-resource controllers guard on a summary
 * existing before their service's gate runs, so without them those routes 404 ahead of the lock).
 * Security OFF; the mock principal is a SUBMITTER, for whom {@code S} is read-only.
 */
@DisplayName("D8 (a'): every converted Draft gate blocks behind the status-row lock, then refuses")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class DraftGateLockIT extends AbstractOracleIT {

  private static final long MILL = 763L;
  private static final int YEAR = 2021;
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String NOT_EDITABLE =
      "This schedule cannot be edited in its current status.";

  /**
   * Long enough that an unblocked request has certainly finished; short enough to keep the suite
   * quick.
   */
  private static final long BLOCKED_FOR_MS = 1500;

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private Schedule1Repository schedule1Repository;
  @MockitoSpyBean private Schedule3Repository schedule3Repository;
  @MockitoSpyBean private Schedule4Repository schedule4Repository;
  @MockitoSpyBean private Schedule6Repository schedule6Repository;
  @MockitoSpyBean private Schedule7aRepository schedule7aRepository;
  @MockitoSpyBean private Schedule7bRepository schedule7bRepository;
  @MockitoSpyBean private Schedule8Repository schedule8Repository;
  @MockitoSpyBean private Schedule10Repository schedule10Repository;
  @MockitoSpyBean private MillContextRepository millContextRepository;
  private final ObjectMapper json = new ObjectMapper();

  enum GateRepository {
    SCHEDULE_1,
    SCHEDULE_3,
    SCHEDULE_4,
    SCHEDULE_6,
    SCHEDULE_7A,
    SCHEDULE_7B,
    SCHEDULE_8,
    SCHEDULE_10,
    SCHEDULE_11
  }

  enum Track {
    SCHEDULES_1_TO_10("ILCR_MILL_REPORT_STATUS_CODE"),
    SCHEDULE_11("MILL_SILVICULTUR_STATUS_CODE");

    private final String column;

    Track(String column) {
      this.column = column;
    }
  }

  /** One converted gate = one DELETE of a nonexistent id, mill/year as request params. */
  record Gate(String name, String path, GateRepository repository, Track track) {
    MockHttpServletRequestBuilder request() {
      return delete(path)
          .param("millId", String.valueOf(MILL))
          .param("year", String.valueOf(YEAR))
          .accept(MediaType.APPLICATION_JSON);
    }
  }

  static Stream<Arguments> convertedGates() {
    return Stream.of(
            new Gate(
                "Schedule 1 Other Costs (requireEditableSummary)",
                "/api/v1/schedule1/other-costs/999999",
                GateRepository.SCHEDULE_1,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 3 Other Acceptable Costs (requireEditableSummary)",
                "/api/v1/schedule3/other-acceptable-costs/999999",
                GateRepository.SCHEDULE_3,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 3 Included Unacceptable Costs (requireEditableSummary)",
                "/api/v1/schedule3/included-unacceptable-costs/999999",
                GateRepository.SCHEDULE_3,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 4 (requireEditable)",
                "/api/v1/schedule4/locations/999999/rows/999999",
                GateRepository.SCHEDULE_4,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 6 (requireEditable)",
                "/api/v1/schedule6/records/999999",
                GateRepository.SCHEDULE_6,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 7A (requireEditable)",
                "/api/v1/schedule7a/bridges/999999",
                GateRepository.SCHEDULE_7A,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 7B (requireEditable)",
                "/api/v1/schedule7b/culverts/999999",
                GateRepository.SCHEDULE_7B,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 8 page (requireEditable)",
                "/api/v1/schedule8/pages/999999",
                GateRepository.SCHEDULE_8,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 8 sample (requireEditable)",
                "/api/v1/schedule8/pages/999999/samples/999999",
                GateRepository.SCHEDULE_8,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 8 rate (requireEditable)",
                "/api/v1/schedule8/samples/999999/rates/999999",
                GateRepository.SCHEDULE_8,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 10 page (requireEditable)",
                "/api/v1/schedule10/pages/999999",
                GateRepository.SCHEDULE_10,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 10 road detail (requireEditable)",
                "/api/v1/schedule10/pages/999999/road-details/999999",
                GateRepository.SCHEDULE_10,
                Track.SCHEDULES_1_TO_10),
            new Gate(
                "Schedule 11 (requireSilvicultureEditable)",
                "/api/v1/schedule11/locations/999999",
                GateRepository.SCHEDULE_11,
                Track.SCHEDULE_11))
        .map(gate -> Arguments.of(gate.name(), gate));
  }

  @AfterEach
  void restoreDraft() {
    jdbc.update(
        "UPDATE THE.ILCR_MILL_REPORT_STATUS SET ILCR_MILL_REPORT_STATUS_CODE = 'D',"
            + " MILL_SILVICULTUR_STATUS_CODE = 'D' WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ?",
        MILL,
        YEAR);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("convertedGates")
  @DisplayName("blocks while the status row is held, then reads S and refuses with no write")
  void gateBlocksBehindTheLockThenRefuses(String name, Gate gate) throws Exception {
    String before = footprint();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    GateProbe probe = installGateProbe(gate, false);
    try (Connection holder = dataSource.getConnection()) {
      holder.setAutoCommit(false);
      lockStatusRow(holder);

      Future<MvcResult> save = pool.submit(() -> mockMvc.perform(gate.request()).andReturn());
      probe.awaitEntry(name);
      // Blocked: the gate's FOR UPDATE waits on the row the holder owns.
      assertThatThrownBy(() -> save.get(BLOCKED_FOR_MS, TimeUnit.MILLISECONDS))
          .as("%s must block while the status row is locked", name)
          .isInstanceOf(TimeoutException.class);

      // The holder is the in-flight transition: it moves ONLY the track this gate owns. Leaving
      // the other track at Draft proves each gate reads the correct independent status column.
      flipOwnedTrack(holder, gate.track());
      holder.commit();

      // Released: the gate reads S — never the D it would have seen unlocked — and refuses.
      MvcResult result = save.get(30, TimeUnit.SECONDS);
      assertThat(result.getResponse().getStatus()).as(name).isEqualTo(409);
      assertThat(result.getResponse().getContentType()).contains(PROBLEM_JSON);
      assertThat(json.readTree(result.getResponse().getContentAsString()).path("detail").asText())
          .isEqualTo(NOT_EDITABLE);
      assertThat(trackCodes())
          .as("%s must transition only its own track", name)
          .isEqualTo(
              gate.track() == Track.SCHEDULES_1_TO_10
                  ? new StoredTracks("S", "D")
                  : new StoredTracks("D", "S"));
    } finally {
      pool.shutdownNow();
    }
    assertThat(footprint()).as("%s must write nothing when refused", name).isEqualTo(before);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("convertedGates")
  @DisplayName("control: with no lock held the same request reaches the gate, reads D and passes")
  void unblockedControl_reachesTheGateAndPasses(String name, Gate gate) throws Exception {
    MvcResult result = mockMvc.perform(gate.request()).andReturn();

    // Past the gate: the schedule's own answer for the unknown id — a 404 where the delete is
    // strict, a 200 where it is an idempotent no-op (Schedules 4 and 8, the #292 rule) — never the
    // gate's 409, and never a block.
    assertThat(result.getResponse().getStatus()).as(name).isIn(200, 404);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("convertedGates")
  @DisplayName("the gate's status-row lock remains held by the surrounding write transaction")
  void gateKeepsTheLockUntilTheWriteTransactionEnds(String name, Gate gate) throws Exception {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    GateProbe probe = installGateProbe(gate, true);
    Future<MvcResult> save = pool.submit(() -> mockMvc.perform(gate.request()).andReturn());
    try {
      // The spy pauses immediately after the real FOR UPDATE returns. At this point the service
      // transaction must still own the status-row lock; an autocommit/released lock would let the
      // contender below acquire it.
      probe.awaitEntry(name);
      assertStatusRowBusy(name);
    } finally {
      probe.releaseGate();
    }

    try {
      assertThat(save.get(30, TimeUnit.SECONDS).getResponse().getStatus()).as(name).isIn(200, 404);
    } finally {
      pool.shutdownNow();
    }
  }

  private GateProbe installGateProbe(Gate gate, boolean pauseAfterRead) {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Answer<Object> answer =
        invocation -> {
          if (!pauseAfterRead) {
            entered.countDown();
          }
          Object result = executeLockedStatusRead(gate);
          if (pauseAfterRead) {
            entered.countDown();
            if (!release.await(30, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Timed out waiting to release the gate probe");
            }
          }
          return result;
        };

    switch (gate.repository()) {
      case SCHEDULE_1 ->
          doAnswer(answer).when(schedule1Repository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_3 ->
          doAnswer(answer).when(schedule3Repository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_4 ->
          doAnswer(answer).when(schedule4Repository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_6 ->
          doAnswer(answer).when(schedule6Repository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_7A ->
          doAnswer(answer).when(schedule7aRepository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_7B ->
          doAnswer(answer).when(schedule7bRepository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_8 ->
          doAnswer(answer).when(schedule8Repository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_10 ->
          doAnswer(answer).when(schedule10Repository).findTrackStatusForUpdate(MILL, YEAR);
      case SCHEDULE_11 ->
          doAnswer(answer).when(millContextRepository).findTrackStatusCodesForUpdate(MILL, YEAR);
    }
    return new GateProbe(entered, release);
  }

  private Object executeLockedStatusRead(Gate gate) {
    if (gate.repository() == GateRepository.SCHEDULE_11) {
      List<MillContextRepository.TrackCodes> rows =
          jdbc.query(
              "SELECT ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE"
                  + " FROM THE.ILCR_MILL_REPORT_STATUS"
                  + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ? FOR UPDATE",
              (statement) -> {
                statement.setLong(1, MILL);
                statement.setInt(2, YEAR);
              },
              (result, row) ->
                  new MillContextRepository.TrackCodes(result.getString(1), result.getString(2)));
      return rows.stream().findFirst();
    }

    List<String> rows =
        jdbc.query(
            "SELECT ILCR_MILL_REPORT_STATUS_CODE FROM THE.ILCR_MILL_REPORT_STATUS"
                + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ? FOR UPDATE",
            (statement) -> {
              statement.setLong(1, MILL);
              statement.setInt(2, YEAR);
            },
            (result, row) -> result.getString(1));
    return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.getFirst());
  }

  private void assertStatusRowBusy(String name) throws Exception {
    try (Connection contender = dataSource.getConnection()) {
      contender.setAutoCommit(false);
      assertThatThrownBy(() -> lockStatusRowNoWait(contender))
          .as("%s must retain the status-row lock until its service transaction ends", name)
          .isInstanceOf(SQLException.class)
          .satisfies(error -> assertThat(((SQLException) error).getErrorCode()).isEqualTo(54));
      contender.rollback();
    }
  }

  private static void lockStatusRowNoWait(Connection connection) throws SQLException {
    try (PreparedStatement lock =
        connection.prepareStatement(
            "SELECT ILCR_MILL_REPORT_STATUS_CODE FROM THE.ILCR_MILL_REPORT_STATUS"
                + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ? FOR UPDATE NOWAIT")) {
      lock.setLong(1, MILL);
      lock.setInt(2, YEAR);
      try (ResultSet rs = lock.executeQuery()) {
        assertThat(rs.next()).isTrue();
      }
    }
  }

  private static void flipOwnedTrack(Connection connection, Track track) throws SQLException {
    try (PreparedStatement flip =
        connection.prepareStatement(
            "UPDATE THE.ILCR_MILL_REPORT_STATUS SET "
                + track.column
                + " = 'S' WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ?")) {
      flip.setLong(1, MILL);
      flip.setInt(2, YEAR);
      assertThat(flip.executeUpdate()).isEqualTo(1);
    }
  }

  private StoredTracks trackCodes() {
    return jdbc.queryForObject(
        "SELECT ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE"
            + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ?",
        (result, row) -> new StoredTracks(result.getString(1), result.getString(2)),
        MILL,
        YEAR);
  }

  private static void lockStatusRow(Connection connection) throws Exception {
    try (PreparedStatement lock =
        connection.prepareStatement(
            "SELECT ILCR_MILL_REPORT_STATUS_CODE FROM THE.ILCR_MILL_REPORT_STATUS"
                + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = ? FOR UPDATE")) {
      lock.setLong(1, MILL);
      lock.setInt(2, YEAR);
      try (ResultSet rs = lock.executeQuery()) {
        assertThat(rs.next()).as("mill 763/2021 must have a status row to lock").isTrue();
        assertThat(rs.getString(1)).isEqualTo("D");
      }
    }
  }

  private record GateProbe(CountDownLatch entered, CountDownLatch release) {
    void awaitEntry(String name) throws InterruptedException {
      assertThat(entered.await(30, TimeUnit.SECONDS))
          .as("%s must reach its locked repository call", name)
          .isTrue();
    }

    void releaseGate() {
      release.countDown();
    }
  }

  private record StoredTracks(String schedules1To10, String schedule11) {}

  /** Row counts of every schedule table — a refused save inserts, updates or deletes nothing. */
  private String footprint() {
    StringBuilder f = new StringBuilder();
    for (String table :
        List.of(
            "ILCR_REPORT_SUMMARY",
            "ILCR_COST_REPORT_DETAIL",
            "TRANSPORTATION_REPORT",
            "ROAD_MAINTENANCE_REPORT",
            "BRIDGE_REPORT",
            "CULVERT_REPORT",
            "TREE_TO_TRUCK_REPORT",
            "TREE_TO_TRUCK_DETAIL_REPORT",
            "TREE_TO_TRUCK_RATE_DETAIL",
            "ROAD_CONSTRUCTION_REPRT",
            "ROAD_CONSTRUCTION_REPRT_DTL",
            "BASIC_SILVICULTURE_REPORT")) {
      f.append(table)
          .append('=')
          .append(
              jdbc.queryForObject(
                  "SELECT COUNT(*) || '/' || NVL(SUM(REVISION_COUNT), 0) FROM THE." + table,
                  String.class))
          .append(';');
    }
    return f.toString();
  }
}
