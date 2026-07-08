package vn.vinfast.vfqc.api.exportjob.model;

import java.util.List;

public record ExportDatasetModel(String datasetName, List<String> headers, List<ExportDatasetRowModel> rows) {}
