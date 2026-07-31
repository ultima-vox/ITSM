package ru.ultimavox.itsm.platform.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document and module-oriented swagger groups for the modular monolith.
 */
@Configuration
class OpenApiConfiguration {

  private static final String BEARER_JWT = "bearer-jwt";

  @Bean
  OpenAPI voxItsmOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Vox ITSM Platform API")
            .version("0.1.0")
            .description(
                "Modular monolith REST API. Default security: OIDC JWT (Keycloak). "
                    + "Profile `dev` disables JWT for local demos only."
            ))
        .components(new Components().addSecuritySchemes(BEARER_JWT, new SecurityScheme()
            .name(BEARER_JWT)
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Keycloak / OIDC access token")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
  }

  @Bean
  GroupedOpenApi serviceDeskApi() {
    return GroupedOpenApi.builder()
        .group("service-desk")
        .displayName("Service Desk")
        .pathsToMatch("/api/v1/work-items/**")
        .build();
  }

  @Bean
  GroupedOpenApi changeProblemApi() {
    return GroupedOpenApi.builder()
        .group("change-problem")
        .displayName("Change & Problem")
        .pathsToMatch("/api/v1/changes/**", "/api/v1/problems/**")
        .build();
  }

  @Bean
  GroupedOpenApi cmdbAssetApi() {
    return GroupedOpenApi.builder()
        .group("cmdb-asset")
        .displayName("CMDB & Assets")
        .pathsToMatch("/api/v1/cmdb/**", "/api/v1/assets/**")
        .build();
  }

  @Bean
  GroupedOpenApi knowledgeCatalogApi() {
    return GroupedOpenApi.builder()
        .group("knowledge-catalog")
        .displayName("Knowledge & Catalog")
        .pathsToMatch("/api/v1/knowledge/**", "/api/v1/catalog/**")
        .build();
  }

  @Bean
  GroupedOpenApi platformApi() {
    return GroupedOpenApi.builder()
        .group("platform")
        .displayName("Platform (metadata, locale, AI, search)")
        .pathsToMatch(
            "/api/v1/metadata/**",
            "/api/v1/me/**",
            "/api/v1/ai/**",
            "/api/v1/search/**"
        )
        .build();
  }
}
