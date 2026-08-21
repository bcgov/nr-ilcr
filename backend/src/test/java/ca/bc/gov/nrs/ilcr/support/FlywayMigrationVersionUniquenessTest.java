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
import java.util.stream.Collectors;
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
 */
class FlywayMigrationVersionUniquenessTest {

  // Versioned Flyway scripts: V<version>__<description>.sql (version may be dotted, e.g. V1.1).
  private static final Pattern VERSIONED = Pattern.compile("^V(\\d+(?:\\.\\d+)*)__.*\\.sql$");

  private static final Path MIGRATION_DIR = Paths.get("src", "test", "resources", "db");

  @Test
  void everyFlywayVersionIsClaimedByExactlyOneMigration() throws IOException {
    assertTrue(
        Files.isDirectory(MIGRATION_DIR),
        () -> "migration directory " + MIGRATION_DIR.toAbsolutePath() + " should exist");

    Map<String, List<String>> filesByVersion = new TreeMap<>();
    try (Stream<Path> entries = Files.list(MIGRATION_DIR)) {
      entries
          .map(path -> path.getFileName().toString())
          .forEach(
              name -> {
                Matcher matcher = VERSIONED.matcher(name);
                if (matcher.matches()) {
                  filesByVersion
                      .computeIfAbsent(matcher.group(1), key -> new ArrayList<>())
                      .add(name);
                }
              });
    }

    List<String> collisions =
        filesByVersion.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> "  V" + entry.getKey() + " -> " + entry.getValue())
            .collect(Collectors.toList());

    assertTrue(
        collisions.isEmpty(),
        () ->
            "Duplicate Flyway migration versions (two branches grabbed the same V number — bump "
                + "the newer one to the next free slot; see src/test/resources/db/README.md):\n"
                + String.join("\n", collisions));
  }
}
