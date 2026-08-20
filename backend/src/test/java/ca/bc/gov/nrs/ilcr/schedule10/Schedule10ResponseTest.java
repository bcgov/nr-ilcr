package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.BecClassificationRow;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins two small seams that no other test reaches: the {@code withMessage} copy declared for Story
 * 11.2's save-echo, and the legacy BEC label concatenation across its null shapes.
 */
@DisplayName("Schedule 10 response seams")
class Schedule10ResponseTest {

  @Nested
  @DisplayName("Schedule10Response.withMessage — Story 11.2's save-echo seam")
  class WithMessage {

    @Test
    @DisplayName("attaches the message and preserves every other component")
    void preservesEveryOtherComponent() {
      // A seven-component positional hand-copy with no caller yet. Transposing two adjacent
      // components (trackStatus/editable, or pages/codeLists) compiles cleanly and would surface
      // in Story 11.2 as a save-echo that silently flips editable or drops the pages — where it
      // would look like an 11.2 bug rather than an 11.1 one.
      ConstructionPage page = new ConstructionPage(
          8900, 1, "Page 1", "RNI", "01", "01A", null, "11", "North", "2021-06", 0, 0, List.of());
      Schedule10Response original =
          new Schedule10Response(710L, 2021, "D", true, List.of(page), null, null);

      Schedule10Response copy = original.withMessage(new MessageInfo("k", "text"));

      assertThat(copy)
          .usingRecursiveComparison()
          .ignoringFields("message")
          .isEqualTo(original);
      assertThat(copy.message()).isNotNull();
      assertThat(copy.message().key()).isEqualTo("k");
      // The original must be untouched — it is a record, but the copy is hand-written.
      assertThat(original.message()).isNull();
    }
  }

  @Nested
  @DisplayName("BecClassificationRow.label — legacy getBiogeoSubZoneVariantPase (:208-212)")
  class BecLabel {

    private static String label(String zone, String subzone, String variant, String phase) {
      return new BecClassificationRow(1, zone, subzone, variant, phase).label();
    }

    @Test
    @DisplayName("variant present, phase absent")
    void variantPresentPhaseAbsent() {
      assertThat(label("ICH", "dw", "1", null)).isEqualTo("ICHdw1");
    }

    @Test
    @DisplayName("both variant and phase absent render as empty, NOT as the text null")
    void bothAbsent() {
      // Only the variant-present shape was previously pinned, so dropping either null guard
      // produced "CWHvmnull" undetected (code review 2026-08-17).
      assertThat(label("CWH", "vm", null, null)).isEqualTo("CWHvm");
    }

    @Test
    @DisplayName("phase present — the leg no seeded offerable row exercises")
    void phasePresent() {
      assertThat(label("ESSF", "wc", "4", "a")).isEqualTo("ESSFwc4a");
    }

    @Test
    @DisplayName("variant absent but phase present still concatenates in legacy order")
    void variantAbsentPhasePresent() {
      assertThat(label("ESSF", "wc", null, "a")).isEqualTo("ESSFwca");
    }
  }
}
