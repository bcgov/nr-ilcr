package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 8 (Tree to Truck) read document from the three flat entity lists returned by
 * {@link Schedule8Repository} — pages, samples, and rate rows — into the pinned three-level hierarchy.
 * Every derived value ({@code percentTotal}, {@code actualHarvested}, {@code additionsTotal}/
 * {@code deductionsTotal}, {@code finalRate}, the counts, {@code editable}) is computed here
 * (AD-5/AD-6), never read from storage as a client-supplied figure and never accepted on write.
 *
 * <p>The mill/year context is validated by {@code MillContextService} in the controller before this
 * runs (AD-4). A valid, active mill/year with NO category-{@code '8'} pages is NOT a 404 — it yields a
 * 200 empty {@code pages: []} (mirrors Schedule 2/4; Schedule 8 has no {@code ILCR_REPORT_SUMMARY}
 * row of its own).
 *
 * <p>Addition vs deduction is decided by each rate row's cost item subcategory (§Decision 1: {@code '1'}
 * /{@code '2'} = addition, {@code '3'}/{@code '4'} = deduction). The eight code FKs are resolved to
 * their {@code DESCRIPTION} labels (§Decision 3) from the repository's code→label maps.
 */
@Service
public class Schedule8Service {

  private static final String STATUS_DRAFT = "D";
  private static final String IND_YES = "Y";

  /** Cost-item subcategories that mark a rate row as an addition (§Decision 1). */
  private static final Set<String> ADDITION_SUBCATEGORIES = Set.of("1", "2");
  /** Cost-item subcategories that mark a rate row as a deduction (§Decision 1). */
  private static final Set<String> DEDUCTION_SUBCATEGORIES = Set.of("3", "4");

  private final Schedule8Repository repository;

  public Schedule8Service(Schedule8Repository repository) {
    this.repository = repository;
  }

  /**
   * Assemble the Schedule 8 read document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds the EDIT_SCHEDULE action (from the controller)
   * @return the read document (never null; {@code pages: []} when the mill/year has none)
   */
  @Transactional(readOnly = true)
  public Schedule8Response getSchedule8(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    // Label maps + the addition/deduction discriminator, loaded once per read.
    Map<String, String> supportCentre = repository.supportCentreLabels();
    Map<String, String> region = repository.regionLabels();
    Map<String, String> becZone = repository.becZoneLabels();
    Map<String, String> tsa = repository.tsaNumberLabels();
    Map<String, String> supplyBlock = repository.supplyBlockLabels();
    Map<String, String> tfl = repository.tflNumberLabels();
    Map<String, String> skidType = repository.skidTypeLabels();
    Map<String, String> costType = repository.costTypeLabels();
    Map<Integer, String> subcategories = repository.costItemSubcategories();

    // Rate rows grouped under their sample; each sample's rows grouped under its page.
    Map<Integer, List<TreeToTruckRateDetailEntity>> ratesBySample = new LinkedHashMap<>();
    for (TreeToTruckRateDetailEntity r : repository.findRateRows(millId, year)) {
      ratesBySample.computeIfAbsent(r.detailReportId(), k -> new ArrayList<>()).add(r);
    }
    Map<Integer, List<Sample>> samplesByPage = new LinkedHashMap<>();
    for (TreeToTruckDetailReportEntity s : repository.findSamples(millId, year)) {
      Sample sample = toSample(s, ratesBySample.getOrDefault(s.id(), List.of()), subcategories,
          skidType, costType);
      samplesByPage.computeIfAbsent(s.reportId(), k -> new ArrayList<>()).add(sample);
    }

    List<Page> pages = new ArrayList<>();
    for (TreeToTruckReportEntity p : repository.findPages(millId, year)) {
      List<Sample> samples = samplesByPage.getOrDefault(p.id(), List.of());
      pages.add(new Page(
          p.id(), p.revisionCount(), p.division(), p.license(), p.contact(), p.phone(),
          p.cuttingPermit(),
          p.supportCentre(), supportCentre.get(p.supportCentre()),
          p.region(), region.get(p.region()),
          p.becZone(), becZone.get(p.becZone()),
          p.tsaNumber(), tsa.get(p.tsaNumber()),
          p.tflNumber(), tfl.get(p.tflNumber()),
          p.supplyBlock(), supplyBlock.get(p.supplyBlock()),
          p.comments(), samples.size(), samples));
    }

    return new Schedule8Response(millId, year, trackStatus, editable, pages, null);
  }

  /** Map one sample entity + its rate rows to the wire {@link Sample}, splitting add/ded + computing. */
  private Sample toSample(TreeToTruckDetailReportEntity s,
      List<TreeToTruckRateDetailEntity> rateRows, Map<Integer, String> subcategories,
      Map<String, String> skidType, Map<String, String> costType) {
    List<RateRow> additions = new ArrayList<>();
    List<RateRow> deductions = new ArrayList<>();
    BigDecimal additionsTotal = BigDecimal.ZERO;
    BigDecimal deductionsTotal = BigDecimal.ZERO;
    for (TreeToTruckRateDetailEntity r : rateRows) {
      String subcategory = subcategories.get(r.costItemCode());
      RateRow row = new RateRow(r.id(), r.revisionCount(), r.costItemCode(), r.itemDescription(),
          normalize(r.costingRate()), r.costTypeCode(), costType.get(r.costTypeCode()));
      if (ADDITION_SUBCATEGORIES.contains(subcategory)) {
        additions.add(row);
        additionsTotal = additionsTotal.add(zeroIfNull(r.costingRate()));
      } else if (DEDUCTION_SUBCATEGORIES.contains(subcategory)) {
        deductions.add(row);
        deductionsTotal = deductionsTotal.add(zeroIfNull(r.costingRate()));
      }
      // A rate row whose cost item is neither an addition nor a deduction subcategory is ignored
      // (defensive — category-'8' items are always one or the other).
    }
    BigDecimal originalRate = zeroIfNull(s.originalRate());
    BigDecimal finalRate = originalRate.add(additionsTotal).subtract(deductionsTotal);
    Integer percentTotal = sumInts(s.groundBasePct(), s.grapplePct(), s.skylinePct(), s.highleadPct(),
        s.helicopterPct(), s.otherSkiddingPct());
    Integer actualHarvested = sumInts(s.coniferousVolume(), s.deciduousVolume());

    return new Sample(
        s.id(), s.revisionCount(), s.contractId(), s.cutBlock(),
        s.groundBasePct(), s.grapplePct(), s.skylinePct(), s.highleadPct(), s.helicopterPct(),
        s.otherSkiddingPct(), percentTotal,
        s.skylineSlopeDistance(), s.skylineSupportNumber(), normalize(s.supportAverageDistance()),
        normalize(s.distance()), normalize(s.cycleTime()),
        IND_YES.equals(s.uphillDirectionInd()), IND_YES.equals(s.waterDumpDestinationInd()),
        s.skidTypeCode(), skidType.get(s.skidTypeCode()),
        s.coniferousVolume(), s.deciduousVolume(), actualHarvested,
        normalize(s.originalRate()), normalize(additionsTotal), normalize(deductionsTotal),
        normalize(finalRate), additions.size(), deductions.size(), additions, deductions);
  }

  private static BigDecimal zeroIfNull(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  /** Sum of the given values treating null as 0; null (never entered) is fine as 0 for a roll-up. */
  private static Integer sumInts(Integer... values) {
    int total = 0;
    for (Integer value : values) {
      if (value != null) {
        total += value;
      }
    }
    return total;
  }

  /**
   * Strip an Oracle {@code NUMBER(18,4)} money/rate value to its natural form so a whole value
   * serializes as an integer ({@code 5}, not {@code 5.0000}) and a decimal drops trailing zeros
   * ({@code 28.5}) — Schedule 1/2/4 wire-contract parity. Null-safe.
   */
  private static BigDecimal normalize(BigDecimal value) {
    if (value == null) {
      return null;
    }
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
