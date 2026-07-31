package ru.ultimavox.itsm.platform.localization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TranslationAdminServiceTest {

  private JdbcTemplate jdbc;
  private TranslationAdminService service;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    service = new TranslationAdminService(jdbc);
  }

  @Test
  void list_applies_namespace_and_locale_filters() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-15T10:00:00Z");
    when(jdbc.query(anyString(), any(RowMapper.class), eq("object.work-item"), eq("ru")))
        .thenReturn(List.of(new TranslationAdminService.TranslationEntry(
            id, "object.work-item", "label", "ru", "Рабочий элемент", 1, now
        )));

    List<TranslationAdminService.TranslationEntry> rows =
        service.list("object.work-item", "ru");

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().value()).isEqualTo("Рабочий элемент");
    verify(jdbc).query(anyString(), any(RowMapper.class), eq("object.work-item"), eq("ru"));
  }

  @Test
  void upsert_rejects_blank_namespace() {
    assertThatThrownBy(() -> service.upsert(" ", "k", "en", "v"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("namespace");
  }

  @Test
  void upsert_persists_and_reloads() {
    UUID id = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-15T12:00:00Z");
    when(jdbc.update(anyString(), eq("ui"), eq("save"), eq("en"), eq("Save"))).thenReturn(1);
    when(jdbc.query(anyString(), any(RowMapper.class), eq("ui"), eq("save"), eq("en")))
        .thenReturn(List.of(new TranslationAdminService.TranslationEntry(
            id, "ui", "save", "en", "Save", 2, now
        )));

    TranslationAdminService.TranslationEntry result =
        service.upsert("ui", "save", "en", "Save");

    assertThat(result.key()).isEqualTo("save");
    assertThat(result.version()).isEqualTo(2);
    assertThat(result.updatedAt()).isEqualTo(now);
  }
}
