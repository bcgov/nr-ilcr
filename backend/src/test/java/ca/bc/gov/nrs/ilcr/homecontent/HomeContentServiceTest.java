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
  @DisplayName(
      "save: applies the legacy transform (tab/newline/&nbsp;) and updates all three roles")
  void save_transformsAndUpdatesAll() {
    when(repository.upsertMessage(anyString(), anyString(), eq(USER))).thenReturn(1);

    service.saveAll(
        new HomeContentSaveRequest("<p>a\tb\nc&nbsp;d</p>", "<p>Aud</p>", "<p>Adm</p>"), USER);

    // \t -> two spaces, \n -> one space, &nbsp; -> one space (preserves word breaks TipTap emits).
    verify(repository).upsertMessage("LICENSEE", "<p>a  b c d</p>", USER);
    verify(repository).upsertMessage("AUDITOR", "<p>Aud</p>", USER);
    verify(repository).upsertMessage("ADMIN", "<p>Adm</p>", USER);
  }

  @Test
  @DisplayName("save: a blank editor is rejected per-field (FLD-001), nothing saved")
  void save_blankEditorRejected() {
    FieldValuesRequiredException ex =
        assertThrows(
            FieldValuesRequiredException.class,
            () ->
                service.saveAll(
                    new HomeContentSaveRequest("<p></p>", "<p>Aud</p>", "<p>Adm</p>"), USER));

    assertTrue(ex.getFieldLabels().contains("Licensee Welcome Message"));
    verify(repository, never()).upsertMessage(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("save: all blank editors are reported together")
  void save_allBlankReportedTogether() {
    FieldValuesRequiredException ex =
        assertThrows(
            FieldValuesRequiredException.class,
            () -> service.saveAll(new HomeContentSaveRequest("&nbsp;", "  ", "<p></p>"), USER));

    assertEquals(3, ex.getFieldLabels().size());
    verify(repository, never()).upsertMessage(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("save: a message over the 4000 cap is rejected 400")
  void save_tooLongRejected() {
    String huge = "<p>" + "x".repeat(4100) + "</p>";

    HomeContentException ex =
        assertThrows(
            HomeContentException.class,
            () ->
                service.saveAll(
                    new HomeContentSaveRequest(huge, "<p>Aud</p>", "<p>Adm</p>"), USER));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  @DisplayName("save: a missing role row is repaired by the upsert")
  void save_missingRowIsRepaired() {
    when(repository.upsertMessage(anyString(), anyString(), eq(USER))).thenReturn(1);

    service.saveAll(new HomeContentSaveRequest("<p>L</p>", "<p>A</p>", "<p>Adm</p>"), USER);

    verify(repository).upsertMessage("LICENSEE", "<p>L</p>", USER);
    verify(repository).upsertMessage("AUDITOR", "<p>A</p>", USER);
    verify(repository).upsertMessage("ADMIN", "<p>Adm</p>", USER);
  }

  @Test
  @DisplayName("save: an ineffective repository write fails instead of reporting success")
  void save_ineffectiveRepositoryWriteFails() {
    when(repository.upsertMessage(anyString(), anyString(), eq(USER))).thenReturn(0);

    assertThrows(
        IllegalStateException.class,
        () ->
            service.saveAll(
                new HomeContentSaveRequest("<p>L</p>", "<p>A</p>", "<p>Adm</p>"), USER));
  }

  @Test
  @DisplayName("readForRole: a missing row degrades to empty content")
  void readForRole_missingIsEmpty() {
    when(repository.findByRole("LICENSEE")).thenReturn(java.util.Optional.empty());

    var content = service.readForRole("LICENSEE");
    assertEquals("LICENSEE", content.role());
    assertEquals("", content.messageText());
  }

  @Test
  @DisplayName("readAll: a partial role set remains repairable")
  void readAll_missingRoleIsRepairable() {
    when(repository.findAll())
        .thenReturn(
            java.util.List.of(
                new ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentEntry("ADMIN", "<p>A</p>")));

    assertEquals(1, service.readAll().size());
  }
}
