package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.math.BigDecimal;

/**
 * The single-page Check Status body: the sample panel currently ON SCREEN under that page, if one
 * is open (bcgov/nr-ilcr#359, group C).
 *
 * <p>Legacy's sample view checked its in-memory sample list with no reload, and the checked sample
 * inputs wrote into that list on change ({@code Schedule8DetailMB.java:151-169}), so an unsaved
 * edit on an open sample moved the verdict. The shipped implementation re-read the database
 * instead, so it could not. This DTO restores the legacy behaviour: the verdict is computed from
 * the page's stored samples with {@code sample} overlaid, and nothing is persisted.
 *
 * <p><strong>A new sample IS evaluated.</strong> Legacy's Add put the unsaved row straight into the
 * checked list ({@code Schedule8DetailMB.java:222-231}). So a null id — or one that matches no
 * stored sample under the page (deleted in another session) — is APPENDED as the page's next
 * sample, the #476 precedent. The page header is NOT carried: it is read-only on the sample view,
 * so it is read from the database.
 *
 * <p>Deliberately NOT reusing {@link Schedule8SampleRequest}: its members are validated (including
 * the cross-field {@code Schedule8SampleRules}) and its {@code revisionCount} exists for writes.
 * Check Status must ACCEPT incomplete input — reporting it is its entire purpose. Only the fields
 * the Check Status rules read are carried; the numeric types are the evaluated document's, so a
 * decimal is never truncated on the way in.
 *
 * <p>Read-only: this type reaches no write path. {@code sample} is nullable and absent means "no
 * sample panel is open", which evaluates the stored samples alone. An absent BODY, by contrast, is
 * a clean 400 from {@code @RequestBody}'s own required-ness rather than a 500.
 *
 * @param sample the sample panel currently on screen, or null when none is open
 */
public record Schedule8PageCheckRequest(SampleEntry sample) {

  /**
   * The open sample panel as on screen. Derived values ({@code percentTotal}, {@code
   * actualHarvested}) are NOT carried — the server recomputes them from these inputs exactly as the
   * read path does. Every member is nullable, and null stays null: a blank figure is reported as
   * missing where it is required, never coerced to zero.
   *
   * @param id the sample's stored id; null (a new sample) or an id matching no stored sample under
   *     the page is evaluated as the page's next sample
   * @param contractId the on-screen Contract ID; carried into the sample label
   * @param cutBlock the on-screen Cut Block
   * @param groundBasePct the on-screen Ground Base %
   * @param grapplePct the on-screen Grapple %
   * @param skylinePct the on-screen Skyline %
   * @param highleadPct the on-screen Highlead %
   * @param helicopterPct the on-screen Helicopter %
   * @param otherSkiddingPct the on-screen Other %
   * @param skylineSlopeDistance the on-screen Slope Distance
   * @param skylineSupportNumber the on-screen Support Number
   * @param supportAvgDistance the on-screen Support Avg Dist
   * @param coniferousVolume the on-screen Coniferous volume
   * @param deciduousVolume the on-screen Deciduous volume
   * @param originalRate the on-screen Original TtT Rate
   */
  public record SampleEntry(
      Integer id,
      String contractId,
      String cutBlock,
      Integer groundBasePct,
      Integer grapplePct,
      Integer skylinePct,
      Integer highleadPct,
      Integer helicopterPct,
      Integer otherSkiddingPct,
      Integer skylineSlopeDistance,
      Integer skylineSupportNumber,
      BigDecimal supportAvgDistance,
      Integer coniferousVolume,
      Integer deciduousVolume,
      BigDecimal originalRate) {}
}
