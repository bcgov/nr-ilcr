package ca.bc.gov.nrs.ilcr.homecontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentSaveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Unit tests for {@link HomeContentService} — atomic save, FLD-001, the legacy transform, caps. */
@DisplayName("HomeContentService — Content Editing (UC-CNT-001)")
class HomeContentServiceTest {

  private static final String USER = "admin@idir";

  private final HomeContentRepository repository = mock(HomeContentRepository.class);
  private final HomeContentService service = new HomeContentService(repository);

  @Test
  @DisplayName("save: applies the legacy transform (tab/newline/&nbsp;) and updates all three roles")
  void save_transformsAndUpdatesAll() {
    when(repository.updateMessage(anyString(), anyString(), eq(USER))).thenReturn(1);

    service.saveAll(
        new HomeContentSaveRequest("<p>a\tb\nc&nbsp;d</p>", "<p>Aud</p>", "<p>Adm</p>"), USER);

    // \t -> two spaces, \n -> one space, &nbsp; -> one space (preserves word breaks TipTap emits).
    verify(repository).updateMessage("LICENSEE", "<p>a  b c d</p>", USER);
    verify(repository).updateMessage("AUDITOR", "<p>Aud</p>", USER);
    verify(repository).updateMessage("ADMIN", "<p>Adm</p>", USER);
  }

  @Test
  @DisplayName("save: a blank editor is rejected per-field (FLD-001), nothing saved")
  void save_blankEditorRejected() {
    FieldValuesRequiredException ex = assertThrows(FieldValuesRequiredException.class,
        () -> service.saveAll(new HomeContentSaveRequest("<p></p>", "<p>Aud</p>", "<p>Adm</p>"), USER));

    assertTrue(ex.getFieldLabels().contains("Licensee Welcome Message"));
    verify(repository, never()).updateMessage(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("save: all blank editors are reported together")
  void save_allBlankReportedTogether() {
    FieldValuesRequiredException ex = assertThrows(FieldValuesRequiredException.class,
        () -> service.saveAll(new HomeContentSaveRequest("&nbsp;", "  ", "<p></p>"), USER));

    assertEquals(3, ex.getFieldLabels().size());
    verify(repository, never()).updateMessage(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("save: a message over the 4000 cap is rejected 400")
  void save_tooLongRejected() {
    String huge = "<p>" + "x".repeat(4100) + "</p>";

    HomeContentException ex = assertThrows(HomeContentException.class,
        () -> service.saveAll(new HomeContentSaveRequest(huge, "<p>Aud</p>", "<p>Adm</p>"), USER));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  @DisplayName("save: a missing role row is a 404 (S10)")
  void save_missingRowIsNotFound() {
    when(repository.updateMessage(anyString(), anyString(), eq(USER))).thenReturn(0);

    HomeContentException ex = assertThrows(HomeContentException.class,
        () -> service.saveAll(new HomeContentSaveRequest("<p>L</p>", "<p>A</p>", "<p>Adm</p>"), USER));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  @DisplayName("readForRole: a missing row yields an entry with null text")
  void readForRole_missingYieldsNull() {
    when(repository.findByRole("LICENSEE")).thenReturn(java.util.Optional.empty());

    assertEquals(null, service.readForRole("LICENSEE").messageText());
  }
}
