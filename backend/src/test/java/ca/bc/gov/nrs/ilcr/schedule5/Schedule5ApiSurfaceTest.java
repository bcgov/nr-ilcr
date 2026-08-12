package ca.bc.gov.nrs.ilcr.schedule5;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
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

  @Mock
  private MillContextService millContextService;

  @Mock
  private Schedule5Service schedule5Service;

  @Mock
  private SchedulePermissions permissions;

  @Mock
  private MessageSource messageSource;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(
            new Schedule5Controller(
                millContextService, schedule5Service, permissions, messageSource))
        .build();
  }

  @Test
  @DisplayName("POST /camps/{id}/copy is NOT a route — copy is 7.3's prefill, not a server call")
  void copyEndpointDoesNotExist() throws Exception {
    mockMvc.perform(post("/api/v1/schedule5/camps/8205/copy")
            .param("millId", "670").param("year", "2022")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("…and the 404 above is meaningful: the neighbouring routes ARE mapped")
  void theRealRoutesAreMapped() throws Exception {
    // An empty body fails @Valid with 400 — anything but 404/405 proves the mapping exists, so the
    // copy probe's 404 is route absence rather than a mis-wired standalone setup.
    mockMvc.perform(post("/api/v1/schedule5/camps")
            .param("millId", "670").param("year", "2022")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(put("/api/v1/schedule5/camps/8205")
            .param("millId", "670").param("year", "2022")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}
