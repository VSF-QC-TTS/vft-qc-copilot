package vn.vinfast.vfqc.api.testrun.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.api.PageInfo;
import vn.vinfast.vfqc.api.testrun.enums.TestCaseResultStatus;

public record ComparisonMatrixResponse(
    List<Column> columns,
    List<Row> rows,
    PageInfo page) {

  public record Column(UUID testRunTargetId, String name, int orderIndex) {}

  public record Row(Long datasetRowId, int rowIndex, List<Cell> cells) {}

  public record Cell(
      UUID testRunTargetId,
      Long resultId,
      TestCaseResultStatus finalStatus,
      BigDecimal finalScore,
      Boolean finalPassed,
      String errorCategory,
      boolean reviewed) {}
}
