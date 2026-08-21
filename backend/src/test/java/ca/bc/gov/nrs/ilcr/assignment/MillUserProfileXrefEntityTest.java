package ca.bc.gov.nrs.ilcr.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@link MillUserProfileXrefEntity} data record — the Spring Data JDBC row shape read by
 * {@link MillUserProfileXrefRepository}. Asserts every column round-trips through its accessor,
 * including the {@code END_DATE null ⇒ active} convention.
 */
class MillUserProfileXrefEntityTest {

  @Test
  void carriesEveryColumnThroughItsAccessor() {
    LocalDate start = LocalDate.of(2026, 8, 4);
    LocalDateTime ts = LocalDateTime.of(2026, 8, 4, 9, 0);

    MillUserProfileXrefEntity e =
        new MillUserProfileXrefEntity(
            7L,
            "B29C746A6BAF45B9844EE2E2984CA472",
            514L,
            "Pascucci, Greg WLRS:EX",
            "GRPASCUC",
            start,
            null,
            0,
            "GRPASCUC",
            ts,
            "GRPASCUC",
            ts);

    assertEquals(7L, e.id());
    assertEquals("B29C746A6BAF45B9844EE2E2984CA472", e.userGuid());
    assertEquals(514L, e.millId());
    assertEquals("Pascucci, Greg WLRS:EX", e.userDisplayName());
    assertEquals("GRPASCUC", e.idpUsername());
    assertEquals(start, e.startDate());
    assertNull(e.endDate()); // null END_DATE ⇒ ACTIVE assignment
    assertEquals(0, e.revisionCount());
    assertEquals("GRPASCUC", e.entryUserid());
    assertEquals(ts, e.entryTimestamp());
    assertEquals("GRPASCUC", e.updateUserid());
    assertEquals(ts, e.updateTimestamp());
  }
}
