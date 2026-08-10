package ca.bc.gov.nrs.ilcr.schedule5.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/**
 * Measures a {@link MaxByteLength} field in UTF-8 bytes — the encoding the delivery database stores
 * (AL32UTF8), so this count is the one the column width is actually spent on.
 *
 * <p>{@code null} passes: presence is {@code @NotBlank}'s job, and a nullable optional field must not
 * acquire a required-ness it never had. Not trimmed either — the value measured here is the value
 * that will be bound, and Schedule 5 trims {@code campName} in the service (deviation (I)) AFTER
 * validation, so trimming here would let a padded 32-byte name through to a 30-byte column on any
 * path that skipped the trim.
 *
 * <p>A value already over the companion {@code @Size(max)} passes here too — deliberately. The two
 * constraints share one message key, so reporting both would hand the licensee the same sentence
 * twice; {@code @Size} owns the character bound and this validator owns only the multibyte overflow
 * {@code @Size} cannot see. See {@link MaxByteLength#charMax()}.
 */
public class MaxByteLengthValidator implements ConstraintValidator<MaxByteLength, String> {

  private int max;
  private int charMax;

  @Override
  public void initialize(MaxByteLength constraint) {
    this.max = constraint.value();
    this.charMax = constraint.charMax();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null || value.length() > charMax) {
      return true;
    }
    return value.getBytes(StandardCharsets.UTF_8).length <= max;
  }
}
