package ca.bc.gov.nrs.ilcr.schedule10.dto;

import java.math.BigDecimal;

/**
 * The Check Status body: the page panel or road editor currently ON SCREEN, if one is open
 * (bcgov/nr-ilcr#359, group C).
 *
 * <p>Legacy's Check Status walked its in-memory model with no reload, and the checked inputs wrote
 * into that model on change ({@code Schedule10MB.java:118-141}), so an unsaved edit on an open page
 * or road moved the verdict. The shipped implementation re-read the database instead, so it could
 * not. This DTO restores the legacy behaviour: the verdict is computed from the stored document
 * with {@code page} and {@code road} overlaid onto it, and nothing is persisted.
 *
 * <p><strong>Why one panel and not the whole document.</strong> The open editor is the only thing
 * the client knows that the server does not. A whole-document payload would make the client assert
 * values for pages and roads it is not displaying and cannot have changed, from its own possibly
 * stale copy — the #476 reasoning.
 *
 * <p><strong>A new page or road is never sent.</strong> Legacy's Add built the new row outside the
 * checked list ({@code Schedule10MB.java:402-414}; the add at {@code :428} is commented out), so
 * its final check never saw it. Only an EXISTING page or road is overlaid, matched by id; an id
 * that matches nothing stored (deleted in another session) is ignored rather than evaluated.
 *
 * <p>Deliberately NOT reusing {@link ConstructionPageRequest} or {@link RoadDetailRequest}: their
 * members are validated and their {@code revisionCount} exists for writes. Check Status addresses
 * no stored row for writing, takes no optimistic lock, and must ACCEPT incomplete input — reporting
 * it is its entire purpose. The nested road shapes mirror the write DTOs' field names, but carry
 * only the fields {@code Schedule10CheckStatus} reads, and type the money fields as {@link
 * BigDecimal} as the evaluated document does.
 *
 * <p>Read-only: this type reaches no write path. Both members are nullable and absent means "no
 * such editor is open", which evaluates the stored document alone. An absent BODY, by contrast, is
 * a clean 400 from {@code @RequestBody}'s own required-ness rather than a 500.
 *
 * @param page the page panel currently on screen at the page level, or null when none is open
 * @param road the road editor currently on screen at the road level, or null when none is open
 */
public record Schedule10CheckRequest(PageEntry page, RoadEntry road) {

  /**
   * The open page panel as on screen. Unvalidated by design: a blank field is exactly what Check
   * Status exists to surface. Blank is sent as null; the server also treats whitespace as blank, as
   * Save does.
   *
   * @param pageId the page's stored id; null (a new page) or an id matching no stored page is
   *     ignored
   * @param divisionName the on-screen Division
   * @param constructionPeriod the on-screen Period Surveyed ({@code yyyy-MM}); carried into the
   *     page label
   * @param tsaOrTfl the TSA/TFL selector: a TSA code, or the {@code "TFL"} sentinel. Mapped through
   *     the write path's own rule, so a TFL page with a blank TFL number reports {@code TFL #}
   * @param supplyBlock the on-screen Supply Block (TSA pages)
   * @param tflNumberCode the on-screen TFL # (TFL pages)
   */
  public record PageEntry(
      Integer pageId,
      String divisionName,
      String constructionPeriod,
      String tsaOrTfl,
      String supplyBlock,
      String tflNumberCode) {}

  /**
   * The open road editor as on screen: every field the Schedule 10 rules read. Derived values
   * (totals, material type total, $/km, labels) are NOT carried — the server recomputes them from
   * these inputs exactly as the read path does. Every member is nullable, and null stays null: a
   * blank figure is reported as missing where it is required, never coerced to zero.
   *
   * @param pageId the owning page's stored id
   * @param roadDetailId the road's stored id; null (a new road) or a {@code (pageId, roadDetailId)}
   *     pair matching no stored road is ignored
   * @param roadName the on-screen Road Name; carried into the road label
   * @param becbiogeoCatalogueId the on-screen BEC classification; its Sub Zone is taken from the
   *     matching catalogue row
   * @param relSoilMoistRgmClsCode the on-screen RSMR Class
   * @param sideSlopePct the on-screen Side Slope (%)
   * @param subGrade the on-screen sub-grade figures, or null when all are blank
   * @param stabilizing the on-screen additional-stabilizing figures, or null when all are blank
   * @param materialComposition the on-screen material percentages, or null when all are blank
   */
  public record RoadEntry(
      Integer pageId,
      Integer roadDetailId,
      String roadName,
      Integer becbiogeoCatalogueId,
      String relSoilMoistRgmClsCode,
      Integer sideSlopePct,
      SubGradeEntry subGrade,
      StabilizingEntry stabilizing,
      MaterialCompositionEntry materialComposition) {}

  /**
   * The on-screen sub-grade inputs, mirroring {@link SubGradeRequest} without its validation.
   *
   * @param length Length (km)
   * @param surfaceWidth Surface Width (m)
   * @param actualCost Actual Cost ($)
   * @param ttTransfer TtT Transfer ($)
   * @param otherTransfer Other Transfer ($)
   * @param lessBridges Less Bridges ($)
   * @param lessCulverts Less Culverts ($)
   * @param lessLandings Less Landings ($)
   * @param lessOverland Less Overland ($)
   * @param lessOtherEng Less Other Engineering ($)
   * @param lessEndHaul Less End Haul ($)
   */
  public record SubGradeEntry(
      BigDecimal length,
      BigDecimal surfaceWidth,
      BigDecimal actualCost,
      BigDecimal ttTransfer,
      BigDecimal otherTransfer,
      BigDecimal lessBridges,
      BigDecimal lessCulverts,
      BigDecimal lessLandings,
      BigDecimal lessOverland,
      BigDecimal lessOtherEng,
      BigDecimal lessEndHaul) {}

  /**
   * The on-screen additional-stabilizing inputs, mirroring {@link StabilizingRequest} without its
   * validation. No ballast-method coupling is applied: legacy's check read the in-memory model, and
   * the coupling happened only in its save.
   *
   * @param ballastMethodCode the Ballast Method; {@code "C"} makes the figures required
   * @param ballastMaterialCode the Additional Stabilizing Type
   * @param length Length (km)
   * @param surfaceWidth Surface Width (m)
   * @param depth Depth (m)
   * @param distanceToSource Distance to Source (km)
   * @param actualCost Actual Cost ($)
   * @param ttTransfer TtT Transfer ($)
   * @param otherTransfer Other Transfer ($)
   */
  public record StabilizingEntry(
      String ballastMethodCode,
      String ballastMaterialCode,
      BigDecimal length,
      BigDecimal surfaceWidth,
      BigDecimal depth,
      BigDecimal distanceToSource,
      BigDecimal actualCost,
      BigDecimal ttTransfer,
      BigDecimal otherTransfer) {}

  /**
   * The on-screen material percentages, mirroring {@link MaterialCompositionRequest} without its
   * validation.
   *
   * @param solidRockPct Solid (Hard) Rock (%)
   * @param rippableRockPct Rippable Rock (%)
   * @param coarsePct Coarse (%)
   * @param finePct Fine (%)
   * @param organicPct Organic (%)
   */
  public record MaterialCompositionEntry(
      Integer solidRockPct,
      Integer rippableRockPct,
      Integer coarsePct,
      Integer finePct,
      Integer organicPct) {}
}
