package ru.ultimavox.itsm.knowledgebase.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeQueryTest {
  @Mock JdbcTemplate jdbc;
  @Mock ResultSet resultSet;

  @Test
  void search_published_passes_title_query_and_maps_rows() throws Exception {
    UUID id = UUID.fromString("e1000000-0000-4000-8000-000000000001");
    when(resultSet.getObject("id")).thenReturn(id);
    when(resultSet.getString("number")).thenReturn("KB-1001");
    when(resultSet.getString("slug")).thenReturn("vpn-connection-guide");
    when(resultSet.getString("status")).thenReturn("PUBLISHED");
    when(resultSet.getInt("version")).thenReturn(1);
    when(resultSet.getString("owner_subject")).thenReturn("kb-owner");
    when(resultSet.getTimestamp("next_review_at")).thenReturn(Timestamp.from(Instant.parse("2026-12-01T00:00:00Z")));
    when(resultSet.getString("title")).thenReturn("Подключение к VPN");
    when(resultSet.getString("summary")).thenReturn("Инструкция");
    when(resultSet.getString("locale")).thenReturn("ru");

    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
        .thenAnswer(invocation -> {
          RowMapper<?> mapper = invocation.getArgument(1);
          return List.of(mapper.mapRow(resultSet, 0));
        });

    var results = new KnowledgeQuery(jdbc).searchPublished("VPN", "ru");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().title()).contains("VPN");
    assertThat(results.getFirst().slug()).isEqualTo("vpn-connection-guide");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("ru"), eq("default"), eq("VPN"), eq("VPN"));
    assertThat(sql.getValue()).contains("PUBLISHED");
    assertThat(sql.getValue()).contains("ILIKE");
  }

  @Test
  void search_without_query_still_filters_published_only() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any())).thenReturn(List.of());

    var results = new KnowledgeQuery(jdbc).searchPublished(null, "ru");

    assertThat(results).isEmpty();
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("ru"), eq("default"), eq(null), eq(null));
    assertThat(sql.getValue()).contains("status = 'PUBLISHED'");
  }
}
