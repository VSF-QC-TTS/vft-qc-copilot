package vn.vinfast.vfqc.api.exportjob.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.exportjob.dto.ExportJobResponse;

public interface ExportJobQueryService {

  /**
   * Retrieves the metadata details of a specific export job if user has access.
   *
   * @param user the current authenticated user
   * @param jobId the unique identifier of the export job
   * @return the export job response details
   */
  ExportJobResponse get(CurrentUser user, UUID jobId);
}
