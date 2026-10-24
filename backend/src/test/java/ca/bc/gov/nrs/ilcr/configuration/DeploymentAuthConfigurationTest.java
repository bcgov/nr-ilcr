package ca.bc.gov.nrs.ilcr.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Browser sign-in is CSS, so no deployment file may still carry a Cognito setting. A Cognito
 * parameter kept while resolving a merge or rebase conflict in a workflow or template would either
 * fail a deployment or configure an identity provider ILCR no longer reads.
 *
 * <p>Skips rather than fails when the repository root is absent: the backend Docker image build has
 * a {@code backend/} context and runs the unit tests, so {@code ../.github} cannot exist there (the
 * same trade as {@code FlywayMigrationConventionTest}). It runs for every full checkout.
 */
class DeploymentAuthConfigurationTest {

  private static final Path REPOSITORY = Paths.get("..");
  private static final Path WORKFLOWS = REPOSITORY.resolve(Paths.get(".github", "workflows"));

  @Test
  @DisplayName("workflows and OpenShift templates carry no Cognito settings")
  void deploymentFilesCarryNoCognitoSettings() throws IOException {
    assumeTrue(
        Files.isDirectory(WORKFLOWS),
        "no ../.github/workflows here — module-only build context, check skipped");

    List<Path> files = new ArrayList<>();
    try (Stream<Path> workflows = Files.list(WORKFLOWS)) {
      workflows.filter(file -> file.getFileName().toString().endsWith(".yml")).forEach(files::add);
    }
    try (Stream<Path> modules = Files.list(REPOSITORY)) {
      for (Path module : modules.filter(Files::isDirectory).toList()) {
        try (Stream<Path> templates = Files.list(module)) {
          templates
              .filter(file -> file.getFileName().toString().matches("openshift.*\\.yml"))
              .forEach(files::add);
        }
      }
    }

    assertThat(files)
        .contains(
            WORKFLOWS.resolve("reusable-deploy.yml"),
            REPOSITORY.resolve(Paths.get("common", "openshift.init.yml")),
            REPOSITORY.resolve(Paths.get("backend", "openshift.deploy.yml")),
            REPOSITORY.resolve(Paths.get("frontend", "openshift.deploy.yml")));
    for (Path file : files) {
      assertThat(Files.readString(file)).as(file.toString()).doesNotContainIgnoringCase("cognito");
    }
  }
}
