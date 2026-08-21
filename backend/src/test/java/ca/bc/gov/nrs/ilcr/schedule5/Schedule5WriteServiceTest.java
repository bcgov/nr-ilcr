package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.exception.RevisionCountRequiredException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampRequest;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryEntry;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * The Schedule 5 write branch matrices, at the service seam — every decision that is either
 * invisible end-to-end or too expensive to provoke through the database.
 *
 * <p>Four things live here and nowhere else:
 *
 * <ul>
 *   <li><strong>The § ITEM WRITE MAP as an ordered, per-argument assertion.</strong> An IT can only
 *       observe the twelve rows through the recomputed document, where a volume written to the
 *       wrong item id or a cost written into item 141 shows up — if at all — as a changed total.
 *       Capturing the twelve {@code upsertCostDetail} calls pins the item ids AND the volume/cost
 *       asymmetry directly.
 *   <li><strong>The null-{@code revisionCount} direct-caller guard.</strong> The API's
 *       {@code @Validated OnUpdate} group makes that unreachable over HTTP, so only a direct call
 *       can reach the unboxing (the 8.2 lesson: a validation group protects one entry point, not a
 *       method).
 *   <li><strong>The 404-vs-409 disambiguation.</strong> Provoking a genuinely stale token through
 *       the database needs two concurrent transactions; here it is a one-line stub.
 *   <li><strong>The two narrow cost ranges</strong> that {@code CategoryEntry} cannot express
 *       declaratively, including the deliberate {@code wagesAndBenefits} outlier (deviation (F))
 *       which must NOT be caught by the standard check.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule5Service — the write branch matrices")
class Schedule5WriteServiceTest {

  private static final long MILL = 670L;
  private static final int YEAR = 2017;
  private static final int CAMP = 8201;
  private static final String USER = "tester";

  @Mock private Schedule5Repository repository;

  private Schedule5Service service;

  @BeforeEach
  void setUp() {
    service = new Schedule5Service(repository);
    // Draft by default; the document rebuild at the end of every write reads an empty mill. These
    // four are lenient() INDIVIDUALLY — the rejection tests never reach the rebuild, so class-wide
    // LENIENT would be the alternative, and that would also disable unnecessary-stubbing detection
    // for every stub a test declares itself.
    // findTrackStatusForUpdate, NOT findTrackStatus: the write gate takes a FOR UPDATE row lock so
    // the
    // status cannot change under the transaction and the BR-02 count-then-insert is serialized.
    // Stubbing
    // the unlocked read here instead would let a future revert to it pass this whole class
    // silently.
    lenient().when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findCamps(anyLong(), anyInt())).thenReturn(List.of());
    lenient().when(repository.findCostDetails(anyLong(), anyInt())).thenReturn(List.of());
    lenient().when(repository.nextCampReportId()).thenReturn(9501);
  }

  /** A full twelve-category request; {@code revisionCount} matters only on update. */
  private static CampRequest request(String campName, Integer revisionCount) {
    return new CampRequest(
        campName,
        new BigDecimal("42.50"),
        60,
        new BigDecimal("120000"),
        true,
        "A comment.",
        new CategoryEntry(new BigDecimal("96000"), 480000), // 56  catering
        new CategoryEntry(new BigDecimal("120000"), 960000), // 58  wages
        new CategoryEntry(new BigDecimal("120000"), 120000), // 59  depreciation
        new CategoryEntry(new BigDecimal("120000"), 60000), // 60  general
        new CategoryEntry(new BigDecimal("80000"), 111), // 141 other camp — cost IGNORED
        new CategoryEntry(new BigDecimal("222"), 44000), // 61  recoveries — volume IGNORED
        new CategoryEntry(new BigDecimal("90000"), 180000), // 63  crew
        new CategoryEntry(new BigDecimal("120000"), 90000), // 64  land
        new CategoryEntry(new BigDecimal("120000"), 15000), // 65  rail
        new CategoryEntry(new BigDecimal("120000"), 12000), // 66  air
        new CategoryEntry(new BigDecimal("120000"), 6000), // 67  water
        new CategoryEntry(new BigDecimal("60000"), 333), // 142 other access — cost IGNORED
        revisionCount);
  }

  private static CampRequest withCost(int categoryIndex, Integer cost) {
    CampRequest base = request("Cost Range Camp", 0);
    CategoryEntry entry = new CategoryEntry(null, cost);
    return new CampRequest(
        base.campName(),
        base.roadDistanceToOperatingArea(),
        base.sizeOfCamp(),
        base.associatedCampVolume(),
        base.isolatedCamp(),
        base.comments(),
        categoryIndex == 0 ? entry : base.cateringAndFood(),
        categoryIndex == 1 ? entry : base.wagesAndBenefits(),
        categoryIndex == 2 ? entry : base.depreciationLease(),
        categoryIndex == 3 ? entry : base.generalCampExpenses(),
        base.otherCampExpenses(),
        categoryIndex == 5 ? entry : base.recoveries(),
        categoryIndex == 6 ? entry : base.crewTransportation(),
        base.equipAndSuppliesLand(),
        base.equipAndSuppliesRail(),
        base.equipAndSuppliesAir(),
        base.equipAndSuppliesWater(),
        base.otherAccessExpenses(),
        base.revisionCount());
  }

  @Nested
  @DisplayName("the Draft gate (BR-06, AC7)")
  class DraftGate {

    @Test
    @DisplayName("a non-Draft track rejects every mutation before anything is read or written")
    void nonDraftRejectsAllThree() {
      when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.of("S"));

      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, request("New Camp", null), true, USER))
          .isInstanceOf(ScheduleNotEditableException.class);
      assertThatThrownBy(
              () -> service.updateCamp(MILL, YEAR, CAMP, request("New Camp", 0), true, USER))
          .isInstanceOf(ScheduleNotEditableException.class);
      assertThatThrownBy(() -> service.deleteCamp(MILL, YEAR, CAMP, true))
          .isInstanceOf(ScheduleNotEditableException.class);

      // The gate is the FIRST statement, so not even the name-uniqueness probe should have run.
      verify(repository, never()).countCampsNamed(anyLong(), anyInt(), anyString());
      verify(repository, never())
          .insertCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString());
      verify(repository, never()).deleteCamp(anyInt(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("a MISSING status row is not Draft either — no report status means not editable")
    void absentTrackStatusRejects() {
      when(repository.findTrackStatusForUpdate(MILL, YEAR)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, request("New Camp", null), true, USER))
          .isInstanceOf(ScheduleNotEditableException.class);
    }
  }

  @Nested
  @DisplayName("the twelve-row ITEM WRITE MAP (AC1)")
  class ItemWriteMap {

    @Test
    @DisplayName("exactly twelve rows, in legacy order, with the 141/61/142 volume-cost asymmetry")
    void writesTwelveRowsWithTheLegacyAsymmetry() {
      service.addCamp(MILL, YEAR, request("New Camp", null), true, USER);

      ArgumentCaptor<Integer> items = ArgumentCaptor.forClass(Integer.class);
      ArgumentCaptor<BigDecimal> volumes = ArgumentCaptor.forClass(BigDecimal.class);
      ArgumentCaptor<Integer> costs = ArgumentCaptor.forClass(Integer.class);
      verify(repository, org.mockito.Mockito.times(12))
          .upsertCostDetail(
              eq(9501), items.capture(), volumes.capture(), costs.capture(), eq(USER));

      // Legacy's own dispatch order, Schedule5DAO.java:387-398.
      assertThat(items.getAllValues())
          .containsExactly(56, 58, 59, 60, 141, 61, 63, 64, 65, 66, 67, 142);

      // Items 57, 62 and 68 are NEVER written from this path: 57 is registered but dead, and 62/68
      // are Story 7.4's sub-page rows.
      assertThat(items.getAllValues()).doesNotContain(57, 62, 68);

      // The asymmetry: item 141 gets its volume and a hard-coded NULL cost even though the request
      // carried 111; item 61 gets its cost and a hard-coded NULL volume even though it carried 222;
      // item 142 the same as 141, with 333 discarded.
      assertThat(volumes.getAllValues().get(4)).isEqualByComparingTo("80000");
      assertThat(costs.getAllValues().get(4)).isNull();
      assertThat(volumes.getAllValues().get(5)).isNull();
      assertThat(costs.getAllValues().get(5)).isEqualTo(44000);
      assertThat(volumes.getAllValues().get(11)).isEqualByComparingTo("60000");
      assertThat(costs.getAllValues().get(11)).isNull();
    }

    @Test
    @DisplayName("the write map IS SINGLE_ROW_ITEMS — the read routing and the writes cannot drift")
    void writeMapEqualsTheSingleRowItemSet() {
      service.addCamp(MILL, YEAR, request("New Camp", null), true, USER);

      ArgumentCaptor<Integer> items = ArgumentCaptor.forClass(Integer.class);
      verify(repository, org.mockito.Mockito.times(12))
          .upsertCostDetail(anyInt(), items.capture(), any(), any(), anyString());

      // Tied to the CONSTANT, not to a duplicated literal: a cost item added to SINGLE_ROW_ITEMS
      // but not to writeCategoryRows (or vice versa) would serve rows it never writes, or write
      // rows it then drops as unknown on the read path. 7.1's everyKnownItemIsRouted ties the read
      // half to the same set.
      assertThat(new HashSet<>(items.getAllValues())).isEqualTo(Schedule5Service.SINGLE_ROW_ITEMS);
    }

    @Test
    @DisplayName(
        "per-category volumes are stored VERBATIM — never re-derived from the camp volume "
            + "(deviation (A))")
    void categoryVolumesAreNotReDerived() {
      service.addCamp(MILL, YEAR, request("New Camp", null), true, USER);

      ArgumentCaptor<BigDecimal> volumes = ArgumentCaptor.forClass(BigDecimal.class);
      verify(repository, org.mockito.Mockito.times(12))
          .upsertCostDetail(anyInt(), anyInt(), volumes.capture(), any(), anyString());

      // The request's camp volume is 120000 but catering carries 96000 and crew 90000. BR-03's
      // propagation is a CLIENT-side ajax listener (Schedule5MB.updateCampVolumes is never called
      // from save()), so a server that "helpfully" stamped the camp volume onto all eleven
      // categories would overwrite a licensee's deliberate per-category edit.
      assertThat(volumes.getAllValues().get(0)).isEqualByComparingTo("96000");
      assertThat(volumes.getAllValues().get(6)).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("an omitted category clears both halves rather than being skipped")
    void omittedCategoryWritesNulls() {
      CampRequest sparse =
          new CampRequest(
              "Sparse Camp",
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      service.addCamp(MILL, YEAR, sparse, true, USER);

      // Still twelve calls — "null means cleared" has to hold for the whole request, and legacy's
      // form always posted all twelve rows, so a partial grid is not a legacy-reachable state.
      verify(repository, org.mockito.Mockito.times(12))
          .upsertCostDetail(eq(9501), anyInt(), eq(null), eq(null), eq(USER));
    }
  }

  @Nested
  @DisplayName("BR-02 camp-name uniqueness (AC3)")
  class NameUniqueness {

    @Test
    @DisplayName("a create checks the UNSCOPED count and rejects a duplicate before any write")
    void createRejectsDuplicate() {
      when(repository.countCampsNamed(MILL, YEAR, "New Camp")).thenReturn(1);

      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, request("New Camp", null), true, USER))
          .isInstanceOf(CampNameConflictException.class);

      verify(repository, never())
          .insertCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString());
      verify(repository, never()).upsertCostDetail(anyInt(), anyInt(), any(), any(), anyString());
    }

    @Test
    @DisplayName("an edit EXCLUDES itself by camp id, so an unrenamed save is not a self-conflict")
    void updateExcludesItselfById() {
      when(repository.countCampsNamedExcluding(MILL, YEAR, "Edit Target Camp", CAMP)).thenReturn(0);
      when(repository.updateCamp(
              eq(CAMP),
              eq(MILL),
              eq(YEAR),
              eq(0),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString()))
          .thenReturn(1);

      service.updateCamp(MILL, YEAR, CAMP, request("Edit Target Camp", 0), true, USER);

      // Excluding by ID, never by the old name — a rename would have invalidated a name-based
      // exclusion. And the create-path query must not be used on an edit: legacy disarmed its check
      // entirely after a new camp's first save (:315), which is deviation (I), not ported.
      verify(repository).countCampsNamedExcluding(MILL, YEAR, "Edit Target Camp", CAMP);
      verify(repository, never()).countCampsNamed(anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName("the name is TRIMMED for both the comparison and the stored value (deviation (I))")
    void nameIsTrimmedOnBothPaths() {
      service.addCamp(MILL, YEAR, request("  Padded Camp  ", null), true, USER);

      // Legacy trimmed only before the insert-path CHECK (:289) and persisted the untrimmed value
      // either way (Schedule5DAO.java:373), so " Cedar " and "Cedar" could coexist while only one
      // of
      // them ever matched.
      verify(repository).countCampsNamed(MILL, YEAR, "Padded Camp");
      verify(repository)
          .insertCamp(
              eq(9501),
              eq(MILL),
              eq(YEAR),
              eq("Padded Camp"),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              eq(USER));
    }

    @Test
    @DisplayName("the name is trimmed on the UPDATE path too — deviation (I) is 'both paths'")
    void nameIsTrimmedOnTheUpdatePathToo() {
      when(repository.countCampsNamedExcluding(MILL, YEAR, "Padded Camp", CAMP)).thenReturn(0);
      when(repository.updateCamp(
              eq(CAMP),
              eq(MILL),
              eq(YEAR),
              eq(0),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString()))
          .thenReturn(1);

      service.updateCamp(MILL, YEAR, CAMP, request("  Padded Camp  ", 0), true, USER);

      // Deviation (I)'s whole point is BOTH paths: legacy compared the stored value UNTRIMMED on
      // edit (:309), which is half of how " Cedar " and "Cedar" coexisted.
      verify(repository).countCampsNamedExcluding(MILL, YEAR, "Padded Camp", CAMP);
      verify(repository)
          .updateCamp(
              eq(CAMP),
              eq(MILL),
              eq(YEAR),
              eq(0),
              eq("Padded Camp"),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              eq(USER));
    }

    @Test
    @DisplayName("a rename onto ANOTHER camp's name rejects on the update path, before any write")
    void updateRejectsARenameOntoAnotherCampsName() {
      when(repository.countCampsNamedExcluding(MILL, YEAR, "Taken Name", CAMP)).thenReturn(1);
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(1);

      // The conflict BRANCH, not just the query call: updateExcludesItselfById above only proves
      // the right query runs — this proves its result is acted on (the review's regression gap).
      assertThatThrownBy(
              () -> service.updateCamp(MILL, YEAR, CAMP, request("Taken Name", 0), true, USER))
          .isInstanceOf(CampNameConflictException.class);

      verify(repository, never())
          .updateCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString());
      verify(repository, never()).upsertCostDetail(anyInt(), anyInt(), any(), any(), anyString());
    }

    @Test
    @DisplayName("a name conflict on an UNKNOWN or foreign id is the 404, never a leaked 409")
    void conflictOnAnUnknownIdIsNotFound() {
      when(repository.countCampsNamedExcluding(MILL, YEAR, "Taken Name", CAMP)).thenReturn(1);
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(0);

      // A 409 "Camp name already exists." for an id the caller cannot see would both contradict
      // the API's documented 404 and confirm the name exists across the tenancy boundary.
      assertThatThrownBy(
              () -> service.updateCamp(MILL, YEAR, CAMP, request("Taken Name", 0), true, USER))
          .isInstanceOf(CampNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("AR11 optimistic locking (AC8)")
  class OptimisticLock {

    @Test
    @DisplayName("a null token is a 400, never a coerced 409 — the direct-caller guard")
    void nullRevisionCountIsBadRequest() {
      assertThatThrownBy(
              () ->
                  service.updateCamp(
                      MILL, YEAR, CAMP, request("Edit Target Camp", null), true, USER))
          .isInstanceOf(RevisionCountRequiredException.class);

      verify(repository, never())
          .updateCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString());
    }

    @Test
    @DisplayName("zero rows + the camp does NOT exist under this mill/year -> 404")
    void zeroRowsAndAbsent_isNotFound() {
      when(repository.updateCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString()))
          .thenReturn(0);
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(0);

      assertThatThrownBy(
              () ->
                  service.updateCamp(MILL, YEAR, CAMP, request("Edit Target Camp", 0), true, USER))
          .isInstanceOf(CampNotFoundException.class);
    }

    @Test
    @DisplayName("zero rows + the camp DOES exist -> 409 stale token")
    void zeroRowsButPresent_isStaleRevision() {
      when(repository.updateCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString()))
          .thenReturn(0);
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(1);

      // The guarded UPDATE cannot tell these two apart on its own — both are zero rows. Swapping
      // the
      // outcomes would tell a licensee to reload when the camp is simply gone, or vice versa.
      assertThatThrownBy(
              () ->
                  service.updateCamp(MILL, YEAR, CAMP, request("Edit Target Camp", 0), true, USER))
          .isInstanceOf(StaleRevisionException.class);
    }

    @Test
    @DisplayName("no detail row is written when the guarded update fails")
    void staleUpdateWritesNoDetails() {
      when(repository.updateCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString()))
          .thenReturn(0);
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(1);

      assertThatThrownBy(
              () ->
                  service.updateCamp(MILL, YEAR, CAMP, request("Edit Target Camp", 0), true, USER))
          .isInstanceOf(StaleRevisionException.class);

      verify(repository, never()).upsertCostDetail(anyInt(), anyInt(), any(), any(), anyString());
    }
  }

  @Nested
  @DisplayName("delete (AC5)")
  class Delete {

    @Test
    @DisplayName(
        "children are deleted BEFORE the parent — mandatory, the FK is ON DELETE NO ACTION")
    void deletesChildrenThenParent() {
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(1);
      when(repository.deleteCamp(CAMP, MILL, YEAR)).thenReturn(1);

      service.deleteCamp(MILL, YEAR, CAMP, true);

      // ILCR_LCRD_CMP_RPT_FK is NO ACTION in delivery (Task 1 gate (ii)), so the reverse order
      // raises ORA-02292 there. The LOCAL snapshot has no such FK, so this ordering assertion is
      // the
      // only thing standing between the port and a production-only failure.
      InOrder order = inOrder(repository);
      order.verify(repository).deleteCostDetailsForCamp(CAMP, MILL, YEAR);
      order.verify(repository).deleteCamp(CAMP, MILL, YEAR);
    }

    @Test
    @DisplayName("an unknown or foreign camp is a 404 and deletes nothing at all")
    void unknownCampDeletesNothing() {
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(0);

      assertThatThrownBy(() -> service.deleteCamp(MILL, YEAR, CAMP, true))
          .isInstanceOf(CampNotFoundException.class);

      verify(repository, never()).deleteCostDetailsForCamp(anyInt(), anyLong(), anyInt());
      verify(repository, never()).deleteCamp(anyInt(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("a parent delete that affects zero rows is a 404, not a silent success")
    void zeroRowParentDeleteIsNotFound() {
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(1);
      when(repository.deleteCamp(CAMP, MILL, YEAR)).thenReturn(0);

      // The 8.2 lesson: a delete that ignored its row count answered 200 "Data deleted
      // successfully"
      // while the row was still there.
      assertThatThrownBy(() -> service.deleteCamp(MILL, YEAR, CAMP, true))
          .isInstanceOf(CampNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("the two narrow cost ranges CategoryEntry cannot express (AC6)")
  class CostRanges {

    @Test
    @DisplayName("an ordinary category is capped at +/-9,999,999 on both sides")
    void standardCategoryRange() {
      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, withCost(0, 10_000_000), true, USER))
          .isInstanceOf(CampCostOutOfRangeException.class)
          .hasMessage("costSize7ValidatorErrorMsg");
      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, withCost(6, -10_000_000), true, USER))
          .isInstanceOf(CampCostOutOfRangeException.class)
          .hasMessage("costSize7ValidatorErrorMsg");

      // Both bounds are INCLUSIVE.
      assertThatCode(() -> service.addCamp(MILL, YEAR, withCost(0, 9_999_999), true, USER))
          .doesNotThrowAnyException();
      assertThatCode(() -> service.addCamp(MILL, YEAR, withCost(0, -9_999_999), true, USER))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("wagesAndBenefits is DELIBERATELY wider — deviation (F), preserved not fixed")
    void wagesIsTheOutlier() {
      // Its input is missing the costSize attribute in BOTH legacy pages, so ILCRCostValidator
      // falls
      // through to its default "8" -> +/-99,999,999. A tidy implementation that treated all eleven
      // categories alike would reject this value, silently narrowing what legacy accepted.
      assertThatCode(() -> service.addCamp(MILL, YEAR, withCost(1, 50_000_000), true, USER))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "recoveries is 0-FLOORED, and capped at the legacy message's 9,999,999 "
            + "(deviation (G)) — not at the wider NUMBER(8,0) column")
    void recoveriesIsZeroFloored() {
      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, withCost(5, -1), true, USER))
          .isInstanceOf(CampCostOutOfRangeException.class)
          .hasMessage("costValidatorSchedule9ErrorMsg");
      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, withCost(5, 10_000_000), true, USER))
          .isInstanceOf(CampCostOutOfRangeException.class)
          .hasMessage("costValidatorSchedule9ErrorMsg");

      assertThatCode(() -> service.addCamp(MILL, YEAR, withCost(5, 0), true, USER))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the programmatic ranges apply on the UPDATE path too, before any write")
    void rangesApplyOnUpdateToo() {
      // Every other range test goes through addCamp; deleting validateCostRanges from updateCamp
      // alone kept the whole suite green (the review's regression gap). The Bean Validation bounds
      // are WIDER (±99,999,999), so they cannot catch what this rejects.
      assertThatThrownBy(() -> service.updateCamp(MILL, YEAR, CAMP, withCost(5, -1), true, USER))
          .isInstanceOf(CampCostOutOfRangeException.class)
          .hasMessage("costValidatorSchedule9ErrorMsg");

      verify(repository, never())
          .updateCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString());
    }

    @Test
    @DisplayName("a range rejection happens before ANY write")
    void rejectionWritesNothing() {
      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, withCost(0, 10_000_000), true, USER))
          .isInstanceOf(CampCostOutOfRangeException.class);

      verify(repository, never())
          .insertCamp(
              anyInt(),
              anyLong(),
              anyInt(),
              anyString(),
              any(),
              any(),
              any(),
              anyString(),
              any(),
              anyString());
      verify(repository, never()).upsertCostDetail(anyInt(), anyInt(), any(), any(), anyString());
    }
  }

  @Nested
  @DisplayName("failure surfacing (deviation (P))")
  class FailureSurfacing {

    @Test
    @DisplayName("a DataAccessException becomes ScheduleNotSavedException, not a leaked 500")
    void dataAccessFailureBecomesNotSaved() {
      when(repository.nextCampReportId()).thenThrow(new DataAccessResourceFailureException("boom"));

      // Legacy swallowed everything and returned -1/false (Schedule5DAO.java:410-427), so the
      // screen
      // could only ever show a generic message; ERR-002's text is not statically resolvable
      // (UC-SCH5-001-technical.md:280), which is why no verbatim string is owed here.
      assertThatThrownBy(() -> service.addCamp(MILL, YEAR, request("New Camp", null), true, USER))
          .isInstanceOf(ScheduleNotSavedException.class);
    }

    @Test
    @DisplayName("a business exception raised INSIDE the try is not swallowed as a 500")
    void businessExceptionIsNotMaskedByTheCatch() {
      when(repository.countCamp(CAMP, MILL, YEAR)).thenReturn(0);

      // deleteCamp throws CampNotFoundException from inside its try block. Widening the catch to
      // Exception — or to RuntimeException — would turn this 404 into a 500.
      assertThatThrownBy(() -> service.deleteCamp(MILL, YEAR, CAMP, true))
          .isInstanceOf(CampNotFoundException.class);
    }
  }

  @Test
  @DisplayName("the echo is built from the Draft status the gate proved, without re-querying")
  void echoDoesNotRequeryTrackStatus() {
    service.addCamp(MILL, YEAR, request("New Camp", null), true, USER);

    // Exactly ONE status read, and it is the LOCKING one: the gate's. Re-reading for the echo would
    // open a second window in which a concurrent submit could flip the status the response reports.
    verify(repository, org.mockito.Mockito.times(1)).findTrackStatusForUpdate(MILL, YEAR);
    // And never the unlocked variant on a write path — that read belongs to Story 7.1's GET only.
    verify(repository, never()).findTrackStatus(anyLong(), anyInt());
  }
}
