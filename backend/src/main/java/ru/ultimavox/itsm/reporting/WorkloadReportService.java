package ru.ultimavox.itsm.reporting;

import java.util.Map;
import org.springframework.stereotype.Service;
import ru.ultimavox.itsm.assetmanagement.AssetReportQuery;
import ru.ultimavox.itsm.changemanagement.ChangeReportQuery;
import ru.ultimavox.itsm.cmdb.CmdbReportQuery;
import ru.ultimavox.itsm.platform.sla.SlaReportQuery;
import ru.ultimavox.itsm.problemmanagement.ProblemReportQuery;
import ru.ultimavox.itsm.releasemanagement.ReleaseReportQuery;
import ru.ultimavox.itsm.servicedesk.ServiceDeskReportQuery;
import ru.ultimavox.itsm.servicedesk.WorkItemEffortQuery;

@Service
public class WorkloadReportService {
  private final ServiceDeskReportQuery serviceDesk;
  private final SlaReportQuery sla;
  private final ChangeReportQuery changes;
  private final ProblemReportQuery problems;
  private final CmdbReportQuery cmdb;
  private final AssetReportQuery assets;
  private final ReleaseReportQuery releases;
  private final WorkItemEffortQuery effort;

  public WorkloadReportService(
      ServiceDeskReportQuery serviceDesk,
      SlaReportQuery sla,
      ChangeReportQuery changes,
      ProblemReportQuery problems,
      CmdbReportQuery cmdb,
      AssetReportQuery assets,
      ReleaseReportQuery releases,
      WorkItemEffortQuery effort
  ) {
    this.serviceDesk = serviceDesk;
    this.sla = sla;
    this.changes = changes;
    this.problems = problems;
    this.cmdb = cmdb;
    this.assets = assets;
    this.releases = releases;
    this.effort = effort;
  }

  public WorkloadReport snapshot() {
    ServiceDeskReportQuery.Snapshot work = serviceDesk.snapshot();
    SlaReportQuery.Snapshot clocks = sla.snapshot();
    ChangeReportQuery.Snapshot change = changes.snapshot();
    ProblemReportQuery.Snapshot problem = problems.snapshot();
    CmdbReportQuery.Snapshot configuration = cmdb.snapshot();
    AssetReportQuery.Snapshot asset = assets.snapshot();
    ReleaseReportQuery.Snapshot release = releases.snapshot();
    WorkItemEffortQuery.Snapshot logged = effort.snapshot();
    return new WorkloadReport(
        work.open(), work.resolved(), work.unassigned(), clocks.breached(), clocks.atRisk(),
        work.mttrHours(), work.byPriority(), work.byState(), work.byType(),
        work.agingBuckets(), "module-contracts",
        new ChangeSnapshot(change.open(), change.closed(), change.rejected(), change.successRate()),
        new ProblemSnapshot(problem.open(), problem.knownErrors(), problem.resolved()),
        new CmdbSnapshot(configuration.configurationItems(), configuration.orphans(), configuration.relationships()),
        new AssetSnapshot(asset.total(), asset.inUse(), asset.inStock()),
        new ReleaseSnapshot(release.inFlight(), release.deployed(), release.rolledBack(),
            release.successRate()),
        new EffortSnapshot(logged.entries(), logged.totalMinutes(), logged.billableMinutes(),
            logged.itemsWithEffort()));
  }

  public record ChangeSnapshot(long open, long closed, long rejected, Double successRate) {}

  public record ProblemSnapshot(long open, long knownErrors, long resolved) {}

  public record CmdbSnapshot(long configurationItems, long orphans, long relationships) {}

  public record AssetSnapshot(long total, long inUse, long inStock) {}

  public record ReleaseSnapshot(long inFlight, long deployed, long rolledBack, Double successRate) {}

  public record EffortSnapshot(
      long entries, long totalMinutes, long billableMinutes, long itemsWithEffort) {}

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
      String source,
      ChangeSnapshot change,
      ProblemSnapshot problem,
      CmdbSnapshot cmdb,
      AssetSnapshot assets,
      ReleaseSnapshot releases,
      EffortSnapshot effort
  ) {}
}
