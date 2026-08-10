package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;

class WorkItemStoreTest {
  @Test
  void staleVersionFailsInsteadOfOverwritingConcurrentChange() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    WorkItemStore store = new WorkItemStore(jdbc);
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    WorkItem item = new WorkItem(
        UUID.randomUUID(), "INC-1", WorkItem.Type.INCIDENT, "title", "description", "service",
        WorkItem.State.NEW, WorkItem.Priority.MEDIUM, WorkItem.Impact.MEDIUM,
        WorkItem.Urgency.MEDIUM, null, "requester", null, null, null, false,
        now, now, null, 7L);

    assertThatThrownBy(() -> store.update(item))
        .isInstanceOf(WorkItemConcurrencyException.class)
        .hasMessageContaining("version 7");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).update(sql.capture(), any(Object[].class));
    org.assertj.core.api.Assertions.assertThat(sql.getValue())
        .contains("version = version + 1")
        .contains("WHERE id = ? AND version = ?");
  }
}
