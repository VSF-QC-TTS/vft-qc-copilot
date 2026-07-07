package vn.vinfast.vfqc.api.dataset.assembler;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetSchemaColumnResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetSchemaResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;
import vn.vinfast.vfqc.api.dataset.entity.Dataset;
import vn.vinfast.vfqc.api.dataset.entity.DatasetSchema;
import vn.vinfast.vfqc.api.dataset.entity.DatasetVersion;
import vn.vinfast.vfqc.api.dataset.mapper.DatasetMapper;
import vn.vinfast.vfqc.api.user.dto.UserSummaryResponse;
import vn.vinfast.vfqc.api.user.service.UserSummaryService;

@Component
@RequiredArgsConstructor
public class DatasetResponseAssembler {

  private final DatasetMapper datasetMapper;
  private final UserSummaryService userSummaryService;

  public DatasetResponse toResponse(Dataset dataset) {
    return withCreatedByUser(datasetMapper.toResponse(dataset), summaryMap(dataset.getCreatedBy()));
  }

  public List<DatasetResponse> toDatasetResponses(List<Dataset> datasets) {
    var usersById =
        userSummaryService.summariesById(datasets.stream().map(Dataset::getCreatedBy).toList());
    return datasets.stream()
        .map(dataset -> withCreatedByUser(datasetMapper.toResponse(dataset), usersById))
        .toList();
  }

  public DatasetVersionResponse toResponse(DatasetVersion version) {
    return withCreatedByUser(datasetMapper.toResponse(version), summaryMap(version.getCreatedBy()));
  }

  public List<DatasetVersionResponse> toVersionResponses(List<DatasetVersion> versions) {
    var usersById =
        userSummaryService.summariesById(versions.stream().map(DatasetVersion::getCreatedBy).toList());
    return versions.stream()
        .map(version -> withCreatedByUser(datasetMapper.toResponse(version), usersById))
        .toList();
  }

  public DatasetSchemaResponse toResponse(
      DatasetSchema schema, List<DatasetSchemaColumnResponse> columns) {
    return withCreatedByUser(
        datasetMapper.toResponse(schema, columns), summaryMap(schema.getCreatedBy()));
  }

  private DatasetResponse withCreatedByUser(
      DatasetResponse response, java.util.Map<UUID, UserSummaryResponse> usersById) {
    return new DatasetResponse(
        response.id(),
        response.scope(),
        response.projectId(),
        response.currentVersionId(),
        response.name(),
        response.description(),
        response.createdBy(),
        usersById.get(response.createdBy()),
        response.createdAt(),
        response.updatedAt());
  }

  private DatasetVersionResponse withCreatedByUser(
      DatasetVersionResponse response, java.util.Map<UUID, UserSummaryResponse> usersById) {
    return new DatasetVersionResponse(
        response.id(),
        response.datasetId(),
        response.version(),
        response.schemaId(),
        response.schemaVersion(),
        response.rowCount(),
        response.status(),
        response.createdBy(),
        usersById.get(response.createdBy()),
        response.createdAt(),
        response.publishedAt(),
        response.discardedAt());
  }

  private DatasetSchemaResponse withCreatedByUser(
      DatasetSchemaResponse response, java.util.Map<UUID, UserSummaryResponse> usersById) {
    return new DatasetSchemaResponse(
        response.id(),
        response.scope(),
        response.projectId(),
        response.version(),
        response.name(),
        response.columns(),
        response.createdBy(),
        usersById.get(response.createdBy()),
        response.createdAt());
  }

  private java.util.Map<UUID, UserSummaryResponse> summaryMap(UUID userId) {
    var summary = userSummaryService.toSummaryById(userId);
    return summary == null ? java.util.Map.of() : java.util.Map.of(userId, summary);
  }
}
