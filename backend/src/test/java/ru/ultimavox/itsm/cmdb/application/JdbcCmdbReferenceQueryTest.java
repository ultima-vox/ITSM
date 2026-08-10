package ru.ultimavox.itsm.cmdb.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcCmdbReferenceQueryTest {
  @Test
  void answersExistenceInsideCmdbBoundary() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID id = UUID.randomUUID();
    when(jdbc.queryForObject(
        "SELECT COUNT(*) FROM configuration_item WHERE id = ?", Integer.class, id))
        .thenReturn(1);

    assertThat(new JdbcCmdbReferenceQuery(jdbc).exists(id)).isTrue();
  }
}
