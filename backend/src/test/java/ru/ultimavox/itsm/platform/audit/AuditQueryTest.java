package ru.ultimavox.itsm.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AuditQueryTest {

  private JdbcTemplate jdbc;
  private AuditQuery query;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    query = new AuditQuery(jdbc, new ObjectMapper());
  }

  @Test
  @SuppressWarnings("unchecked")
  void list_maps_row() throws Exception {
    UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    Instant at = Instant.parse("2026-07-01T12:00:00Z");

    when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<AuditEventRecord>>any(), eq(50), eq(0)))
        .thenAnswer(inv -> {
          RowMapper<AuditEventRecord> mapper = inv.getArgument(1);
          ResultSet rs = mock(ResultSet.class);
          when(rs.getObject("id", UUID.class)).thenReturn(id);
          when(rs.getTimestamp("occurred_at")).thenReturn(Timestamp.from(at));
          when(rs.getString("actor_id")).thenReturn("agent-1");
          when(rs.getString("action")).thenReturn("assign");
          when(rs.getString("object_type")).thenReturn("work_item");
          when(rs.getString("object_id")).thenReturn("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
          when(rs.getString("before_state")).thenReturn("{}");
          when(rs.getString("after_state")).thenReturn("{\"number\":\"INC-1\",\"title\":\"VPN\"}");
          when(rs.getObject("correlation_id", UUID.class)).thenReturn(UUID.randomUUID());
          when(rs.getString("metadata")).thenReturn("{}");
          return List.of(mapper.mapRow(rs, 0));
        });

    List<AuditEventRecord> rows = query.list(null, 50, 0);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).action()).isEqualTo("assign");
    assertThat(rows.get(0).afterState()).containsEntry("number", "INC-1");
  }

  @Test
  void distinct_actions_delegates() {
    when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<String>>any(), eq(100)))
        .thenReturn(List.of("assign", "create"));
    assertThat(query.distinctActions(100)).containsExactly("assign", "create");
  }
}
