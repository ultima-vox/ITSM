package ru.ultimavox.itsm.platform.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtConverter =
        new KeycloakJwtAuthenticationConverter();

    return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/actuator/health",
                "/actuator/health/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
            ).permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth -> oauth
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
        )
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
    config.setExposedHeaders(List.of("Location"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
