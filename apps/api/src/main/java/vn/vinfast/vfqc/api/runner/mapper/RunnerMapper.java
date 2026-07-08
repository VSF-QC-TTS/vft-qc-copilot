package vn.vinfast.vfqc.api.runner.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import vn.vinfast.vfqc.api.dataset.entity.DatasetRow;
import vn.vinfast.vfqc.api.evaluator.enums.EvaluatorType;
import vn.vinfast.vfqc.api.runner.dto.RunnerInstanceResponse;
import vn.vinfast.vfqc.api.runner.dto.RunnerTestRunConfigResponse.DatasetRowSnapshot;
import vn.vinfast.vfqc.api.runner.dto.RunnerTestRunConfigResponse.EvaluatorSnapshot;
import vn.vinfast.vfqc.api.runner.dto.RunnerTestRunConfigResponse.TargetSnapshot;
import vn.vinfast.vfqc.api.runner.entity.RunnerInstance;
import vn.vinfast.vfqc.api.testrun.entity.TestRunEvaluatorVersion;
import vn.vinfast.vfqc.api.testrun.entity.TestRunTarget;

@Mapper(componentModel = "spring")
public interface RunnerMapper {

  RunnerInstanceResponse toResponse(RunnerInstance runner);

  @Mapping(target = "config", source = "configJson")
  TargetSnapshot toTargetSnapshot(TestRunTarget target);

  @Mapping(target = "cells", source = "cellsJson")
  DatasetRowSnapshot toRowSnapshot(DatasetRow row);

  @Mapping(target = "config", source = "configJson")
  @Mapping(target = "rules", source = "rulesJson")
  @Mapping(target = "evaluatorType", source = "evaluatorType", qualifiedByName = "enumToString")
  EvaluatorSnapshot toEvaluatorSnapshot(TestRunEvaluatorVersion evaluator);

  @Named("enumToString")
  default String enumToString(EvaluatorType type) {
    return type == null ? null : type.name();
  }
}
