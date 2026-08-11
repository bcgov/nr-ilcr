package ca.bc.gov.nrs.ilcr.schedule5.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * One volume/cost/$-per-m&sup3; triple on a Schedule 5 camp (AD-12) — the single sub-shape
 * reused by all twelve stored category amounts AND by the four derived totals, so the frontend
 * renders every row of the camp grid through one component.
 *
 * <p>{@code cost} is whole dollars ({@code ILCR_COST_REPORT_DETAIL.COST} is {@code NUMBER(8,0)};
 * widened to {@code Long} so a sum can never overflow an {@code int}). {@code volume} is m&sup3;
 * ({@code NUMBER(10,2)}). {@code costPerVolume} is DERIVED server-side at scale 2 (legacy
 * {@code CoreUtil.bigDecimalDivision}: scale 10 HALF_UP, then scale 2 HALF_UP) and is null when
 * either side is null OR the volume is zero — no divide-by-zero, never a client input.
 *
 * <p><strong>null is not 0.</strong> A stored null cost/volume stays null and Jackson
 * {@code non_null} omits the field entirely; the legacy screen rendered null as {@code ""} and
 * zero as {@code 0}, and the API serves that distinction rather than collapsing it.
 *
 * <p>Recoveries is the one volume-less category: it serializes as {@code {"cost": n}} alone,
 * because legacy renders its volume and $/m&sup3; as {@code h:inputHidden}
 * ({@code schedule5ExistingCamp.xhtml:248, 259}) and the read path never populates them
 * ({@code Schedule5DAO.java:242-244} sets cost only).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryAmount(BigDecimal volume, Long cost, BigDecimal costPerVolume) {
}
