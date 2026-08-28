package ca.bc.gov.nrs.ilcr.support;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Fast, no-container guard against the class of bug where two feature branches independently claim
 * the same Flyway version. Flyway itself only surfaces a duplicate at {@code migrate()} time — i.e.
 * when the whole Testcontainers {@code *IT} suite boots (see {@link AbstractOracleIT}) — so a green
 * unit build can still hide a landmine that reds every integration test the moment they run.
 *
 * <p>This runs in the normal {@code surefire} phase (plain {@code *Test}, no Oracle/Docker) so the
 * collision is caught at PR time, locally and in CI, with a message naming the offending files
 * rather than a cryptic {@code FlywayException}.
 *
 * <p>Scope is deliberately narrow: version-number uniqueness only. It does NOT check seed-data ID
 * collisions (duplicate mill/cost-item PKs across schedules) — those are a separate concern tracked
 * in {@code src/test/resources/db/README.md}.
 *
 * <p>Its sibling {@link FlywayMigrationConventionTest} covers the rest of the fixture conventions:
 * that a new {@code V__} carries no seed rows, that every {@code R__} has a two-digit ordering
 * prefix, that the grandfathering manifest has not rotted, and that every {@code .sql} here is
 * classifiable. Look there before concluding a rule is unenforced.
 */
class FlywayMigrationVersionUniquenessTest {

  // Versioned Flyway scripts: V<version>__<description>.sql. Flyway accepts BOTH a dot and an
  // underscore between version parts, so V1.1__x.sql and V1_1__x.sql are the SAME version 1.1 —
  // which is precisely the collision this test exists to catch. Matching only the dotted form made
  // that pair invisible here (found by the 2026-08-27 review of #367); the version is normalised
  // below so the two spellings compare equal.
  private static final Pattern VERSIONED = Pattern.compile("^V(\\d+(?:[._]\\d+)*)__.*\\.sql$");

  private static final Path MIGRATION_DIR = Paths.get("src", "test", "resources", "db");

  @Test
  void everyFlywayVersionIsClaimedByExactlyOneMigration() throws IOException {
    assertTrue(
        Files.isDirectory(MIGRATION_DIR),
        () -> "migration directory " + MIGRATION_DIR.toAbsolutePath() + " should exist");

    // Files.walk, not Files.list: Flyway scans a location recursively, so db/<sub>/V9__x.sql is
    // applied at IT boot and must be counted here too.
    Map<String, List<String>> filesByVersion = new TreeMap<>();
    try (Stream<Path> entries = Files.walk(MIGRATION_DIR)) {
      entries
          .filter(Files::isRegularFile)
          .forEach(
              path -> {
                String name = path.getFileName().toString();
                Matcher matcher = VERSIONED.matcher(name);
                if (matcher.matches()) {
                  filesByVersion
                      .computeIfAbsent(matcher.group(1).replace('_', '.'), key -> new ArrayList<>())
                      .add(MIGRATION_DIR.relativize(path).toString().replace('\\', '/'));
                }
              });
    }

    List<String> collisions =
        filesByVersion.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> "  V" + entry.getKey() + " -> " + entry.getValue())
            .toList();

    assertTrue(
        collisions.isEmpty(),
        () ->
            "Duplicate Flyway migration versions (two branches grabbed the same V number — bump "
                + "the newer one to the next free slot; see src/test/resources/db/README.md):\n"
                + String.join("\n", collisions));
  }
}
