package ca.bc.gov.nrs.ilcr.schedule5;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageDocument;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The Schedule 5 API SURFACE — what the contract deliberately does NOT expose (AC4, deviation (B)).
 *
 * <p>Legacy's {@code copyCamp()} makes no database call at all ({@code Schedule5MB.java:270-275}):
 * it clones in memory, blanks the name and PK, and warns. Copy is therefore Story 7.3's client-side
 * prefill, a renamed copy is an ordinary {@code POST /camps}, and an unrenamed one is the BR-02 409
 * — so a copy endpoint appearing later would be a contract change, not a completion. This test is
 * what fails when one appears.
 *
 * <p>Standalone MockMvc on purpose: route absence is a property of the controller's mappings alone,
 * and the full-context ITs cannot run under surefire (CI runs no Oracle ITs — AR17).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule 5 API surface — no copy endpoint exists (AC4, deviation (B))")
class Schedule5ApiSurfaceTest {

  @Mock private MillContextService millContextService;

  @Mock private Schedule5Service schedule5Service;

  @Mock private SchedulePermissions permissions;

  @Mock private MessageSource messageSource;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new Schedule5Controller(
                    millContextService,
                    schedule5Service,
                    permissions,
                    messageSource,
                    new Schedule5CheckStatusResolver(schedule5Service, messageSource)))
            .build();
  }

  @Test
  @DisplayName("POST /camps/{id}/copy is NOT a route — copy is 7.3's prefill, not a server call")
  void copyEndpointDoesNotExist() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/schedule5/camps/8205/copy")
                .param("millId", "670")
                .param("year", "2022")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("…and the 404 above is meaningful: the neighbouring routes ARE mapped")
  void theRealRoutesAreMapped() throws Exception {
    // An empty body fails @Valid with 400 — anything but 404/405 proves the mapping exists, so the
    // copy probe's 404 is route absence rather than a mis-wired standalone setup.
    mockMvc
        .perform(
            post("/api/v1/schedule5/camps")
                .param("millId", "670")
                .param("year", "2022")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/api/v1/schedule5/camps/8205")
                .param("millId", "670")
                .param("year", "2022")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  // -----------------------------------------------------------------------------------------
  // Story 7.4 — the six sub-page routes exist, and the shapes that were considered do NOT.
  // -----------------------------------------------------------------------------------------

  @Test
  @DisplayName("all six sub-page routes are mapped")
  void subPageRoutesAreMapped() throws Exception {
    // The guard is stubbed so the handler can actually run: an unstubbed mock returns null and the
    // controller NPEs, which would be a 500 and would prove nothing about the MAPPING either way.
    when(millContextService.validateMillYearActive(any(), any()))
        .thenReturn(new MillYearContext(690L, 2016));
    // The write handlers chain .withMessage() onto whatever the service returns, so the service has
    // to hand back a real document — a null would NPE into a 500 and say nothing about the mapping.
    SubPageDocument doc =
        new SubPageDocument(8700, "Reconcile Camp", null, true, List.of(), null, null);
    when(schedule5Service.saveSubPage(
            anyLong(), anyInt(), anyInt(), any(), any(), anyBoolean(), any()))
        .thenReturn(doc);
    when(schedule5Service.deleteSubPageRow(
            anyLong(), anyInt(), anyInt(), any(), anyInt(), anyBoolean()))
        .thenReturn(doc);
    // A principal is supplied because the save path reads authentication.getName() for the audit
    // columns; Spring resolves an Authentication parameter from request.getUserPrincipal(), which
    // standalone MockMvc leaves null unless it is set here.
    Authentication auth = new UsernamePasswordAuthenticationToken("tester", "n/a", List.of());
    for (String page : new String[] {"other-camp-expenses", "other-access-expenses"}) {
      mockMvc
          .perform(
              get("/api/v1/schedule5/camps/8700/" + page)
                  .principal(auth)
                  .param("millId", "690")
                  .param("year", "2016"))
          .andExpect(status().isOk());
      mockMvc
          .perform(
              put("/api/v1/schedule5/camps/8700/" + page)
                  .principal(auth)
                  .param("millId", "690")
                  .param("year", "2016")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"rows\":[]}"))
          .andExpect(status().isOk());
      mockMvc
          .perform(
              delete("/api/v1/schedule5/camps/8700/" + page + "/8722")
                  .principal(auth)
                  .param("millId", "690")
                  .param("year", "2016"))
          .andExpect(status().isOk());
    }
  }

  @Test
  @DisplayName("there is NO PATCH and no per-row POST — the batch PUT is the only writer")
  void noPerRowWriteEndpoints() throws Exception {
    // Open question 3 was settled on the Schedule 3 batch reconcile (Scho, 2026-08-12): legacy's
    // Save persists the whole list and its Add is "append then save the list", so one call
    // reproduces both. A per-row POST appearing later would be a contract change, not a completion.
    //
    // Both probes expect 405 rather than 404: the PATHS exist (PUT and DELETE are mapped on them),
    // so Spring resolves the URL and rejects the VERB. A 404 here would actually mean the path
    // itself had gone missing, which is a different failure and is covered by the mapping test
    // above.
    mockMvc
        .perform(
            post("/api/v1/schedule5/camps/8700/other-camp-expenses")
                .param("millId", "690")
                .param("year", "2016")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(
            patch("/api/v1/schedule5/camps/8700/other-camp-expenses/8722")
                .param("millId", "690")
                .param("year", "2016")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  @DisplayName("the sub-resource nests exactly ONE level — no deeper route exists on either page")
  void noDeeperNesting() throws Exception {
    // Both twins probed: asserting only the camp page would leave half the negative-space contract
    // unpinned (review patch, 2026-08-12).
    for (String page : new String[] {"other-camp-expenses", "other-access-expenses"}) {
      mockMvc
          .perform(
              get("/api/v1/schedule5/camps/8700/" + page + "/8722/details")
                  .param("millId", "690")
                  .param("year", "2016"))
          .andExpect(status().isNotFound());
    }
  }
}
