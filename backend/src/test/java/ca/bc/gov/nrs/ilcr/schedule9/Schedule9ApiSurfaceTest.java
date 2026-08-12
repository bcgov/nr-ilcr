package ca.bc.gov.nrs.ilcr.schedule9;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The Schedule 9 API SURFACE — what the contract deliberately does NOT expose.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule 9 API surface — endpoint mappings (Story 9.2)")
class Schedule9ApiSurfaceTest {

  @Mock
  private MillContextService millContextService;

  @Mock
  private Schedule9Service schedule9Service;

  @Mock
  private SchedulePermissions permissions;

  @Mock
  private MessageSource messageSource;

  @Mock
  private Authentication authentication;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(
            new Schedule9Controller(
                millContextService, schedule9Service, permissions, messageSource))
        .build();
    lenient().when(millContextService.validateMillYearActive(any(), any()))
        .thenReturn(new MillYearContext(700L, 2021));
    lenient().when(authentication.getName()).thenReturn("SEED");

    Schedule9Response dummyResponse = new Schedule9Response(700L, 2021, "D", true, List.of(), null);
    lenient().when(schedule9Service.addRecord(anyLong(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(dummyResponse);
    lenient().when(schedule9Service.updateRecord(anyLong(), anyInt(), anyInt(), any(), anyBoolean(), any()))
        .thenReturn(dummyResponse);
  }

  @Test
  @DisplayName("POST /records/{id}/copy is NOT a route — copy is a client-side prefill, not a server call")
  void copyEndpointDoesNotExist() throws Exception {
    mockMvc.perform(post("/api/v1/schedule9/records/9101/copy")
            .param("millId", "700").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("…and the 404 above is meaningful: the neighbouring routes ARE mapped")
  void theRealRoutesAreMapped() throws Exception {
    mockMvc.perform(post("/api/v1/schedule9/records")
            .principal(authentication)
            .param("millId", "700").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk());
    mockMvc.perform(put("/api/v1/schedule9/records/9101")
            .principal(authentication)
            .param("millId", "700").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content("{\"revisionCount\": 0}"))
        .andExpect(status().isOk());
  }
}
