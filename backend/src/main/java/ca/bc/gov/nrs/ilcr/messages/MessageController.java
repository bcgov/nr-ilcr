package ca.bc.gov.nrs.ilcr.messages;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Resolves the handful of bundle strings a client renders on its own (AD-8).
 *
 * <p><strong>Why an allowlist rather than open lookup.</strong> The bundle is the server's, and
 * most of it is text the server composes and is answerable for — check-status lines, validation
 * rejections, save echoes. Exposing it wholesale would let any authenticated caller enumerate it,
 * and would offer a tempting second path to strings that must arrive on their own response so they
 * cannot drift from the outcome that produced them. Only keys with genuinely NO request behind them
 * belong here.
 *
 * <p>{@code VIEW_SCHEDULE} rather than a looser gate: every key here is schedule-screen chrome, so
 * a caller who cannot view a schedule has no use for one.
 */
@RestController
public class MessageController implements MessageApi {

  /**
   * The client-renderable keys.
   *
   * <p>{@code sch5.copy.msg} is the founding member and the reason this endpoint exists. Legacy's
   * {@code copyCamp()} makes no database call at all ({@code Schedule5MB.java:270-275}) — it clones
   * the camp in memory, blanks the name and warns — so Schedule 5's copy has no response to carry
   * the warning on. Story 7.2 required 7.3 to "resolve it from the API rather than hardcode it",
   * which was not satisfiable until this seam existed.
   *
   * <p>Schedule 4 hardcodes its equivalent ({@code schedule4/index.tsx:45-46}); converging it onto
   * this endpoint belongs to the cross-schedule consistency PR, not here.
   *
   * <p>Add a key only when a client must render it with no request behind it. If a request DOES
   * produce the text, put it on that response instead.
   */
  private static final Set<String> CLIENT_RENDERABLE_KEYS = Set.of("sch5.copy.msg");

  private final MessageSource messageSource;

  public MessageController(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'VIEW_SCHEDULE')")
  public ResponseEntity<MessageInfo> resolve(String key, WebRequest request) {
    if (!CLIENT_RENDERABLE_KEYS.contains(key)) {
      throw new MessageNotResolvableException();
    }
    // Read verbatim off the request — a bound List<String> would comma-split a single free-text
    // value (see MessageApi). Null args (not an empty array) leaves MessageFormat placeholders
    // unfilled rather than substituting blanks — a caller that omits the argument gets the raw
    // template, never a sentence with a hole silently closed up.
    String[] args = request.getParameterValues("arg");
    Object[] formatArgs = args == null || args.length == 0 ? null : args;
    try {
      String text = messageSource.getMessage(key, formatArgs, LocaleContextHolder.getLocale());
      return ResponseEntity.ok(new MessageInfo(key, text));
    } catch (NoSuchMessageException ex) {
      // An allowlisted key that the bundle no longer holds: a renamed or deleted key must surface
      // as a failure, never as its own key echoed back to a licensee as text (the Schedule 6 code
      // review 2026-08-04 finding — "Road : 1 - TFL Number : missingRequiredFieldMsg" shipped
      // because a default message was supplied).
      throw new MessageNotResolvableException();
    }
  }
}
