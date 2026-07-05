package vn.vinfast.vfqc.api.dataset.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.CreateDatasetRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetResponse;
import vn.vinfast.vfqc.api.dataset.dto.DatasetVersionResponse;
import vn.vinfast.vfqc.api.dataset.dto.UpdateDatasetRequest;

public interface DatasetService {

  /**
   * Lists project datasets visible to the current user.
   *
   * @param user authenticated user
   * @param projectId project id
   * @return dataset summaries
   */
  List<DatasetResponse> list(CurrentUser user, UUID projectId);

  /**
   * Creates a PROJECT-scoped dataset with an initial editable DRAFT version.
   *
   * @param user authenticated creator
   * @param projectId project id
   * @param request dataset creation request
   * @return created dataset summary
   */
  DatasetResponse create(CurrentUser user, UUID projectId, CreateDatasetRequest request);

  /**
   * Loads a dataset after project permission checks.
   *
   * @param user authenticated user
   * @param datasetId dataset id
   * @return dataset summary
   */
  DatasetResponse get(CurrentUser user, UUID datasetId);

  /**
   * Updates dataset metadata without mutating dataset rows or versions.
   *
   * @param user authenticated user with edit permission
   * @param datasetId dataset id
   * @param request partial metadata update
   * @return updated dataset summary
   */
  DatasetResponse update(CurrentUser user, UUID datasetId, UpdateDatasetRequest request);

  /**
   * Soft-deletes a dataset.
   *
   * @param user authenticated user with edit permission
   * @param datasetId dataset id
   */
  void delete(CurrentUser user, UUID datasetId);

  /**
   * Lists versions for a dataset.
   *
   * @param user authenticated user with view permission
   * @param datasetId dataset id
   * @return dataset version summaries
   */
  List<DatasetVersionResponse> versions(CurrentUser user, UUID datasetId);
}
