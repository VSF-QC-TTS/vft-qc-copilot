package vn.vinfast.vfqc.api.evaluator.service.impl;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaRepository;
import vn.vinfast.vfqc.api.evaluator.dto.EvaluatorFieldSourceResponse;
import vn.vinfast.vfqc.api.evaluator.service.EvaluatorFieldSourceService;
import vn.vinfast.vfqc.api.target.assembler.ChatbotTargetResponseAssembler;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluatorFieldSourceServiceImpl implements EvaluatorFieldSourceService {

  private final ProjectPermissionService projectPermissionService;
  private final DatasetSchemaRepository schemaRepository;
  private final DatasetSchemaColumnRepository columnRepository;
  private final ChatbotTargetRepository targetRepository;
  private final ChatbotTargetResponseAssembler targetAssembler;

  @Override
  @Transactional(readOnly = true)
  public EvaluatorFieldSourceResponse list(CurrentUser user, UUID projectId, UUID chatbotTargetId) {
    projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    log.info("Listing evaluator field sources: projectId={}, chatbotTargetId={}", projectId, chatbotTargetId);
    var datasetVariables =
        schemaRepository.findTopByProjectIdOrderByVersionDesc(projectId).stream()
            .flatMap(schema -> columnRepository.findBySchemaIdOrderByOrderIndexAsc(schema.getId()).stream())
            .map(
                column ->
                    new EvaluatorFieldSourceResponse.DatasetVariable(
                        "$dataset." + column.getName(),
                        column.getName(),
                        column.getDisplayName(),
                        column.getDataType().name(),
                        column.isRequired()))
            .toList();
    var extractedFields =
        chatbotTargetId == null ? List.<EvaluatorFieldSourceResponse.ExtractedField>of() : extractedFields(projectId, chatbotTargetId);
    return new EvaluatorFieldSourceResponse(
        datasetVariables,
        extractedFields,
        List.of(
            new EvaluatorFieldSourceResponse.ResponseField(
                "response.statusCode", "HTTP status code", "NUMBER"),
            new EvaluatorFieldSourceResponse.ResponseField("latencyMs", "Latency in milliseconds", "NUMBER")));
  }

  private List<EvaluatorFieldSourceResponse.ExtractedField> extractedFields(
      UUID projectId, UUID chatbotTargetId) {
    var target =
        targetRepository
            .findByIdAndDeletedAtIsNull(chatbotTargetId)
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (!projectId.equals(target.getProjectId())) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    var config = targetAssembler.toExtractionConfig(target.getResponseExtractionConfigJson());
    if (config == null) {
      return List.of();
    }
    return config.safeFields().stream()
        .map(
            field ->
                new EvaluatorFieldSourceResponse.ExtractedField(
                    "extracted." + field.fieldName(), field.fieldName(), "TEXT", field.path()))
        .toList();
  }
}
