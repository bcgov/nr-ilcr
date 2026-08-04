package ca.bc.gov.nrs.ilcr.fam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.fam.dto.FamSubmitter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Unit test for {@link FamDirectoryController} — passthrough + graceful directory failure. */
@ExtendWith(MockitoExtension.class)
class FamDirectoryControllerTest {

  @Mock private FamDirectoryClient directoryClient;
  @InjectMocks private FamDirectoryController controller;

  private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken(
      "dev-ilcr_admin", null, List.of(new SimpleGrantedAuthority("ILCR_ADMIN")));

  @Test
  void search_returnsTheDirectoryCandidates() {
    List<FamSubmitter> found = List.of(
        new FamSubmitter("A1B2C3D4E5F607182930A4B5C6D7E8F0", "Meng, Catherine", "CMENG", "idir"));
    when(directoryClient.searchSubmitters("meng")).thenReturn(found);

    assertEquals(found, controller.searchSubmitters("meng", ADMIN).getBody());
  }

  @Test
  void search_directoryFailure_propagatesUnavailable() {
    when(directoryClient.searchSubmitters(null)).thenThrow(new FamDirectoryUnavailableException());

    assertThrows(FamDirectoryUnavailableException.class,
        () -> controller.searchSubmitters(null, ADMIN));
  }
}
