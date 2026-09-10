package ca.bc.gov.nrs.ilcr.dto.base;

/**
 * One field's originally-submitted value — the licensee's own figure, retained for audit comparison
 * once a report has left Draft (UC-CHK-005/UC-CHK-010 BR-04).
 *
 * <p>The canonical, feature-neutral home for this envelope, alongside {@link MessageInfo}: it is a
 * cross-schedule sub-shape pinned once and reused verbatim by all twelve schedules, so no feature
 * module has to reach into another for it and the wire contract stays uniform (AD-12).
 *
 * <p>Documents and rows carry these as a nullable {@code Map<String, OriginalValue> originalValues}
 * keyed by the owning object's own camelCase field name. Three states, all meaningful:
 *
 * <ul>
 *   <li><b>map is {@code null}</b> — the track is at Draft (or has no resolvable status): no
 *       indicator renders anywhere. This is legacy's {@code isSubmit()} gate.
 *   <li><b>key present</b> — a submitted value is on file; the indicator shows when the current
 *       value differs from {@link #value}.
 *   <li><b>key absent</b> — no submitted value is on file for that field (legacy's {@code
 *       originalVal == null}); the indicator shows whenever the current value is non-empty.
 * </ul>
 *
 * <p>Read-only and server-computed: echoed on GET and on the AD-8 save echo, and ignored if it ever
 * appears on a request body (AD-12).
 *
 * @param value the submitted value in canonical unformatted form ({@code "60000"}, {@code
 *     "60000.5"}, {@code "Y"}, or raw text) — never grouped and never currency-decorated, because
 *     this is what the page compares the operator's typed value against. Empty when the snapshot
 *     column holds no value.
 * @param tooltip the verbatim text to render, e.g. {@code "Original Submission Value: 60,000"},
 *     formatted server-side by the field's legacy converter rule so the frontend never composes it
 *     (AD-8). Ends after the separator when there is no value, which is legacy's own empty-tooltip
 *     behaviour.
 */
public record OriginalValue(String value, String tooltip) {}
