package ca.bc.gov.nrs.ilcr;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Application-wide constants: SQL parameter placeholders, sentinel values, and shared
 * security path lists.
 *
 * <p>The token values in this class indicate an absent or unspecified value (for example when
 * binding query parameters) and a placeholder client identifier used when no client is
 * available. The path arrays centralize the request matchers referenced by the security
 * configuration.</p>
 *
 * <p>This class is not instantiable and only exposes static constant values.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BackendConstants {

  /**
   * Token representing a missing or unspecified value when interacting with legacy queries.
   *
   * <p>Used in SQL where clauses and parameter binding to indicate the absence of a filter
   * value.</p>
   */
  public static final String NOVALUE = "NOVALUE";

  /**
   * Token representing the absence of a client value.
   *
   * <p>Used as a fallback client identifier in places where a client list is required but
   * none are available.</p>
   */
  public static final String NOCLIENT = "NOCLIENT";

  /**
   * Paths permitted without authentication regardless of whether security is enabled.
   *
   * <p>Referenced by the security filter chain to allow the API root, health, info, and
   * metrics endpoints.</p>
   */
  public static final String[] PUBLIC_PATHS = {
    "/api",
    "/api/health",
    "/api/health/**",
    "/api/info",
    "/api/prometheus"
  };

  /**
   * Home-page option-list endpoints (Story 1.1). Pre-selection reads with no action gate and no
   * {@code @PreAuthorize}; permitted even when security is enabled. The per-user mill-association
   * filter arrives with the FAM auth story (AR4); until then these are open like the other
   * pre-auth reads.
   */
  public static final String[] HOME_PUBLIC_PATHS = {
    "/api/v1/mills",
    "/api/v1/reporting-years",
    "/api/v1/mill-context"
  };
}
