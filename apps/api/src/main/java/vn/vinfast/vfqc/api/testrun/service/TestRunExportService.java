package vn.vinfast.vfqc.api.testrun.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;
import vn.vinfast.vfqc.api.exportjob.model.ExportResultModel;

public interface TestRunExportService {

  /**
   * Verifies if the current user has permission to export the specified test run results.
   *
   * @param user the current authenticated user
   * @param testRunId the unique identifier of the test run to check
   */
  void requireCanExport(CurrentUser user, UUID testRunId);

  /**
   * Builds the export results model for the specified test run and result mode.
   *
   * @param testRunId the unique identifier of the test run to export
   * @param resultMode the export result mode
   * @return the constructed export results model containing targets and test cases
   */
  ExportResultModel buildExport(UUID testRunId, ExportResultMode resultMode);
}
