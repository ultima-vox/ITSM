package ru.ultimavox.itsm.platform.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

class SearchReindexSchedulerTest {

  private final SearchIndexService index = mock(SearchIndexService.class);

  private final SearchDocument docA = doc("a");
  private final SearchDocument docB = doc("b");

  private SearchReindexScheduler scheduler(SearchReindexSource source) {
    return new SearchReindexScheduler(List.of(source), index);
  }

  @Test
  void indexesEveryPageUntilShortPageIsReturned() {
    SearchReindexSource source = mock(SearchReindexSource.class);
    when(source.sourceName()).thenReturn("work-item");
    when(source.organizationIds()).thenReturn(List.of("org-1"));
    when(source.snapshotPage("org-1", 0, SearchReindexScheduler.PAGE_SIZE))
        .thenReturn(List.of(docA, docB));
    when(source.snapshotPage("org-1", 2, SearchReindexScheduler.PAGE_SIZE))
        .thenReturn(List.of());

    scheduler(source).reconcile();

    verify(index).index(docA);
    verify(index).index(docB);
  }

  @Test
  void reconcilesEveryOrganizationScope() {
    SearchReindexSource source = mock(SearchReindexSource.class);
    when(source.sourceName()).thenReturn("knowledge-article");
    when(source.organizationIds()).thenReturn(List.of("org-1", "org-2"));
    when(source.snapshotPage(anyString(), anyInt(), anyInt())).thenReturn(List.of(docA));

    scheduler(source).reconcile();

    verify(index, times(2)).index(docA);
    verify(source).snapshotPage("org-1", 0, SearchReindexScheduler.PAGE_SIZE);
    verify(source).snapshotPage("org-2", 0, SearchReindexScheduler.PAGE_SIZE);
  }

  @Test
  void runsEachOrganizationScopeUnderItsOwnContext() {
    SearchReindexSource source = mock(SearchReindexSource.class);
    when(source.sourceName()).thenReturn("work-item");
    when(source.organizationIds()).thenReturn(List.of("org-7"));
    when(source.snapshotPage(eq("org-7"), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          org.assertj.core.api.Assertions.assertThat(OrganizationContext.current())
              .isEqualTo("org-7");
          return List.of();
        });

    scheduler(source).reconcile();
  }

  @Test
  void aFailingSourceDoesNotBlockOthers() {
    SearchReindexSource failing = mock(SearchReindexSource.class);
    when(failing.sourceName()).thenReturn("failing");
    when(failing.organizationIds()).thenThrow(new IllegalStateException("db down"));

    SearchReindexSource fine = mock(SearchReindexSource.class);
    when(fine.sourceName()).thenReturn("fine");
    when(fine.organizationIds()).thenReturn(List.of("org-1"));
    when(fine.snapshotPage(eq("org-1"), anyInt(), anyInt())).thenReturn(List.of(docA));

    new SearchReindexScheduler(List.of(failing, fine), index).reconcile();

    verify(index).index(docA);
  }

  @Test
  void doesNothingWhenNoOrganizationsExist() {
    SearchReindexSource source = mock(SearchReindexSource.class);
    when(source.sourceName()).thenReturn("work-item");
    when(source.organizationIds()).thenReturn(List.of());

    scheduler(source).reconcile();

    verify(source, never()).snapshotPage(anyString(), anyInt(), anyInt());
    verify(index, never()).index(any());
  }

  private static SearchDocument doc(String id) {
    return new SearchDocument(
        id, "work-item", "title " + id, "body", Set.of("work-item"),
        Instant.parse("2026-01-01T00:00:00Z"), Map.of("number", id));
  }
}
