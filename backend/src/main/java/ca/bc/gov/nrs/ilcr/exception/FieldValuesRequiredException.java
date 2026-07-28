package ca.bc.gov.nrs.ilcr.exception;

import java.util.List;

/**
 * One or more required selection fields are missing/blank/invalid (UC-SEC-001 S04/S05/S08). Carries
 * the ordered field labels (screen order — e.g. {@code Mill} before {@code Reporting Year}) so
 * {@link GlobalExceptionHandler} can resolve the verbatim legacy required-field text
 * ({@code javax.faces.component.UIInput.REQUIRED = "{0}: Value is required."}) once per field and
 * return ALL messages together on a single 400 (S08) — unlike a typed {@code @RequestParam}, which
 * would fail on the first field only.
 */
public class FieldValuesRequiredException extends RuntimeException {

  private final transient List<String> fieldLabels;

  public FieldValuesRequiredException(List<String> fieldLabels) {
    super("Required fields missing: " + String.join(", ", fieldLabels));
    this.fieldLabels = List.copyOf(fieldLabels);
  }

  public List<String> getFieldLabels() {
    return fieldLabels;
  }
}
