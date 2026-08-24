package ca.bc.gov.nrs.ilcr.dto.base;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Caps a {@code String} by its UTF-8 BYTE length, the unit the delivery columns are actually
 * declared in. Pairs with — never replaces — the {@code @Size} character cap on the same field.
 *
 * <p><strong>Why both.</strong> Verified against the seeded delivery image (2026-08-10): the
 * database is {@code NLS_CHARACTERSET = AL32UTF8} with {@code NLS_LENGTH_SEMANTICS = BYTE}, and
 * {@code ALL_TAB_COLUMNS} reports {@code CHAR_USED = 'B'} for {@code CAMP_REPORT.CAMP_NAME} (30)
 * and {@code CAMP_REPORT.COMMENTS} (4000). {@code @Size} measures Java characters, so a name of 30
 * accented or CJK characters satisfies it and then overflows a 30-BYTE column: Oracle raises
 * ORA-12899, the service can only map that {@code DataAccessException} to {@link
 * ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException ScheduleNotSavedException}, and the
 * licensee gets an opaque 500 on an ordinary save. This constraint turns that into a clean 400 at
 * the same field. The character cap stays because it is the LEGACY bound (the screen's own {@code
 * maxlength}) and is the tighter one for ASCII input, which is effectively all stored data today.
 *
 * <p><strong>Message.</strong> Each field reuses its existing per-field length key rather than
 * introducing a new one, so a byte violation and a character violation are indistinguishable to the
 * client and no frontend or bundle change is owed. The cost is that the text says "30 characters or
 * fewer" for what is really a byte overflow, which understates the limit for multibyte input —
 * accepted deliberately: a new "N bytes" message would leak storage encoding into licensee-facing
 * text, and the honest alternative (character-semantics columns) is DDL this project cannot issue.
 *
 * <p><strong>{@link #charMax()} exists to prevent a DOUBLE-REPORTED message, not to duplicate
 * {@code @Size}.</strong> Because the two constraints share a message key, a value that violates
 * both produced {@code "Camp Name must be 30 characters or fewer.; Camp Name must be 30 characters
 * or fewer."} — the {@code GlobalExceptionHandler} joins violations with {@code "; "}. That is the
 * COMMON case, not an edge one: on {@code campName} the caps are both 30 and a byte is never
 * narrower than a character, so every over-long ASCII name trips both. So this validator DEFERS
 * whenever the character cap is already violated: {@code @Size} owns the character bound, and this
 * constraint owns only what {@code @Size} lets through. Set {@code charMax} to the same value as
 * the field's {@code @Size(max)}; the two annotations are then jointly exhaustive and mutually
 * exclusive, and exactly one message reaches the licensee whatever they typed.
 *
 * <p><strong>Scope.</strong> SHARED. Introduced for Schedule 5 in {@code schedule5/dto} on the
 * per-schedule custom-constraint house pattern ({@code schedule4/dto/DistanceCategoryComplete},
 * {@code schedule8/dto/Schedule8SampleRules}), with the instruction to hoist it here once a second
 * schedule adopted it — which Schedule 7B did on 2026-08-11 for {@code CulvertRequest.comments}
 * against the same {@code VARCHAR2(4000 BYTE)} column. Consumers: {@code schedule5/dto/CampRequest}
 * ({@code campName}, {@code comments}) and {@code schedule7b/dto/CulvertRequest} ({@code
 * comments}). The same char-vs-byte gap is still recorded in {@code deferred-work.md} for Schedule
 * 6/11 comments and for the client-side {@code .length} mirrors; those adopt this constraint rather
 * than growing their own.
 */
@Documented
@Constraint(validatedBy = MaxByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxByteLength {

  /** The column's declared BYTE width. */
  int value();

  /**
   * The companion {@code @Size(max)} on the same field. A value already over this many CHARACTERS
   * is left to {@code @Size} so the shared message is not reported twice.
   */
  int charMax();

  /**
   * The bundle key reported on violation. Every consumer passes its field's OWN existing length key
   * explicitly, so a byte overflow is indistinguishable from a character overflow to the client and
   * no new message is owed. The default is Schedule 5's, retained only because it was the original
   * consumer — do not rely on it; name the key.
   *
   * @return the message template
   */
  String message() default "{campNameMaxLengthErrorMsg}";

  /**
   * Bean-Validation groups this constraint participates in.
   *
   * @return the groups (default: none, i.e. {@code Default})
   */
  Class<?>[] groups() default {};

  /**
   * Bean-Validation payload, unused here.
   *
   * @return the payload types
   */
  Class<? extends Payload>[] payload() default {};
}
