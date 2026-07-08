package vn.vinfast.vfqc.api.exportjob.service;

import vn.vinfast.vfqc.api.exportjob.model.ExportDatasetModel;
import vn.vinfast.vfqc.api.exportjob.model.ExportFileWriteResult;
import vn.vinfast.vfqc.api.exportjob.model.ExportResultModel;

public interface ExportFileWriter {

  /**
   * Writes the dataset export model to a binary output format (such as Excel).
   *
   * @param dataset the dataset export model containing headers and rows
   * @return the write result containing raw bytes and content type
   */
  ExportFileWriteResult writeDataset(ExportDatasetModel dataset);

  /**
   * Writes the test run results export model to a binary output format (such as Excel).
   *
   * @param results the test run results export model containing test cases
   * @return the write result containing raw bytes and content type
   */
  ExportFileWriteResult writeResults(ExportResultModel results);
}
