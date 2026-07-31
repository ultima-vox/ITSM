package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ultimavox.itsm.platform.search.SearchDocument;
import ru.ultimavox.itsm.platform.search.SearchIndexService;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Impact;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Priority;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.State;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Type;
import ru.ultimavox.itsm.servicedesk.domain.WorkItem.Urgency;

@ExtendWith(MockitoExtension.class)
class WorkItemSearchIndexerTest {

  @Mock SearchIndexService searchIndex;

  @Test
  void projects_work_item_fields_into_search_document() {
    WorkItemSearchIndexer indexer = new WorkItemSearchIndexer(searchIndex);
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-07-31T10:00:00Z");
    WorkItem item = new WorkItem(
        id, "INC-001000", Type.INCIDENT, "VPN down", "Cannot connect",
        "Workplace", State.NEW, Priority.HIGH, Impact.HIGH, Urgency.HIGH,
        null, "actor", null, null, null, now, now, null
    );

    indexer.index(item);

    ArgumentCaptor<SearchDocument> cap = ArgumentCaptor.forClass(SearchDocument.class);
    verify(searchIndex).index(cap.capture());
    SearchDocument doc = cap.getValue();
    assertThat(doc.id()).isEqualTo(id.toString());
    assertThat(doc.objectType()).isEqualTo("work-item");
    assertThat(doc.title()).contains("INC-001000").contains("VPN down");
    assertThat(doc.scopes()).contains("work-item", "incident");
    assertThat(doc.facets()).containsEntry("priority", "HIGH");
  }

  @Test
  void index_failure_does_not_throw() {
    doThrow(new IllegalStateException("down")).when(searchIndex).index(any());
    WorkItemSearchIndexer indexer = new WorkItemSearchIndexer(searchIndex);
    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    WorkItem item = new WorkItem(
        id, "INC-1", Type.INCIDENT, "t", "d", "s", State.NEW, Priority.LOW,
        Impact.LOW, Urgency.LOW, null, "a", null, null, null, now, now, null
    );
    indexer.index(item); // must not throw
  }
}
