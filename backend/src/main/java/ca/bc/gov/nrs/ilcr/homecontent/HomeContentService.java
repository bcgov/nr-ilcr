package ca.bc.gov.nrs.ilcr.homecontent;

import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentEntry;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentSaveRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Content Editing service (Story 24.2 / UC-CNT-001): reads the three role messages, serves one
 * role's message for the Home render, and saves all three ATOMICALLY (A-3 — the legacy per-role
 * non-atomic save S09 is fixed: one transaction, all-or-nothing).
 *
 * <p>Each editor is required (FLD-001) — validated before any write so a rejection saves nothing,
 * with ALL blank editors reported together. On save the legacy transform is applied
 * (tabs/newlines/{@code &nbsp;}, D-3). Rich text is stored raw (legacy stored the WYSIWYG HTML
 * unsanitized); the Home render sanitizes with DOMPurify (defence-in-depth), so no server-side HTML
 * rewrite happens here.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class HomeContentService {

  static final String ROLE_LICENSEE = "LICENSEE";
  static final String ROLE_AUDITOR = "AUDITOR";
  static final String ROLE_ADMIN = "ADMIN";
  private static final int MAX_MESSAGE_LENGTH = 4000;
  // Field labels for the FLD-001 required-field messages — verbatim from legacy content.xhtml.
  private static final String LABEL_LICENSEE = "Licensee Welcome Message";
  private static final String LABEL_AUDITOR = "Auditor Welcome Message";
  private static final String LABEL_ADMIN = "Administrator Welcome Message";

  private final HomeContentRepository repository;

  public HomeContentService(HomeContentRepository repository) {
    this.repository = repository;
  }

  /** All three role messages for the Content Editing page. */
  public List<HomeContentEntry> readAll() {
    return repository.findAll();
  }

  /** The message for one role — the Home render of the viewer's role (empty text when none). */
  public HomeContentEntry readForRole(String role) {
    return repository.findByRole(role).orElse(new HomeContentEntry(role, null));
  }

  /**
   * Save all three role messages in one transaction (A-3). Validate every editor first (FLD-001,
   * all blanks together), then transform + update each; any failure rolls the whole save back.
   *
   * @param request the three messages
   * @param user the acting administrator (audit)
   */
  @Transactional
  public void saveAll(HomeContentSaveRequest request, String user) {
    List<RoleMessage> messages =
        List.of(
            new RoleMessage(ROLE_LICENSEE, LABEL_LICENSEE, request.licensee()),
            new RoleMessage(ROLE_AUDITOR, LABEL_AUDITOR, request.auditor()),
            new RoleMessage(ROLE_ADMIN, LABEL_ADMIN, request.administrator()));

    List<String> blankLabels = new ArrayList<>();
    for (RoleMessage message : messages) {
      if (isBlankHtml(message.text())) {
        blankLabels.add(message.label());
      }
    }
    if (!blankLabels.isEmpty()) {
      throw new FieldValuesRequiredException(blankLabels);
    }

    for (RoleMessage message : messages) {
      String transformed = transform(message.text());
      // Cap by BYTES: MESSAGE_TEXT is VARCHAR2(4000 BYTE), so multi-byte content (smart quotes,
      // em dashes from paste) could pass a char-count check and then fail the insert with
      // ORA-12899.
      if (transformed.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_LENGTH) {
        throw HomeContentException.tooLong();
      }
      if (repository.updateMessage(message.role(), transformed, user) == 0) {
        throw HomeContentException.contentNotFound();
      }
    }
    log.info("Home content updated (3 role messages) by {}", user);
  }

  /** Empty once tags, {@code &nbsp;} and whitespace are stripped — the required-editor check. */
  private static boolean isBlankHtml(String html) {
    if (html == null) {
      return true;
    }
    return html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").strip().isEmpty();
  }

  /**
   * Save-transform after {@code CoreUtil.replaceCharsForExtractFormat}: tab &rarr; two spaces,
   * newline &rarr; one space. Legacy dropped {@code &nbsp;} entirely (CoreUtil.java:972), but the
   * legacy PrimeFaces editor rarely emitted it; TipTap emits {@code &nbsp;} for leading/consecutive
   * spaces, so dropping it would silently delete word breaks. We map it to a space instead
   * (deliberate, editor-driven deviation from legacy).
   */
  private static String transform(String text) {
    return text.replace("\t", "  ").replace("\n", " ").replace("&nbsp;", " ");
  }

  private record RoleMessage(String role, String label, String text) {}
}
