package vn.vinfast.vfqc.api.dataset.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.exportjob.model.ExportDatasetModel;

public interface DatasetExportService {

  /**
   * Verifies if the current user has permission to export the specified dataset version.
   *
   * @param user the current authenticated user
   * @param datasetVersionId the unique identifier of the dataset version to check
   */
  void requireCanExport(CurrentUser user, UUID datasetVersionId);

  /**
   * Builds the export data model for the specified dataset version.
   *
   * @param datasetVersionId the unique identifier of the dataset version to export
   * @return the constructed export data model containing headers and rows
   */
  ExportDatasetModel buildExport(UUID datasetVersionId);
}
