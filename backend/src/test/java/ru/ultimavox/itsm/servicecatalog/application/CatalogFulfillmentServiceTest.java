package ru.ultimavox.itsm.servicecatalog.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import ru.ultimavox.itsm.platform.audit.AuditTrail;

@ExtendWith(MockitoExtension.class)
class CatalogFulfillmentServiceTest {
  @Mock JdbcTemplate jdbc;
  @Mock AuditTrail audit;
  private final UUID requestId = UUID.randomUUID();
  private final UUID childId = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-08-11T00:00:00Z");

  @Test void approvalDecisionIsImmutable() {
    var decided = new CatalogFulfillmentService.ApprovalView(
        childId, "SERVICE_DESK_MANAGER", "APPROVED", "manager", now, null, now);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(decided));

    assertThatThrownBy(() -> service().decide(requestId, childId,
        CatalogFulfillmentService.Decision.REJECTED, null, "other-manager"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("already decided");
    verify(audit, never()).append(any());
  }

  @Test void terminalFulfillmentTaskCannotBeReopened() {
    var completed = new CatalogFulfillmentService.TaskView(
        childId, "Fulfill laptop", "COMPLETED", "agent", now, now);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(completed));

    assertThatThrownBy(() -> service().updateTask(requestId, childId,
        CatalogFulfillmentService.TaskState.IN_PROGRESS, "agent", "agent"))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Terminal task");
    verify(audit, never()).append(any());
  }

  private CatalogFulfillmentService service() { return new CatalogFulfillmentService(jdbc, audit); }
}
