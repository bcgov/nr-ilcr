package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the executable Java on {@link Schedule7bRepository} — its {@code default} methods.
 *
 * <p>These exist because the {@code @Query} SQL is proven only by the {@code *IT} suite, which this
 * project's Sonar pipeline does not run (surefire only). Everything in this interface that is real
 * Java rather than SQL — the {@code upsertCost} update-then-insert fallback and the {@code
 * culvertTypeOptions} year-to-as-of mapping — was therefore executed by nothing under surefire, and
 * {@code Schedule7bServiceTest} cannot help: it {@code @Mock}s this interface, so Mockito replaces
 * the very methods that hold the logic. Uses {@link Answers#CALLS_REAL_METHODS} so the default
 * bodies run against stubbed abstract methods.
 */
@DisplayName("Schedule7bRepository — default-method logic (upsert fallback, year-scoped code list)")
class Schedule7bRepositoryTest {

  private static Schedule7bRepository realDefaults() {
    return mock(
        Schedule7bRepository.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
  }

  @Test
  @DisplayName("upsertCost updates in place when the row exists — and does NOT insert")
  void upsertUpdatesInPlaceWhenRowExists() {
    Schedule7bRepository repository = realDefaults();
    when(repository.updateCost(7801L, 77, 4000, "tester")).thenReturn(1);

    repository.upsertCost(7801L, 77, 4000, "tester");

    verify(repository).updateCost(7801L, 77, 4000, "tester");
    verify(repository, never()).insertCost(anyLong(), anyLong(), anyInt(), any(), anyString());
    // Audit continuity matters here: a delete/re-insert would churn the row's ENTRY_* stamps.
    verify(repository, never()).nextCostDetailId();
  }

  @Test
  @DisplayName("upsertCost inserts with a fresh sequence PK when no row exists")
  void upsertInsertsWhenRowAbsent() {
    Schedule7bRepository repository = realDefaults();
    when(repository.updateCost(7801L, 78, 1500, "tester")).thenReturn(0);
    when(repository.nextCostDetailId()).thenReturn(9042L);

    repository.upsertCost(7801L, 78, 1500, "tester");

    verify(repository).insertCost(9042L, 7801L, 78, 1500, "tester");
  }

  @Test
  @DisplayName(
      "upsertCost carries a NULL cost through both branches — a cleared cost keeps its row")
  void upsertCarriesNullCost() {
    Schedule7bRepository updating = realDefaults();
    when(updating.updateCost(7801L, 77, null, "tester")).thenReturn(1);
    updating.upsertCost(7801L, 77, null, "tester");
    verify(updating).updateCost(7801L, 77, null, "tester");

    Schedule7bRepository inserting = realDefaults();
    when(inserting.updateCost(7801L, 77, null, "tester")).thenReturn(0);
    when(inserting.nextCostDetailId()).thenReturn(9043L);
    inserting.upsertCost(7801L, 77, null, "tester");
    // A NULL row, never a missing row — legacy's update branch has no insert path, so a missing row
    // would make that cost permanently uneditable from the legacy screen.
    verify(inserting).insertCost(9043L, 7801L, 77, null, "tester");
  }

  @Test
  @DisplayName("culvertTypeOptions evaluates the code list at JANUARY 1 of the reporting year")
  void codeListIsEvaluatedAtJanuaryFirst() {
    Schedule7bRepository repository = realDefaults();
    when(repository.findCulvertTypeCodes(any())).thenReturn(List.of());

    repository.culvertTypeOptions(2021);

    ArgumentCaptor<LocalDate> asOf = ArgumentCaptor.forClass(LocalDate.class);
    verify(repository).findCulvertTypeCodes(asOf.capture());
    // Legacy CoreUtil.getDate(int) built Calendar.JANUARY, 1 for the reporting year and fed it to
    // LookupCache.getCacheList(year). Any other instant offers a different set of codes.
    assertThat(asOf.getValue()).isEqualTo(LocalDate.of(2021, 1, 1));
  }

  @Test
  @DisplayName("culvertTypeOptions maps code-table rows to the shared option DTO, order preserved")
  void codeListMapsToOptionDto() {
    Schedule7bRepository repository = realDefaults();
    when(repository.findCulvertTypeCodes(LocalDate.of(2021, 1, 1)))
        .thenReturn(
            List.of(
                new Schedule7bRepository.CulvertTypeCode("O", "Others"),
                new Schedule7bRepository.CulvertTypeCode("R", "Round")));

    List<CodeDescriptionDto> options = repository.culvertTypeOptions(2021);

    assertThat(options)
        .containsExactly(
            new CodeDescriptionDto("O", "Others"), new CodeDescriptionDto("R", "Round"));
  }

  @Test
  @DisplayName("The cost-item constants agree with the SQL literals in findCostDetails")
  void costItemConstantsMatchTheQueryLiterals() {
    // findCostDetails filters `IN (77, 78)` as literals because Spring Data JDBC cannot bind an IN
    // list from an interface constant. This asserts the two stay in step: correcting one id without
    // the other would silently load no costs, and every culvert would then report its material and
    // install costs as missing in Check Status while the values sat in the database.
    assertThat(Schedule7bRepository.ITEM_MATERIAL).isEqualTo(77);
    assertThat(Schedule7bRepository.ITEM_INSTALL).isEqualTo(78);

    String sql = queryOf("findCostDetails");
    assertThat(sql)
        .contains(
            "IN ("
                + Schedule7bRepository.ITEM_MATERIAL
                + ", "
                + Schedule7bRepository.ITEM_INSTALL
                + ")");
  }

  @Test
  @DisplayName("Every culvert-row statement is scoped to mill, year and category '7' (IDOR)")
  void culvertStatementsAreMillYearScoped() {
    // The repository javadoc makes this claim; this asserts it rather than trusting the prose. The
    // two
    // cost-CHILD writes are deliberately excluded — their ownership is call-order based and
    // recorded
    // as deferred work.
    for (String method :
        List.of(
            "findCulverts", "findCostDetails", "countCulvert", "updateCulvert", "deleteCulvert")) {
      String sql = queryOf(method);
      assertThat(sql).as("%s scopes by mill", method).contains("ILCR_MILL_ID = :millId");
      assertThat(sql).as("%s scopes by year", method).contains("REPORT_YEAR = :year");
      assertThat(sql).as("%s scopes by category", method).contains("ILCR_CATEGORY_ID = '7'");
    }
  }

  /** The {@code @Query} value declared on the named repository method (first match wins). */
  private static String queryOf(String methodName) {
    for (var method : Schedule7bRepository.class.getDeclaredMethods()) {
      if (method.getName().equals(methodName)) {
        var query =
            method.getAnnotation(org.springframework.data.jdbc.repository.query.Query.class);
        if (query != null) {
          return query.value();
        }
      }
    }
    throw new AssertionError("no @Query found on Schedule7bRepository." + methodName);
  }
}
