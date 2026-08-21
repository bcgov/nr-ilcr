package ca.bc.gov.nrs.ilcr.user.api;

import ca.bc.gov.nrs.ilcr.user.dto.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The current signed-in user. Controller + api-interface split, the established idiom: the
 * interface owns the request mapping, {@link ca.bc.gov.nrs.ilcr.user.UserController} implements it.
 *
 * <p>The SPA calls this after sign-in to learn who it is and what it may do, then branches its
 * navigation on {@link CurrentUser#roles()}. Identity and role come from the validated FAM/Cognito
 * token, not a database read (FAM is the source of truth).
 */
@RequestMapping("/api/v1/me")
public interface UserApi {

  /**
   * Return the authenticated caller's identity and roles from the validated token.
   *
   * @return 200 with the {@link CurrentUser}; the request never reaches here unauthenticated (the
   *     security filter chain answers 401 first when security is enabled).
   */
  @GetMapping
  ResponseEntity<CurrentUser> me();
}
