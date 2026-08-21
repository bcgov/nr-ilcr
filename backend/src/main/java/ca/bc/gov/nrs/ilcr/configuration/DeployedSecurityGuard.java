package ca.bc.gov.nrs.ilcr.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fails startup of a deployed (OpenShift) pod that would serve real data without enforced
 * authentication. With {@code ilcr.security.enabled=false}, {@link SecurityConfiguration} installs
 * {@code MockPrincipalFilter}, so every anonymous request acts as an authenticated {@code
 * ILCR_SUBMITTER} — acceptable for local development, an authentication bypass when the pod is
 * reachable through a public route and connected to a real database.
 *
 * <p>Allowed deployed combinations: security on (with or without a datasource), or mock auth over
 * no datasource (data-less smoke deployments). Local profiles are unaffected.
 */
@Configuration
@Profile("openshift")
public class DeployedSecurityGuard {

  DeployedSecurityGuard(
      @Value("${ilcr.security.enabled:false}") boolean securityEnabled,
      @Value("${ilcr.datasource.enabled:false}") boolean datasourceEnabled) {
    if (!securityEnabled && datasourceEnabled) {
      throw new IllegalStateException(
          "Refusing to start: ILCR_SECURITY_ENABLED=false while ILCR_DATASOURCE_ENABLED=true. "
              + "Mock auth must not front real data in a deployed environment; enable security "
              + "or disable the datasource for a data-less smoke deployment.");
    }
  }
}
