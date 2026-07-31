package ru.ultimavox.itsm.platform.security;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * WARNING: DEVELOPMENT ONLY.
 *
 * <p>Activated with Spring profile {@code dev}. Disables OIDC JWT enforcement so local demos
 * work when Keycloak is down. <b>Never enable profile {@code dev} in production or shared
 * staging environments that process real data.</b>
 *
 * <p>Anonymous requests are authenticated as subject {@code dev-local} via
 * {@link DevAuthenticationFilter} so {@code Authentication.getName()} and AccessControl
 * still have a principal for permission checks against seeded RBAC.
 */
@Configuration
@EnableWebSecurity
@Profile("dev")
class DevSecurityConfiguration {

  private static final Logger log = LoggerFactory.getLogger(DevSecurityConfiguration.class);

  @Value("${itsm.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
  private String allowedOrigins;

  @Bean
  SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
    log.warn("""
        ================================================================================
        SECURITY WARNING: profile 'dev' is active — JWT authentication is DISABLED.
        All /api/** endpoints are open with synthetic principal 'dev-local'.
        Do NOT use this profile in production.
        ================================================================================
        """);

    return http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(devCorsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/actuator/health",
                "/actuator/health/**",
                "/actuator/info",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
            ).permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            // Intentional open API under dev only — see class Javadoc warning.
            .anyRequest().permitAll()
        )
        .addFilterBefore(new DevAuthenticationFilter(),
            org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
        .build();
  }

  @Bean
  @ConditionalOnMissingBean(CorsConfigurationSource.class)
  CorsConfigurationSource devCorsConfigurationSource() {
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
