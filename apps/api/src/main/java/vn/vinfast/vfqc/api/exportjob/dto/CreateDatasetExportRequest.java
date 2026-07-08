package vn.vinfast.vfqc.api.exportjob.dto;

import vn.vinfast.vfqc.api.exportjob.enums.ExportFormat;

public record CreateDatasetExportRequest(ExportFormat format) {

  public ExportFormat safeFormat() {
    return format == null ? ExportFormat.XLSX : format;
  }
}
