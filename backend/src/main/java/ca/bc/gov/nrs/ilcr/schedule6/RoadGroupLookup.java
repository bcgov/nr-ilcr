package ca.bc.gov.nrs.ilcr.schedule6;

/**
 * The Schedule 6 Resource Management Grouping (RMG) lookup (BR-04), ported verbatim from the legacy
 * {@code RoadGroupUtil.setRmgByTfaTsbNumberCode}/{@code setRmgByTflNumberCode}. The hardcoded
 * TSA/TSB and TFL &rarr; RMG tables are the business rule itself and must not be simplified. A TFL
 * record resolves by its TFL code; otherwise by its TSA+TSB pair. Legacy returned an empty string
 * for the unmatched paths (TSA matched but TSB pattern didn't, or a null operand); that is
 * normalized to {@code null} here so "no grouping" is one absent value, not an empty string.
 */
final class RoadGroupLookup {

  private RoadGroupLookup() {
  }

  /**
   * The RMG for a road record: by TFL code when present, else by the TSA+TSB pair (legacy
   * {@code RoadMaintenanceReportType.getRmg}). Null when no rule matches.
   */
  static String rmgFor(String tsaNumberCode, String tsbNumberCode, String tflNumberCode) {
    String rmg = tflNumberCode != null
        ? rmgByTflNumberCode(tflNumberCode)
        : rmgByTsaTsbNumberCode(tsaNumberCode, tsbNumberCode);
    return (rmg == null || rmg.isEmpty()) ? null : rmg;
  }

  /** Verbatim port of legacy {@code RoadGroupUtil.setRmgByTfaTsbNumberCode}. */
  @SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
  private static String rmgByTsaTsbNumberCode(String tsaNumberCode, String tsbNumberCode) {
    String rmg = "";
    if (tsaNumberCode != null && tsbNumberCode != null) {
      switch (tsaNumberCode) {
        case "01":
          // TSB Arrow All
          if (tsbNumberCode.startsWith("01")) {
            rmg = "15";
          }
          break;
        case "02":
          // TSB Boundary
          if ("02C".equalsIgnoreCase(tsbNumberCode) || "02D".equalsIgnoreCase(tsbNumberCode)
              || "02G".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "15";
          } else if ("02E".equalsIgnoreCase(tsbNumberCode)
              || "02F".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "7";
          }
          break;
        case "03":
          // TSB Bulkley All
          if (tsbNumberCode.startsWith("03")) {
            rmg = "1";
          }
          break;
        case "04":
          // TSB Cassiar All
          if (tsbNumberCode.startsWith("04")) {
            rmg = "1";
          }
          break;
        case "05":
          // TSB Cranbrook All
          if (tsbNumberCode.startsWith("05")) {
            rmg = "16";
          }
          break;
        case "07":
          // TSB Golden All
          if (tsbNumberCode.startsWith("07")) {
            rmg = "27";
          }
          break;
        case "08":
          // TSB Fort Nelson All
          if (tsbNumberCode.startsWith("08")) {
            rmg = "10";
          }
          break;
        case "09":
          // TSB Invermere All
          if (tsbNumberCode.startsWith("09")) {
            rmg = "16";
          }
          break;
        case "10":
          // TSB Kalum All
          if (tsbNumberCode.startsWith("10")) {
            rmg = "1";
          }
          break;
        case "11":
          // TSB Kamloops
          if ("11A".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "4";
          } else if ("11B".equalsIgnoreCase(tsbNumberCode) || "11C".equalsIgnoreCase(tsbNumberCode)
              || "11D".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "5";
          }
          break;
        case "12":
          // TSB Kispiox All
          if (tsbNumberCode.startsWith("12")) {
            rmg = "1";
          }
          break;
        case "13":
          // TSB Kootenay Lake All
          if (tsbNumberCode.startsWith("13")) {
            rmg = "18";
          }
          break;
        case "14":
          // TSB Lakes Lake All
          if (tsbNumberCode.startsWith("14")) {
            rmg = "25";
          }
          break;
        case "15":
          // TSB Lillooet All
          if (tsbNumberCode.startsWith("15")) {
            rmg = "1";
          }
          break;
        case "16":
          // TSB Mackenzie All
          if (tsbNumberCode.matches(".*[A-Fa-f]")) {
            rmg = "11";
          } else if (tsbNumberCode.matches(".*[G-Pg-p]")) {
            rmg = "11";
          }
          break;
        case "17":
          // TSB Robson Valley All
          if (tsbNumberCode.startsWith("17")) {
            rmg = "4";
          }
          break;
        case "18":
          // TSB Merritt All
          if (tsbNumberCode.startsWith("18")) {
            rmg = "6";
          }
          break;
        case "20":
          // TSB Morice All
          if (tsbNumberCode.startsWith("20")) {
            rmg = "25";
          }
          break;
        case "22":
          // TSB Okanagan
          if ("22A".equalsIgnoreCase(tsbNumberCode) || "22B".equalsIgnoreCase(tsbNumberCode)
              || "22C".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "7";
          } else if ("22D".equalsIgnoreCase(tsbNumberCode)
              || "22E".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "26";
          } else if ("22F".equalsIgnoreCase(tsbNumberCode)
              || "22G".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "26";
          } else if ("22H".equalsIgnoreCase(tsbNumberCode)
              || "22I".equalsIgnoreCase(tsbNumberCode)) {
            rmg = "27";
          }
          break;
        case "23":
          // TSB 100 Mile House
          if (tsbNumberCode.matches(".*[A-Da-d]")) {
            rmg = "21";
          } else if (tsbNumberCode.matches(".*[E-Fe-f]")) {
            rmg = "22";
          } else if (tsbNumberCode.matches(".*[G-Hg-h]")) {
            rmg = "22";
          }
          break;
        case "24":
          // TSB Prince George
          if (tsbNumberCode.matches(".*[A-Ba-b]")) {
            rmg = "11";
          } else if (tsbNumberCode.matches(".*[Cc]")) {
            rmg = "11";
          } else if (tsbNumberCode.matches(".*[Dd]")) {
            rmg = "12";
          } else if (tsbNumberCode.matches(".*[E-Fe-fIi]")) {
            rmg = "13";
          } else if (tsbNumberCode.matches(".*[G-Hg-h]")) {
            rmg = "14";
          }
          break;
        case "26":
          // TSB Quesnel. Legacy wrote this class as [E-ie-i], whose E..i span runs past 'Z'
          // through the ASCII punctuation gap ([ \ ] ^ _ `) and so also matches J-Z. Spelled
          // here as two same-case ranges instead: same matches for every TSB value (none can
          // end in punctuation), minus the cross-case range CodeQL flags as unintended width.
          // The J-Z reach is kept rather than narrowed to E-I because it is legacy behaviour;
          // THE.TSB_NUMBER_CODE stops at 26I, so it resolves nothing in practice either way.
          if (tsbNumberCode.matches(".*[A-Da-d]")) {
            rmg = "19";
          } else if (tsbNumberCode.matches(".*[E-Za-i]")) {
            rmg = "14";
          }
          break;
        case "27":
          // TSB Revelstoke All
          if (tsbNumberCode.startsWith("27")) {
            rmg = "27";
          }
          break;
        case "29":
          // TSB Williams Lake
          if (tsbNumberCode.matches(".*[A-Ea-eIi]")) {
            rmg = "20";
          } else if (tsbNumberCode.matches(".*[F-Hf-hJj]")) {
            rmg = "21";
          } else if (tsbNumberCode.matches(".*[K-Lk-l]")) {
            rmg = "22";
          } else if (tsbNumberCode.matches(".*[M-Nm-n]")) {
            rmg = "22";
          }
          break;
        case "40":
          // TSB Fort St. John All
          if (tsbNumberCode.startsWith("40")) {
            rmg = "10";
          }
          break;
        case "41":
          // TSB Dawson Creek All
          if (tsbNumberCode.startsWith("41")) {
            rmg = "10";
          }
          break;
        case "42":
          // TSB Cranberry All
          if (tsbNumberCode.startsWith("42")) {
            rmg = "1";
          }
          break;
        case "43":
          // TSB Nass All
          if (tsbNumberCode.startsWith("43")) {
            rmg = "1";
          }
          break;
        default:
          rmg = null;
      }
    }
    return rmg;
  }

  /** Verbatim port of legacy {@code RoadGroupUtil.setRmgByTflNumberCode}. */
  private static String rmgByTflNumberCode(String tflNumberCode) {
    return switch (tflNumberCode) {
      case "01", "41" -> "1";
      case "18" -> "4";
      case "35" -> "5";
      case "08", "15", "59" -> "7";
      case "48" -> "10";
      // Legacy's table carries two codes that can never resolve, both kept here as comments so the
      // ported table stays a faithful record without offering a width the rest of the schedule
      // rejects. "42" legacy itself commented out (not used as described in TFL list v2, ILCR-161).
      // "52B" legacy left live but unreachable: a 3-char TFL is unstorable on both sides —
      // ROAD_MAINTENANCE_REPORT.TFL_NUMBER_CODE and the THE.TFL_NUMBER_CODE reference key are each
      // VARCHAR2(2) (delivery-DB verified 2026-08-05) — and legacy's own add/edit inputs are
      // maxlength="2" (schedule6.xhtml:106,295), so no 3-char value ever reached this switch on read
      // or on save. Demoted from a live case at code review (2026-08-05): as a case it read as an
      // accepted value contradicting the RoadRecordRequest @Size(max = 2) and requireValidTfl width
      // check, when in fact all three agree that a 3-char TFL is invalid.
      // case "42" -> "11";
      // case "52B" -> "13";
      case "05" -> "13";
      case "30", "52", "53" -> "14";
      case "03", "23" -> "15";
      case "14" -> "16";
      case "49" -> "26";
      case "33", "55", "56" -> "27";
      case "62" -> "13";
      default -> null;
    };
  }
}
