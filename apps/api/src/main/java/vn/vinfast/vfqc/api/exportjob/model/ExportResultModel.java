package vn.vinfast.vfqc.api.exportjob.model;

import java.util.List;

public record ExportResultModel(String testRunName, List<ExportResultRowModel> rows) {}
