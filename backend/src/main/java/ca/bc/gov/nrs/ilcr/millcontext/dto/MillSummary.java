package ca.bc.gov.nrs.ilcr.millcontext.dto;

/**
 * A selectable mill for the Home page option list (Story 1.1, pinned wire contract AD-12). Projected
 * from {@code THE.MILL} joined to its one-to-one {@code THE.ILCR_MILL_STATUS_XREF} status row.
 * Closed mills are included (no status filter) — the frontend renders the label
 * {@code {millNumber} - {millName}} and treats {@code CLS} as not-viewable downstream (S06).
 *
 * <p>{@code millNumber} is a String even though {@code THE.MILL.MILL_NUMBER} is numeric
 * ({@code NUMBER(15)}, legacy {@code BigDecimal}): it is a display identifier, never arithmetic,
 * read via {@code ResultSet#getString}. Keeping it String honours the once-pinned contract that
 * Stories 1.2-1.4 derive from and avoids {@code int} overflow on {@code NUMBER(15)}.
 *
 * @param millId the mill id ({@code THE.MILL.MILL_ID})
 * @param millNumber the mill number as a display string ({@code THE.MILL.MILL_NUMBER})
 * @param millName the mill name ({@code THE.MILL.MILL_NAME})
 * @param millStatusCode the current status code {@code "ACT"} or {@code "CLS"}
 *     ({@code THE.ILCR_MILL_STATUS_XREF.ILCR_MILL_STATUS_CODE})
 */
public record MillSummary(long millId, String millNumber, String millName, String millStatusCode) {
}
