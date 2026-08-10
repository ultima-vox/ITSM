package ru.ultimavox.itsm.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.platform.sla.SlaReportQuery;
import ru.ultimavox.itsm.servicedesk.ServiceDeskReportQuery;

class WorkloadReportServiceTest {
  @Test
  void composesPublicModuleSnapshotsWithoutDatabaseAccess() {
    ServiceDeskReportQuery serviceDesk = mock(ServiceDeskReportQuery.class);
    SlaReportQuery sla = mock(SlaReportQuery.class);
    when(serviceDesk.snapshot()).thenReturn(new ServiceDeskReportQuery.Snapshot(
        12, 5, 3, 8.4, Map.of("HIGH", 4L), Map.of("NEW", 6L),
        Map.of("INCIDENT", 9L), Map.of("0_1d", 7L)));
    when(sla.snapshot()).thenReturn(new SlaReportQuery.Snapshot(2, 1));

    var report = new WorkloadReportService(serviceDesk, sla).snapshot();

    assertThat(report.open()).isEqualTo(12);
    assertThat(report.breached()).isEqualTo(2);
    assertThat(report.byPriority()).containsEntry("HIGH", 4L);
    assertThat(report.source()).isEqualTo("module-contracts");
  }
}
