package vn.vinfast.vfqc.api.exportjob.model;

import java.math.BigDecimal;

public record ExportResultRowModel(
    int datasetRowIndex,
    String targetName,
    String machineStatus,
    BigDecimal machineScore,
    String finalStatus,
    BigDecimal finalScore,
    Boolean passed,
    String errorCategory,
    String machineSummary,
    String reviewStatus,
    String reviewNote,
    String extractedOutput,
    Integer latencyMs) {}
