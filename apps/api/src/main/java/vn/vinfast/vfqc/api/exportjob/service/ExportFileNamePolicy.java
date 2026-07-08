package vn.vinfast.vfqc.api.exportjob.service;

import vn.vinfast.vfqc.api.exportjob.entity.ExportJob;

public interface ExportFileNamePolicy {

  /**
   * Generates a file name for the given export job.
   *
   * @param job the export job to generate file name for
   * @return the generated file name string
   */
  String fileNameFor(ExportJob job);

  /**
   * Generates the storage path for storing the exported file.
   *
   * @param job the export job associated with the file
   * @param fileName the name of the file to store
   * @return the generated relative storage path
   */
  String storagePathFor(ExportJob job, String fileName);
}
