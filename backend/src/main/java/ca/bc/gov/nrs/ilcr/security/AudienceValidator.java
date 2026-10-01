package ca.bc.gov.nrs.ilcr.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects a JWT whose {@code aud} does not include this application's Cognito client id. A Cognito
 * ID token is minted for a specific client and carries it in {@code aud}; requiring it stops a
 * token issued for a different client in the same pool from being accepted here.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final String requiredAudience;

  public AudienceValidator(String requiredAudience) {
    this.requiredAudience = requiredAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    if (jwt.getAudience() != null && jwt.getAudience().contains(requiredAudience)) {
      return OAuth2TokenValidatorResult.success();
    }
    // Deliberately generic: the failure is surfaced to the caller, so it names no client id.
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The required audience is missing", null));
  }
}
