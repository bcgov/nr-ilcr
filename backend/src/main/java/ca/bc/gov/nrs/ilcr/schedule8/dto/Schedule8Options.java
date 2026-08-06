package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.util.List;

/**
 * Option lists for the Schedule 8 (Tree to Truck) page-editor dropdowns — each a code + its resolved
 * {@code DESCRIPTION} label, ordered as the underlying {@code THE.*_CODE} table returns them. These
 * back the six selectors so the editor shows descriptions (never raw codes) and writes back the code.
 *
 * <p>The {@code tsaNumbers}/{@code tflNumbers}/{@code supplyBlocks} lists drive the mutually-exclusive
 * TSA-or-TFL selector: the UI's {@code TSA or TFL} choice is a TSA number OR the {@code "TFL"} marker
 * (legacy sentinel), which in turn enables the TFL list and disables the supply-block list (§BR-03).
 */
public record Schedule8Options(
    List<CodeOption> supportCentres,
    List<CodeOption> regions,
    List<CodeOption> becZones,
    List<CodeOption> tsaNumbers,
    List<CodeOption> tflNumbers,
    List<CodeOption> supplyBlocks,
    List<CodeOption> skidTypes,
    List<CodeOption> additionCostItems,
    List<CodeOption> deductionCostItems,
    List<CodeOption> costTypes) {

  /** A single dropdown choice: the stored code and the {@code DESCRIPTION} label shown to the user. */
  public record CodeOption(String code, String description) {
  }
}
