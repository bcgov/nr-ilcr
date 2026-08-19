package ca.bc.gov.nrs.ilcr.configuration;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.context.annotation.Configuration;

/**
 * Anchors the application's default time zone to Pacific ({@code America/Vancouver}). The app serves
 * the BC Ministry of Forests, but its OpenShift containers default to UTC — which shifts date
 * boundaries (reporting-year rollover, {@code SYSDATE}/audit timestamps read as {@code java.util.Date},
 * and Jackson date serialization). Setting the JVM default once at startup makes the whole app Pacific
 * regardless of the container's TZ; {@code spring.jackson.time-zone} pins the same zone for JSON.
 */
@Configuration
public class TimeZoneConfiguration {

  @PostConstruct
  public void setDefaultTimeZone() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));
  }
}
