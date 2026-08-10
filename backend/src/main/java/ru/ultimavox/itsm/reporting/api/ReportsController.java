package ru.ultimavox.itsm.reporting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ultimavox.itsm.platform.authorization.AccessControl;
import ru.ultimavox.itsm.reporting.WorkloadReportService;
import ru.ultimavox.itsm.reporting.WorkloadReportService.WorkloadReport;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reporting")
class ReportsController {
  private final WorkloadReportService reports;
  private final AccessControl access;

  ReportsController(WorkloadReportService reports, AccessControl access) {
    this.reports = reports;
    this.access = access;
  }

  @GetMapping("/workload")
  @Operation(summary = "Workload and SLA snapshot")
  WorkloadReport workload(Authentication authentication) {
    access.require(authentication.getName(), "work-item.read", "work-item", null);
    return reports.snapshot();
  }
}
