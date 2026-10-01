package ca.bc.gov.nrs.ilcr.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's {@code @Scheduled} support, which the application did not use before Story
 * 21.2's {@link ca.bc.gov.nrs.ilcr.reporting.SpoolReaper}.
 *
 * <p>A configuration class of its own rather than an annotation on the application class, so that a
 * slice test can pull scheduling in, or leave it out, by naming this one type.
 *
 * <p>The default scheduler is a SINGLE-threaded executor, which is the right size for what this
 * enables: one periodic directory sweep. A second scheduled job would queue behind it, so anything
 * added later must either be quick or bring its own executor — the alternative, a pool sized for
 * jobs that do not exist, is capacity held open for nothing.
 *
 * <p>Note that {@code @Scheduled} fires on EVERY replica. The sweep is safe under that: it deletes
 * by name and age with {@code deleteIfExists}, so two replicas racing on the same mounted volume
 * lose the race harmlessly rather than double-deleting or throwing.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {}
