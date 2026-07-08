package vn.vinfast.vfqc.api.exportjob.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.exportjob.dto.ExportJobResponse;
import vn.vinfast.vfqc.api.exportjob.enums.ExportFormat;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;

public interface ExportJobService {

  /**
   * Enqueues a new background job to export a dataset version.
   *
   * @param user the current authenticated user requesting the export
   * @param datasetVersionId the unique identifier of the dataset version to export
   * @param format the target export format (e.g. XLSX)
   * @return the created export job metadata response
   */
  ExportJobResponse createDatasetExport(CurrentUser user, UUID datasetVersionId, ExportFormat format);

  /**
   * Enqueues a new background job to export test run results.
   *
   * @param user the current authenticated user requesting the export
   * @param testRunId the unique identifier of the test run to export
   * @param format the target export format (e.g. XLSX)
   * @param resultMode the export result mode (e.g. final reviews or raw results)
   * @return the created export job metadata response
   */
  ExportJobResponse createTestRunExport(
      CurrentUser user, UUID testRunId, ExportFormat format, ExportResultMode resultMode);
}
