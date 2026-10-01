package ca.bc.gov.nrs.ilcr.support;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
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
 * <h2>The detectors are unit-tested, and that is not decoration</h2>
 *
 * <p>{@link DetectorContracts} below asserts the pure predicates directly — no filesystem, no
 * fixtures. It exists because the filesystem checks all pass <em>vacuously</em> against a compliant
 * tree: a regex edit that quietly narrows a detector leaves this class green and stops protecting
 * anything. The 2026-08-27 code review found <b>four</b> live bypasses that way — a block-comment
 * opener inside a line comment, Flyway's underscore version separator, a subdirectory, and an
 * un-banded prefix — each of which left every check reporting success while unlisted files carrying
 * real {@code INSERT}s sat in the directory. All four now have cases here.
 *
 * <h2>What this test does NOT catch — read before trusting it</h2>
 *
 * <ol>
 *   <li><b>A grandfathered file gaining NEW {@code INSERT}s.</b> Check 1 reads the tree, not the
 *       diff. Adding rows to an already-listed file stays green. Deliberate: an edit to an existing
 *       file shows up in review as an ordinary SQL diff on a visible path, unlike a brand-new file
 *       — which is exactly the invisible-to-git case the decision exists to close.
 *   <li><b>Growth of the manifest.</b> "Shrink-only" is a review rule, not an enforced one. Nothing
 *       here stops a new versioned seed file from going green by appending one line — the check
 *       makes that act <em>visible</em>, it does not prevent it.
 *   <li><b>Seed-data ID collisions (P2).</b> Duplicate {@code MILL_ID} / {@code
 *       ILCR_REPORT_COST_ITEM_ID} across schedules still produce {@code ORA-00001} at migrate time
 *       and still take the IT suite down at boot. Governed only by the ID-range registry in {@code
 *       src/test/resources/db/README.md}. Nothing here covers it.
 *   <li><b>A WRONG {@code R__} band.</b> Check 2 asserts a two-digit prefix in {@code 10}-{@code
 *       99}. The convention splits that into {@code 10}-{@code 80} for data and {@code 90}+ for
 *       constraints, and <b>{@code 81}-{@code 89} belongs to neither</b> — all of those pass.
 *       Deciding data-versus-constraint from SQL needs heuristics that would need their own
 *       allowlist, so the band split stays human (D2, ratified 2026-08-27).
 *   <li><b>Seed rows written as {@code CREATE TABLE … AS SELECT}.</b> CTAS populates rows with no
 *       {@code INSERT} keyword at all and is not matched. No fixture uses it.
 *   <li><b>Whether an {@code R__} migration is idempotent.</b> Currently moot ({@code
 *       AbstractOracleIT} builds a fresh container per JVM, no {@code withReuse}); it stops being
 *       moot the day someone reuses one.
 *   <li><b>Check 4 does not run everywhere.</b> It self-skips when {@code ../scripts} is absent —
 *       notably inside the backend Docker image build, whose context is {@code backend/} only. See
 *       {@link #applyLocalDdlScriptNamesOnlyMigrationsThatExist()}.
 * </ol>
 */
class FlywayMigrationConventionTest {

  private static final Path MIGRATION_DIR = Paths.get("src", "test", "resources", "db");

  /** Shrink-only allowlist of versioned files that already carried seed rows at the baseline. */
  private static final Path MANIFEST = MIGRATION_DIR.resolve("grandfathered-seeded-versions.txt");

  /** Developer tooling that reapplies local-only DDL; lives outside the module. */
  private static final Path LOCAL_DDL_SCRIPT = Paths.get("..", "scripts", "apply-local-ddl.sh");

  private static final String DECISION_DOC = "docs/decisions/flyway-test-fixture-strategy.md";

  private static final String BOM = "﻿";

  /**
   * Versioned migration. Flyway accepts BOTH a dot and an underscore between version parts, so
   * {@code V35_1__x.sql} is version 35.1 exactly as {@code V35.1__x.sql} is. Missing the underscore
   * form let a real seed file through every check (found 2026-08-27); {@link
   * FlywayMigrationVersionUniquenessTest} carried the same hole, which made {@code V1.1__a} plus
   * {@code V1_1__b} an undetected version collision.
   */
  private static final Pattern VERSIONED = Pattern.compile("^V\\d+(?:[._]\\d+)*__.*\\.sql$");

  private static final Pattern REPEATABLE = Pattern.compile("^R__.*\\.sql$");

  /**
   * {@code R__<two digits>_<lower_snake>.sql}.
   *
   * <p>Two digits, not one: Flyway orders repeatables lexicographically by description, so a
   * one-digit prefix sorts <b>after</b> every two-digit prefix from {@code 10} to {@code 49} —
   * {@code "5 seed"} is greater than {@code "10 seed"} because {@code '5'} is greater than {@code
   * '1'}. It sorts <i>before</i> {@code "90 fks"}; an earlier revision of this comment, of {@code
   * db/README.md} and of the commit message had that example inverted. Asserted in {@link
   * DetectorContracts#oneDigitPrefixSortsAfterTheTwoDigitDataBands()} so prose cannot drift again.
   */
  private static final Pattern REPEATABLE_PREFIXED =
      Pattern.compile("^R__(\\d{2})_[a-z0-9_]+\\.sql$");

  /** Any {@code R__} whose prefix is digits, used to tell "wrong digits" from "no prefix". */
  private static final Pattern REPEATABLE_ANY_DIGITS = Pattern.compile("^R__(\\d+)_.*\\.sql$");

  /** Lowest legal prefix. Below this a file sorts ahead of every legitimate seed. */
  private static final int MIN_PREFIX = 10;

  /**
   * Seed DML. All three {@code INSERT} forms plus {@code MERGE}: Oracle's conditional multi-table
   * insert is {@code INSERT FIRST WHEN … THEN INTO …}, the sibling of {@code INSERT ALL}, and
   * omitting it let a seed file through (found 2026-08-27). {@code INSERT ALL}, {@code INSERT
   * FIRST} and {@code MERGE INTO} have zero occurrences on the tree today and are matched anyway.
   */
  private static final Pattern SEED_DML =
      Pattern.compile(
          "\\bINSERT\\s+(?:INTO|ALL|FIRST)\\b|\\bMERGE\\s+INTO\\b", Pattern.CASE_INSENSITIVE);

  /**
   * Line and block comments in ONE left-to-right alternation, so whichever opens first wins.
   *
   * <p>Stripping blocks before lines was a live false-GREEN: a line comment containing a
   * block-comment opener started a block match that ran on to the next block terminator, swallowing
   * every statement in between and hiding real {@code INSERT}s (found 2026-08-27). A single pass
   * cannot make that mistake, because the line comment is consumed before the opener inside it is
   * ever considered.
   */
  private static final Pattern COMMENT = Pattern.compile("--[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

  /** Start of a bash array assignment, anchored to a line so a mention in prose cannot match. */
  private static final Pattern MIGRATIONS_ARRAY_OPEN =
      Pattern.compile("^\\s*MIGRATIONS\\+?=\\(", Pattern.MULTILINE);

  // ---------------------------------------------------------------------------------------------
  // Check 1 — no NEW versioned migration may carry seed rows
  // ---------------------------------------------------------------------------------------------

  @Test
  void newVersionedMigrationsCarryNoSeedData() throws IOException {
    Set<String> grandfathered = readManifest();

    List<String> offenders =
        migrationFiles().entrySet().stream()
            .filter(entry -> VERSIONED.matcher(fileNameOf(entry.getKey())).matches())
            .filter(entry -> !grandfathered.contains(entry.getKey()))
            .filter(entry -> containsSeedDml(read(entry.getValue())))
            .map(Map.Entry::getKey)
            .toList();

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
                + "Adding the filename to "
                + MANIFEST
                + " also turns this green. That is deliberately possible and deliberately visible "
                + "— an exemption to argue for in review, not the quick way out of a red build.\n"
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

    for (String relative : migrationFiles().keySet()) {
      String name = fileNameOf(relative);
      if (!REPEATABLE.matcher(name).matches()) {
        continue;
      }
      String problem = repeatablePrefixProblem(name);
      if (problem != null) {
        offenders.add(relative + " -> " + problem);
      }
    }

    assertTrue(
        offenders.isEmpty(),
        () ->
            "Repeatable migration without a usable ordering prefix:\n"
                + bullets(offenders)
                + "\n\nFlyway applies repeatables in lexicographic order of description (verified "
                + "on 12.4.0), so the prefix is the ONLY thing fixing FK order between them. Name "
                + "the file R__<NN>_<lower_snake>.sql, NN from 10 to 99.\n"
                + "Two digits matter: a one-digit prefix sorts AFTER every prefix from 10 to 49 "
                + "(\"5 seed\" > \"10 seed\"), which is the ordering trap the rule closes.\n"
                + "By convention 10-80 is seed data and 90-99 is constraints, FKs and indexes that "
                + "must land after it. That split is NOT enforced here — 81-89 passes too, and so "
                + "does the wrong band. See "
                + DECISION_DOC
                + ".");
  }

  /** Returns null when the name is fine, else the specific reason — never a blanket "no prefix". */
  private static String repeatablePrefixProblem(String name) {
    Matcher twoDigit = REPEATABLE_PREFIXED.matcher(name);
    if (twoDigit.matches()) {
      int prefix = Integer.parseInt(twoDigit.group(1));
      return prefix < MIN_PREFIX
          ? "prefix " + twoDigit.group(1) + " is below the " + MIN_PREFIX + " floor"
          : null;
    }
    Matcher anyDigits = REPEATABLE_ANY_DIGITS.matcher(name);
    if (anyDigits.matches()) {
      String digits = anyDigits.group(1);
      if (digits.length() != 2) {
        return "prefix '" + digits + "' has " + digits.length() + " digit(s); exactly two required";
      }
      return "prefix is fine; the description after it must be lower_snake_case ([a-z0-9_])";
    }
    return "no R__<two digits>_ prefix at all";
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

  /**
   * Skips rather than fails when the script is absent. The backend Docker image build has a {@code
   * backend/} context ({@code Dockerfile:4-8} copies only {@code pom.xml}, {@code .mvn}, {@code
   * src}) and runs {@code mvn package} with unit tests enabled, so {@code ../scripts} genuinely
   * cannot exist there. A hard assertion red-lined the image build and the required merge gate
   * (found 2026-08-27). The trade is stated rather than hidden: in that one context this check does
   * not run. It does run for every full checkout — local, {@code analysis.yml}, and the CI test
   * job.
   */
  @Test
  void applyLocalDdlScriptNamesOnlyMigrationsThatExist() {
    assumeTrue(
        Files.isRegularFile(LOCAL_DDL_SCRIPT),
        "no ../scripts/apply-local-ddl.sh here — module-only build context, check skipped");

    String script = read(LOCAL_DDL_SCRIPT);
    List<String> entries = parseMigrationsArray(script);

    assertFalse(
        entries.isEmpty() && arrayHasContent(script),
        () ->
            "found a MIGRATIONS=( ... ) array in "
                + LOCAL_DDL_SCRIPT
                + " with content, but parsed no entries out of it — this check would pass without "
                + "checking anything. Entries are read one per line, quoted or bare. If the array "
                + "is written some other way, teach this parser about it rather than leaving it "
                + "silently vacuous.");

    List<String> unusable =
        entries.stream()
            .map(
                name ->
                    localDdlEntryProblem(name) == null
                        ? null
                        : name + " -> " + localDdlEntryProblem(name))
            .filter(java.util.Objects::nonNull)
            .toList();

    assertTrue(
        unusable.isEmpty(),
        () ->
            "scripts/apply-local-ddl.sh lists entries that are not usable migrations in "
                + MIGRATION_DIR
                + ":\n"
                + bullets(unusable)
                + "\n\nThat script runs under `set -euo pipefail` and `cat`s each entry, so a "
                + "stale name does not degrade — it aborts the whole run, and the migrations after "
                + "it are never applied. The developer sees a broken local database and reasonably "
                + "blames the image.\n"
                + "This happened: PR #356 replaced V30__ilcr_mill_user_profile_xref.sql with "
                + "V20260825__the_ilcr_user_and_mill_user_xref.sql and left the array pointing at "
                + "the deleted file.\n"
                + "If you renamed a migration, update the array in the same commit.\n"
                + "Entries must be a bare migration filename living in "
                + MIGRATION_DIR
                + " — existence alone is not enough, because a traversal path resolves to a real "
                + "file that is not a migration at all.");
  }

  /**
   * Returns null when the entry is a usable migration, else the specific reason.
   *
   * <p>Existence alone is NOT the script's contract, and checking only that was a real hole (raised
   * in review on PR #372). {@code apply-local-ddl.sh} pipes every entry through {@code cat} into
   * {@code sqlplus}, so an entry that escapes the migration directory — {@code
   * ../../../../../scripts/apply-local-ddl.sh}, an absolute path, anything carrying {@code ..} —
   * satisfies a bare {@code Files.isRegularFile} check and feeds a NON-SQL file to the database.
   * Verified before fixing: with that traversal path as the sole array entry, the check passed.
   *
   * <p>So the guard now asserts what the script actually needs: a bare filename, shaped like a
   * Flyway migration, present in {@code db/}.
   */
  private static String localDdlEntryProblem(String name) {
    if (name.contains("/") || name.contains("\\")) {
      return "is a path, not a bare filename in " + MIGRATION_DIR;
    }
    if (!VERSIONED.matcher(name).matches() && !REPEATABLE.matcher(name).matches()) {
      return "is not a Flyway migration filename (V<version>__<desc>.sql or R__<desc>.sql)";
    }
    if (!Files.isRegularFile(MIGRATION_DIR.resolve(name))) {
      return "does not exist in " + MIGRATION_DIR;
    }
    return null;
  }

  /**
   * Extracts array entries line-by-line rather than with one regex over the whole array.
   *
   * <p>A single {@code MIGRATIONS=\((.*?)\)} was fooled three ways (found 2026-08-27): a closing
   * parenthesis inside a comment such as {@code # local-only (see #356)} truncated the match to
   * zero entries; unquoted and single-quoted entries matched nothing; and only the first occurrence
   * in the file was read, so a {@code # Usage: MIGRATIONS=(…)} header hid the real array. Each of
   * those returned a vacuous pass on a script that was actually broken.
   */
  private static List<String> parseMigrationsArray(String script) {
    List<String> entries = new ArrayList<>();
    forEachArrayBodyLine(
        script,
        line -> {
          for (String token : line.split("\\s+")) {
            String entry = unquote(token.trim());
            if (!entry.isEmpty()) {
              entries.add(entry);
            }
          }
        });
    return entries;
  }

  /** True when some array block holds non-blank, non-comment content. */
  private static boolean arrayHasContent(String script) {
    List<String> nonBlank = new ArrayList<>();
    forEachArrayBodyLine(
        script,
        line -> {
          if (!line.trim().isEmpty()) {
            nonBlank.add(line);
          }
        });
    return !nonBlank.isEmpty();
  }

  /** Feeds every comment-stripped line inside every {@code MIGRATIONS=(...)} block to the sink. */
  private static void forEachArrayBodyLine(
      String script, java.util.function.Consumer<String> sink) {
    boolean inArray = false;
    for (String rawLine : script.split("\n", -1)) {
      String line = stripHashComment(rawLine);
      if (!inArray) {
        if (!MIGRATIONS_ARRAY_OPEN.matcher(rawLine).find() || !line.contains("(")) {
          continue;
        }
        inArray = true;
        line = line.substring(line.indexOf('(') + 1);
      }
      int close = line.indexOf(')');
      if (close >= 0) {
        inArray = false;
        line = line.substring(0, close);
      }
      sink.accept(line);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Check 5 — every .sql here is classifiable, so nothing can hide between the patterns
  // ---------------------------------------------------------------------------------------------

  /**
   * A {@code .sql} matching neither {@code V…__} nor {@code R__} is read by none of the checks
   * above while Flyway may still act on it. {@code V35_seed.sql} (one underscore), {@code
   * v35__x.sql} (lower case) and {@code V35__x.SQL} all used to vanish silently.
   */
  @Test
  void everySqlFileIsEitherVersionedOrRepeatable() throws IOException {
    List<String> unclassified =
        migrationFiles().keySet().stream()
            .filter(
                relative -> {
                  String name = fileNameOf(relative);
                  return !VERSIONED.matcher(name).matches() && !REPEATABLE.matcher(name).matches();
                })
            .toList();

    assertTrue(
        unclassified.isEmpty(),
        () ->
            "SQL file here that is neither a versioned nor a repeatable Flyway migration:\n"
                + bullets(unclassified)
                + "\n\nEvery other check in this class keys off one of those two shapes, so a name "
                + "matching neither is invisible to all of them. Usual causes: one underscore "
                + "instead of two, a lower-case v or .SQL, or a stray scratch file.");
  }

  // ---------------------------------------------------------------------------------------------

  /**
   * Every {@code .sql} under the migration directory, keyed by path RELATIVE to it.
   *
   * <p>Recursive on purpose: Flyway scans a location recursively, so {@code db/sub/V96__seed.sql}
   * is applied at IT boot. {@code Files.list} saw one level, which made a subdirectory a silent
   * escape hatch from every check (found 2026-08-27).
   */
  private static Map<String, Path> migrationFiles() throws IOException {
    assertTrue(
        Files.isDirectory(MIGRATION_DIR),
        () -> "migration directory " + MIGRATION_DIR.toAbsolutePath() + " should exist");
    Map<String, Path> files = new TreeMap<>();
    try (Stream<Path> entries = Files.walk(MIGRATION_DIR)) {
      entries
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
          .forEach(path -> files.put(relativeName(path), path));
    }
    return files;
  }

  private static String relativeName(Path path) {
    return MIGRATION_DIR.relativize(path).toString().replace('\\', '/');
  }

  private static String fileNameOf(String relative) {
    int slash = relative.lastIndexOf('/');
    return slash < 0 ? relative : relative.substring(slash + 1);
  }

  /**
   * Reads the manifest, dropping blank lines, comments (leading or trailing) and a byte-order mark.
   *
   * <p>Fails on a duplicated entry: a repeated line is the likeliest way this list rots — an
   * append-ordered allowlist resolved badly in a merge — and a plain {@code Set} would swallow it.
   */
  private static Set<String> readManifest() throws IOException {
    assertTrue(
        Files.isRegularFile(MANIFEST),
        () ->
            "grandfathering manifest "
                + MANIFEST.toAbsolutePath()
                + " should exist — without it every baseline fixture reads as a violation");
    Set<String> names = new LinkedHashSet<>();
    List<String> duplicates = new ArrayList<>();
    for (String line : Files.readAllLines(MANIFEST, StandardCharsets.UTF_8)) {
      String entry = manifestEntry(line);
      if (!entry.isEmpty() && !names.add(entry)) {
        duplicates.add(entry);
      }
    }
    assertTrue(
        duplicates.isEmpty(),
        () ->
            "duplicate entries in "
                + MANIFEST
                + " — each file is grandfathered once, and a repeat is usually a bad merge:\n"
                + bullets(duplicates));
    return names;
  }

  /** One manifest line reduced to the filename it names, or empty. */
  private static String manifestEntry(String line) {
    return stripHashComment(line.replace(BOM, "")).trim();
  }

  /** Drops everything from the first hash onward. */
  private static String stripHashComment(String line) {
    int hash = line.indexOf('#');
    return hash < 0 ? line : line.substring(0, hash);
  }

  private static String unquote(String token) {
    if (token.length() >= 2
        && ((token.startsWith("\"") && token.endsWith("\""))
            || (token.startsWith("'") && token.endsWith("'")))) {
      return token.substring(1, token.length() - 1);
    }
    return token;
  }

  /**
   * True if the script contains seed DML outside a comment.
   *
   * <p>Comments are stripped first because these fixtures are heavily annotated — several open with
   * a page of prose — and a header saying {@code -- INSERT INTO … (moved to R__30_…)} is
   * compliance, not a violation. Failing a compliant file is the failure mode most likely to get
   * the whole check deleted by whoever hits it.
   *
   * <p>Known limit: an {@code INSERT INTO} inside a quoted string literal still matches. There are
   * none on the tree (checked 2026-08-27, including the four files that build DDL through {@code
   * EXECUTE IMMEDIATE}), and recognising them needs a real parser.
   */
  private static boolean containsSeedDml(String sql) {
    return SEED_DML.matcher(COMMENT.matcher(sql).replaceAll(" ")).find();
  }

  /**
   * Decodes leniently. A strict UTF-8 decode threw {@link java.nio.charset.MalformedInputException}
   * on any Latin-1 byte — one accented character in one fixture comment turned checks 1 and 3 into
   * an ERROR reporting nothing about the other files. Replacement characters can neither create nor
   * destroy an {@code INSERT} keyword.
   */
  private static String read(Path file) {
    try {
      CharsetDecoder decoder =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPLACE)
              .onUnmappableCharacter(CodingErrorAction.REPLACE);
      return decoder.decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
    } catch (CharacterCodingException e) {
      throw new IllegalStateException("a replacing decoder should not throw for " + file, e);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + file, e);
    }
  }

  private static String bullets(List<String> lines) {
    return lines.stream().map(line -> "  " + line).collect(joining("\n"));
  }

  // ===============================================================================================

  /**
   * Direct assertions on the detectors, with no filesystem involved.
   *
   * <p>The checks above cannot fail on a compliant tree, so they cannot tell you whether the
   * detectors still work. These can. Every case marked {@code (bypass)} was a real defect found by
   * the 2026-08-27 review, and each fails if its fix is reverted.
   */
  @Nested
  class DetectorContracts {

    @Test
    void seedDmlIsDetectedInAllItsForms() {
      assertTrue(containsSeedDml("INSERT INTO THE.MILL (MILL_ID) VALUES (1);"));
      assertTrue(containsSeedDml("insert into the.mill (mill_id) values (1);"));
      assertTrue(containsSeedDml("INSERT   INTO\n  THE.MILL (MILL_ID) VALUES (1);"));
      assertTrue(containsSeedDml("INSERT ALL INTO a VALUES (1) SELECT 1 FROM DUAL;"));
      assertTrue(
          containsSeedDml("INSERT FIRST WHEN 1=1 THEN INTO a VALUES (1) SELECT 1 FROM DUAL;"),
          "(bypass) Oracle conditional multi-table insert");
      assertTrue(containsSeedDml("MERGE INTO a d USING (SELECT 1 x FROM DUAL) s ON (1=1);"));
      assertTrue(
          containsSeedDml("INSERT /*+ APPEND */ INTO THE.MILL (MILL_ID) VALUES (1);"),
          "an optimizer hint must not hide the statement");
    }

    @Test
    void commentedOutSeedDmlIsNotDetected() {
      assertFalse(containsSeedDml("-- INSERT INTO THE.MILL (MILL_ID) VALUES (1); moved to R__30_"));
      assertFalse(containsSeedDml("/*\n INSERT INTO THE.MILL (MILL_ID) VALUES (1);\n*/"));
      assertFalse(containsSeedDml("CREATE TABLE THE.SCRATCH (ID NUMBER);"));
    }

    @Test
    void aBlockOpenerInsideALineCommentDoesNotSwallowRealSeedDml() {
      // (bypass) block-before-line stripping paired the opener on line 1 with the closer on line 3.
      assertTrue(
          containsSeedDml(
              """
              -- Ratio note: cost/*unit* is stored pre-split.
              INSERT INTO THE.MILL (MILL_ID) VALUES (1);
              -- End of header. Multiplier a*/b applies.
              """));
    }

    @Test
    void versionedPatternAcceptsEveryFlywayVersionSeparator() {
      assertTrue(VERSIONED.matcher("V1__x.sql").matches());
      assertTrue(VERSIONED.matcher("V20260825__x.sql").matches());
      assertTrue(VERSIONED.matcher("V1.1__x.sql").matches());
      assertTrue(
          VERSIONED.matcher("V35_1__x.sql").matches(),
          "(bypass) Flyway reads _ as a version separator, so V35_1__ is version 35.1");
      assertFalse(VERSIONED.matcher("R__90_x.sql").matches());
      assertFalse(VERSIONED.matcher("V35_x.sql").matches(), "one underscore is not a migration");
    }

    @Test
    void repeatablePrefixProblemNamesTheActualProblem() {
      assertNull(repeatablePrefixProblem("R__90_cost_detail_bridge_culvert_fks.sql"));
      assertNull(repeatablePrefixProblem("R__10_seed_x.sql"));
      assertTrue(repeatablePrefixProblem("R__seed_x.sql").contains("no R__<two digits>_ prefix"));
      assertTrue(repeatablePrefixProblem("R__5_seed_x.sql").contains("1 digit(s)"));
      assertTrue(repeatablePrefixProblem("R__100_seed_x.sql").contains("3 digit(s)"));
      assertTrue(repeatablePrefixProblem("R__00_seed_x.sql").contains("below the 10 floor"));
      assertTrue(repeatablePrefixProblem("R__05_seed_x.sql").contains("below the 10 floor"));
      assertTrue(repeatablePrefixProblem("R__90_CostDetail.sql").contains("lower_snake_case"));
    }

    @Test
    void oneDigitPrefixSortsAfterTheTwoDigitDataBands() {
      // The reason for the two-digit rule, asserted rather than restated in prose and inverted.
      assertTrue("5 seed".compareTo("10 seed") > 0, "R__5_ lands after R__10_ — the actual trap");
      assertTrue("5 seed".compareTo("49 seed") > 0);
      assertTrue(
          "5 seed".compareTo("90 fks") < 0, "and BEFORE R__90_, which the old comment denied");
      assertTrue("10 seed".compareTo("90 fks") < 0);
    }

    @Test
    void bashArrayEntriesSurviveTheWaysPeopleActuallyWriteThem() {
      assertTrue(parseMigrationsArray("MIGRATIONS=(\n  \"V1__a.sql\"\n)").contains("V1__a.sql"));
      assertTrue(
          parseMigrationsArray("MIGRATIONS=(\n  'V1__a.sql'\n)").contains("V1__a.sql"),
          "(bypass) single quotes");
      assertTrue(
          parseMigrationsArray("MIGRATIONS=(\n  V1__a.sql\n)").contains("V1__a.sql"),
          "(bypass) no quotes");
      assertTrue(
          parseMigrationsArray("MIGRATIONS=(\n  # local-only (see #356)\n  \"V1__a.sql\"\n)")
              .contains("V1__a.sql"),
          "(bypass) a closing paren inside a comment truncated the array to nothing");
      assertTrue(
          parseMigrationsArray(
                  "# Usage: MIGRATIONS=(\"V0__old.sql\")\nMIGRATIONS=(\n  \"V1__a.sql\"\n)")
              .contains("V1__a.sql"),
          "(bypass) a mention above the real array hid it");
      assertEquals(2, parseMigrationsArray("MIGRATIONS=(\"V1__a.sql\" \"V2__b.sql\")").size());
      assertTrue(parseMigrationsArray("MIGRATIONS=()").isEmpty());
      assertFalse(arrayHasContent("MIGRATIONS=()"), "an empty array is legal, not vacuous");
      assertTrue(parseMigrationsArray("NOTHING_HERE=(\"V1__a.sql\")").isEmpty());
    }

    @Test
    void localDdlEntriesMustBeBareMigrationFilenamesInTheMigrationDirectory() {
      // (bypass) existence alone passed a traversal path; the script cats each entry into sqlplus.
      assertNull(localDdlEntryProblem("V20260825__the_ilcr_user_and_mill_user_xref.sql"));
      assertTrue(
          localDdlEntryProblem("../../../../../scripts/apply-local-ddl.sh").contains("is a path"),
          "(bypass) a traversal path resolves to a real file and used to pass");
      assertTrue(localDdlEntryProblem("/etc/passwd").contains("is a path"));
      assertTrue(localDdlEntryProblem("sub/V1__x.sql").contains("is a path"));
      assertTrue(localDdlEntryProblem("..").contains("not a Flyway migration filename"));
      assertTrue(localDdlEntryProblem("README.md").contains("not a Flyway migration filename"));
      assertTrue(localDdlEntryProblem("V99__never_existed.sql").contains("does not exist"));
    }

    @Test
    void manifestLineCleaningHandlesCommentsAndBom() {
      assertEquals("V1__a.sql", manifestEntry("V1__a.sql  # keep, rewrite pending"));
      assertEquals("", manifestEntry("# whole line"));
      assertEquals("V1__a.sql", manifestEntry(BOM + "V1__a.sql"));
      assertEquals("V1__a.sql", manifestEntry("  V1__a.sql  "));
    }
  }
}
