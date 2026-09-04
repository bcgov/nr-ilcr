package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * AC 1 — every schedule's Check Status is callable IN PROCESS from another package, and answers
 * with fully-resolved user-facing text, given only a mill and a year.
 *
 * <p>That is the premise Story 15.1's sweep rests on, and before Story 15.0 it was false three
 * ways: six services emitted {@code MessageInfo(key, null)} because the verbatim strings were
 * composed in PRIVATE controller methods; Schedule 10's service returned a package-private type, so
 * this class could not even name it; and Schedule 6 had no database-reading path at all.
 *
 * <p><strong>The package matters.</strong> This test lives outside every {@code schedule*} package
 * on purpose — that is what makes it a real check of reachability rather than of visibility inside
 * a package. If Schedule 10's response were still assembled in its controller, this file would not
 * compile.
 *
 * <p><strong>Why reflection.</strong> The twelve responses are two different DTO families (six
 * {@code outcome/messages}, six {@code requirementsMet/errors}) and Story 15.0 deliberately does
 * NOT unify them — 29.12 refused to and so does this story. Naming every message field by hand
 * would therefore assert twelve different shapes and would silently skip any field a future story
 * adds. Walking the record graph for message-shaped components asserts the property AC 1 states —
 * ALL text populated — over whatever shape each response actually has. See {@link #collectMessages}
 * for why "message-shaped" is structural rather than a check against {@link MessageInfo}.
 */
@DisplayName("Check Status — callable in process from another package (Story 15.0 AC1)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class CheckStatusInProcessIT extends AbstractOracleIT {

  @Autowired private Schedule1Service schedule1Service;
  @Autowired private Schedule2CheckStatusResolver schedule2;
  @Autowired private Schedule3Service schedule3Service;
  @Autowired private Schedule4CheckStatusResolver schedule4;
  @Autowired private Schedule5CheckStatusResolver schedule5;
  @Autowired private Schedule6CheckStatusResolver schedule6;
  @Autowired private Schedule7aService schedule7aService;
  @Autowired private Schedule7bService schedule7bService;
  @Autowired private Schedule8CheckStatusResolver schedule8;
  @Autowired private Schedule9Service schedule9Service;
  @Autowired private Schedule10CheckStatusResolver schedule10;
  @Autowired private Schedule11Service schedule11Service;

  /**
   * The twelve in-process entry points, each taking ONLY (millId, year). Anchors are the read-only
   * seeded fixtures each schedule's own check-status IT uses, chosen to reach the branch that
   * actually composes text rather than the single-banner pass.
   */
  private Map<String, Object> allTwelve() {
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("schedule1", schedule1Service.checkSchedule1Status(530, 2021));
    responses.put("schedule2", schedule2.checkStatus(621, 2021));
    responses.put("schedule3", schedule3Service.checkSchedule3Status(572, 2021));
    responses.put("schedule4", schedule4.checkStatus(514, 2021));
    responses.put("schedule5", schedule5.checkStatus(673, 2021));
    responses.put("schedule6", schedule6.checkStatusStored(726, 2020));
    responses.put("schedule7a", schedule7aService.checkStatus(514, 2021));
    responses.put("schedule7b", schedule7bService.checkStatus(514, 2021));
    responses.put("schedule8", schedule8.checkStatus(601, 2021));
    responses.put("schedule9", schedule9Service.checkStatus(703, 2021));
    responses.put("schedule10", schedule10.checkStatus(720, 2021));
    responses.put("schedule11", schedule11Service.checkStatus(617, 2021));
    return responses;
  }

  @Test
  @DisplayName("all twelve answer, and every message carries resolved text — never a bare key")
  void everyScheduleResolvesEveryMessage() {
    Map<String, Object> responses = allTwelve();
    assertEquals(12, responses.size(), "all twelve schedules must be represented");

    responses.forEach(
        (schedule, response) -> {
          assertNotNull(response, schedule + " returned no response");
          List<Message> messages = collectMessages(response);
          assertFalse(
              messages.isEmpty(),
              schedule + " produced no message at all — the anchor reaches no message branch");
          for (Message message : messages) {
            assertNotNull(message.text(), schedule + " left text null for key " + message.key());
            assertFalse(
                message.text().isBlank(), schedule + " left text blank for key " + message.key());
            assertFalse(
                message.key().equalsIgnoreCase(message.text()),
                schedule
                    + " resolved key '"
                    + message.key()
                    + "' to itself — that is an unresolved bundle key, not user-facing text");
          }
        });
  }

  @Test
  @DisplayName("AC 3: Schedule 10's response type is nameable and assembled outside its controller")
  void schedule10ResponseIsReachableFromAnotherPackage() {
    // The declared type is the point of this test: before 15.0 the only assembled form of this
    // response existed inside Schedule10Controller, and the service's return type could not be
    // named here at all. A `var` would have hidden exactly that.
    Schedule10CheckStatusResponse response = schedule10.checkStatus(720, 2021);

    assertEquals(Schedule10CheckStatusResponse.ISSUES, response.outcome());
    assertFalse(response.pages().isEmpty(), "the ISSUES branch must carry its pages");
    assertTrue(
        collectMessages(response).size() > 1,
        "mill 720 has several outstanding requirements, each with composed text");
  }

  // ===============================================================================================
  // Helpers
  // ===============================================================================================

  /** One user-facing message found in a response: its bundle key and its resolved text. */
  private record Message(String key, String text) {}

  /**
   * Every user-facing message reachable from a response, at any depth.
   *
   * <p><strong>Detected structurally, not by type.</strong> The first version of this walked for
   * {@link MessageInfo} alone and reported Schedule 5 as carrying no messages at all — because
   * Schedule 5's per-camp findings are a bespoke {@code CampCheckResult.CampCheckMessage} record
   * ({@code key, field, text}), not a {@code MessageInfo}. Its text was resolved correctly the
   * whole time; the test was blind to it. Matching on the COMPONENTS ({@code key} + {@code text})
   * instead of the class covers both shapes and any third one a later story introduces — which is
   * the point, since a message a sweep cannot see is exactly the defect AC 1 is about.
   */
  private static List<Message> collectMessages(Object root) {
    List<Message> found = new ArrayList<>();
    walk(root, found);
    return found;
  }

  private static void walk(Object node, List<Message> found) {
    if (node == null) {
      return;
    }
    if (node instanceof Collection<?> collection) {
      collection.forEach(element -> walk(element, found));
      return;
    }
    Class<?> type = node.getClass();
    // Only the project's own records are worth descending into; anything else (String, Number,
    // Boolean, enum) is a leaf. Guarding on the package also keeps this off the JDK's own graphs.
    if (!type.isRecord() || !type.getName().startsWith("ca.bc.gov.nrs.ilcr")) {
      return;
    }
    Map<String, Object> components = new LinkedHashMap<>();
    for (RecordComponent component : type.getRecordComponents()) {
      components.put(component.getName(), read(node, component));
    }
    if (components.get("key") instanceof String key && components.containsKey("text")) {
      // A message record. `text` may legitimately be null — that is precisely what this test is
      // here to catch — so it is recorded as-is and asserted by the caller, never skipped.
      found.add(new Message(key, (String) components.get("text")));
      return;
    }
    components.values().forEach(value -> walk(value, found));
  }

  private static Object read(Object owner, RecordComponent component) {
    try {
      return component.getAccessor().invoke(owner);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read " + component, e);
    }
  }
}
