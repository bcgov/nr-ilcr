package ca.bc.gov.nrs.ilcr.schedule10;

/**
 * The Schedule 10 Road Group ({@code RMG}) tables, ported verbatim from legacy
 * {@code RoadGroupUtil.setRG10ByTsaTsbNumberCode} (:285-468) and
 * {@code setRG10ByTflNumberCode} (:475-520).
 *
 * <p><strong>These are NOT Schedule 6's tables.</strong> Legacy {@code RoadGroupUtil} carries two
 * independent pairs — {@code setRmgBy*} (:10, :222) for Schedule 6 and {@code setRG10By*} (:285,
 * :475) for Schedule 10 — and the values genuinely differ: TSA {@code "01"} maps to {@code "15"} in
 * Schedule 6 but {@code "11"} here; TFL {@code "08"} maps to {@code "7"} there but {@code "10"}
 * here; TSA {@code "08"} maps to {@code "10"} there but {@code "7"} here. The shipped
 * {@code schedule6.RoadGroupLookup} ports only the {@code setRmg*} pair, so reusing it would
 * serve a wrong Road Group on every Schedule 10 page. It is package-private in {@code schedule6},
 * so there is no accidental-import hazard — but there is a "helpful refactor" hazard. Do not merge
 * these.
 *
 * <p>Road Group is <strong>derived on every read and never stored</strong>: there is no
 * {@code RMG}/{@code ROAD_GROUP} column on {@code THE.ROAD_CONSTRUCTION_REPRT} (delivery-verified,
 * Story 11.1 Task 1 gate (i)).
 *
 * <p><strong>Three legacy literal quirks are preserved on purpose.</strong> Case {@code "45"} uses
 * {@code .*[I-Ki-j]} — uppercase {@code I}–{@code K} but lowercase only {@code i}–{@code j}, so a
 * lowercase {@code k} suffix falls through to blank. Case {@code "23"} has no branch covering
 * {@code I} at all. Case {@code "26"} uses {@code .*[E-ie-i]}, which despite reading like "E to I"
 * is an ASCII range spanning {@code E}–{@code Z}, six punctuation characters
 * ({@code [ \ ] ^ _ `}) and {@code a}–{@code i} — by far the widest blast radius of the three, and
 * the easiest to mistake for a typo. All three are reproduced exactly; they are the shipped
 * business rule, not typos to quietly fix (AD-12 legacy parity, third quirk documented at code
 * review 2026-08-17).
 *
 * <p><strong>Unmapped combinations are silent.</strong> Legacy has three distinct no-match paths
 * and
 * none throws, logs, or raises a message: a null TSA or TSB never enters the outer guard and leaves
 * the initialiser {@code new String()} → {@code ""}; a TSA that matches a case whose inner branches
 * all miss also falls through to {@code ""}; a TSA or TFL absent from the switch hits
 * {@code default} → {@code null}. All three normalize to {@code null} here so the served document
 * carries a blank Road Group without error (S12, BR-04; Story 11.1 deviation (h)).
 */
final class RoadGroup10Lookup {

  /**
   * Case 26's second branch, written as the exact set of characters legacy matches.
   *
   * <p>Legacy writes {@code .*[E-ie-i]} ({@code RoadGroupUtil:419}). That <em>looks</em> like
   * "E through I", but a character class reads {@code E-i} as an ASCII range, so it actually spans
   * {@code E}(69) to {@code i}(105): {@code E-Z}, the six characters
   * <code>[ \ ] ^ _ &#96;</code>, and {@code a-i}. The trailing {@code e-i} is entirely redundant —
   * {@code e..i} already sits inside {@code E..i} — and it is that redundant overlap which makes
   * the class read as a typo.
   *
   * <p>Expanded here to the identical character set with no overlapping ranges. <strong>The
   * matching behaviour is unchanged</strong> — {@code RoadGroup10LookupTest} asserts this form and
   * the original legacy form agree across the entire printable ASCII range, so the expansion is
   * provably a rewrite and not a behaviour change. Written out rather than left as {@code [E-i]}
   * so the surprising breadth is visible to the next reader instead of hidden behind two
   * innocuous-looking letters (CodeQL {@code java/overly-large-range}, 2026-08-17).
   *
   * <p>Do NOT "tidy" this to {@code [E-Ie-i]}. That is almost certainly what the legacy author
   * meant, but it is not what the shipped system does, and Road Group is a user-visible derived
   * value (AD-12 legacy parity).
   */
  static final String QUESNEL_SECOND_BRANCH = ".*[E-Z\\[\\\\\\]^_`a-i]";

  private RoadGroup10Lookup() {
  }

  /**
   * Derives the Road Group for a page. TFL wins whenever it is present, regardless of TSA — the
   * routing is {@code tflNumberCode != null ? tfl-table : tsa-table}, taken verbatim from
   * {@code RoadConstructionReportType.getRmg} (:455-464). Do not reorder.
   *
   * @param tsaNumber the TSA number, or {@code null} on a TFL-located page
   * @param tsbNumberCode the supply block, or {@code null}
   * @param tflNumberCode the TFL number, or {@code null} on a TSA-located page
   * @return the derived Road Group, or {@code null} when the combination maps to nothing
   */
  static String rmgFor(String tsaNumber, String tsbNumberCode, String tflNumberCode) {
    String rmg = tflNumberCode != null
        ? rg10ByTflNumberCode(tflNumberCode)
        : rg10ByTsaTsbNumberCode(tsaNumber, tsbNumberCode);
    // Legacy returns "" for the two fall-through paths and null for the default branch; the served
    // contract makes no distinction between them (S12: "blank, no error").
    return (rmg == null || rmg.isEmpty()) ? null : rmg;
  }

  /**
   * The storable form of an entered TFL number, or {@code null} when it is not a valid TFL.
   *
   * <p>Legacy's validator ({@code ILCRTflNumberValidator:33-45}) accepts a TFL if the lookup
   * resolves it directly, or if it resolves after applying the missing-leading-zero aliases ({@code
   * RoadGroupUtil.translateNoLeadingZeroButNumberMatch} :202-215). It validates the alias but then
   * stores the raw entry, which leaves an accepted value in a form the reference table does not
   * hold; this returns the canonical form so the stored value is the one that resolves.
   *
   * <p><strong>On which table validates.</strong> The legacy validator calls Schedule <em>6</em>'s
   * lookup even for this screen, which reads like a defect. Validating here against Schedule 10's
   * own table is behaviourally equivalent, because <strong>the two accept and reject exactly the
   * same values</strong>: they agree on every key either one holds, and the only code they ever
   * disagreed on was {@code "52B"}, which neither offers now.
   *
   * <p>{@code "52B"} is worth a sentence because it is the one real difference in the two tables'
   * history. Legacy kept it live here and Schedule 6 demoted it to a comment, on the grounds that
   * {@code TFL_NUMBER_CODE} is {@code VARCHAR2(2)} on both sides and {@code
   * ConstructionPageRequest.tflNumberCode} carries {@code @Size(max = 2)} — so a 3-character TFL
   * can never reach either switch. This schedule followed at code review 2026-08-18.
   *
   * <p>Deliberately stated as a PROPERTY rather than a key count. Two earlier revisions of this
   * note cited a number — first "an identical set of 22 keys", then "21 versus 20" — and both went
   * stale, the second within the same change that demoted {@code 52B} (flagged at review
   * 2026-08-19). The property is what the write path depends on; the count is trivia that rots.
   *
   * <p>Only the returned Road Group values differ between the tables, which is what this class
   * exists to keep separate. The cross-wiring is still worth reporting upstream: the tables are
   * maintained independently, so a future edit to either would silently split validation from
   * derivation.
   *
   * @param tflNumberCode the entered TFL number, possibly missing a leading zero
   * @return the canonical TFL to store, or {@code null} when the value is not a valid TFL
   */
  static String canonicalTfl(String tflNumberCode) {
    if (tflNumberCode == null) {
      return null;
    }
    if (rg10ByTflNumberCode(tflNumberCode) != null) {
      return tflNumberCode;
    }
    String alias = leadingZeroAlias(tflNumberCode);
    return alias != null && rg10ByTflNumberCode(alias) != null ? alias : null;
  }

  /** Verbatim {@code RoadGroupUtil.translateNoLeadingZeroButNumberMatch} (:202-215). */
  private static String leadingZeroAlias(String tflNumberCode) {
    return switch (tflNumberCode) {
      case "1" -> "01";
      case "3" -> "03";
      case "5" -> "05";
      case "8" -> "08";
      default -> null;
    };
  }

  /**
   * Verbatim port of {@code RoadGroupUtil.setRG10ByTsaTsbNumberCode} (:285-468).
   *
   * <p>Sonar measures this at cognitive complexity 109 against a limit of 15, and the measurement
   * is correct — but the complexity is <em>data</em>, not logic. This is a 30-branch lookup table
   * that the Ministry owns, transcribed one-for-one from legacy; there is no algorithm here to
   * simplify, and every branch is an independent business fact.
   *
   * <p>It is deliberately NOT decomposed into helper methods or a rule map. The property that
   * makes this class trustworthy is that a reviewer can diff it line-by-line against
   * {@code RoadGroupUtil.java:285-468} and confirm the transcription. That auditability is the
   * defence against the specific bug this class exists to prevent — Schedule 6 ships a
   * near-identical table that maps the same inputs to DIFFERENT road groups, and a wrong
   * transcription would serve wrong values silently. Restructuring would trade a real safeguard
   * for a metric.
   *
   * <p>If the team later wants this expressed as a declarative table, that is a defensible change
   * — but it should be its own commit with its own review, not folded into a story.
   */
  @SuppressWarnings("java:S3776") // Cognitive Complexity: irreducible lookup table, see above.
  private static String rg10ByTsaTsbNumberCode(String tsaNumberCode, String tsbNumberCode) {
    String roadGroup = "";
    if (tsaNumberCode != null && tsbNumberCode != null) {
      switch (tsaNumberCode) {
        case "01": // TSB Arrow All
          if (tsbNumberCode.startsWith("01")) {
            roadGroup = "11";
          }
          break;
        case "02": // TSB Boundary
          if (tsbNumberCode.startsWith("02")) {
            roadGroup = "10";
          }
          break;
        case "03": // TSB Bulkley All
          if (tsbNumberCode.startsWith("03")) {
            roadGroup = "2";
          }
          break;
        case "04": // TSB Cassiar All
          if (tsbNumberCode.startsWith("04")) {
            roadGroup = "2";
          }
          break;
        case "05": // TSB Cranbrook All
          if (tsbNumberCode.startsWith("05")) {
            roadGroup = "10";
          }
          break;
        case "07": // TSB Golden All
          if (tsbNumberCode.startsWith("07")) {
            roadGroup = "11";
          }
          break;
        case "08": // TSB Fort Nelson All
          if (tsbNumberCode.startsWith("08")) {
            roadGroup = "7";
          }
          break;
        case "09": // TSB Invermere All
          if (tsbNumberCode.startsWith("09")) {
            roadGroup = "10";
          }
          break;
        case "10": // TSB Kalum All
          if (tsbNumberCode.startsWith("10")) {
            roadGroup = "1";
          }
          break;
        case "11": // TSB Kamloops
          if ("11A".equalsIgnoreCase(tsbNumberCode)) {
            roadGroup = "12";
          } else if ("11B".equalsIgnoreCase(tsbNumberCode)
              || "11C".equalsIgnoreCase(tsbNumberCode)
              || "11D".equalsIgnoreCase(tsbNumberCode)) {
            roadGroup = "9";
          }
          break;
        case "12": // TSB Kispiox All
          if (tsbNumberCode.startsWith("12")) {
            roadGroup = "2";
          }
          break;
        case "13": // TSB Kootenay Lake All
          if (tsbNumberCode.startsWith("13")) {
            roadGroup = "11";
          }
          break;
        case "14": // TSB Lakes Lake All
          if (tsbNumberCode.startsWith("14")) {
            roadGroup = "3";
          }
          break;
        case "15": // TSB Lillooet All
          if (tsbNumberCode.startsWith("15")) {
            roadGroup = "9";
          }
          break;
        case "16": // TSB Mackenzie All
          if (tsbNumberCode.matches(".*[A-Fa-f]")) {
            roadGroup = "7";
          } else if (tsbNumberCode.matches(".*[G-Pg-p]")) {
            roadGroup = "6";
          }
          break;
        case "17": // TSB Robson Valley All
          if (tsbNumberCode.startsWith("17")) {
            roadGroup = "12";
          }
          break;
        case "18": // TSB Merritt All
          if (tsbNumberCode.startsWith("18")) {
            roadGroup = "9";
          }
          break;
        case "20": // TSB Morice All
          if (tsbNumberCode.startsWith("20")) {
            roadGroup = "3";
          }
          break;
        case "22": // TSB Okanagan
          if ("22A".equalsIgnoreCase(tsbNumberCode)
              || "22B".equalsIgnoreCase(tsbNumberCode)
              || "22C".equalsIgnoreCase(tsbNumberCode)) {
            roadGroup = "9";
          } else if ("22D".equalsIgnoreCase(tsbNumberCode)
              || "22E".equalsIgnoreCase(tsbNumberCode)) {
            roadGroup = "9";
          } else if ("22F".equalsIgnoreCase(tsbNumberCode)
              || "22G".equalsIgnoreCase(tsbNumberCode)) {
            roadGroup = "12";
          } else if ("22H".equalsIgnoreCase(tsbNumberCode)
              || "22I".equalsIgnoreCase(tsbNumberCode)) {
            roadGroup = "11";
          }
          break;
        case "23": // TSB 100 Mile House — NOTE: no branch covers "I" (legacy gap, preserved)
          if (tsbNumberCode.matches(".*[A-Fa-f]")) {
            roadGroup = "8";
          } else if (tsbNumberCode.matches(".*[G-Hg-h]")) {
            roadGroup = "4";
          }
          break;
        case "24": // TSB Prince George
          if (tsbNumberCode.matches(".*[A-Ba-b]")) {
            roadGroup = "6";
          } else if (tsbNumberCode.matches(".*[Cc]")) {
            roadGroup = "5";
          } else if (tsbNumberCode.matches(".*[Dd]")) {
            roadGroup = "5";
          } else if (tsbNumberCode.matches(".*[E-Fe-fIi]")) {
            roadGroup = "5";
          } else if (tsbNumberCode.matches(".*[G-Hg-h]")) {
            roadGroup = "4";
          }
          break;
        case "26": // TSB Quesnel
          if (tsbNumberCode.matches(".*[A-Da-d]")) {
            roadGroup = "8";
          } else if (tsbNumberCode.matches(QUESNEL_SECOND_BRANCH)) {
            roadGroup = "4";
          }
          break;
        case "27": // TSB Revelstoke All
          if (tsbNumberCode.startsWith("27")) {
            roadGroup = "11";
          }
          break;
        case "29": // TSB Williams Lake
          if (tsbNumberCode.matches(".*[A-Ia-iPp]")) {
            roadGroup = "8";
          } else if (tsbNumberCode.matches(".*[J-Lj-l]")) {
            roadGroup = "4";
          } else if (tsbNumberCode.matches(".*[M-Nm-n]")) {
            roadGroup = "12";
          }
          break;
        case "40": // TSB Fort St. John All
          if (tsbNumberCode.startsWith("40")) {
            roadGroup = "7";
          }
          break;
        case "41": // TSB Dawson Creek All
          if (tsbNumberCode.startsWith("41")) {
            roadGroup = "7";
          }
          break;
        case "45": // TSB Cascadia — NOTE: [I-Ki-j] is asymmetric (legacy quirk, preserved)
          if (tsbNumberCode.matches(".*[A-Da-d]")) {
            roadGroup = "11";
          } else if (tsbNumberCode.matches(".*[Hh]")) {
            roadGroup = "8";
          } else if (tsbNumberCode.matches(".*[E-Ge-g]")) {
            roadGroup = "7";
          } else if (tsbNumberCode.matches(".*[I-Ki-j]")) {
            roadGroup = "1";
          }
          break;
        case "43": // TSB Nass All
          if (tsbNumberCode.startsWith("43")) {
            roadGroup = "1";
          }
          break;
        default:
          roadGroup = null;
          break;
      }
    }
    return roadGroup;
  }

  /**
   * Verbatim port of {@code RoadGroupUtil.setRG10ByTflNumberCode} (:475-520). TFL {@code "42"} is
   * deliberately absent — legacy comments it out as "not used as described in TFL list v2
   * (ILCR-161)".
   */
  private static String rg10ByTflNumberCode(String tflNumberCode) {
    String roadGroup = "";
    switch (tflNumberCode) {
      case "08", "14":
        roadGroup = "10";
        break;
      case "15", "35", "49", "59", "62":
        roadGroup = "9";
        break;
      case "03", "23", "33", "55", "56":
        roadGroup = "11";
        break;
      // Legacy maps the three-character TFL "52B" to this same road group. It is deliberately NOT
      // reproduced: TFL_NUMBER_CODE is VARCHAR2(2) on both sides and the request constrains the
      // field to two characters, so such a value cannot reach this switch on read or on save.
      // Including it would read as an accepted value the request contract rejects.
      // schedule6.RoadGroupLookup dropped it earlier for the same reason (code review 2026-08-18).
      case "05":
        roadGroup = "5";
        break;
      case "30", "52", "53":
        roadGroup = "4";
        break;
      case "18":
        roadGroup = "9";
        break;
      case "48":
        roadGroup = "7";
        break;
      case "01", "41":
        roadGroup = "1";
        break;
      default:
        roadGroup = null;
        break;
    }
    return roadGroup;
  }
}
