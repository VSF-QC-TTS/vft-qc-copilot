package vn.vinfast.vfqc.api.dataset.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vinfast.vfqc.api.common.error.ErrorCode;
import vn.vinfast.vfqc.api.common.permission.PermissionAction;
import vn.vinfast.vfqc.api.common.permission.ProjectPermissionService;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetRowRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetSchemaColumnRepository;
import vn.vinfast.vfqc.api.dataset.repository.DatasetVersionRepository;
import vn.vinfast.vfqc.api.dataset.service.DatasetExportService;
import vn.vinfast.vfqc.api.exportjob.model.ExportDatasetModel;
import vn.vinfast.vfqc.api.exportjob.model.ExportDatasetRowModel;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetExportServiceImpl implements DatasetExportService {

  private static final int PAGE_SIZE = 500;

  private final DatasetVersionRepository versionRepository;
  private final DatasetRepository datasetRepository;
  private final DatasetSchemaColumnRepository columnRepository;
  private final DatasetRowRepository rowRepository;
  private final ProjectPermissionService projectPermissionService;

  @Override
  @Transactional(readOnly = true)
  public void requireCanExport(CurrentUser user, UUID datasetVersionId) {
    log.info("Checking export permission for datasetVersion={}", datasetVersionId);
    var version =
        versionRepository.findById(datasetVersionId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(version.getDatasetId())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    if (dataset.getProjectId() == null) {
      throw ErrorCode.RESOURCE_NOT_FOUND.throwIt();
    }
    projectPermissionService.requireProject(user, dataset.getProjectId(), PermissionAction.VIEW);
  }

  @Override
  @Transactional(readOnly = true)
  public ExportDatasetModel buildExport(UUID datasetVersionId) {
    log.info("Building dataset export for version={}", datasetVersionId);
    var version =
        versionRepository.findById(datasetVersionId).orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var dataset =
        datasetRepository
            .findByIdAndDeletedAtIsNull(version.getDatasetId())
            .orElseThrow(ErrorCode.RESOURCE_NOT_FOUND::throwIt);
    var columns = columnRepository.findBySchemaIdOrderByOrderIndexAsc(version.getSchemaId());
    var headers =
        columns.stream()
            .map(column -> column.getDisplayName() == null || column.getDisplayName().isBlank()
                ? column.getName()
                : column.getDisplayName())
            .toList();
    var rows = new ArrayList<ExportDatasetRowModel>();
    Pageable pageable = PageRequest.of(0, PAGE_SIZE);
    while (true) {
      var page = rowRepository.findByDatasetVersionIdOrderByRowIndexAsc(datasetVersionId, pageable);
      page.forEach(
          row ->
              rows.add(
                  new ExportDatasetRowModel(
                      row.getRowIndex(),
                      columns.stream().map(column -> row.getCellsJson().get(column.getName())).toList())));
      if (!page.hasNext()) {
        break;
      }
      pageable = page.nextPageable();
    }
    rows.sort(Comparator.comparingInt(ExportDatasetRowModel::rowIndex));
    return new ExportDatasetModel(dataset.getName(), headers, rows);
  }
}
