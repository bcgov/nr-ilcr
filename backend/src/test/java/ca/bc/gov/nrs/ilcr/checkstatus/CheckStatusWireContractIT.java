package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The twelve Check Status wire responses, pinned to checked-in golden files.
 *
 * <p>This is the regression gate for the Story 15.0 enabling refactor, whose whole premise is that
 * message composition MOVES without any response changing. The per-schedule {@code *CheckStatusIT}
 * classes each assert selected fields of their own schedule; none of them can see a change in a
 * field they do not name, in field ORDER, or in a branch their fixtures do not reach. A whole-body
 * comparison can, and it compares the same bytes for all twelve at once — which is the property AC
 * 2 actually claims.
 *
 * <p><strong>The goldens are the raw response body, verbatim.</strong> Not pretty-printed and not
 * re-serialized: the assertion is on the bytes the client receives, so JSON field order (which
 * follows record component order) is part of what is pinned.
 *
 * <p><strong>The comparison is on raw bytes, not on a decoded String.</strong> Decoding first would
 * let a change in the response's character encoding through — the same text re-encoded is a
 * different response to the client, and "byte-identical" is the claim this class makes. The decoded
 * text is materialised only to render a readable diff once a mismatch is already established.
 *
 * <p><strong>Regenerating.</strong> Run with {@code -Dilcr.checkstatus.golden.regenerate=true} to
 * rewrite the files from the current behaviour. That run then FAILS on purpose: a regenerated
 * golden is a claim that the wire contract changed deliberately, so it has to be reviewed as a diff
 * and the suite re-run without the flag. A test that can be made green by regenerating it is not a
 * gate.
 *
 * <p><strong>Anchor choice is deliberate.</strong> Every (mill, year) below was checked against
 * every write-path {@code *IT} in the suite and is touched by read-only tests only — the write
 * fixtures live on separate mills (Schedule 5 writes 670-672/675/676, Schedule 8 writes 580-595,
 * Schedule 10 writes 718/719, Schedule 11 writes 614-616, Schedule 2 writes 622-625, and mills
 * 515/517 are written by several schedules). Without that check a golden would silently depend on
 * class execution order. Schedule 6 is exempt: its verdict is a pure function of the posted payload
 * (the stored-rows source was retired by Story 8.2 Task 8), so its stored state cannot move its
 * response.
 *
 * <p><strong>One deliberate omission.</strong> Schedule 2's ISSUES branch has no anchor here: its
 * only fixture for the unsaved state is mill 515, which Schedule 1/2/3's write ITs all write to.
 * The branch is a single labelled {@code missingRequiredFieldMsg}, already pinned byte-exact by
 * {@code Schedule2CheckStatusIT.unsavedSchedule_returnsIssues_notFoundSuppressed} and by {@code
 * Schedule2CheckStatusServiceTest}.
 */
@DisplayName("Check Status — the twelve wire responses, byte-for-byte (Story 15.0 AC2)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class CheckStatusWireContractIT extends AbstractOracleIT {

  /** Classpath root of the golden bodies; also the source path the regenerate branch writes to. */
  private static final String GOLDEN_CLASSPATH = "/checkstatus-golden/";

  private static final Path GOLDEN_SOURCE_DIR =
      Path.of("src", "test", "resources", "checkstatus-golden");

  private static final String REGENERATE_FLAG = "ilcr.checkstatus.golden.regenerate";

  /**
   * One pinned call: a name that becomes the golden filename, the endpoint, the mill/year, and the
   * request body for the one schedule that takes one.
   */
  private record Anchor(String name, String path, String millId, String year, String body) {

    Anchor(String name, String path, String millId, String year) {
      this(name, path, millId, year, null);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * Every schedule appears at least once, and the schedules whose composed lines are richest appear
   * on both branches. Schedule 6's third anchor is the highest-value row in the table: one payload
   * reaches all four field segments (including the legacy cost mislabel), the row ordinals, the D2
   * zero-cost-is-MET quirk, and the per-record met banner in a single response.
   */
  static List<Anchor> anchors() {
    return List.of(
        new Anchor("schedule1-528-2021", "/api/v1/schedule1/check-status", "528", "2021"),
        new Anchor("schedule1-530-2021", "/api/v1/schedule1/check-status", "530", "2021"),
        new Anchor("schedule2-621-2021", "/api/v1/schedule2/check-status", "621", "2021"),
        new Anchor("schedule3-572-2021", "/api/v1/schedule3/check-status", "572", "2021"),
        new Anchor("schedule4-560-2021", "/api/v1/schedule4/check-status", "560", "2021"),
        new Anchor("schedule4-514-2021", "/api/v1/schedule4/check-status", "514", "2021"),
        new Anchor("schedule5-673-2021", "/api/v1/schedule5/check-status", "673", "2021"),
        new Anchor("schedule5-692-2016", "/api/v1/schedule5/check-status", "692", "2016"),
        new Anchor(
            "schedule6-726-2020-issues",
            "/api/v1/schedule6/check-status",
            "726",
            "2020",
            """
            {"generalComments":null,
             "records":[{"areaType":"Y9","supplyBlock":"Y9A","volume":10,"cost":null,
                         "comments":null}]}
            """),
        new Anchor(
            "schedule6-662-2021-met",
            "/api/v1/schedule6/check-status",
            "662",
            "2021",
            """
            {"generalComments":null,
             "records":[{"areaType":"01","supplyBlock":"01B","cost":1}]}
            """),
        new Anchor(
            "schedule6-726-2020-all-segments",
            "/api/v1/schedule6/check-status",
            "726",
            "2020",
            """
            {"generalComments":null,
             "records":[{"areaType":null,"tflNumber":null,"supplyBlock":null,"cost":null},
                        {"areaType":"TFL","tflNumber":null,"cost":5},
                        {"areaType":"01","supplyBlock":"01B","cost":0}]}
            """),
        new Anchor("schedule7a-514-2021", "/api/v1/schedule7a/check-status", "514", "2021"),
        new Anchor("schedule7b-514-2021", "/api/v1/schedule7b/check-status", "514", "2021"),
        new Anchor("schedule8-600-2021", "/api/v1/schedule8/check-status", "600", "2021"),
        new Anchor("schedule8-601-2021", "/api/v1/schedule8/check-status", "601", "2021"),
        new Anchor("schedule8-602-2021", "/api/v1/schedule8/check-status", "602", "2021"),
        new Anchor(
            "schedule8-603-2021-page-8976",
            "/api/v1/schedule8/pages/8976/check-status",
            "603",
            "2021"),
        new Anchor("schedule9-703-2021", "/api/v1/schedule9/check-status", "703", "2021"),
        new Anchor("schedule9-704-2021", "/api/v1/schedule9/check-status", "704", "2021"),
        new Anchor("schedule10-720-2021", "/api/v1/schedule10/check-status", "720", "2021"),
        new Anchor("schedule10-715-2021", "/api/v1/schedule10/check-status", "715", "2021"),
        new Anchor("schedule11-617-2021", "/api/v1/schedule11/check-status", "617", "2021"),
        new Anchor("schedule11-613-2021", "/api/v1/schedule11/check-status", "613", "2021"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("anchors")
  @DisplayName("the response body is byte-identical to its golden")
  void wireResponseIsUnchanged(Anchor anchor) throws Exception {
    byte[] actual = call(anchor);

    if (Boolean.getBoolean(REGENERATE_FLAG)) {
      Files.createDirectories(GOLDEN_SOURCE_DIR);
      // Written as the bytes that came off the wire, not re-encoded from a String: the golden has
      // to be able to hold a response this test would then reject.
      Files.write(GOLDEN_SOURCE_DIR.resolve(anchor.name() + ".json"), actual);
      fail(
          "Golden '"
              + anchor.name()
              + "' was REGENERATED. Review the diff, then re-run without -D"
              + REGENERATE_FLAG
              + " — a regenerated golden asserts nothing.");
    }

    byte[] expected = golden(anchor.name());
    if (!Arrays.equals(expected, actual)) {
      // The bytes are the gate; this comparison exists only to print the difference as text, which
      // is the form a reviewer can actually read.
      assertEquals(
          new String(expected, StandardCharsets.UTF_8),
          new String(actual, StandardCharsets.UTF_8),
          "wire response changed for " + anchor.name());
      // Reached only when the decoded text is identical and the bytes are not — i.e. the response
      // ENCODING moved. That is exactly the change a String comparison would have passed.
      fail(
          "wire response bytes changed for "
              + anchor.name()
              + " while the decoded UTF-8 text did not — the response encoding moved.");
    }
  }

  private byte[] call(Anchor anchor) throws Exception {
    MockHttpServletRequestBuilder request =
        post(anchor.path())
            .param("millId", anchor.millId())
            .param("year", anchor.year())
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON);
    if (anchor.body() != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(anchor.body());
    }
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  /**
   * Read the golden from the CLASSPATH, never from a working-directory-relative path: a test whose
   * assertion depends on the process working directory passes or fails for reasons that have
   * nothing to do with the code (Story 30.2 code review). Only the regenerate branch above touches
   * the filesystem, where a wrong directory is immediately obvious to the developer who asked for
   * it.
   */
  private static byte[] golden(String name) throws Exception {
    try (InputStream in =
        CheckStatusWireContractIT.class.getResourceAsStream(GOLDEN_CLASSPATH + name + ".json")) {
      if (in == null) {
        return fail(
            "Missing golden '"
                + name
                + ".json'. Create it with: mvn -P integration-test verify -D"
                + REGENERATE_FLAG
                + "=true");
      }
      return in.readAllBytes();
    }
  }
}
