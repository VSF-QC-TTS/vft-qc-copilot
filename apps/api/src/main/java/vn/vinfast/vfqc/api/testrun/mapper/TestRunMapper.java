package vn.vinfast.vfqc.api.testrun.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vinfast.vfqc.api.testrun.dto.TestRunEvaluatorVersionResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunSummaryResponse;
import vn.vinfast.vfqc.api.testrun.dto.TestRunTargetResponse;
import vn.vinfast.vfqc.api.testrun.entity.TestRun;
import vn.vinfast.vfqc.api.testrun.entity.TestRunEvaluatorVersion;
import vn.vinfast.vfqc.api.testrun.entity.TestRunTarget;

@Mapper(componentModel = "spring")
public interface TestRunMapper {

  @Mapping(target = "summaryMetrics", source = "summaryMetricsJson")
  TestRunSummaryResponse toSummary(TestRun run);

  @Mapping(target = "config", source = "configJson")
  TestRunTargetResponse toTarget(TestRunTarget target);

  @Mapping(target = "config", source = "configJson")
  @Mapping(target = "rules", source = "rulesJson")
  TestRunEvaluatorVersionResponse toEvaluator(TestRunEvaluatorVersion evaluator);
}
