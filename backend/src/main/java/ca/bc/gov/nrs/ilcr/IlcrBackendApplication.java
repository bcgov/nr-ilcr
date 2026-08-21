package ca.bc.gov.nrs.ilcr;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The main entry point for the ILCR Backend Application. */
@SpringBootApplication
public class IlcrBackendApplication {

  /**
   * The main method to start the Spring Boot application.
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // Anchor the JVM to Pacific BEFORE the context refreshes. The app serves the BC Ministry of
    // Forests, but its OpenShift containers default to UTC, which shifts date boundaries
    // (reporting-year rollover, SYSDATE/audit timestamps read as java.util.Date). Setting it here
    // — ahead of SpringApplication.run — is deterministic: logging init, the Hikari pool, and every
    // bean's @PostConstruct all observe Pacific, with no startup window left in UTC. Deploy may
    // also set TZ=America/Vancouver on the container; this guarantees it even when it doesn't.
    TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));
    SpringApplication.run(IlcrBackendApplication.class, args);
  }
}
