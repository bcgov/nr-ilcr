package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.util.List;

/**
 * Check Status result for one report page (Story 14.6): the page {@code id}, whether it {@code met}
 * all its rules (its own page-level fields AND every sample), the page-level {@code issues}
 * (Contact/Phone/TFL-or-Supply-Block/at-least-one-sample), and its per-sample {@code samples}
 * results.
 *
 * <p>{@code pageNumber} and {@code pageLabel} (#461) say WHICH page: the 1-based position in the
 * document's page order and the legacy page title ({@code "Page # 1 -TSA: TSA5 -CP: cp123"}) the
 * Page Summary shows for it, so two pages missing the same field are distinguishable in the notices
 * — legacy printed the title above each page's findings. The single-page scope keeps the page's
 * position in the full document, not {@code 1}. Same shape as Schedule 10's {@code
 * PageCheckResult}.
 *
 * @param id the page's database id, for UI correlation
 * @param pageNumber the 1-based positional ordinal, as the Page Summary numbers it
 * @param pageLabel the legacy page title, the prefix of every notice for this page
 * @param met whether the page and all its samples have no outstanding requirements
 * @param issues the page-level outstanding requirements, in legacy emission order
 * @param samples the per-sample outcomes, in sample order
 */
public record Schedule8PageCheckResult(
    Integer id,
    int pageNumber,
    String pageLabel,
    boolean met,
    List<Schedule8CheckFieldIssue> issues,
    List<Schedule8SampleCheckResult> samples) {}
