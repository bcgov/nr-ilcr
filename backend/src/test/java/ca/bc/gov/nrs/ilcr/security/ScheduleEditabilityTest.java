package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The role&times;status editability truth table (AD-9). This test is the specification: the rule
 * exists in exactly one place, so its table belongs in exactly one test.
 *
 * <p>Ported from legacy {@code UserSessionMB.disableUserInput():458-481} and collapsed through the
 * two-role model, in which the merged administrator carries the union of the legacy Administrator
 * and Auditor permission sets — so the legacy {@code S} + non-Licensee row becomes the ADMIN row.
 */
class ScheduleEditabilityTest {

  private final ScheduleEditability editability =
      new ScheduleEditability(new SchedulePermissions());

  private Authentication auth(String... authorities) {
    return new UsernamePasswordAuthenticationToken(
        "u", "p", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
  }

  private boolean editable(String authority, String statusCode) {
    return editability.forCaller(auth(authority)).allows(statusCode);
  }

  @Nested
  @DisplayName("the pinned matrix")
  class Matrix {

    @ParameterizedTest(name = "SUBMITTER at {0} -> editable={1}")
    @CsvSource({"D,true", "S,false", "V,false", "O,false"})
    void submitter_editsAtDraftOnly(String status, boolean expected) {
      assertTrue(expected == editable("ILCR_SUBMITTER", status), "SUBMITTER at " + status);
    }

    @ParameterizedTest(name = "ADMIN at {0} -> editable={1}")
    @CsvSource({"D,false", "S,true", "V,true", "O,false"})
    void admin_editsAtSubmittedAndVerifiedButNotDraft(String status, boolean expected) {
      assertTrue(expected == editable("ILCR_ADMIN", status), "ADMIN at " + status);
    }

    @Test
    @DisplayName("ADMIN is read-only at Draft — the row the blanket gate wrongly allowed")
    void admin_readOnlyAtDraft() {
      assertFalse(editable("ILCR_ADMIN", ScheduleEditability.DRAFT));
    }

    @Test
    @DisplayName("SUBMITTER is read-only once the track leaves Draft")
    void submitter_readOnlyAfterDraft() {
      assertFalse(editable("ILCR_SUBMITTER", ScheduleEditability.SUBMITTED));
      assertFalse(editable("ILCR_SUBMITTER", ScheduleEditability.VERIFIED));
    }
  }

  @Nested
  @DisplayName("the cells legacy leaves implicit")
  class ImplicitCells {

    @ParameterizedTest(name = "status {0} is read-only for every role")
    @ValueSource(strings = {"O", "X", "", "d", "s"})
    void unhandledStatusCodes_readOnlyForEveryRole(String status) {
      assertFalse(editable("ILCR_SUBMITTER", status), "SUBMITTER at " + status);
      assertFalse(editable("ILCR_ADMIN", status), "ADMIN at " + status);
    }

    @Test
    @DisplayName("a missing status row (null) is read-only, never an implicit Draft")
    void nullStatus_readOnlyForEveryRole() {
      assertFalse(editable("ILCR_SUBMITTER", null));
      assertFalse(editable("ILCR_ADMIN", null));
    }

    @Test
    @DisplayName("an unrecognised role is read-only everywhere — legacy failed open at Submitted")
    void unrecognisedRole_readOnlyEverywhere() {
      for (String status : new String[] {"D", "S", "V", "O"}) {
        assertFalse(editable("ILCR_AUDITOR", status), "ILCR_AUDITOR at " + status);
        assertFalse(editable("SCOPE_read", status), "SCOPE_read at " + status);
      }
    }
  }

  @Nested
  @DisplayName("caller resolution")
  class CallerResolution {

    @Test
    @DisplayName("roles are unioned, not read positionally as legacy did")
    void multipleRoles_unionTheirPermittedStatuses() {
      EditableStatuses both = editability.forCaller(auth("ILCR_SUBMITTER", "ILCR_ADMIN"));
      assertTrue(both.allows("D"));
      assertTrue(both.allows("S"));
      assertTrue(both.allows("V"));
      assertFalse(both.allows("O"));
    }

    @Test
    @DisplayName("order does not matter — the union is symmetric")
    void multipleRoles_orderIndependent() {
      EditableStatuses reversed = editability.forCaller(auth("ILCR_ADMIN", "ILCR_SUBMITTER"));
      assertTrue(reversed.allows("D"));
      assertTrue(reversed.allows("S"));
    }

    @Test
    void nullAuthentication_permitsNothing() {
      assertFalse(editability.forCaller(null).allows("D"));
      assertNotNull(editability.forCaller(null));
    }

    @Test
    void unauthenticated_permitsNothing() {
      UsernamePasswordAuthenticationToken anonymous =
          new UsernamePasswordAuthenticationToken("u", "p");
      assertFalse(anonymous.isAuthenticated());
      assertFalse(editability.forCaller(anonymous).allows("D"));
    }

    @Test
    @DisplayName("bare enum names resolve as well as the FAM group labels")
    void bareEnumNames_resolve() {
      assertTrue(editable("SUBMITTER", "D"));
      assertTrue(editable("ADMIN", "S"));
    }
  }

  @Nested
  @DisplayName("EditableStatuses value semantics")
  class ValueSemantics {

    @Test
    void none_allowsNothing() {
      assertFalse(EditableStatuses.NONE.allows("D"));
      assertFalse(EditableStatuses.NONE.allows(null));
    }

    @Test
    void codes_areDefensivelyCopied() {
      java.util.Set<String> mutable = new java.util.HashSet<>(java.util.Set.of("D"));
      EditableStatuses statuses = new EditableStatuses(mutable);
      mutable.add("S");
      assertFalse(statuses.allows("S"), "a later mutation must not widen the permission");
      assertTrue(statuses.allows("D"));
    }
  }
}
