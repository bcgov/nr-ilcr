package ca.bc.gov.nrs.ilcr.configuration;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.security.LocalDevPrincipalFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String[] PUBLIC_PATHS = {
        "/api",
        "/api/health",
        "/api/health/**",
        "/api/info",
        "/api/prometheus"
    };

    // Home-page option-list endpoints (Story 1.1). Pre-selection reads with no action gate and no
    // @PreAuthorize; permitted even when security is enabled. The per-user mill-association filter
    // arrives with the FAM auth story (AR4); until then these are open like the other pre-auth reads.
    private static final String[] HOME_PUBLIC_PATHS = {
        "/api/v1/mills",
        "/api/v1/reporting-years"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${ilcr.security.enabled:false}") boolean securityEnabled,
            @Value("${ilcr.security.local-dev-role:ILCR_SUBMITTER}") String localDevRoleName,
            CognitoGroupsJwtAuthenticationConverter cognitoGroupsConverter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.spa())
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
                            .requestMatchers("/api/health", "/api/health/**", "/api/info").permitAll()
                            .requestMatchers(HOME_PUBLIC_PATHS).permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().authenticated());
        } else {
            // Local dev: seed a placeholder principal so @PreAuthorize action checks run while
            // FAM/JWT integration is disabled. Requests are permitted at the request level; method
            // security still evaluates against the configured local-dev authority.
            Role localDevRole = Role.fromValue(localDevRoleName);
            http.addFilterBefore(
                    new LocalDevPrincipalFilter(localDevRole != null ? localDevRole : Role.SUBMITTER),
                    UsernamePasswordAuthenticationFilter.class);
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(PUBLIC_PATHS).permitAll()
                    .anyRequest().permitAll());
        }

        return http.build();
    }
}
