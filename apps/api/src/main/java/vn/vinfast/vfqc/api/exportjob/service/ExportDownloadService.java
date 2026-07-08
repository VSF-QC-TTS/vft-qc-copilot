package vn.vinfast.vfqc.api.exportjob.service;

import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.exportjob.model.ExportDownload;

public interface ExportDownloadService {

  /**
   * Downloads the export file resource for the specified export job after validating access permissions.
   *
   * @param user the current authenticated user requesting the download
   * @param jobId the unique identifier of the export job
   * @return export download model containing the resource, file name, content type, and size
   */
  ExportDownload download(CurrentUser user, UUID jobId);
}
