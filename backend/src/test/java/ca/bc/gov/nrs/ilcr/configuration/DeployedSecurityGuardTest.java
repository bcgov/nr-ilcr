package ca.bc.gov.nrs.ilcr.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeployedSecurityGuardTest {

    @Test
    void rejectsMockAuthOverRealData() {
        assertThrows(IllegalStateException.class, () -> new DeployedSecurityGuard(false, true));
    }

    @Test
    void allowsEnforcedAuthOverRealData() {
        assertDoesNotThrow(() -> new DeployedSecurityGuard(true, true));
    }

    @Test
    void allowsMockAuthWithoutData() {
        assertDoesNotThrow(() -> new DeployedSecurityGuard(false, false));
    }

    @Test
    void allowsEnforcedAuthWithoutData() {
        assertDoesNotThrow(() -> new DeployedSecurityGuard(true, false));
    }
}
