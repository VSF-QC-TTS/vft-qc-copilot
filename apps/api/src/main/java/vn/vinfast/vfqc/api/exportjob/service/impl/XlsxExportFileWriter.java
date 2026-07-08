package vn.vinfast.vfqc.api.exportjob.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.masking.FormulaInjectionSanitizer;
import vn.vinfast.vfqc.api.common.masking.VfqcSensitiveDataMasker;
import vn.vinfast.vfqc.api.exportjob.model.ExportDatasetModel;
import vn.vinfast.vfqc.api.exportjob.model.ExportFileWriteResult;
import vn.vinfast.vfqc.api.exportjob.model.ExportResultModel;
import vn.vinfast.vfqc.api.exportjob.service.ExportFileWriter;

@Slf4j
@Component
@RequiredArgsConstructor
public class XlsxExportFileWriter implements ExportFileWriter {

  private static final String CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final FormulaInjectionSanitizer formulaInjectionSanitizer;
  private final VfqcSensitiveDataMasker masker;

  @Override
  public ExportFileWriteResult writeDataset(ExportDatasetModel dataset) {
    log.info("Writing dataset XLSX file: name={}, rowCount={}", dataset.datasetName(), dataset.rows().size());
    try (var workbook = new SXSSFWorkbook(100);
        var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Dataset");
      writeRow(sheet.createRow(0), dataset.headers());
      var rowIndex = 1;
      for (var row : dataset.rows()) {
        writeRow(sheet.createRow(rowIndex++), row.values());
      }
      workbook.write(out);
      return new ExportFileWriteResult(out.toByteArray(), CONTENT_TYPE);
    } catch (IOException exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Export file could not be written");
    }
  }

  @Override
  public ExportFileWriteResult writeResults(ExportResultModel results) {
    log.info("Writing test run results XLSX file: name={}, rowCount={}", results.testRunName(), results.rows().size());
    try (var workbook = new SXSSFWorkbook(100);
        var out = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Results");
      writeRow(
          sheet.createRow(0),
          List.of(
              "datasetRowIndex",
              "target",
              "machineStatus",
              "machineScore",
              "finalStatus",
              "finalScore",
              "passed",
              "errorCategory",
              "machineSummary",
              "reviewStatus",
              "reviewNote",
              "extractedOutput",
              "latencyMs"));
      var rowIndex = 1;
      for (var row : results.rows()) {
        writeRow(
            sheet.createRow(rowIndex++),
            Arrays.asList(
                row.datasetRowIndex(),
                row.targetName(),
                row.machineStatus(),
                row.machineScore(),
                row.finalStatus(),
                row.finalScore(),
                row.passed(),
                row.errorCategory(),
                row.machineSummary(),
                row.reviewStatus(),
                row.reviewNote(),
                row.extractedOutput(),
                row.latencyMs()));
      }
      workbook.write(out);
      return new ExportFileWriteResult(out.toByteArray(), CONTENT_TYPE);
    } catch (IOException exception) {
      throw ErrorCode.INTERNAL_ERROR.throwIt("Export file could not be written");
    }
  }

  private void writeRow(Row row, List<?> values) {
    for (var index = 0; index < values.size(); index++) {
      writeCell(row.createCell(index), values.get(index));
    }
  }

  private void writeCell(Cell cell, Object value) {
    if (value == null) {
      cell.setBlank();
      return;
    }
    if (value instanceof Number number && !(value instanceof BigDecimal)) {
      cell.setCellValue(number.doubleValue());
      return;
    }
    if (value instanceof Boolean booleanValue) {
      cell.setCellValue(booleanValue);
      return;
    }
    var text = value.toString();
    var masked = masker.sanitizeText(text).value();
    cell.setCellValue(formulaInjectionSanitizer.sanitize(masked));
  }
}
