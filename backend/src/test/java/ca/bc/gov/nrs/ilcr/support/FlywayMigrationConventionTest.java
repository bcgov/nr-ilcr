package ca.bc.gov.nrs.ilcr.support;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Machine check for the fixture-authoring conventions decided on 2026-08-20 in {@code
 * docs/decisions/flyway-test-fixture-strategy.md}: seed data lives in repeatable ({@code R__})
 * migrations, only DDL keeps a version, and every repeatable carries a two-digit ordering prefix.
 *
 * <p>Why a machine check at all: the README convention alone did not prevent any of the five
 * version collisions on record, and in the seven days after the decision was accepted two more
 * versioned seed files landed (V20260822, V20260823 — 64 {@code INSERT}s between them) while zero
 * {@code R__} seed files were created. The convention is the whole of the fix; without this it
 * rests on the same discipline that has already failed.
 *
 * <p>Sibling to {@link FlywayMigrationVersionUniquenessTest}, deliberately not folded into it —
 * that class states its own scope as "version-number uniqueness only", and a duplicate version and
 * a misfiled seed are different mistakes made by different people. Like it, this runs in the plain
 * {@code surefire} phase (no Oracle, no Docker) so a violation is caught at PR time.
 *
 * <h2>What this test does NOT catch — read before trusting it</h2>
 *
 * <ol>
 *   <li><b>A grandfathered file gaining NEW {@code INSERT}s.</b> Check 1 reads the tree, not the
 *       diff. Adding rows to an already-listed file stays green. Deliberate: an edit to an existing
 *       file shows up in review as an ordinary SQL diff on a visible path, unlike a brand-new file
 *       — which is exactly the invisible-to-git case the decision exists to close.
 *   <li><b>Seed-data ID collisions (P2).</b> Duplicate {@code MILL_ID} / {@code
 *       ILCR_REPORT_COST_ITEM_ID} across schedules still produce {@code ORA-00001} at migrate time
 *       and still take the IT suite down at boot. Governed only by the ID-range registry in {@code
 *       src/test/resources/db/README.md}. Nothing here covers it.
 *   <li><b>A WRONG {@code R__} prefix.</b> Check 2 asserts the prefix exists and is orderable. It
 *       does NOT assert it is the right one — {@code 90} on a seed file passes. Deciding
 *       data-versus-constraint from SQL needs heuristics that would need their own allowlist.
 *   <li><b>Whether an {@code R__} migration is idempotent.</b> Currently moot ({@code
 *       AbstractOracleIT} builds a fresh container per JVM, no {@code withReuse}); it stops being
 *       moot the day someone reuses one.
 * </ol>
 */
class FlywayMigrationConventionTest {

  private static final Path MIGRATION_DIR = Paths.get("src", "test", "resources", "db");

  /** Shrink-only allowlist of pre-decision versioned files that carry seed rows. */
  private static final Path MANIFEST = MIGRATION_DIR.resolve("grandfathered-seeded-versions.txt");

  /** Developer tooling that reapplies local-only DDL; lives outside the module. */
  private static final Path LOCAL_DDL_SCRIPT = Paths.get("..", "scripts", "apply-local-ddl.sh");

  private static final String DECISION_DOC = "docs/decisions/flyway-test-fixture-strategy.md";

  private static final Pattern VERSIONED = Pattern.compile("^V\\d+(?:\\.\\d+)*__.*\\.sql$");

  private static final Pattern REPEATABLE = Pattern.compile("^R__.*\\.sql$");

  /**
   * {@code R__<two digits>_<lower_snake>.sql}. Two digits, not one and not three, so every
   * repeatable sorts against every other by the same number of characters — Flyway orders
   * repeatables lexicographically by description, so {@code R__5_} would sort AFTER {@code R__90_}.
   */
  private static final Pattern REPEATABLE_PREFIXED =
      Pattern.compile("^R__(\\d{2})_[a-z0-9_]+\\.sql$");

  /** Lowest legal prefix. Below this a file sorts ahead of every legitimate seed. */
  private static final int MIN_PREFIX = 10;

  /**
   * Seed DML. {@code INSERT ALL} and {@code MERGE INTO} have zero occurrences on the tree today and
   * are matched anyway — both are seed-data DML, and one alternation now is cheaper than a ticket
   * later.
   */
  private static final Pattern SEED_DML =
      Pattern.compile(
          "\\bINSERT\\s+INTO\\b|\\bINSERT\\s+ALL\\b|\\bMERGE\\s+INTO\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

  private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");

  private static final Pattern MIGRATIONS_ARRAY =
      Pattern.compile("MIGRATIONS=\\((.*?)\\)", Pattern.DOTALL);

  private static final Pattern QUOTED_ENTRY = Pattern.compile("\"([^\"]+)\"");

  // ---------------------------------------------------------------------------------------------
  // Check 1 — no NEW versioned migration may carry seed rows
  // ---------------------------------------------------------------------------------------------

  @Test
  void newVersionedMigrationsCarryNoSeedData() throws IOException {
    Set<String> grandfathered = readManifest();

    List<String> offenders =
        migrationFileNames().stream()
            .filter(name -> VERSIONED.matcher(name).matches())
            .filter(name -> !grandfathered.contains(name))
            .filter(name -> containsSeedDml(read(MIGRATION_DIR.resolve(name))))
            .collect(toList());

    assertTrue(
        offenders.isEmpty(),
        () ->
            "Seed rows in a NEW versioned migration — this reopens the version-collision class of "
                + "bug the 2026-08-20 decision closed (two branches claiming one V number produce "
                + "two differently named files, so git merges them cleanly and Flyway kills the "
                + "whole IT suite at boot):\n"
                + bullets(offenders)
                + "\n\nPut the seed rows in a repeatable migration instead: "
                + "R__<10-80>_<what_it_seeds>.sql (constraints, FKs and indexes go in R__9x_). A "
                + "repeatable has no version to collide with and Flyway applies it after every "
                + "versioned migration. Keep the V__ file for DDL only.\n"
                + "If the file genuinely must stay versioned, add its name to "
                + MANIFEST
                + " — that is a deliberate, reviewable act, not a formality.\n"
                + "See "
                + DECISION_DOC
                + " and src/test/resources/db/README.md conventions 1 and 1a.");
  }

  // ---------------------------------------------------------------------------------------------
  // Check 2 — every repeatable carries a two-digit ordering prefix
  // ---------------------------------------------------------------------------------------------

  @Test
  void everyRepeatableMigrationCarriesATwoDigitOrderingPrefix() throws IOException {
    List<String> offenders = new ArrayList<>();

    for (String name : migrationFileNames()) {
      if (!REPEATABLE.matcher(name).matches()) {
        continue;
      }
      Matcher matcher = REPEATABLE_PREFIXED.matcher(name);
      if (!matcher.matches()) {
        offenders.add(name + " -> no R__<two digits>_ prefix");
      } else if (Integer.parseInt(matcher.group(1)) < MIN_PREFIX) {
        offenders.add(
            name + " -> prefix " + matcher.group(1) + " is below the " + MIN_PREFIX + " floor");
      }
    }

    assertTrue(
        offenders.isEmpty(),
        () ->
            "Repeatable migration without a usable ordering prefix:\n"
                + bullets(offenders)
                + "\n\nFlyway applies repeatables in lexicographic order of description (verified "
                + "on 12.4.0), so the prefix is the ONLY thing fixing FK order between them. Name "
                + "the file R__<10-80>_<what_it_seeds>.sql for data, R__<90-99>_<name>.sql for "
                + "constraints, FKs and indexes that must land after it.\n"
                + "This check asserts the prefix EXISTS and is orderable. It does not and cannot "
                + "assert the prefix is the right one — picking the band is on you.\n"
                + "See "
                + DECISION_DOC
                + ".");
  }

  // ---------------------------------------------------------------------------------------------
  // Check 3 — the grandfathering manifest is shrink-only and cannot rot
  // ---------------------------------------------------------------------------------------------

  @Test
  void theGrandfatheringManifestDoesNotRot() throws IOException {
    List<String> stale = new ArrayList<>();

    for (String name : readManifest()) {
      Path file = MIGRATION_DIR.resolve(name);
      if (!Files.isRegularFile(file)) {
        stale.add(name + " -> no such file; the migration was deleted or renamed");
      } else if (!containsSeedDml(read(file))) {
        stale.add(name + " -> no longer contains seed rows; it is compliant now");
      }
    }

    assertTrue(
        stale.isEmpty(),
        () ->
            "Stale entries in "
                + MANIFEST
                + " — delete these lines:\n"
                + bullets(stale)
                + "\n\nThe manifest is SHRINK-ONLY. A line that no longer describes a real "
                + "violation is cover for a file nobody is looking at, and a hand-copied baseline "
                + "goes stale fast: issue #367 was filed naming V30__ilcr_mill_user_profile_xref"
                + ".sql as one of three clean files two days after PR #356 deleted it.\n"
                + "If you converted a fixture to R__, removing its line here is the record that "
                + "the baseline shrank. Do it in the same commit.");
  }

  // ---------------------------------------------------------------------------------------------
  // Check 4 — the local-DDL script cannot name a migration that does not exist
  // ---------------------------------------------------------------------------------------------

  @Test
  void applyLocalDdlScriptNamesOnlyMigrationsThatExist() throws IOException {
    assertTrue(
        Files.isRegularFile(LOCAL_DDL_SCRIPT),
        () -> "expected the local-DDL script at " + LOCAL_DDL_SCRIPT.toAbsolutePath());

    String script = read(LOCAL_DDL_SCRIPT);
    Matcher array = MIGRATIONS_ARRAY.matcher(script);
    assertTrue(
        array.find(),
        () ->
            "no MIGRATIONS=( ... ) array found in " + LOCAL_DDL_SCRIPT + "; has it been renamed?");

    List<String> missing = new ArrayList<>();
    Matcher entry = QUOTED_ENTRY.matcher(array.group(1));
    while (entry.find()) {
      String name = entry.group(1);
      if (!Files.isRegularFile(MIGRATION_DIR.resolve(name))) {
        missing.add(name);
      }
    }

    assertTrue(
        missing.isEmpty(),
        () ->
            "scripts/apply-local-ddl.sh lists migrations that do not exist in "
                + MIGRATION_DIR
                + ":\n"
                + bullets(missing)
                + "\n\nThat script runs under `set -euo pipefail` and `cat`s each entry, so a "
                + "stale name does not degrade — it aborts the whole run, and the migrations after "
                + "it are never applied. The developer sees a broken local database and reasonably "
                + "blames the image.\n"
                + "This happened: PR #356 replaced V30__ilcr_mill_user_profile_xref.sql with "
                + "V20260825__the_ilcr_user_and_mill_user_xref.sql and left the array pointing at "
                + "the deleted file.\n"
                + "If you renamed a migration, update the array in the same commit.");
  }

  // ---------------------------------------------------------------------------------------------

  private static List<String> migrationFileNames() throws IOException {
    assertTrue(
        Files.isDirectory(MIGRATION_DIR),
        () -> "migration directory " + MIGRATION_DIR.toAbsolutePath() + " should exist");
    try (Stream<Path> entries = Files.list(MIGRATION_DIR)) {
      return entries.map(path -> path.getFileName().toString()).sorted().collect(toList());
    }
  }

  /** Reads the manifest, dropping blank lines and {@code #} comments. Order is preserved. */
  private static Set<String> readManifest() throws IOException {
    assertTrue(
        Files.isRegularFile(MANIFEST),
        () ->
            "grandfathering manifest "
                + MANIFEST.toAbsolutePath()
                + " should exist — without it every pre-decision fixture reads as a violation");
    Set<String> names = new LinkedHashSet<>();
    for (String line : Files.readAllLines(MANIFEST, StandardCharsets.UTF_8)) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
        names.add(trimmed);
      }
    }
    return names;
  }

  /**
   * True if the script contains seed DML outside a comment.
   *
   * <p>Comments are stripped first because these fixtures are heavily annotated — several open with
   * a page of prose — and a header saying {@code -- INSERT INTO … (moved to R__30_…)} is
   * compliance, not a violation. Failing a compliant file is the failure mode most likely to get
   * the whole check deleted by whoever hits it.
   *
   * <p>Known limit: an {@code INSERT INTO} inside a quoted string literal would still match. There
   * are none on the tree (checked 2026-08-27, including the four files that build DDL through
   * {@code EXECUTE IMMEDIATE}), and recognising them needs a real parser.
   */
  private static boolean containsSeedDml(String sql) {
    String code = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
    code = LINE_COMMENT.matcher(code).replaceAll(" ");
    return SEED_DML.matcher(code).find();
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + file, e);
    }
  }

  private static String bullets(List<String> lines) {
    return lines.stream().map(line -> "  " + line).collect(joining("\n"));
  }
}
