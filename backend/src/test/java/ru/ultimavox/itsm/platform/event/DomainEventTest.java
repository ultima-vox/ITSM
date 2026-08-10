package ru.ultimavox.itsm.platform.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class DomainEventTest {
  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void capturesAuthenticatedActorAndOrganization() {
    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("operator-7")
        .claim("organization_id", "org-42")
        .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        jwt, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")), "operator-7"));
    UUID correlation = UUID.randomUUID();

    DomainEvent event = new DomainEvent(
        UUID.randomUUID(), "work-item.created", 1, Instant.now(), correlation,
        "work-item", UUID.randomUUID().toString(), Map.of("title", "test"));

    assertThat(event.actorId()).isEqualTo("operator-7");
    assertThat(event.organizationId()).isEqualTo("org-42");
    assertThat(event.correlationId()).isEqualTo(correlation);
    assertThat(event.causationId()).isNull();
  }

  @Test
  void marksBackgroundEventsExplicitly() {
    DomainEvent event = new DomainEvent(
        UUID.randomUUID(), "sla.breached", 1, Instant.now(), UUID.randomUUID(),
        "work-item", UUID.randomUUID().toString(), Map.of());

    assertThat(event.actorId()).isEqualTo("system");
    assertThat(event.organizationId()).isEqualTo("default");
  }
}
