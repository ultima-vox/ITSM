package ru.ultimavox.itsm.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import ru.ultimavox.itsm.platform.authorization.OrganizationContext;

class JdbcServiceDeskReportQueryTest {

  @Test
  void everyAggregateQueryIsScopedToTheCurrentOrganization() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);
    when(jdbc.query(anyString(), any(ResultSetExtractor.class), any())).thenReturn(null);
    when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(List.of());

    OrganizationContext.runAs("tenant-east", () -> {
      new JdbcServiceDeskReportQuery(jdbc).snapshot();
      return null;
    });

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.atLeastOnce())
        .queryForObject(sql.capture(), eq(Long.class), eq("tenant-east"));
    assertThat(sql.getAllValues()).isNotEmpty();
    assertThat(sql.getAllValues()).allMatch(statement -> statement.contains("org_id = ?"));
  }
}
