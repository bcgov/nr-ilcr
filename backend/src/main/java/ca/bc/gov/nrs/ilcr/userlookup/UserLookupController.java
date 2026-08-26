package ca.bc.gov.nrs.ilcr.userlookup;

import ca.bc.gov.nrs.ilcr.userlookup.api.UserLookupApi;
import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Directory search endpoint for the assignment picker (UC-USR-001). Gated on the ADMIN-only {@code
 * MAINTAIN_USERS} action like the assignment endpoints it feeds, and on the {@code
 * ilcr.user-lookup} feature flag, which stays off until the DL-27 service account exists.
 *
 * <p>Inapplicable parameters are rejected rather than ignored. The two directories answer different
 * shapes of question — IDIR a contains-search over names, BCeID an exact lookup by key — and
 * quietly discarding the ones that do not fit would answer a narrower question than the admin
 * asked, with nothing to tell them their filter was dropped.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.user-lookup.enabled", havingValue = "true")
public class UserLookupController implements UserLookupApi {

  private static final String MAINTAIN_USERS =
      "@permissions.hasPermission(authentication, 'MAINTAIN_USERS')";

  private static final Logger log = LoggerFactory.getLogger(UserLookupController.class);

  /**
   * The shortest criterion accepted for the IDIR contains-search. A single character matches a
   * large fraction of a government-wide directory, and the picker issues a request per keystroke —
   * so the floor is enforced server-side rather than left to the client, which is advisory by
   * design.
   */
  private static final int MIN_CRITERION_LENGTH = 2;

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
    String first = StringUtils.trimToNull(firstName);
    String last = StringUtils.trimToNull(lastName);
    String user = StringUtils.trimToNull(userId);
    String guid = StringUtils.trimToNull(userGuid);

    recordSearch(idp, authentication);

    if (UserLookupClient.IDP_BCEID_BUSINESS.equalsIgnoreCase(idp)) {
      if (first != null) {
        throw InvalidLookupRequestException.unsupportedParameter("firstName");
      }
      if (last != null) {
        throw InvalidLookupRequestException.unsupportedParameter("lastName");
      }
      // The BCeID directory answers exact questions only, and the GUID is the stronger key when
      // the caller has both.
      if (guid != null) {
        return ResponseEntity.ok(directory.findBusinessBceid("userGuid", guid));
      }
      if (user != null) {
        return ResponseEntity.ok(directory.findBusinessBceid("userId", user));
      }
      throw InvalidLookupRequestException.noCriteria();
    }
    if (!UserLookupClient.IDP_IDIR.equalsIgnoreCase(idp)) {
      throw InvalidLookupRequestException.unknownIdentityProvider();
    }
    if (guid != null) {
      throw InvalidLookupRequestException.unsupportedParameter("userGuid");
    }
    if (first == null && last == null && user == null) {
      throw InvalidLookupRequestException.noCriteria();
    }
    requireSearchableLength(first, last, user);
    return ResponseEntity.ok(directory.searchIdir(first, last, user));
  }

  private static void requireSearchableLength(String... criteria) {
    for (String criterion : criteria) {
      if (criterion != null && criterion.length() < MIN_CRITERION_LENGTH) {
        throw InvalidLookupRequestException.criterionTooShort(MIN_CRITERION_LENGTH);
      }
    }
  }

  /**
   * Records that a directory search happened, and by whom.
   *
   * <p>The endpoint serves names and usernames of BC public servants and BCeID business users on
   * demand, through a shared service account, to every ILCR administrator. Without this line there
   * is no server-side evidence a search ever occurred, so a privacy inquiry about directory
   * scraping through ILCR could not be answered at all.
   *
   * <p>Actor and provider only — deliberately never the criteria (a surname is itself personal
   * data) and never the results (AD-11). That is enough to answer "was this endpoint scraped, and
   * by whom" without the log becoming a copy of the directory.
   */
  private static void recordSearch(String idp, Authentication authentication) {
    log.info(
        "Directory search of {} performed by {}",
        StringUtils.defaultIfBlank(idp, UserLookupClient.IDP_IDIR),
        authentication == null ? "unknown" : authentication.getName());
  }
}
