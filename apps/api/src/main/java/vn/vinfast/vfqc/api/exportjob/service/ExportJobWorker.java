package vn.vinfast.vfqc.api.exportjob.service;

import java.util.UUID;

public interface ExportJobWorker {

  /**
   * Executes the background file export processing for the specified job.
   *
   * @param jobId the unique identifier of the export job to execute
   */
  void run(UUID jobId);
}
