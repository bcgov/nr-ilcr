package ca.bc.gov.nrs.ilcr.messages;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The client-renderable message endpoint — the seam that lets a client render a bundle string it
 * would otherwise have to hardcode.
 *
 * <p>Schedule 5's copy warning is the case that forced it: legacy's {@code copyCamp()} makes no
 * database call ({@code Schedule5MB.java:270-275}), so the copy path has no response to carry
 * {@code sch5.copy.msg} and Story 7.2 nonetheless required 7.3 to "resolve it from the API rather
 * than hardcode it". Schedule 4 shipped the hardcode ({@code schedule4/index.tsx:45-46}); this
 * endpoint is what lets Schedule 5 not repeat it, and what Schedule 4 can converge onto later.
 *
 * <p>Resolution is ALLOWLISTED, not open. An unrestricted key lookup would turn the whole bundle
 * into a probe-able surface — including the check-status and validation keys whose text the server
 * composes and is solely responsible for (AD-8). Only keys a client legitimately renders with no
 * request behind them belong here.
 *
 * <p>Standalone MockMvc against the REAL bundle on purpose: the point of the endpoint is that the
 * text is the bundle's, so a mocked {@code MessageSource} would assert nothing. The full-context
 * ITs cannot run under surefire (CI runs no Oracle ITs — AR17).
 */
@DisplayName("Client-renderable message resolution (Story 7.3 open question 1)")
class MessageControllerTest {

  private static final String COPY_KEY = "sch5.copy.msg";

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");
    MessageSource bundle = messageSource;
    mockMvc = MockMvcBuilders.standaloneSetup(new MessageController(bundle))
        .setControllerAdvice(new GlobalExceptionHandler(bundle))
        .build();
  }

  @Test
  @DisplayName("resolves an allowlisted key, composing its MessageFormat argument verbatim")
  void resolvesAllowlistedKeyWithArgument() throws Exception {
    mockMvc
        .perform(get("/api/v1/messages").param("key", COPY_KEY).param("arg", "Cedar Flats Camp"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value(COPY_KEY))
        .andExpect(
            jsonPath("$.text")
                .value(
                    "To complete copy of Camp: Cedar Flats Camp, "
                        + "provide a new Camp Name and invoke save."));
  }

  @Test
  @DisplayName("leaves the placeholder unfilled when no argument is supplied (never invents one)")
  void resolvesAllowlistedKeyWithoutArgument() throws Exception {
    mockMvc
        .perform(get("/api/v1/messages").param("key", COPY_KEY))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.text")
                .value("To complete copy of Camp: {0}, provide a new Camp Name and invoke save."));
  }

  @Test
  @DisplayName("keeps a comma inside a single argument intact — no collection-split truncation")
  void keepsCommaInsideSingleArgument() throws Exception {
    // Camp names are free text: bound to a List<String>, Spring would split the single value on
    // its comma and resolve {0} to "Cedar" alone. The args are read off the request verbatim.
    mockMvc
        .perform(get("/api/v1/messages").param("key", COPY_KEY).param("arg", "Cedar, North Camp"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.text")
                .value(
                    "To complete copy of Camp: Cedar, North Camp, "
                        + "provide a new Camp Name and invoke save."));
  }

  @Test
  @DisplayName("404s a real bundle key that is NOT allowlisted — the bundle is not a public surface")
  void rejectsKeyOutsideTheAllowlist() throws Exception {
    // A genuine key (messages.properties:207) that the server composes and owns. It resolves fine
    // through MessageSource; the allowlist is the only thing standing between it and a caller.
    // The detail TEXT is pinned too: the handler falls back to echoing the key itself if the
    // bundle entry is renamed away — byte-for-byte the Schedule 6 defect this controller cites.
    mockMvc
        .perform(get("/api/v1/messages").param("key", "campAlreadyExists"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Message not found."));
  }

  @Test
  @DisplayName("404s an unknown key with the SAME shape — allowlist membership is not probe-able")
  void rejectsUnknownKey() throws Exception {
    mockMvc
        .perform(get("/api/v1/messages").param("key", "noSuchKeyAnywhere"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Message not found."));
  }

  @Test
  @DisplayName("404s an allowlisted key the bundle no longer holds — never echoes the key as text")
  void allowlistedKeyMissingFromBundleFails() throws Exception {
    // The rename/delete case the catch block exists for. A default message here would ship the
    // raw key to a licensee as if it were a sentence — exactly the Schedule 6 review finding
    // ("Road : 1 - TFL Number : missingRequiredFieldMsg"). The allowlist and the bundle drifting
    // apart has to be a failure.
    ResourceBundleMessageSource realBundle = new ResourceBundleMessageSource();
    realBundle.setBasename("messages");
    realBundle.setDefaultEncoding("UTF-8");
    MockMvc emptyBundleMvc =
        MockMvcBuilders.standaloneSetup(new MessageController(new StaticMessageSource()))
            .setControllerAdvice(new GlobalExceptionHandler(realBundle))
            .build();

    emptyBundleMvc
        .perform(get("/api/v1/messages").param("key", COPY_KEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Message not found."));
  }
}
