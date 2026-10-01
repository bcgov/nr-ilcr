package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckRequest.SampleEntry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Overlays the Schedule 8 panel currently on screen onto the stored pages, so Check Status judges
 * what the user is LOOKING AT (bcgov/nr-ilcr#359, group C).
 *
 * <p>The overlaid pages go through the one evaluator, {@code Schedule8Service.evaluate} (AD-5), so
 * rules, message bytes and emission order are those of the stored path. Only the INPUTS change, and
 * every derived value is rebuilt through the code that builds it on read:
 *
 * <ul>
 *   <li><strong>Page panel</strong> (all-pages check). Division, Contact, Phone, Cutting Permit and
 *       the TSA/TFL location replace the stored ones on the page with the same id. The location
 *       maps through {@link Schedule8Service#locate}, Save's own rule, so an unsaved switch to TFL
 *       with TFL # blank reports {@code TFL #}, not {@code Supply Block}; and the page label
 *       ({@link Schedule8Labels#pageLabel}) carries the on-screen TSA and Cutting Permit. Samples
 *       stay stored. A null or unknown id is ignored — a new page is never evaluated, as in legacy.
 *   <li><strong>Sample panel</strong> (single-page check). The checked fields replace the stored
 *       ones on the sample with the same id; a null or unknown id is APPENDED as the page's next
 *       sample, as legacy's Add put it into the checked list. {@code percentTotal}, {@code
 *       actualHarvested} and {@code finalRate} are recomputed by the read path's own helpers. The
 *       page header stays stored.
 * </ul>
 *
 * <p>Null stays null: a blank on-screen value is judged as blank, never coerced to zero. Strings
 * are trimmed to null, as Save stores them.
 */
final class Schedule8CheckOverlay {

  private Schedule8CheckOverlay() {}

  /**
   * The stored pages with the open page panel overlaid.
   *
   * @param pages the stored pages, as assembled for the GET
   * @param request the all-pages Check Status body; a null {@code page} means no panel is open
   * @return the pages to evaluate; the same list when nothing applies
   */
  static List<Page> overlayPage(List<Page> pages, Schedule8CheckRequest request) {
    Schedule8CheckRequest.PageEntry entry = request == null ? null : request.page();
    if (entry == null || entry.id() == null) {
      return pages;
    }
    List<Page> result = new ArrayList<>(pages.size());
    for (Page page : pages) {
      result.add(entry.id().equals(page.id()) ? withPageFields(page, entry) : page);
    }
    return result;
  }

  /**
   * The stored pages with the open sample panel overlaid onto page {@code pageId}'s samples.
   *
   * @param pages the stored pages, as assembled for the GET
   * @param pageId the page whose sample view is open
   * @param request the single-page Check Status body; a null {@code sample} means no panel is open
   * @return the pages to evaluate; the same list when nothing applies
   */
  static List<Page> overlaySample(List<Page> pages, int pageId, Schedule8PageCheckRequest request) {
    SampleEntry entry = request == null ? null : request.sample();
    if (entry == null) {
      return pages;
    }
    List<Page> result = new ArrayList<>(pages.size());
    for (Page page : pages) {
      result.add(Objects.equals(page.id(), pageId) ? withSample(page, entry) : page);
    }
    return result;
  }

  /**
   * The page with the on-screen header fields. The {@code *Label} companions are carried over
   * unchanged: the rules and the page label read the codes, never the resolved descriptions.
   */
  private static Page withPageFields(Page page, Schedule8CheckRequest.PageEntry entry) {
    Schedule8Service.Location location =
        Schedule8Service.locate(entry.tsaNumber(), entry.tflNumber(), entry.supplyBlock());
    return new Page(
        page.id(),
        page.revisionCount(),
        Schedule8Service.trimToNull(entry.division()),
        page.license(),
        Schedule8Service.trimToNull(entry.contact()),
        Schedule8Service.trimToNull(entry.phone()),
        Schedule8Service.trimToNull(entry.cuttingPermit()),
        page.supportCentre(),
        page.supportCentreLabel(),
        page.region(),
        page.regionLabel(),
        page.becZone(),
        page.becZoneLabel(),
        location.tsaNumber(),
        page.tsaNumberLabel(),
        location.tflNumber(),
        page.tflNumberLabel(),
        location.supplyBlock(),
        page.supplyBlockLabel(),
        page.comments(),
        page.sampleCount(),
        page.samples(),
        page.originalValues());
  }

  /** The page with the open sample replacing its stored namesake, or appended after the rest. */
  private static Page withSample(Page page, SampleEntry entry) {
    List<Sample> samples = new ArrayList<>(page.samples().size() + 1);
    boolean replaced = false;
    for (Sample sample : page.samples()) {
      if (entry.id() != null && entry.id().equals(sample.id())) {
        samples.add(onScreen(sample, entry));
        replaced = true;
      } else {
        samples.add(sample);
      }
    }
    if (!replaced) {
      // An unsaved sample has no stored id to report; a synthetic one would read as a persisted
      // row (the Schedule 4 and 5 precedent).
      samples.add(onScreen(null, entry));
    }
    return new Page(
        page.id(),
        page.revisionCount(),
        page.division(),
        page.license(),
        page.contact(),
        page.phone(),
        page.cuttingPermit(),
        page.supportCentre(),
        page.supportCentreLabel(),
        page.region(),
        page.regionLabel(),
        page.becZone(),
        page.becZoneLabel(),
        page.tsaNumber(),
        page.tsaNumberLabel(),
        page.tflNumber(),
        page.tflNumberLabel(),
        page.supplyBlock(),
        page.supplyBlockLabel(),
        page.comments(),
        samples.size(),
        samples,
        page.originalValues());
  }

  /**
   * The on-screen sample: the checked fields from {@code entry}, everything else from the stored
   * sample — or, for an unsaved sample ({@code stored} null), empty: no id, no rate rows.
   */
  private static Sample onScreen(Sample stored, SampleEntry entry) {
    BigDecimal additionsTotal = stored == null ? BigDecimal.ZERO : stored.additionsTotal();
    BigDecimal deductionsTotal = stored == null ? BigDecimal.ZERO : stored.deductionsTotal();
    return new Sample(
        stored == null ? null : stored.id(),
        stored == null ? null : stored.revisionCount(),
        Schedule8Service.trimToNull(entry.contractId()),
        Schedule8Service.trimToNull(entry.cutBlock()),
        entry.groundBasePct(),
        entry.grapplePct(),
        entry.skylinePct(),
        entry.highleadPct(),
        entry.helicopterPct(),
        entry.otherSkiddingPct(),
        Schedule8Service.percentTotal(
            entry.groundBasePct(),
            entry.grapplePct(),
            entry.skylinePct(),
            entry.highleadPct(),
            entry.helicopterPct(),
            entry.otherSkiddingPct()),
        entry.skylineSlopeDistance(),
        entry.skylineSupportNumber(),
        Schedule8Service.normalize(entry.supportAvgDistance()),
        stored == null ? null : stored.distance(),
        stored == null ? null : stored.cycleTime(),
        stored != null && stored.uphillDirection(),
        stored != null && stored.waterDumpDestination(),
        stored == null ? null : stored.skidTypeCode(),
        stored == null ? null : stored.skidTypeDescription(),
        entry.coniferousVolume(),
        entry.deciduousVolume(),
        Schedule8Service.actualHarvested(entry.coniferousVolume(), entry.deciduousVolume()),
        Schedule8Service.normalize(entry.originalRate()),
        additionsTotal,
        deductionsTotal,
        Schedule8Service.finalRate(entry.originalRate(), additionsTotal, deductionsTotal),
        stored == null ? 0 : stored.additionCount(),
        stored == null ? 0 : stored.deductionCount(),
        stored == null ? List.of() : stored.additions(),
        stored == null ? List.of() : stored.deductions(),
        stored == null ? null : stored.originalValues());
  }
}
