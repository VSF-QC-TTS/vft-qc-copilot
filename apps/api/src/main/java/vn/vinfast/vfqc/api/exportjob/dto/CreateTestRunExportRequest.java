package vn.vinfast.vfqc.api.exportjob.dto;

import vn.vinfast.vfqc.api.exportjob.enums.ExportFormat;
import vn.vinfast.vfqc.api.exportjob.enums.ExportResultMode;

public record CreateTestRunExportRequest(ExportFormat format, ExportResultMode resultMode) {

  public ExportFormat safeFormat() {
    return format == null ? ExportFormat.XLSX : format;
  }

  public ExportResultMode safeResultMode() {
    return resultMode == null ? ExportResultMode.FINAL : resultMode;
  }
}
