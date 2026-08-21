package ru.ultimavox.itsm.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.ultimavox.itsm.assetmanagement.AssetReportQuery;
import ru.ultimavox.itsm.changemanagement.ChangeReportQuery;
import ru.ultimavox.itsm.cmdb.CmdbReportQuery;
import ru.ultimavox.itsm.platform.sla.SlaReportQuery;
import ru.ultimavox.itsm.problemmanagement.ProblemReportQuery;
import ru.ultimavox.itsm.servicedesk.ServiceDeskReportQuery;

class WorkloadReportServiceTest {
  @Test
  void composesPublicModuleSnapshotsWithoutDatabaseAccess() {
    ServiceDeskReportQuery serviceDesk = mock(ServiceDeskReportQuery.class);
    SlaReportQuery sla = mock(SlaReportQuery.class);
    ChangeReportQuery changes = mock(ChangeReportQuery.class);
    ProblemReportQuery problems = mock(ProblemReportQuery.class);
    CmdbReportQuery cmdb = mock(CmdbReportQuery.class);
    AssetReportQuery assets = mock(AssetReportQuery.class);
    when(serviceDesk.snapshot()).thenReturn(new ServiceDeskReportQuery.Snapshot(
        12, 5, 3, 8.4, Map.of("HIGH", 4L), Map.of("NEW", 6L),
        Map.of("INCIDENT", 9L), Map.of("0_1d", 7L)));
    when(sla.snapshot()).thenReturn(new SlaReportQuery.Snapshot(2, 1));
    when(changes.snapshot()).thenReturn(new ChangeReportQuery.Snapshot(4, 8, 2, 80.0));
    when(problems.snapshot()).thenReturn(new ProblemReportQuery.Snapshot(3, 1, 6));
    when(cmdb.snapshot()).thenReturn(new CmdbReportQuery.Snapshot(40, 5, 22));
    when(assets.snapshot()).thenReturn(new AssetReportQuery.Snapshot(18, 11, 4));

    var report = new WorkloadReportService(serviceDesk, sla, changes, problems, cmdb, assets).snapshot();

    assertThat(report.open()).isEqualTo(12);
    assertThat(report.breached()).isEqualTo(2);
    assertThat(report.byPriority()).containsEntry("HIGH", 4L);
    assertThat(report.source()).isEqualTo("module-contracts");
    assertThat(report.change().successRate()).isEqualTo(80.0);
    assertThat(report.problem().knownErrors()).isEqualTo(1);
    assertThat(report.cmdb().orphans()).isEqualTo(5);
    assertThat(report.assets().inUse()).isEqualTo(11);
  }
}
