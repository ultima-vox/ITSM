package ru.ultimavox.itsm.servicecatalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CatalogRequestQueryTest {
  @Mock JdbcTemplate jdbc;

  @Test void listAlwaysScopesByOrganizationAndAuthenticatedRequester() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    var result = new CatalogRequestQuery(jdbc, new ObjectMapper()).listMine("alice", -2, 1000);
    assertThat(result).isEmpty();
    verify(jdbc).query(anyString(), any(RowMapper.class), eq("default"), eq("alice"), eq(100), eq(0));
  }

  @Test void detailAlwaysScopesByOrganizationAndAuthenticatedRequester() {
    UUID id = UUID.randomUUID();
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    assertThat(new CatalogRequestQuery(jdbc, new ObjectMapper()).findMine(id, "bob")).isEmpty();
    verify(jdbc).query(anyString(), any(RowMapper.class), eq(id), eq("default"), eq("bob"));
  }

  @Test void operationsQueueAlwaysScopesByOrganization() {
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
    assertThat(new CatalogRequestQuery(jdbc, new ObjectMapper()).listOperations(0, 500)).isEmpty();
    verify(jdbc).query(anyString(), any(RowMapper.class), eq("default"), eq(100), eq(0));
  }
}
