package ru.ultimavox.itsm.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigurationTest {

    @Test
    void corsUsesExactOriginsWithoutCredentials() {
        SecurityConfiguration configuration = new SecurityConfiguration();
        ReflectionTestUtils.setField(
                configuration,
                "allowedOrigins",
                "https://portal.example.test,https://desk.example.test");

        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) configuration.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfigurations().get("/**");

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly(
                "https://portal.example.test", "https://desk.example.test");
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThat(cors.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(cors.getAllowedHeaders()).contains("X-Correlation-ID");
        assertThat(cors.getExposedHeaders()).contains("X-Correlation-ID");
    }
}
