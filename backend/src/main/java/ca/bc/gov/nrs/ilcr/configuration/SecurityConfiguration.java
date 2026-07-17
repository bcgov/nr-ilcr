package ca.bc.gov.nrs.ilcr.configuration;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final String[] PUBLIC_PATHS = {
        "/api",
        "/api/health",
        "/api/info",
        "/api/prometheus",
        "/api/v1/users/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${ilcr.security.enabled:false}") boolean securityEnabled
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
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/health", "/api/info").permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().authenticated());
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(PUBLIC_PATHS).permitAll()
                    .anyRequest().permitAll());
        }

        return http.build();
    }
}
