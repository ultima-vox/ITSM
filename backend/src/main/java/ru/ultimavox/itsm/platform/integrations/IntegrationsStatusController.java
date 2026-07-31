package ru.ultimavox.itsm.platform.integrations;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.platform.cache.ItsmRedisProperties;
import ru.ultimavox.itsm.platform.search.ItsmOpenSearchProperties;
import ru.ultimavox.itsm.platform.storage.ItsmStorageProperties;

/**
 * Operator-facing snapshot of optional infrastructure wiring (Redis, OpenSearch, storage).
 * Prefer Actuator for k8s probes; this endpoint is for admin UI / smoke scripts.
 */
@RestController
@RequestMapping("/api/v1/platform/integrations")
@Tag(name = "Platform — Integrations")
class IntegrationsStatusController {

  private final AccessControl access;
  private final ItsmRedisProperties redisProps;
  private final ItsmOpenSearchProperties openSearchProps;
  private final ItsmStorageProperties storageProps;
  private final ObjectProvider<HealthIndicator> healthIndicators;

  IntegrationsStatusController(
      AccessControl access,
      ItsmRedisProperties redisProps,
      ItsmOpenSearchProperties openSearchProps,
      ItsmStorageProperties storageProps,
      ObjectProvider<HealthIndicator> healthIndicators
  ) {
    this.access = access;
    this.redisProps = redisProps;
    this.openSearchProps = openSearchProps;
    this.storageProps = storageProps;
    this.healthIndicators = healthIndicators;
  }

  @GetMapping
  @Operation(summary = "Configured optional integrations and live health where available")
  Map<String, Object> status(Authentication authentication) {
    access.require(authentication.getName(), "metadata.read", "platform", null);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("redis", redisBlock());
    body.put("opensearch", openSearchBlock());
    body.put("storage", storageBlock());
    return body;
  }

  private Map<String, Object> redisBlock() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("enabled", redisProps.isEnabled());
    m.put("host", redisProps.getHost());
    m.put("port", redisProps.getPort());
    m.put("health", probeByNameFragment("redis"));
    return m;
  }

  private Map<String, Object> openSearchBlock() {
    Map<String, Object> m = new LinkedHashMap<>();
    boolean configured = openSearchProps.isConfigured();
    m.put("enabled", configured);
    m.put("url", openSearchProps.getUrl());
    m.put("index", openSearchProps.getIndex());
    m.put("health", configured ? probeByNameFragment("opensearch") : Map.of("status", "DISABLED"));
    return m;
  }

  private Map<String, Object> storageBlock() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", storageProps.getType());
    m.put("endpoint", storageProps.getS3().getEndpoint());
    m.put("bucket", storageProps.getS3().getBucket());
    return m;
  }

  private Map<String, Object> probeByNameFragment(String fragment) {
    for (HealthIndicator indicator : healthIndicators) {
      if (indicator.getClass().getSimpleName().toLowerCase().contains(fragment.toLowerCase())) {
        return healthMap(indicator.health());
      }
    }
    Map<String, Object> unknown = new LinkedHashMap<>();
    unknown.put("status", "UNKNOWN");
    return unknown;
  }

  private static Map<String, Object> healthMap(Health health) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("status", health.getStatus().getCode());
    m.putAll(health.getDetails());
    return m;
  }
}
