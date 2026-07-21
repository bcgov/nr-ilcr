package ca.bc.gov.nrs.ilcr.configuration;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Wires the application {@code MessageSource} (the {@code messages.properties} bundle, AD-8) into the
 * Bean Validation message interpolator so constraint messages written as legacy keys — e.g.
 * {@code @Max(message = "{costValidatorErrorMsg}")} — resolve to the verbatim legacy text instead of
 * the default {@code ValidationMessages.properties}. Without this, {@code @Valid} range violations on
 * {@code Schedule1Request} would not carry the legacy FLD-001/002/003 wording (AD-6/AD-8).
 */
@Configuration
public class ValidationConfiguration {

  /**
   * The primary validator, interpolating constraint messages against the application bundle.
   *
   * @param messageSource the application message source (legacy keys, verbatim text)
   * @return a validator that resolves {@code {key}} constraint messages from {@code messages.properties}
   */
  @Bean
  public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
    bean.setValidationMessageSource(messageSource);
    return bean;
  }
}
