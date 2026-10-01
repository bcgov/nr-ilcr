package ca.bc.gov.nrs.ilcr.reportingyear.dto;

import java.util.List;

/**
 * The state the Open Reporting Year admin page needs to render (UC-RY-001). Drives the recurring vs
 * first-time branch and the bounded starting-year dropdown.
 *
 * @param openYears the already-opened reporting years, most recent first
 * @param nextYear the year the recurring path would create ({@code max + 1}), or {@code null}
 *     first-time
 * @param firstTime true when no reporting year exists yet (first-time setup path)
 * @param selectableStartYears the bounded starting-year options for first-time setup (BR-07), else
 *     empty
 */
public record ReportingYearAdminView(
    List<Integer> openYears,
    Integer nextYear,
    boolean firstTime,
    List<Integer> selectableStartYears) {}
