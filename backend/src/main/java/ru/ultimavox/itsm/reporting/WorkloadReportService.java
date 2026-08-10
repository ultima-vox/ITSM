package ru.ultimavox.itsm.reporting;

import java.util.Map;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.platform.sla.SlaReportQuery;
import ru.ultimavox.itsm.servicedesk.ServiceDeskReportQuery;

@Service
public class WorkloadReportService {
  private final ServiceDeskReportQuery serviceDesk;
  private final SlaReportQuery sla;

  public WorkloadReportService(ServiceDeskReportQuery serviceDesk, SlaReportQuery sla) {
    this.serviceDesk = serviceDesk;
    this.sla = sla;
  }

  public WorkloadReport snapshot() {
    ServiceDeskReportQuery.Snapshot work = serviceDesk.snapshot();
    SlaReportQuery.Snapshot clocks = sla.snapshot();
    return new WorkloadReport(
        work.open(), work.resolved(), work.unassigned(), clocks.breached(), clocks.atRisk(),
        work.mttrHours(), work.byPriority(), work.byState(), work.byType(),
        work.agingBuckets(), "module-contracts");
  }

  public record WorkloadReport(
      long open,
      long resolved,
      long unassigned,
      long breached,
      long atRisk,
      Double mttrHours,
      Map<String, Long> byPriority,
      Map<String, Long> byState,
      Map<String, Long> byType,
      Map<String, Long> agingBuckets,
      String source
  ) {}
}
