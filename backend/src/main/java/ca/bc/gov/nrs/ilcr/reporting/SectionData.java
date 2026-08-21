package ca.bc.gov.nrs.ilcr.reporting;

import java.util.List;
import java.util.Map;

/**
 * The mapped input for a bean-datasource section fill: the detail {@code rows} (one map per record,
 * keyed by the template field names) plus the section-level {@code parameters} (footer totals,
 * general comments) that ride as report parameters. An empty {@code rows} list signals the schedule
 * has no data for the mill/year, so the orchestrator skips the section (BR-09).
 *
 * @param rows one map per detail record, keyed by the template's field names
 * @param parameters section-level report parameters (footer totals, general comments)
 */
public record SectionData(List<Map<String, ?>> rows, Map<String, Object> parameters) {}
