package ru.ultimavox.itsm.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ultimavox.itsm.platform.event.DomainEvent;

class JdbcIntegrationEventOutboxTest {
  @Test
  void storesQueryableEnvelopeContextAlongsidePayload() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ObjectMapper json = mock(ObjectMapper.class);
    when(json.writeValueAsString(any())).thenReturn("{\"type\":\"asset.created\"}");
    UUID correlation = UUID.randomUUID();
    UUID causation = UUID.randomUUID();
    DomainEvent event = new DomainEvent(
        UUID.randomUUID(), "asset.created", 2, Instant.now(), correlation, causation,
        "org-9", "operator-3", "asset", UUID.randomUUID().toString(), Map.of());

    new JdbcIntegrationEventOutbox(jdbc, json).record(event);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).update(sql.capture(), args.capture());
    assertThat(sql.getValue()).contains("schema_version", "correlation_id", "organization_id", "actor_id");
    assertThat(args.getValue()).contains(correlation, causation, "org-9", "operator-3");
  }
}
