package ca.bc.gov.nrs.ilcr.userlookup.api;

import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Directory search for the assignment picker (UC-USR-001; controller + api-interface split). The
 * interface owns the request mapping; {@code UserLookupController} implements it and adds the
 * ADMIN-only {@code MAINTAIN_USERS} authorization.
 *
 * <p>The whole surface is a lookup — it reads the ministry directory through the NR User Lookup API
 * and never touches ILCR data, so a hit proves nothing about mill access and creates nothing as a
 * side effect.
 */
@RequestMapping("/api/v1")
public interface UserLookupApi {

  /**
   * Search the ministry directory for assignment candidates.
   *
   * <p>Two directories, two shapes: IDIR is a contains-search over name and username; BCeID
   * business is an exact lookup by username or GUID, because that directory offers no broad search.
   *
   * <p>Parameters that do not apply to the chosen provider are rejected, not ignored — a silently
   * discarded criterion answers a narrower question than the caller asked, with no signal that it
   * was dropped.
   *
   * @param idp {@code IDIR} (default) or {@code BCEIDBUSINESS}
   * @param firstName IDIR contains-match on first name
   * @param lastName IDIR contains-match on last name
   * @param userId IDIR contains-match on username, or the BCeID exact username
   * @param userGuid BCeID exact directory GUID
   * @param authentication the caller (must hold {@code MAINTAIN_USERS})
   * @return 200 with the candidates (empty when nothing matches); 400 when no criterion fits the
   *     chosen directory, when an IDIR criterion is shorter than two characters, or when a
   *     parameter does not apply to the chosen provider; 502 when the directory cannot answer
   */
  @GetMapping("/users/lookup")
  ResponseEntity<List<DirectoryUser>> lookup(
      @RequestParam(defaultValue = "IDIR") String idp,
      @RequestParam(required = false) String firstName,
      @RequestParam(required = false) String lastName,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String userGuid,
      Authentication authentication);
}
