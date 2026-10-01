package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the Schedule 10 Road Group tables against legacy {@code RoadGroupUtil}.
 *
 * <p>The headline assertion is {@link #isNotSchedule6sTable()}: Schedule 6 ships a similarly-named
 * lookup with genuinely different values, and reusing it would corrupt every page.
 */
class RoadGroup10LookupTest {

  @Nested
  @DisplayName("TSA/TSB table (setRG10ByTsaTsbNumberCode)")
  class TsaTsbTable {

    @ParameterizedTest(name = "TSA {0} + TSB {1} -> {2}")
    @CsvSource({
      // startsWith branches
      "01, 01A, 11",
      "02, 02A, 10",
      "03, 03B, 2",
      "04, 04A, 2",
      "05, 05C, 10",
      "07, 07A, 11",
      "08, 08A, 7",
      "09, 09A, 10",
      "10, 10A, 1",
      "12, 12A, 2",
      "13, 13A, 11",
      "14, 14A, 3",
      "15, 15A, 9",
      "17, 17A, 12",
      "18, 18A, 9",
      "20, 20A, 3",
      "27, 27A, 11",
      "40, 40A, 7",
      "41, 41A, 7",
      "43, 43A, 1",
      // exact-match branches
      "11, 11A, 12",
      "11, 11B, 9",
      "11, 11C, 9",
      "11, 11D, 9",
      "22, 22A, 9",
      "22, 22D, 9",
      "22, 22F, 12",
      "22, 22H, 11",
      // regex branches
      "16, 16A, 7",
      "16, 16G, 6",
      "23, 23A, 8",
      "23, 23G, 4",
      "24, 24A, 6",
      "24, 24C, 5",
      "24, 24D, 5",
      "24, 24E, 5",
      "24, 24G, 4",
      "26, 26A, 8",
      "26, 26E, 4",
      "29, 29A, 8",
      "29, 29J, 4",
      "29, 29M, 12",
      "45, 45A, 11",
      "45, 45H, 8",
      "45, 45E, 7",
      "45, 45I, 1",
    })
    void mapsToLegacyRoadGroup(String tsa, String tsb, String expected) {
      assertThat(RoadGroup10Lookup.rmgFor(tsa, tsb, null)).isEqualTo(expected);
    }

    @Test
    @DisplayName("case-insensitive exact matches, per legacy equalsIgnoreCase")
    void exactMatchesAreCaseInsensitive() {
      assertThat(RoadGroup10Lookup.rmgFor("11", "11a", null)).isEqualTo("12");
      assertThat(RoadGroup10Lookup.rmgFor("22", "22h", null)).isEqualTo("11");
    }
  }

  @Nested
  @DisplayName("TFL table (setRG10ByTflNumberCode)")
  class TflTable {

    @ParameterizedTest(name = "TFL {0} -> {1}")
    @CsvSource({
      "08, 10", "14, 10", "15, 9", "35, 9", "49, 9", "59, 9", "62, 9", "18, 9", "03, 11", "23, 11",
      "33, 11", "55, 11", "56, 11", "05, 5", "30, 4", "52, 4", "53, 4", "48, 7", "01, 1", "41, 1",
    })
    void mapsToLegacyRoadGroup(String tfl, String expected) {
      assertThat(RoadGroup10Lookup.rmgFor(null, null, tfl)).isEqualTo(expected);
    }

    @Test
    @DisplayName("TFL 52B is unmapped — unstorable at VARCHAR2(2), demoted as Schedule 6 did")
    void tfl52bIsUnmapped() {
      // Legacy leaves this case live, but it is unreachable on both sides: TFL_NUMBER_CODE is
      // VARCHAR2(2) and ConstructionPageRequest.tflNumberCode carries @Size(max = 2), so a 3-char
      // TFL never reaches the switch. Schedule 6 demoted it at an earlier review for exactly this
      // reason; Schedule 10 followed at code review 2026-08-18. Asserted rather than deleted so the
      // unreachability is recorded instead of merely absent.
      assertThat(RoadGroup10Lookup.rmgFor(null, null, "52B")).isNull();
      assertThat(RoadGroup10Lookup.canonicalTfl("52B")).isNull();
    }

    @Test
    @DisplayName("TFL 42 is deliberately absent (legacy commented it out, ILCR-161)")
    void tfl42IsUnmapped() {
      assertThat(RoadGroup10Lookup.rmgFor(null, null, "42")).isNull();
    }

    @Test
    @DisplayName("TFL wins over TSA whenever present, regardless of TSA")
    void tflTakesPrecedenceOverTsa() {
      // TSA 01+01A would yield "11" on its own; the TFL branch must win and yield "10".
      assertThat(RoadGroup10Lookup.rmgFor("01", "01A", "08")).isEqualTo("10");
    }
  }

  @Nested
  @DisplayName("Unmapped combinations are silent (S12 / BR-04)")
  class UnmappedCombinations {

    @Test
    @DisplayName("path 1: a null TSA or TSB never enters the guard")
    void nullTsaOrTsbYieldsNull() {
      assertThat(RoadGroup10Lookup.rmgFor(null, "01A", null)).isNull();
      assertThat(RoadGroup10Lookup.rmgFor("01", null, null)).isNull();
      assertThat(RoadGroup10Lookup.rmgFor(null, null, null)).isNull();
    }

    @Test
    @DisplayName("path 2: TSA matches a case but no inner branch does")
    void matchedTsaWithUnmatchedTsbYieldsNull() {
      // TSA 16 exists but "16Z" matches neither [A-Fa-f] nor [G-Pg-p].
      assertThat(RoadGroup10Lookup.rmgFor("16", "16Z", null)).isNull();
      // TSA 01 exists but the TSB does not start with "01".
      assertThat(RoadGroup10Lookup.rmgFor("01", "99A", null)).isNull();
    }

    @ParameterizedTest(name = "path 3: TSA {0} is absent from the switch")
    @ValueSource(strings = {"99", "00", "06", "19", "21", "25", "28", "42", "44"})
    void unknownTsaYieldsNull(String tsa) {
      assertThat(RoadGroup10Lookup.rmgFor(tsa, tsa + "A", null)).isNull();
    }

    @Test
    @DisplayName("path 3: an unknown TFL is absent from the switch")
    void unknownTflYieldsNull() {
      assertThat(RoadGroup10Lookup.rmgFor(null, null, "77")).isNull();
    }
  }

  @Nested
  @DisplayName("Legacy quirks preserved deliberately")
  class LegacyQuirks {

    @Test
    @DisplayName("case 45: [I-Ki-j] is asymmetric, so lowercase 'k' falls through to blank")
    void case45LowercaseKFallsThrough() {
      // Uppercase I, J, K all map to "1" ...
      assertThat(RoadGroup10Lookup.rmgFor("45", "45I", null)).isEqualTo("1");
      assertThat(RoadGroup10Lookup.rmgFor("45", "45K", null)).isEqualTo("1");
      // ... and lowercase i, j do too ...
      assertThat(RoadGroup10Lookup.rmgFor("45", "45i", null)).isEqualTo("1");
      assertThat(RoadGroup10Lookup.rmgFor("45", "45j", null)).isEqualTo("1");
      // ... but lowercase k is NOT in the character class. Legacy behaviour, not a typo to fix.
      assertThat(RoadGroup10Lookup.rmgFor("45", "45k", null)).isNull();
    }

    @Test
    @DisplayName("case 23: no branch covers 'I', so it falls through to blank")
    void case23HasNoBranchForI() {
      assertThat(RoadGroup10Lookup.rmgFor("23", "23A", null)).isEqualTo("8");
      assertThat(RoadGroup10Lookup.rmgFor("23", "23H", null)).isEqualTo("4");
      // 'I' is covered by neither [A-Fa-f] nor [G-Hg-h].
      assertThat(RoadGroup10Lookup.rmgFor("23", "23I", null)).isNull();
    }

    @Test
    @DisplayName("case 26: the expanded class matches legacy's [E-ie-i] for EVERY ASCII character")
    void case26ExpandedClassIsBehaviourIdenticalToLegacy() {
      // The legacy form, verbatim from RoadGroupUtil:419. CodeQL flags it as an overly permissive
      // range (java/overly-large-range) because E-i silently spans out of the uppercase block,
      // through six punctuation characters, and into lowercase. RoadGroup10Lookup replaces it with
      // an explicit expansion — this test is what proves the two are the same function, so the
      // expansion cannot quietly become a behaviour change.
      final String legacy = ".*[E-ie-i]";

      for (char c = 0x20; c <= 0x7E; c++) {
        String candidate = "26" + c;
        assertThat(candidate.matches(RoadGroup10Lookup.QUESNEL_SECOND_BRANCH))
            .as("character '%s' (0x%02X) must classify identically under both forms", c, (int) c)
            .isEqualTo(candidate.matches(legacy));
      }
    }

    @Test
    @DisplayName("case 26: [E-ie-i] is an ASCII range spanning E-Z, punctuation and a-i")
    void case26RangeIsFarWiderThanEtoI() {
      // Reads like "E to I" but is not: [E-i] covers E-Z, [ \ ] ^ _ ` and a-i. Documented and
      // pinned at code review 2026-08-17 because it is the widest of the three preserved quirks
      // and the most likely to be "helpfully" narrowed to [E-Ie-i] by a later reader.
      assertThat(RoadGroup10Lookup.rmgFor("26", "26A", null)).isEqualTo("8");
      assertThat(RoadGroup10Lookup.rmgFor("26", "26E", null)).isEqualTo("4");
      assertThat(RoadGroup10Lookup.rmgFor("26", "26I", null)).isEqualTo("4");
      // Well past I, still inside the range — narrowing the class would break these.
      assertThat(RoadGroup10Lookup.rmgFor("26", "26Z", null)).isEqualTo("4");
      assertThat(RoadGroup10Lookup.rmgFor("26", "26_", null)).isEqualTo("4");
      assertThat(RoadGroup10Lookup.rmgFor("26", "26i", null)).isEqualTo("4");
      // And j is genuinely outside it.
      assertThat(RoadGroup10Lookup.rmgFor("26", "26j", null)).isNull();
    }

    @Test
    @DisplayName("regex branches match the LAST character, not the numeric prefix")
    void regexBranchesMatchTrailingCharacter() {
      // TSA 16's branches are regex over the whole code, so a non-16-prefixed TSB still matches.
      assertThat(RoadGroup10Lookup.rmgFor("16", "99A", null)).isEqualTo("7");
      // Whereas TSA 01 uses startsWith, so the prefix genuinely matters.
      assertThat(RoadGroup10Lookup.rmgFor("01", "99A", null)).isNull();
    }
  }

  @Test
  @DisplayName("is NOT Schedule 6's RMG table — the values genuinely differ")
  void isNotSchedule6sTable() {
    // Schedule 6's setRmgByTfaTsbNumberCode maps TSA 01 -> "15" and TSA 08 -> "10";
    // Schedule 10's setRG10ByTsaTsbNumberCode maps them to "11" and "7".
    assertThat(RoadGroup10Lookup.rmgFor("01", "01A", null)).isEqualTo("11").isNotEqualTo("15");
    assertThat(RoadGroup10Lookup.rmgFor("08", "08A", null)).isEqualTo("7").isNotEqualTo("10");
    // Schedule 6's setRmgByTflNumberCode maps TFL 08 -> "7"; Schedule 10 maps it to "10".
    assertThat(RoadGroup10Lookup.rmgFor(null, null, "08")).isEqualTo("10").isNotEqualTo("7");
  }
}
