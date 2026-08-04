package ca.bc.gov.nrs.ilcr.fam;

import ca.bc.gov.nrs.ilcr.fam.dto.FamSubmitter;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline stub of the FAM directory (Story 2.3), so the admin assignment picker works locally without
 * AWS/Cognito credentials — mirroring the FAM-auth epic's "stub behind a seam" approach and the
 * nr-ilcr-old prototype's seeded test data. Active by default; a real Cognito {@code ListUsersInGroup}
 * / FAM external-API client replaces it (this flag set {@code false}) once credentials are available.
 * The seeded {@code userGuid}s are 32-char GUIDs (the {@code custom:idp_user_id} shape — Story 1.0), so
 * assigning a stub submitter round-trips through {@code ILCR_MILL_USER_PROFILE_XREF.USER_GUID}.
 */
@Component
@ConditionalOnProperty(name = "ilcr.fam.directory.stub", havingValue = "true", matchIfMissing = true)
public class StubFamDirectoryClient implements FamDirectoryClient {

  private static final List<FamSubmitter> SEED = List.of(
      new FamSubmitter("A1B2C3D4E5F607182930A4B5C6D7E8F0", "Vandegriend, Basil WLRS:EX",
          "BVANDEGR", "idir"),
      new FamSubmitter("0F1E2D3C4B5A69788796A5B4C3D2E1F0", "Meng, Catherine WLRS:EX",
          "CMENG", "idir"),
      new FamSubmitter("11223344556677889900AABBCCDDEEFF", "Tollestrup, Pete WLRS:EX",
          "PTOLLEST", "idir"),
      new FamSubmitter("FFEEDDCCBBAA00998877665544332211", "FAM-TEST-2 02",
          "FAM-TEST-2", "bceidbusiness"));

  @Override
  public List<FamSubmitter> searchSubmitters(String query) {
    if (query == null || query.isBlank()) {
      return SEED;
    }
    String q = query.trim().toLowerCase(Locale.ROOT);
    return SEED.stream()
        .filter(s -> contains(s.displayName(), q) || contains(s.idpUsername(), q))
        .toList();
  }

  private static boolean contains(String value, String lowerQuery) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(lowerQuery);
  }
}
