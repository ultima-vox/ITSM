package ru.ultimavox.itsm.platform.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.ultimavox.itsm.platform.identity.IdentitySyncFilter;
import ru.ultimavox.itsm.platform.identity.IdentitySyncService;

/**
 * Production-oriented security (active when profile is not {@code dev}).
 *
 * <p><b>Authentication:</b> OIDC JWT resource server (Keycloak by default).
 * Realm roles are mapped via {@link KeycloakJwtAuthenticationConverter}.
 *
 * <p><b>Endpoints that need auth (any non-public path):</b>
 * <ul>
 *   <li>{@code /api/v1/work-items/**} — Service Desk</li>
 *   <li>{@code /api/v1/changes/**}, {@code /api/v1/problems/**}</li>
 *   <li>{@code /api/v1/cmdb/**}, {@code /api/v1/assets/**}</li>
 *   <li>{@code /api/v1/knowledge/**}, {@code /api/v1/catalog/**}</li>
 *   <li>{@code /api/v1/metadata/**}, {@code /api/v1/ai/**}, {@code /api/v1/search/**}</li>
 *   <li>{@code /api/v1/me/**} — locale preference</li>
 *   <li>Actuator beyond {@code /actuator/health} (info/metrics if exposed)</li>
 * </ul>
 *
 * <p><b>Public (no JWT):</b> {@code /actuator/health}, OpenAPI/Swagger UI.
 */
@Configuration
@EnableWebSecurity
@Profile("!dev")
class SecurityConfiguration {

  @Value("${itsm.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
  private String allowedOrigins;

  @Value("${spring.security.oauth2.resourceserver.jwt.audiences:itsm-backend}")
  private String[] jwtAudiences;

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ObjectProvider<IdentitySyncService> identitySync) throws Exception {
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtConverter =
        new KeycloakJwtAuthenticationConverter(expectedAudiences());

    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
            .referrerPolicy(referrer -> referrer.policy(
                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            .frameOptions(frame -> frame.deny())
            .addHeaderWriter(new StaticHeadersWriter(
                "Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/actuator/health",
                "/actuator/health/**"
            ).permitAll()
            .requestMatchers(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
            ).hasRole("ADMIN")
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth -> oauth
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
        );

    IdentitySyncService sync = identitySync.getIfAvailable();
    if (sync != null) {
      http.addFilterBefore(new IdentitySyncFilter(sync), AuthorizationFilter.class);
    }
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(ApiCors.allowedHeaders());
    config.setExposedHeaders(ApiCors.exposedHeaders());
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  private List<String> expectedAudiences() {
    if (jwtAudiences == null || jwtAudiences.length == 0) {
      return List.of(KeycloakJwtAuthenticationConverter.DEFAULT_AUDIENCE);
    }
    List<String> values = Arrays.stream(jwtAudiences)
        .flatMap(value -> Arrays.stream(value.split("[,\\s]+")))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
    return values.isEmpty()
        ? List.of(KeycloakJwtAuthenticationConverter.DEFAULT_AUDIENCE)
        : values;
  }
}
