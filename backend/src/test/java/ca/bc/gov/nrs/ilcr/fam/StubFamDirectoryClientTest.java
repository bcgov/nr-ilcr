package ca.bc.gov.nrs.ilcr.fam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.fam.dto.FamSubmitter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit test for {@link StubFamDirectoryClient} — the offline submitter-picker seed + filter. */
class StubFamDirectoryClientTest {

  private final StubFamDirectoryClient client = new StubFamDirectoryClient();

  @Test
  void blankQuery_returnsAllSeeded_withGuidShapedIds() {
    List<FamSubmitter> all = client.searchSubmitters("  ");
    assertTrue(all.size() >= 3);
    // userGuid must be the 32-char custom:idp_user_id shape so it round-trips through the xref key.
    assertTrue(all.stream().allMatch(s -> s.userGuid().length() == 32));
  }

  @Test
  void query_filtersByDisplayNameOrUsername_caseInsensitive() {
    assertEquals(1, client.searchSubmitters("bvandegr").size());   // by username, lowercased
    assertEquals(1, client.searchSubmitters("Meng").size());       // by display name
    assertEquals(1, client.searchSubmitters("fam-test-2").size()); // BCeID username
  }

  @Test
  void query_noMatch_returnsEmpty() {
    assertTrue(client.searchSubmitters("no-such-user").isEmpty());
  }
}
