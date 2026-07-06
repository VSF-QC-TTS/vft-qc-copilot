package vn.vinfast.vfqc.api.project.service.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.enums.DatasetVersionStatus;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.project.assembler.ProjectSetupStatusAssembler;
import vn.vinfast.vfqc.api.project.dto.ProjectSetupStatusResponse;
import vn.vinfast.vfqc.api.project.service.ProjectSetupStatusService;
import vn.vinfast.vfqc.api.target.enums.ChatbotTargetStatus;
import vn.vinfast.vfqc.api.target.repository.ChatbotTargetRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectSetupStatusServiceImpl implements ProjectSetupStatusService {

  private final ProjectPermissionService projectPermissionService;
  private final DatasetRepository datasetRepository;
  private final DatasetVersionRepository datasetVersionRepository;
  private final ChatbotTargetRepository chatbotTargetRepository;
  private final ProjectSetupStatusAssembler assembler;

  @Transactional(readOnly = true)
  @Override
  public ProjectSetupStatusResponse get(CurrentUser user, UUID projectId) {
    var project = projectPermissionService.requireProject(user, projectId, PermissionAction.VIEW);
    var datasetCount = datasetRepository.countByProjectIdAndDeletedAtIsNull(projectId);
    var activeVersions =
        datasetVersionRepository.countActiveVersionsByProject(
            projectId, DatasetVersionStatus.ACTIVE);
    var activeTargets =
        chatbotTargetRepository.countByProjectIdAndStatusAndDeletedAtIsNull(
            projectId, ChatbotTargetStatus.ACTIVE);
    var activeTargetsWithExtraction =
        chatbotTargetRepository.countWithExtractionByProjectIdAndStatus(
            projectId, ChatbotTargetStatus.ACTIVE);
    log.debug(
        "Project setup status calculated: projectId={} datasetCount={} activeDatasetVersions={} activeTargets={}",
        projectId,
        datasetCount,
        activeVersions,
        activeTargets);
    return assembler.toResponse(project, datasetCount, activeVersions, activeTargets, activeTargetsWithExtraction);
  }
}
