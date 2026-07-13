package vn.vinfast.vfqc.api.dataset.service;

import java.util.UUID;
import org.springframework.data.domain.Pageable;
import vn.vinfast.vfqc.api.common.api.CollectionResponse;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.dataset.dto.CreateDatasetRowRequest;
import vn.vinfast.vfqc.api.dataset.dto.DatasetRowResponse;
import vn.vinfast.vfqc.api.dataset.dto.PatchDatasetRowCellsRequest;

public interface DatasetRowService {

  /**
   * Lists rows for a dataset version.
   *
   * @param user authenticated user with view permission
   * @param versionId dataset version id
   * @param pageable page request for large dataset versions
   * @return ordered dataset rows with page metadata
   */
  CollectionResponse<DatasetRowResponse> rows(CurrentUser user, UUID versionId, Pageable pageable);

  /**
   * Adds a row to an editable DRAFT dataset version.
   *
   * @param user authenticated user with edit permission
   * @param versionId draft version id
   * @param request row cells
   * @return created row summary
   */
  DatasetRowResponse addRow(CurrentUser user, UUID versionId, CreateDatasetRowRequest request);

  /**
   * Replaces cells for a row in an editable DRAFT dataset version.
   *
   * @param user authenticated user with edit permission
   * @param rowId dataset row id
   * @param request replacement cells
   * @return updated row summary
   */
  DatasetRowResponse patchRow(CurrentUser user, Long rowId, PatchDatasetRowCellsRequest request);
}
