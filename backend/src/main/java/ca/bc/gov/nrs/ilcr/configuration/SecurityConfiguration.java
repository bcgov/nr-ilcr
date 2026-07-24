package ca.bc.gov.nrs.ilcr.configuration;

import ca.bc.gov.nrs.ilcr.BackendConstants;
import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.security.MockPrincipalFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String PATH_HEALTH = "/api/health";
    private static final String PATH_INFO = "/api/info";

    @Bean
    // java:S4502 — Disabling CSRF is safe here: this is a stateless REST API (SessionCreationPolicy
    // .STATELESS below) authenticated by bearer JWTs in the Authorization header, with no session
    // cookie. CSRF requires an ambient credential the browser attaches automatically to a forged
    // cross-site request; a bearer token is not sent automatically, so there is no CSRF surface to
    // protect. Revisit this suppression if cookie- or session-based auth is ever introduced.
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${ilcr.security.enabled:false}") boolean securityEnabled,
            @Value("${ilcr.security.mock-role:ILCR_SUBMITTER}") String mockRoleName,
            CognitoGroupsJwtAuthenticationConverter cognitoGroupsConverter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "object-src 'none'; "
                                        + "base-uri 'self'; "
                                        + "frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000)));

        if (securityEnabled) {
            http
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                            jwt.jwtAuthenticationConverter(cognitoGroupsConverter)))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(PATH_HEALTH, PATH_INFO).permitAll()
                            .requestMatchers(PATH_HEALTH, PATH_HEALTH + "/**", PATH_INFO).permitAll()
                            .requestMatchers(HttpMethod.GET, BackendConstants.HOME_PUBLIC_PATHS).permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().authenticated());
        } else {
            // Dev/UAT: seed a mock principal so @PreAuthorize action checks run identically with
            // security off (AD-7). Requests are permitted at the request level; method security
            // still evaluates against the mock authority.
            Role mockRole = Role.fromValue(mockRoleName);
            http.addFilterBefore(
                    new MockPrincipalFilter(mockRole != null ? mockRole : Role.SUBMITTER),
                    UsernamePasswordAuthenticationFilter.class);
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(BackendConstants.PUBLIC_PATHS).permitAll()
                    .anyRequest().permitAll());
        }

        return http.build();
    }
}
