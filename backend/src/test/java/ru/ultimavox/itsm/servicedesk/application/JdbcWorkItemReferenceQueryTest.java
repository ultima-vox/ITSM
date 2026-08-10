package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class JdbcWorkItemReferenceQueryTest {
  @Test
  void delegatesToOrganizationScopedServiceDeskStore() {
    WorkItemStore store = Mockito.mock(WorkItemStore.class);
    UUID id = UUID.randomUUID();
    when(store.findById(id)).thenReturn(Optional.empty());

    assertThat(new JdbcWorkItemReferenceQuery(store).exists(id)).isFalse();

    verify(store).findById(id);
  }
}
