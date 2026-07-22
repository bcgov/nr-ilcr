package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckFieldIssue;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageCheckResult;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8RateRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleCheckResult;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
@Slf4j
public class Schedule8Service {

  private static final String STATUS_DRAFT = "D";
  private static final String IND_YES = "Y";

  // Check Status (Story 14.6) — bundle keys (controller resolves to verbatim text, AD-8) + outcomes.
  private static final String OUTCOME_MET = "MET";
  private static final String OUTCOME_ISSUES = "ISSUES";
  private static final String MSG_REQUIRED = "missingRequiredFieldMsg";
  private static final String MSG_AT_LEAST_ONE_SAMPLE = "treeToTruckReportAtleastOneSample";
  private static final String MSG_HARVEST_ZERO = "invalidLowerRangeZeroErrorMsg";
  private static final String MSG_PERCENT_100 = "skiddingYardingEqualsCentPercent";
  private static final String MSG_SCHEDULE_MET = "scheduleRequirementsMetMsg";
  /** Legacy sentinel: {@code TSA_NUMBER == "TFL"} means the page uses a TFL (else a supply block). */
  private static final String TFL_MARKER = "TFL";
  private static final int PERCENT_TOTAL = 100;

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

  /**
   * Save (create-or-edit) one Schedule 8 report page and return the recomputed document (Story 14.2,
   * S01/S04). The mill/year context is validated in the controller (AD-4). Enforces the Draft gate
   * (AD-9), per-page optimistic locking, and the TFL⇄supply-block mutual exclusion (S10/BR-03).
   *
   * <p>{@code request.id()} null → CREATE (insert the page, revision 0→1); present → EDIT (bump the
   * page revision, then re-stamp its fields). The whole method is one transaction: a persistence fault
   * rolls back and surfaces as 500 ({@code scheduleNotSavedErrorMsg}), logging type-only (AD-11). The
   * TFL-resolves-to-Road-Group check (S22) is deferred with the {@code RoadGroupUtil} port (14.1 §2).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the page fields + optimistic-lock token (validated)
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit)
   * @return the recomputed Schedule 8 document
   */
  @Transactional
  public Schedule8Response savePage(long millId, int year, Schedule8PageRequest request,
      boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    // TFL vs Supply Block are mutually exclusive (BR-03): a TFL selection clears the supply block and
    // vice-versa — normalized server-side so exactly one is ever stored.
    boolean usesTfl = isNotBlank(request.tflNumber());
    String tflNumber = usesTfl ? request.tflNumber().trim() : null;
    String supplyBlock = usesTfl ? null : trimToNull(request.supplyBlock());
    String tsaNumber = trimToNull(request.tsaNumber());
    try {
      if (request.id() == null) {
        int id = repository.insertPage(millId, year, trimToNull(request.supportCentre()),
            trimToNull(request.region()), trimToNull(request.becZone()), tsaNumber, supplyBlock,
            tflNumber, trimToNull(request.cuttingPermit()), trimToNull(request.license()),
            trimToNull(request.division()), trimToNull(request.contact()),
            trimToNull(request.phone()), trimToNull(request.comments()), user);
        repository.bumpPageRevision(id, 0, user); // 0 -> 1 (monotonic, mirrors Schedule 2/4)
      } else {
        int expectedRevision = request.revisionCount() == null ? 0 : request.revisionCount();
        if (repository.bumpPageRevision(request.id(), expectedRevision, user) == 0) {
          throw new StaleRevisionException();
        }
        repository.updatePageFields(request.id(), trimToNull(request.supportCentre()),
            trimToNull(request.region()), trimToNull(request.becZone()), tsaNumber, supplyBlock,
            tflNumber, trimToNull(request.cuttingPermit()), trimToNull(request.license()),
            trimToNull(request.division()), trimToNull(request.contact()),
            trimToNull(request.phone()), trimToNull(request.comments()), user);
      }
    } catch (StaleRevisionException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Schedule 8 page save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule8(millId, year, callerMayEdit);
  }

  /**
   * Delete a whole Schedule 8 report page — the page, its samples, and all their rate details (BR-05,
   * S07) — for a mill/year, targeted by the page {@code id}. Enforces the same Draft gate as save.
   * Idempotent: an absent/unknown id (or one not belonging to this mill/year) is a no-op that still
   * returns success (never 404), mirroring Schedule 2/4. Context is validated in the controller (AD-4).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param id the page id to delete
   */
  @Transactional
  public void deletePage(long millId, int year, int id) {
    requireDraft(millId, year);
    if (!repository.pageExists(id, millId, year)) {
      return; // idempotent — nothing to remove
    }
    try {
      repository.deletePage(id);
    } catch (DataAccessException ex) {
      log.warn("Schedule 8 page delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  /**
   * Save (create-or-edit) one sample under a page and return the recomputed document (Story 14.3,
   * S01/S05). Draft gate (AD-9), per-sample optimistic lock, unknown page/sample → 404. Field/cross-
   * field validation (Contract ID, per-% 0–100, sum ≤ 100, Helicopter/Other conditionals, ranges) is
   * on the request DTO; this method persists and recomputes. The Y/N indicator columns are written
   * from the request's Booleans. One transaction; a persistence fault → 500 (type-only log, AD-11).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param pageId the parent page id (its {@code TREE_TO_TRUCK_REPORT_ID})
   * @param request the sample fields + optimistic-lock token (validated)
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit)
   * @return the recomputed Schedule 8 document
   */
  @Transactional
  public Schedule8Response saveSample(long millId, int year, int pageId,
      Schedule8SampleRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    if (!repository.pageExists(pageId, millId, year)) {
      throw new ScheduleNotFoundException(); // 404 — no such page to attach the sample to
    }
    String uphill = toIndicator(request.uphillDirection());
    String waterDump = toIndicator(request.waterDumpDestination());
    try {
      if (request.id() == null) {
        int id = repository.insertSample(pageId, trimToNull(request.contractId()),
            trimToNull(request.cutBlock()), request.groundBasePct(), request.grapplePct(),
            request.skylinePct(), request.highleadPct(), request.helicopterPct(),
            request.otherSkiddingPct(), request.skylineSlopeDistance(),
            request.skylineSupportNumber(), request.supportAvgDistance(), request.cycleTime(),
            request.distance(), waterDump, uphill, trimToNull(request.skidTypeCode()),
            request.coniferousVolume(), request.deciduousVolume(), request.originalRate(), user);
        repository.bumpSampleRevision(id, 0, user); // 0 -> 1
      } else {
        if (!repository.sampleExists(request.id(), pageId)) {
          throw new ScheduleNotFoundException(); // 404 — sample is not under this page
        }
        int expectedRevision = request.revisionCount() == null ? 0 : request.revisionCount();
        if (repository.bumpSampleRevision(request.id(), expectedRevision, user) == 0) {
          throw new StaleRevisionException();
        }
        repository.updateSampleFields(request.id(), trimToNull(request.contractId()),
            trimToNull(request.cutBlock()), request.groundBasePct(), request.grapplePct(),
            request.skylinePct(), request.highleadPct(), request.helicopterPct(),
            request.otherSkiddingPct(), request.skylineSlopeDistance(),
            request.skylineSupportNumber(), request.supportAvgDistance(), request.cycleTime(),
            request.distance(), waterDump, uphill, trimToNull(request.skidTypeCode()),
            request.coniferousVolume(), request.deciduousVolume(), request.originalRate(), user);
      }
    } catch (StaleRevisionException | ScheduleNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Schedule 8 sample save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule8(millId, year, callerMayEdit);
  }

  /**
   * Delete one sample (cascading its rate details) under a page and return the recomputed document
   * (Story 14.3, S08 / BR-05). Draft gate (AD-9). Idempotent: an unknown page or sample id is a no-op
   * success (never 404), mirroring Schedule 2/4 deletes.
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param pageId the parent page id
   * @param sampleId the sample id to delete
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @return the recomputed Schedule 8 document
   */
  @Transactional
  public Schedule8Response deleteSample(
      long millId, int year, int pageId, int sampleId, boolean callerMayEdit) {
    requireDraft(millId, year);
    if (repository.pageExists(pageId, millId, year) && repository.sampleExists(sampleId, pageId)) {
      try {
        repository.deleteSample(sampleId);
      } catch (DataAccessException ex) {
        log.warn("Schedule 8 sample delete failed for mill {} year {} [{}]",
            millId, year, ex.getClass().getSimpleName());
        throw new ScheduleNotSavedException();
      }
    }
    return getSchedule8(millId, year, callerMayEdit);
  }

  /**
   * Add or edit one rate-detail row under a sample and return the recomputed document (Story 14.4,
   * S01/S06). {@code request.id()} null → ADD (insert at revision 0); present → EDIT (optimistic-lock
   * update). Whether the row is an addition or a deduction is derived on the read from its cost item's
   * subcategory — not stored here. Draft gate (AD-9); unknown sample or (on edit) unknown row → 404;
   * stale → 409; persistence fault → 500 (type-only log, AD-11). One transaction.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param sampleId the parent sample id
   * @param rowId the rate-row id to edit; null to add
   * @param request the rate-row fields + optimistic-lock token (validated)
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit)
   * @return the recomputed Schedule 8 document (the sample's totals + finalRate + counts update)
   */
  @Transactional
  public Schedule8Response saveRate(long millId, int year, int sampleId, Integer rowId,
      Schedule8RateRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    if (!repository.sampleInMillYear(sampleId, millId, year)) {
      throw new ScheduleNotFoundException(); // 404 — no such sample under this mill/year
    }
    try {
      if (rowId == null) {
        repository.insertRate(sampleId, trimToNull(request.costTypeCode()), request.costItemCode(),
            trimToNull(request.itemDescription()), request.costingRate(), user);
      } else {
        if (!repository.rateExists(rowId, sampleId)) {
          throw new ScheduleNotFoundException(); // 404 — row is not under this sample
        }
        int expectedRevision = request.revisionCount() == null ? 0 : request.revisionCount();
        int updated = repository.updateRateRow(rowId, expectedRevision,
            trimToNull(request.costTypeCode()), request.costItemCode(),
            trimToNull(request.itemDescription()), request.costingRate(), user);
        if (updated == 0) {
          throw new StaleRevisionException();
        }
      }
    } catch (StaleRevisionException | ScheduleNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Schedule 8 rate save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule8(millId, year, callerMayEdit);
  }

  /**
   * Delete one rate-detail row under a sample and return the recomputed document (Story 14.4, S09 /
   * BR-05). Draft gate (AD-9). Idempotent: an unknown sample or row id (or a row not under this
   * sample) is a no-op success (never 404, never deletes another sample's row).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param sampleId the parent sample id
   * @param rowId the rate-row id to delete
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @return the recomputed Schedule 8 document
   */
  @Transactional
  public Schedule8Response deleteRate(
      long millId, int year, int sampleId, int rowId, boolean callerMayEdit) {
    requireDraft(millId, year);
    if (repository.sampleInMillYear(sampleId, millId, year) && repository.rateExists(rowId, sampleId)) {
      try {
        repository.deleteRateRow(rowId);
      } catch (DataAccessException ex) {
        log.warn("Schedule 8 rate delete failed for mill {} year {} [{}]",
            millId, year, ex.getClass().getSimpleName());
        throw new ScheduleNotSavedException();
      }
    }
    return getSchedule8(millId, year, callerMayEdit);
  }

  /** Map a nullable request Boolean to the legacy Y/N indicator column value (null stays null). */
  private static String toIndicator(Boolean value) {
    if (value == null) {
      return null;
    }
    return value ? IND_YES : "N";
  }

  /**
   * Evaluate the Schedule 8 completion requirement (BR-07, Check Status) for a mill/year — read-only
   * (AD-5), mutates nothing (Story 14.6, all-pages sweep). Reuses the assembled read model and applies
   * the Check-Status-only rules per page and sample. {@code outcome} is {@code MET} only when EVERY
   * page (and its samples) passes. Emits bundle KEYS; the controller resolves verbatim text (AD-8). A
   * mill/year with no pages is vacuously MET.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return the MET/ISSUES outcome + per-page/per-sample/per-field breakdown
   */
  @Transactional(readOnly = true)
  public Schedule8CheckStatusResponse checkStatus(long millId, int year) {
    return evaluate(getSchedule8(millId, year, false).pages());
  }

  /**
   * Evaluate Check Status for a single page (Story 14.6, S14/BR-09) — read-only, mutates nothing.
   * Scopes the sweep to the one page (legacy {@code Schedule8DetailMB.checkStatus} single-page
   * overload). A {@code pageId} not present for the mill/year yields a vacuously-MET empty result.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param pageId the page to check
   * @return the MET/ISSUES outcome scoped to that page
   */
  @Transactional(readOnly = true)
  public Schedule8CheckStatusResponse checkStatusPage(long millId, int year, int pageId) {
    List<Page> scoped = getSchedule8(millId, year, false).pages().stream()
        .filter(p -> p.id() != null && p.id() == pageId)
        .toList();
    return evaluate(scoped);
  }

  /** Apply the Check-Status rules to the given pages and build the all-or-nothing result. */
  private Schedule8CheckStatusResponse evaluate(List<Page> pages) {
    List<Schedule8PageCheckResult> pageResults = new ArrayList<>(pages.size());
    boolean scheduleMet = true;
    for (Page page : pages) {
      List<Schedule8CheckFieldIssue> pageIssues = new ArrayList<>();
      requireField(pageIssues, "Contact", page.contact());
      requireField(pageIssues, "Phone", page.phone());
      // TFL vs Supply Block: TSA_NUMBER == "TFL" means the page uses a TFL, else a supply block.
      if (TFL_MARKER.equals(page.tsaNumber())) {
        requireField(pageIssues, "TFL #", page.tflNumber());
      } else {
        requireField(pageIssues, "Supply Block", page.supplyBlock());
      }
      if (page.samples().isEmpty()) {
        pageIssues.add(issue("Sample", MSG_AT_LEAST_ONE_SAMPLE));
      }

      List<Schedule8SampleCheckResult> sampleResults = new ArrayList<>(page.samples().size());
      boolean allSamplesMet = true;
      for (Sample sample : page.samples()) {
        List<Schedule8CheckFieldIssue> issues = evaluateSample(sample);
        boolean sampleMet = issues.isEmpty();
        allSamplesMet &= sampleMet;
        sampleResults.add(new Schedule8SampleCheckResult(sample.id(), sampleMet, issues));
      }

      boolean pageMet = pageIssues.isEmpty() && allSamplesMet;
      scheduleMet &= pageMet;
      pageResults.add(new Schedule8PageCheckResult(page.id(), pageMet, pageIssues, sampleResults));
    }
    String outcome = scheduleMet ? OUTCOME_MET : OUTCOME_ISSUES;
    List<MessageInfo> messages =
        scheduleMet ? List.of(new MessageInfo(MSG_SCHEDULE_MET, null)) : List.of();
    return new Schedule8CheckStatusResponse(outcome, messages, pageResults);
  }

  /** The Check-Status-only field rules for one sample (S28–S30, S16 check half, FLD-003/007). */
  private List<Schedule8CheckFieldIssue> evaluateSample(Sample sample) {
    List<Schedule8CheckFieldIssue> issues = new ArrayList<>();
    requireField(issues, "Cut Block", sample.cutBlock());
    // Skyline supports are required only when Skyline % > 0 (JIRA-365).
    if (sample.skylinePct() != null && sample.skylinePct() > 0) {
      requireField(issues, "Slope Distance", sample.skylineSlopeDistance());
      requireField(issues, "Support Number", sample.skylineSupportNumber());
      requireField(issues, "Support Avg Dist", sample.supportAvgDistance());
    }
    requireField(issues, "Coniferous", sample.coniferousVolume());
    requireField(issues, "Deciduous", sample.deciduousVolume());
    // Actual Harvested > 0 is checked only once at least one volume was entered (legacy parity).
    if ((sample.coniferousVolume() != null || sample.deciduousVolume() != null)
        && (sample.actualHarvested() == null || sample.actualHarvested() <= 0)) {
      issues.add(issue("Actual Harvested", MSG_HARVEST_ZERO));
    }
    requireField(issues, "Original TtT Rate", sample.originalRate());
    // The exact-100 rule Save deliberately omits (14.3 §Decision) lives here (FLD-003, S16 check half).
    if (sample.percentTotal() == null || sample.percentTotal() != PERCENT_TOTAL) {
      issues.add(issue("Skidding/Yarding", MSG_PERCENT_100));
    }
    return issues;
  }

  /** Flag {@code field} as missing (Value Required) when {@code value} is null/blank. */
  private static void requireField(
      List<Schedule8CheckFieldIssue> issues, String field, Object value) {
    boolean missing = value == null || (value instanceof String s && s.isBlank());
    if (missing) {
      issues.add(issue(field, MSG_REQUIRED));
    }
  }

  private static Schedule8CheckFieldIssue issue(String field, String messageKey) {
    return new Schedule8CheckFieldIssue(field, new MessageInfo(messageKey, null));
  }

  /** The Draft gate shared by the writes: the Schedules 1–10 track must be Draft (else 409). */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  private static boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
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
