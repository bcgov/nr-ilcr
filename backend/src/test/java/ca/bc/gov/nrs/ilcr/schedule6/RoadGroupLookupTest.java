package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pins the FULL Schedule 6 RMG table (BR-04) against the legacy {@code RoadGroupUtil} source values
 * — the lookup is a verbatim port of a hardcoded business rule, so every branch (all TSA cases with
 * each distinct TSB outcome, the whole TFL table, the TFL-first routing, and the legacy regex
 * quirks) is asserted here; a single transposed digit in the port fails one of these rows.
 */
@DisplayName("RoadGroupLookup — the verbatim BR-04 RMG table")
class RoadGroupLookupTest {

  @ParameterizedTest(name = "TSA {0} + TSB {1} -> RMG {2}")
  @CsvSource(nullValues = "NULL", value = {
      // 01 Arrow (startsWith) — and the TSA-matched-but-TSB-unmatched empty->null normalization.
      "01, 01B, 15", "01, 02B, NULL",
      // 02 Boundary (equalsIgnoreCase lists — lowercase must match too).
      "02, 02C, 15", "02, 02D, 15", "02, 02G, 15", "02, 02E, 7", "02, 02F, 7",
      "02, 02c, 15", "02, 02X, NULL",
      // 03-10 all-of-TSA blocks (startsWith).
      "03, 03B, 1", "04, 04A, 1", "05, 05A, 16", "07, 07A, 27", "08, 08A, 10",
      "09, 09A, 16", "10, 10A, 1",
      // 11 Kamloops.
      "11, 11A, 4", "11, 11a, 4", "11, 11B, 5", "11, 11C, 5", "11, 11D, 5", "11, 11E, NULL",
      // 12-15, 17, 18, 20, 27 all-of-TSA blocks.
      "12, 12A, 1", "13, 13A, 18", "14, 14A, 25", "15, 15A, 1", "17, 17A, 4",
      "18, 18A, 6", "20, 20A, 25", "27, 27A, 27",
      // 16 Mackenzie (regex, both letter ranges -> 11).
      "16, 16A, 11", "16, 16F, 11", "16, 16G, 11", "16, 16P, 11", "16, 16Z, NULL",
      // 22 Okanagan.
      "22, 22A, 7", "22, 22B, 7", "22, 22C, 7", "22, 22D, 26", "22, 22E, 26",
      "22, 22F, 26", "22, 22G, 26", "22, 22H, 27", "22, 22I, 27", "22, 22Z, NULL",
      // 23 100 Mile House (regex ranges).
      "23, 23A, 21", "23, 23D, 21", "23, 23E, 22", "23, 23F, 22", "23, 23G, 22",
      "23, 23H, 22", "23, 23Z, NULL",
      // 24 Prince George (regex ranges; I rides with E-F -> 13).
      "24, 24A, 11", "24, 24B, 11", "24, 24C, 11", "24, 24D, 12", "24, 24E, 13",
      "24, 24F, 13", "24, 24I, 13", "24, 24G, 14", "24, 24H, 14", "24, 24Z, NULL",
      // 26 Quesnel — legacy's [E-ie-i] spans E..i in ASCII, so EVERY uppercase letter E-Z
      // (26Z included) resolves 14, not just E-I; 26Z pins that width so the rewritten
      // [E-Za-i] class cannot silently narrow it.
      "26, 26A, 19", "26, 26D, 19", "26, 26E, 14", "26, 26I, 14", "26, 26Z, 14",
      // 29 Williams Lake (regex ranges; I with A-E, J with F-H).
      "29, 29A, 20", "29, 29E, 20", "29, 29I, 20", "29, 29F, 21", "29, 29H, 21",
      "29, 29J, 21", "29, 29K, 22", "29, 29L, 22", "29, 29M, 22", "29, 29N, 22", "29, 29Z, NULL",
      // 40-43 all-of-TSA blocks.
      "40, 40A, 10", "41, 41A, 10", "42, 42A, 1", "43, 43A, 1",
      // Unknown TSA -> switch default -> null (06 and 99 are not cases).
      "06, 06A, NULL", "99, 99A, NULL",
  })
  void tsaTsbTable(String tsa, String tsb, String expectedRmg) {
    assertEquals(expectedRmg, RoadGroupLookup.rmgFor(tsa, tsb, null));
  }

  @ParameterizedTest(name = "TFL {0} -> RMG {1}")
  @CsvSource(nullValues = "NULL", value = {
      "01, 1", "41, 1", "18, 4", "35, 5", "08, 7", "15, 7", "59, 7", "48, 10",
      "05, 13", "62, 13", "30, 14", "52, 14", "53, 14", "03, 15", "23, 15",
      "14, 16", "49, 26", "33, 27", "55, 27", "56, 27",
      // Both unresolvable legacy codes stay unmapped: "42" legacy itself commented out (TFL list
      // v2, ILCR-161), and "52B" cannot be stored or entered at all (TFL_NUMBER_CODE VARCHAR2(2),
      // legacy inputs maxlength="2"), so it must not resolve where @Size/requireValidTfl reject it.
      "42, NULL", "52B, NULL", "99, NULL",
      // The legacy TFL switch is exact-case (unlike the TSB equalsIgnoreCase lists) — preserved.
      "52b, NULL",
  })
  void tflTable(String tfl, String expectedRmg) {
    assertEquals(expectedRmg, RoadGroupLookup.rmgFor(null, null, tfl));
  }

  @Test
  @DisplayName("a present TFL code routes RMG through the TFL table even alongside a TSA+TSB pair")
  void tflRoutesFirst() {
    // Legacy RoadMaintenanceReportType.getRmg checks the TFL operand first (verbatim routing).
    assertEquals("4", RoadGroupLookup.rmgFor("01", "01B", "18"));
  }

  @Test
  @DisplayName("null operands -> null RMG (legacy empty-string outcomes normalized to null)")
  void nullOperands() {
    assertNull(RoadGroupLookup.rmgFor(null, null, null));
    assertNull(RoadGroupLookup.rmgFor("01", null, null));
    assertNull(RoadGroupLookup.rmgFor(null, "01B", null));
  }
}
