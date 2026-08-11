package ca.bc.gov.nrs.ilcr.messages;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Resolution of the few {@code messages.properties} strings a CLIENT renders on its own — text with
 * no request behind it, which would otherwise have to be hardcoded in the frontend and drift from
 * the bundle. Controller + api-interface split, the established idiom: the interface owns the
 * request mapping and parameter contract, {@link MessageController} implements it and adds
 * authorization.
 *
 * <p>Every other user-facing string already reaches the client attached to the response that caused
 * it: a success echo carries {@link MessageInfo}, a rejection carries a {@code ProblemDetail}
 * {@code detail}, and check-status carries composed lines. Those must keep arriving that way (AD-8)
 * — this endpoint is not a general escape hatch from that rule and must not become one.
 *
 * <p><strong>Allowlisted by design.</strong> See {@link MessageController} for why the key set is
 * closed rather than open.
 */
@RequestMapping("/api/v1/messages")
public interface MessageApi {

  /**
   * Resolve one allowlisted bundle key to its verbatim text, applying any {@code MessageFormat}
   * arguments in the order supplied.
   *
   * @param key the {@code messages.properties} key; must be on the client-renderable allowlist
   * @param args the {@code MessageFormat} arguments ({@code {0}}, {@code {1}}…), in order; may be
   *     absent, in which case placeholders are left unfilled rather than guessed at
   * @return 200 with the key and its resolved text; 404 when the key is not client-renderable
   */
  @GetMapping
  ResponseEntity<MessageInfo> resolve(
      @RequestParam(name = "key") String key,
      @RequestParam(name = "arg", required = false) List<String> args);
}
