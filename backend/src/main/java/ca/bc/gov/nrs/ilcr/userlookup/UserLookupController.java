package ca.bc.gov.nrs.ilcr.userlookup;

import ca.bc.gov.nrs.ilcr.userlookup.api.UserLookupApi;
import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directory search endpoint for the assignment picker (UC-USR-001). Gated on the ADMIN-only {@code
 * MAINTAIN_USERS} action like the assignment endpoints it feeds, and on the {@code
 * ilcr.user-lookup} feature flag, which stays off until the DL-27 service account exists.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.user-lookup.enabled", havingValue = "true")
public class UserLookupController implements UserLookupApi {

  private static final String MAINTAIN_USERS =
      "@permissions.hasPermission(authentication, 'MAINTAIN_USERS')";

  private final UserLookupClient directory;

  public UserLookupController(UserLookupClient directory) {
    this.directory = directory;
  }

  @Override
  @PreAuthorize(MAINTAIN_USERS)
  public ResponseEntity<List<DirectoryUser>> lookup(
      String idp,
      String firstName,
      String lastName,
      String userId,
      String userGuid,
      Authentication authentication) {
    if (UserLookupClient.IDP_BCEID_BUSINESS.equalsIgnoreCase(idp)) {
      // The BCeID directory answers exact questions only, and the GUID is the stronger key when
      // the caller has both.
      if (StringUtils.isNotBlank(userGuid)) {
        return ResponseEntity.ok(directory.findBusinessBceid("userGuid", userGuid));
      }
      if (StringUtils.isNotBlank(userId)) {
        return ResponseEntity.ok(directory.findBusinessBceid("userId", userId));
      }
      throw new InvalidLookupRequestException("error.user.lookup.criteria");
    }
    if (!UserLookupClient.IDP_IDIR.equalsIgnoreCase(idp)) {
      throw new InvalidLookupRequestException("error.user.lookup.idp");
    }
    if (StringUtils.isAllBlank(firstName, lastName, userId)) {
      throw new InvalidLookupRequestException("error.user.lookup.criteria");
    }
    return ResponseEntity.ok(directory.searchIdir(firstName, lastName, userId));
  }
}
