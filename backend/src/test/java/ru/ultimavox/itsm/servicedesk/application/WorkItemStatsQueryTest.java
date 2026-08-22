package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkItemStatsQueryTest {

  @Mock WorkItemStore store;

  private WorkItemStatsQuery query;

  @BeforeEach
  void setUp() {
    query = new WorkItemStatsQuery(store);
  }

  @Test
  void stats_returns_open_mine_unassigned_and_breached_counts() {
    when(store.countOpen(null)).thenReturn(11L);
    when(store.countMine("agent-9", null)).thenReturn(3L);
    when(store.countUnassigned(null)).thenReturn(5L);
    when(store.countSlaDueToday()).thenReturn(2L);
    when(store.countSlaBreached(null)).thenReturn(1L);
    when(store.averageCsatSince(org.mockito.ArgumentMatchers.any())).thenReturn(80.0);

    WorkItemStatsQuery.Stats stats = query.stats("agent-9", null);

    assertThat(stats.open()).isEqualTo(11L);
    assertThat(stats.mine()).isEqualTo(3L);
    assertThat(stats.unassigned()).isEqualTo(5L);
    assertThat(stats.breached()).isEqualTo(1L);
    assertThat(stats.dueToday()).isEqualTo(2L);
    verify(store).countOpen(null);
    verify(store).countMine("agent-9", null);
    verify(store).countUnassigned(null);
    verify(store).countSlaBreached(null);
  }

  @Test
  void stats_scopes_counts_to_requester_when_not_unrestricted() {
    when(store.countOpen("user-42")).thenReturn(2L);
    when(store.countMine("user-42", "user-42")).thenReturn(0L);
    when(store.countUnassigned("user-42")).thenReturn(1L);
    when(store.countSlaDueToday()).thenReturn(0L);
    when(store.countSlaBreached("user-42")).thenReturn(0L);
    when(store.averageCsatSince(org.mockito.ArgumentMatchers.any())).thenReturn(null);

    WorkItemStatsQuery.Stats stats = query.stats("user-42", "user-42");

    assertThat(stats.open()).isEqualTo(2L);
    assertThat(stats.mine()).isZero();
    assertThat(stats.unassigned()).isEqualTo(1L);
    assertThat(stats.breached()).isZero();
    verify(store).countOpen("user-42");
    verify(store).countMine("user-42", "user-42");
    verify(store).countUnassigned("user-42");
    verify(store).countSlaBreached("user-42");
  }
}
