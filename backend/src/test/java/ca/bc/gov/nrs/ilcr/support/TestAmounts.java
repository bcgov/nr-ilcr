package ca.bc.gov.nrs.ilcr.support;

import java.math.BigDecimal;

/**
 * Shared numeric-literal helpers for the mocked-repository service tests, so each test class stops
 * re-declaring the same one-liner {@code bd(...)} (review nit — schedule4 write / sub-page /
 * check-status all needed it).
 */
public final class TestAmounts {

  private TestAmounts() {
  }

  /** A concise {@link BigDecimal} literal for volume / distance amounts in tests. */
  public static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
