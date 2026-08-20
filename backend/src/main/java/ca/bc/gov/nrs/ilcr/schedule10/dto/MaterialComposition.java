package ca.bc.gov.nrs.ilcr.schedule10.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The material composition percentages.
 *
 * <p>{@code totalPct} is DERIVED and, unlike every other derived value in Schedule 10, is
 * <strong>never null</strong>: legacy computes it with {@code int} arithmetic that coerces each
 * absent percentage to {@code 0} ({@code getMaterialTypeTotal} :634-647). A detail with no
 * percentages recorded therefore serves {@code 0}.
 *
 * <p>Legacy performs no "sums to 100" validation, so a total of 87 or 140 is served as-is.
 *
 * @param solidRockPct solid/hard rock percentage
 * @param rippableRockPct rippable rock percentage
 * @param coarsePct coarse material percentage
 * @param finePct fine material percentage
 * @param organicPct organic material percentage
 * @param totalPct DERIVED sum of the five; never null, never validated against 100
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialComposition(
    Integer solidRockPct,
    Integer rippableRockPct,
    Integer coarsePct,
    Integer finePct,
    Integer organicPct,
    Integer totalPct) {}
